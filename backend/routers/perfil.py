# HU12 - Ver y editar perfil (nombre, idioma y moneda principal)
# El perfil está en qori.profiles y se crea solo al registrarse (trigger handle_new_user).
# El usuario siempre sale del token validado: nunca se recibe un user_id desde Android (HU04).

import os
from typing import Literal
from uuid import UUID

import httpx
from dotenv import load_dotenv
from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from database import engine
from routers.session import require_user_id

load_dotenv()

router = APIRouter()

SUPABASE_URL = os.getenv("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY = os.getenv("SUPABASE_PUBLISHABLE_KEY", "")


class ProfileResponse(BaseModel):
    display_name: str
    preferred_language: str
    preferred_currency: str
    timezone: str


class UpdateProfileRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    display_name: str = Field(min_length=2, max_length=80)
    preferred_language: Literal["es", "en"]
    preferred_currency: Literal["PEN", "USD"]

    @field_validator("display_name", mode="before")
    @classmethod
    def trim_name(cls, value):
        return value.strip() if isinstance(value, str) else value


PROFILE_SQL = """
    SELECT display_name, preferred_language, preferred_currency, timezone
    FROM qori.profiles
    WHERE user_id = :user_id AND status = 'ACTIVE'
"""


@router.get("/perfil", response_model=ProfileResponse)
def get_profile(user_id: UUID = Depends(require_user_id)):
    try:
        with engine.connect() as connection:
            row = connection.execute(text(PROFILE_SQL), {"user_id": user_id}).mappings().one_or_none()
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudo consultar el perfil.") from error
    if row is None:
        raise HTTPException(404, "No se encontró tu perfil.")
    return ProfileResponse(**row)


@router.patch("/perfil", response_model=ProfileResponse)
async def update_profile(
    datos: UpdateProfileRequest,
    user_id: UUID = Depends(require_user_id),
    authorization: str | None = Header(default=None),
):
    try:
        with engine.begin() as connection:
            row = connection.execute(text("""
                UPDATE qori.profiles
                SET display_name = :display_name,
                    preferred_language = :preferred_language,
                    preferred_currency = :preferred_currency,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = :user_id AND status = 'ACTIVE'
                RETURNING display_name, preferred_language, preferred_currency, timezone
            """), {**datos.model_dump(), "user_id": user_id}).mappings().one_or_none()
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudo guardar el perfil. Intenta nuevamente.") from error
    if row is None:
        raise HTTPException(404, "No se encontró tu perfil.")

    # El login lee el nombre desde Supabase Auth: se actualiza también allí para que coincida.
    # Si falla, el perfil ya quedó guardado; el nombre se corrige en el siguiente cambio.
    if SUPABASE_URL and SUPABASE_KEY and authorization:
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                await client.put(
                    f"{SUPABASE_URL}/auth/v1/user",
                    headers={"apikey": SUPABASE_KEY, "Authorization": authorization},
                    json={"data": {"display_name": datos.display_name}},
                )
        except httpx.RequestError:
            pass

    return ProfileResponse(**row)
