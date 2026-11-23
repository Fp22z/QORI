from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from database import engine
from routers.session import require_user_id

router = APIRouter()


class CategoryResponse(BaseModel):
    id: UUID
    name: str


@router.get("/categorias", response_model=list[CategoryResponse])
def list_categories(tipo: Literal["INCOME", "EXPENSE"], user_id: UUID = Depends(require_user_id)):
    try:
        with engine.connect() as connection:
            rows = connection.execute(text("""
                SELECT id, name
                FROM qori.categories
                WHERE type = :type AND status = 'ACTIVE'
                  AND (is_system OR user_id = :user_id)
                ORDER BY name, id
            """), {"type": tipo, "user_id": user_id}).mappings()
            return [CategoryResponse(**row) for row in rows]
    except SQLAlchemyError as error:
        raise HTTPException(503, "No se pudieron consultar las categorías.") from error
