from datetime import date, datetime
from decimal import Decimal
from hashlib import sha256
from zoneinfo import ZoneInfo
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from database import engine
from routers.session import require_user_id

router = APIRouter()


class CreateIncomeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    account_id: UUID
    category_id: UUID
    occurred_on: date
    amount: Decimal = Field(gt=0, max_digits=19, decimal_places=2)
    request_id: UUID


class IncomeResponse(BaseModel):
    id: UUID


@router.post("/movimientos/ingresos", status_code=201, response_model=IncomeResponse)
def create_income(income: CreateIncomeRequest, user_id: UUID = Depends(require_user_id)):
    today = datetime.now(ZoneInfo("America/Lima")).date()
    if income.occurred_on > today:
        raise HTTPException(422, "La fecha del ingreso no puede estar en el futuro.")

    payload = f"{income.account_id}|{income.category_id}|{income.occurred_on}|{income.amount:.2f}"
    params = income.model_dump(exclude={"request_id"})
    params.update(user_id=user_id, key=str(income.request_id), hash=sha256(payload.encode()).hexdigest())

    try:
        with engine.begin() as connection:
            connection.execute(text("""
                SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))
            """), {"key": f"{user_id}:{income.request_id}"})
            existing = connection.execute(text("""
                SELECT id, request_hash FROM qori.transactions
                WHERE user_id = :user_id AND idempotency_key = :key
            """), params).mappings().one_or_none()
            if existing:
                if existing["request_hash"] != params["hash"]:
                    raise HTTPException(409, "La confirmación ya se usó con otros datos.")
                return IncomeResponse(id=existing["id"])

            account = connection.execute(text("""
                SELECT opening_on FROM qori.accounts
                WHERE id = :account_id AND user_id = :user_id AND status = 'ACTIVE'
                FOR UPDATE
            """), params).scalar_one_or_none()
            if account is None:
                raise HTTPException(404, "Cuenta activa no disponible.")
            if income.occurred_on < account:
                raise HTTPException(422, "El ingreso no puede ser anterior a la apertura de la cuenta.")

            category = connection.execute(text("""
                SELECT id FROM qori.categories
                WHERE id = :category_id AND status = 'ACTIVE' AND type = 'INCOME'
                  AND (is_system OR user_id = :user_id)
                FOR SHARE
            """), params).scalar_one_or_none()
            if category is None:
                raise HTTPException(422, "Selecciona una categoría de ingreso válida.")

            income_id = connection.execute(text("""
                INSERT INTO qori.transactions
                    (user_id, account_id, category_id, type, amount, occurred_on,
                     status, source, confirmed_at, idempotency_key, request_hash)
                VALUES (:user_id, :account_id, :category_id, 'INCOME', :amount, :occurred_on,
                        'CONFIRMED', 'MANUAL', CURRENT_TIMESTAMP, :key, :hash)
                RETURNING id
            """), params).scalar_one()
            return IncomeResponse(id=income_id)
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudo registrar el ingreso. Intenta nuevamente.") from error


# HU09 - Registrar gasto
# Sigue el mismo formato que el ingreso (HU08): la cuenta y la categoría deben ser del usuario
# autenticado, el importe debe ser mayor que 0 y request_id evita guardar el gasto dos veces
# si Android reintenta por mala conexión.

class CreateExpenseRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    account_id: UUID
    category_id: UUID
    occurred_on: date
    amount: Decimal = Field(gt=0, max_digits=19, decimal_places=2)
    request_id: UUID


class ExpenseResponse(BaseModel):
    id: UUID


@router.post("/movimientos/gastos", status_code=201, response_model=ExpenseResponse)
def create_expense(expense: CreateExpenseRequest, user_id: UUID = Depends(require_user_id)):
    today = datetime.now(ZoneInfo("America/Lima")).date()
    if expense.occurred_on > today:
        raise HTTPException(422, "La fecha del gasto no puede estar en el futuro.")

    payload = f"EXPENSE|{expense.account_id}|{expense.category_id}|{expense.occurred_on}|{expense.amount:.2f}"
    params = expense.model_dump(exclude={"request_id"})
    params.update(user_id=user_id, key=str(expense.request_id), hash=sha256(payload.encode()).hexdigest())

    try:
        with engine.begin() as connection:
            # Bloqueo por request_id: dos envíos iguales al mismo tiempo no crean dos gastos.
            connection.execute(text("""
                SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))
            """), {"key": f"{user_id}:{expense.request_id}"})
            existing = connection.execute(text("""
                SELECT id, request_hash FROM qori.transactions
                WHERE user_id = :user_id AND idempotency_key = :key
            """), params).mappings().one_or_none()
            if existing:
                if existing["request_hash"] != params["hash"]:
                    raise HTTPException(409, "La confirmación ya se usó con otros datos.")
                return ExpenseResponse(id=existing["id"])

            # La cuenta debe pertenecer al usuario del token y estar activa (HU04).
            account = connection.execute(text("""
                SELECT opening_on FROM qori.accounts
                WHERE id = :account_id AND user_id = :user_id AND status = 'ACTIVE'
                FOR UPDATE
            """), params).scalar_one_or_none()
            if account is None:
                raise HTTPException(404, "Cuenta activa no disponible.")
            if expense.occurred_on < account:
                raise HTTPException(422, "El gasto no puede ser anterior a la apertura de la cuenta.")

            # Solo categorías de gasto: del sistema o creadas por el mismo usuario.
            category = connection.execute(text("""
                SELECT id FROM qori.categories
                WHERE id = :category_id AND status = 'ACTIVE' AND type = 'EXPENSE'
                  AND (is_system OR user_id = :user_id)
                FOR SHARE
            """), params).scalar_one_or_none()
            if category is None:
                raise HTTPException(422, "Selecciona una categoría de gasto válida.")

            expense_id = connection.execute(text("""
                INSERT INTO qori.transactions
                    (user_id, account_id, category_id, type, amount, occurred_on,
                     status, source, confirmed_at, idempotency_key, request_hash)
                VALUES (:user_id, :account_id, :category_id, 'EXPENSE', :amount, :occurred_on,
                        'CONFIRMED', 'MANUAL', CURRENT_TIMESTAMP, :key, :hash)
                RETURNING id
            """), params).scalar_one()
            return ExpenseResponse(id=expense_id)
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudo registrar el gasto. Intenta nuevamente.") from error
