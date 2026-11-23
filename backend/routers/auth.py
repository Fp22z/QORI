import os

import httpx
from dotenv import load_dotenv
from fastapi import APIRouter, HTTPException, Header
from pydantic import BaseModel

load_dotenv()

router = APIRouter()

SUPABASE_URL = os.getenv("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY = os.getenv("SUPABASE_PUBLISHABLE_KEY", "")


# Modelo de los datos de registro
class RegistroUsuario(BaseModel):
    nombre: str
    correo: str
    contrasena: str


# HU01 - Registro de usuarios
@router.post("/auth/registro")
async def registrar_usuario(datos: RegistroUsuario):

    nombre = datos.nombre.strip()
    correo = datos.correo.strip().lower()

    # Validaciones básicas
    if not nombre or not correo or not datos.contrasena:
        raise HTTPException(
            status_code=400,
            detail="Todos los campos son obligatorios."
        )

    if "@" not in correo or "." not in correo.split("@")[-1]:
        raise HTTPException(
            status_code=400,
            detail="Ingrese un correo electrónico válido."
        )

    if len(datos.contrasena) < 8:
        raise HTTPException(
            status_code=400,
            detail="La contraseña debe tener al menos 8 caracteres."
        )

    if not SUPABASE_URL or not SUPABASE_KEY:
        raise HTTPException(
            status_code=500,
            detail="La autenticación no está configurada."
        )

    # Enviar solicitud a Supabase Auth
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            respuesta = await client.post(
                f"{SUPABASE_URL}/auth/v1/signup",
                headers={
                    "apikey": SUPABASE_KEY,
                    "Content-Type": "application/json"
                },
                json={
                    "email": correo,
                    "password": datos.contrasena,
                    "data": {
                        "display_name": nombre
                    }
                }
            )

    except httpx.RequestError:
        raise HTTPException(
            status_code=502,
            detail="No se pudo conectar con Supabase."
        )

    # Diagnosticar errores devueltos por Supabase
    if respuesta.status_code >= 400:

        try:
            error_supabase = respuesta.json()
        except ValueError:
            error_supabase = {}

        print("=== ERROR DE SUPABASE AUTH ===")
        print("Código HTTP:", respuesta.status_code)
        print("Código de error:", error_supabase.get("error_code"))
        print("Mensaje:", error_supabase.get("msg"))
        print("==============================")

        if respuesta.status_code == 429:
            raise HTTPException(
                status_code=429,
                detail="Demasiados intentos. Intente más tarde."
            )

        raise HTTPException(
            status_code=400,
            detail="No se pudo completar el registro. Verifique los datos."
        )

    # Respuesta cuando Supabase acepta la solicitud
    return {
        "estado": "OK",
        "mensaje": (
            "Solicitud de registro recibida. "
            "Revise su correo si se requiere confirmación."
        )
    }



# HU02 - Inicio de sesión

class LoginUsuario(BaseModel):
    correo: str
    contrasena: str


@router.post("/auth/login")
async def iniciar_sesion(datos: LoginUsuario):

    correo = datos.correo.strip().lower()

    if not correo or not datos.contrasena:
        raise HTTPException(
            status_code=400,
            detail="Ingrese su correo y contraseña."
        )

    if not SUPABASE_URL or not SUPABASE_KEY:
        raise HTTPException(
            status_code=500,
            detail="La autenticación no está configurada."
        )

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            respuesta = await client.post(
                f"{SUPABASE_URL}/auth/v1/token",
                params={"grant_type": "password"},
                headers={
                    "apikey": SUPABASE_KEY,
                    "Content-Type": "application/json"
                },
                json={
                    "email": correo,
                    "password": datos.contrasena
                }
            )

    except httpx.RequestError:
        raise HTTPException(
            status_code=502,
            detail="No se pudo conectar con Supabase."
        )

    if respuesta.status_code != 200:
        if respuesta.status_code == 429:
            raise HTTPException(
                status_code=429,
                detail="Demasiados intentos. Intente más tarde."
            )

        if respuesta.status_code >= 500:
            raise HTTPException(
                status_code=502,
                detail="El servicio de autenticación no está disponible."
            )

        raise HTTPException(
            status_code=401,
            detail="No se pudo iniciar sesión. Verifique sus credenciales y la confirmación del correo."
        )

    sesion = respuesta.json()

    
    return {
        "estado": "OK",
        "mensaje": "Inicio de sesión exitoso.",
        "nombre": sesion.get("user", {})
            .get("user_metadata", {})
            .get("display_name", ""),
        "correo": sesion.get("user", {}).get("email", ""),
        "access_token": sesion["access_token"],
        "refresh_token": sesion["refresh_token"],
        "token_type": sesion["token_type"],
        "expires_in": sesion["expires_in"]
    }



# HU03 - Cierre de sesión

from fastapi import Header

@router.post("/auth/logout")
async def cerrar_sesion(
    authorization: str = Header(...)
):

    if not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=401,
            detail="Token de sesión no válido."
        )

    if not SUPABASE_URL or not SUPABASE_KEY:
        raise HTTPException(
            status_code=500,
            detail="La autenticación no está configurada."
        )

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            respuesta = await client.post(
                f"{SUPABASE_URL}/auth/v1/logout",
                headers={
                    "apikey": SUPABASE_KEY,
                    "Authorization": authorization
                }
            )

    except httpx.RequestError:
        raise HTTPException(
            status_code=502,
            detail="No se pudo conectar con Supabase."
        )

    if respuesta.status_code not in (200, 204):
        raise HTTPException(
            status_code=502,
            detail="No se pudo completar el cierre de sesión."
        )

    return {
        "estado": "OK",
        "mensaje": "Sesión cerrada correctamente."
    }



# HU04 - Identificación del usuario autenticado

@router.get("/auth/me")
async def obtener_usuario(
    authorization: str = Header(...)
):

    if not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=401,
            detail="Se requiere una sesión válida."
        )

    if not SUPABASE_URL or not SUPABASE_KEY:
        raise HTTPException(
            status_code=500,
            detail="La autenticación no está configurada."
        )

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            respuesta = await client.get(
                f"{SUPABASE_URL}/auth/v1/user",
                headers={
                    "apikey": SUPABASE_KEY,
                    "Authorization": authorization
                }
            )

    except httpx.RequestError:
        raise HTTPException(
            status_code=502,
            detail="No se pudo verificar la sesión."
        )

    if respuesta.status_code != 200:
        raise HTTPException(
            status_code=401,
            detail="Sesión inválida o expirada."
        )

    usuario = respuesta.json()

    return {
        "user_id": usuario["id"],
        "correo": usuario.get("email", ""),
        "nombre": usuario.get(
            "user_metadata", {}
        ).get("display_name", "")
    }
