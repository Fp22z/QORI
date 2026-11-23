from datetime import date
from decimal import Decimal
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from starlette.concurrency import run_in_threadpool

from database import engine
from routers.auth import obtener_usuario
from routers.session import require_user_id

router = APIRouter()


class CreateAccountRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=120)
    type: Literal["CASH", "BANK", "DIGITAL_WALLET"]
    currency: Literal["PEN", "USD"]
    opening_balance: Decimal = Field(ge=0, max_digits=19, decimal_places=2)

    opening_on: date | None = None

    @field_validator("name", mode="before")
    @classmethod
    def trim_name(cls, value):
        return value.strip() if isinstance(value, str) else value


class CreateAccountResponse(BaseModel):
    id: UUID


def insert_account(account: CreateAccountRequest, user_id: UUID) -> UUID:
    values = account.model_dump()
    values["user_id"] = user_id

    try:
        with engine.begin() as connection:
            account_id = connection.execute(
                text("""
                    INSERT INTO qori.accounts
                        (user_id, name, type, currency, opening_balance, opening_on)
                    SELECT user_id, :name, :type, :currency, :opening_balance,
                           COALESCE(CAST(:opening_on AS date),
                                    (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date)
                    FROM qori.profiles
                    WHERE user_id = :user_id
                    RETURNING id
                """),
                values,
            ).scalar_one_or_none()
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudo guardar la cuenta. Intenta nuevamente.") from error

    if account_id is None:
        raise HTTPException(403, "Se requiere un perfil para crear cuentas.")
    return account_id


@router.post("/cuentas", status_code=201, response_model=CreateAccountResponse)
async def create_account(
    account: CreateAccountRequest,
    authorization: str | None = Header(default=None),
):
    if not authorization:
        raise HTTPException(401, "Se requiere una sesión válida.")

    user = await obtener_usuario(authorization)
    account_id = await run_in_threadpool(insert_account, account, UUID(user["user_id"]))
    return CreateAccountResponse(id=account_id)


class AccountResponse(BaseModel):
    id: UUID
    name: str
    currency: str
    opening_on: date
    available_balance: Decimal
    reserved_balance: Decimal


@router.get("/cuentas", response_model=list[AccountResponse])
def list_accounts(user_id: UUID = Depends(require_user_id)):
    try:
        with engine.connect() as connection:
            rows = connection.execute(text("""
                SELECT a.id, a.name, a.currency, a.opening_on,
                       a.opening_balance + COALESCE(m.net, 0) + COALESCE(t.net, 0)
                           - COALESCE(r.reserved, 0) AS available_balance,
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
                ORDER BY a.name, a.id
            """), {"user_id": user_id}).mappings()
            return [AccountResponse(**row) for row in rows]
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudieron consultar las cuentas.") from error
