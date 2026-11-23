# HU10 - Consultar saldos
# Saldo registrado (IND07) = saldo inicial + ingresos - gastos + transferencias recibidas
#                            - transferencias enviadas, contando solo movimientos CONFIRMED.
# Saldo disponible (IND08) = saldo registrado - montos reservados en metas.
# Los totales se calculan por moneda: nunca se suman soles con dólares.

from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from database import engine
from routers.session import require_user_id

router = APIRouter()


class AccountBalance(BaseModel):
    id: UUID
    name: str
    type: str
    currency: str
    opening_balance: Decimal
    registered_balance: Decimal
    reserved_balance: Decimal
    available_balance: Decimal


class CurrencyTotal(BaseModel):
    currency: str
    registered_balance: Decimal
    reserved_balance: Decimal
    available_balance: Decimal


class BalancesResponse(BaseModel):
    accounts: list[AccountBalance]
    totals: list[CurrencyTotal]


BALANCE_SQL = """
    SELECT a.id, a.name, a.type, a.currency, a.opening_balance,
           a.opening_balance + COALESCE(m.net, 0) + COALESCE(t.net, 0) AS registered_balance,
           COALESCE(r.reserved, 0) AS reserved_balance
    FROM qori.accounts a
    LEFT JOIN LATERAL (
        SELECT SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END) AS net
        FROM qori.transactions
        WHERE user_id = a.user_id AND account_id = a.id AND status = 'CONFIRMED'
    ) m ON TRUE
    LEFT JOIN LATERAL (
        SELECT SUM(CASE WHEN to_account_id = a.id THEN amount ELSE -amount END) AS net
        FROM qori.internal_transfers
        WHERE user_id = a.user_id AND status = 'CONFIRMED'
          AND (from_account_id = a.id OR to_account_id = a.id)
    ) t ON TRUE
    LEFT JOIN LATERAL (
        SELECT SUM(amount) AS reserved FROM qori.goal_reservations
        WHERE account_id = a.id AND status = 'ACTIVE'
    ) r ON TRUE
    WHERE a.user_id = :user_id AND a.status = 'ACTIVE'
"""


def to_balance(row) -> AccountBalance:
    return AccountBalance(**row, available_balance=row["registered_balance"] - row["reserved_balance"])


@router.get("/saldos", response_model=BalancesResponse)
def list_balances(user_id: UUID = Depends(require_user_id)):
    try:
        with engine.connect() as connection:
            rows = connection.execute(text(BALANCE_SQL + " ORDER BY a.currency, a.name, a.id"),
                                      {"user_id": user_id}).mappings().all()
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudieron consultar los saldos.") from error

    accounts = [to_balance(row) for row in rows]
    totals = {}
    for account in accounts:
        total = totals.setdefault(account.currency, CurrencyTotal(
            currency=account.currency, registered_balance=0, reserved_balance=0, available_balance=0))
        total.registered_balance += account.registered_balance
        total.reserved_balance += account.reserved_balance
        total.available_balance += account.available_balance
    return BalancesResponse(accounts=accounts, totals=list(totals.values()))


@router.get("/saldos/{account_id}", response_model=AccountBalance)
def account_balance(account_id: UUID, user_id: UUID = Depends(require_user_id)):
    try:
        with engine.connect() as connection:
            row = connection.execute(text(BALANCE_SQL + " AND a.id = :account_id"),
                                     {"user_id": user_id, "account_id": account_id}).mappings().one_or_none()
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudo consultar el saldo.") from error
    # Si la cuenta no existe o es de otro usuario, la respuesta es la misma (HU04).
    if row is None:
        raise HTTPException(404, "Cuenta no encontrada.")
    return to_balance(row)
