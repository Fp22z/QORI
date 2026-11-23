from uuid import UUID

from fastapi import Header, HTTPException

from routers.auth import obtener_usuario


async def require_user_id(authorization: str | None = Header(default=None)) -> UUID:
    if not authorization:
        raise HTTPException(401, "Se requiere una sesión válida.")
    user = await obtener_usuario(authorization)
    return UUID(user["user_id"])
