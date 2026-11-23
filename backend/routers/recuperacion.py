# HU11 - Recuperar contraseña
# Paso 1: el usuario escribe su correo y Supabase Auth le envía un código.
# Paso 2: escribe el código y su nueva contraseña; FastAPI valida el código con Supabase
#         y cambia la contraseña. QORI nunca guarda contraseñas en sus tablas.
# Requisito en Supabase: Authentication → Emails → "Reset Password" debe incluir {{ .Token }}.

import os

import httpx
from dotenv import load_dotenv
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

load_dotenv()

router = APIRouter()

SUPABASE_URL = os.getenv("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY = os.getenv("SUPABASE_PUBLISHABLE_KEY", "")

MENSAJE_ENVIO = "Si el correo está registrado, recibirás un código para restablecer tu contraseña."


class SolicitarCodigo(BaseModel):
    correo: str = Field(min_length=3, max_length=254)


class RestablecerContrasena(BaseModel):
    correo: str = Field(min_length=3, max_length=254)
    codigo: str = Field(min_length=6, max_length=10)
    nueva_contrasena: str = Field(min_length=8, max_length=72)


def validar_configuracion():
    if not SUPABASE_URL or not SUPABASE_KEY:
        raise HTTPException(500, "La autenticación no está configurada.")


def validar_correo(correo: str) -> str:
    correo = correo.strip().lower()
    if "@" not in correo or "." not in correo.split("@")[-1]:
        raise HTTPException(400, "Ingrese un correo electrónico válido.")
    return correo


@router.post("/recuperacion/solicitar")
async def solicitar_codigo(datos: SolicitarCodigo):
    validar_configuracion()
    correo = validar_correo(datos.correo)
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            respuesta = await client.post(
                f"{SUPABASE_URL}/auth/v1/recover",
                headers={"apikey": SUPABASE_KEY, "Content-Type": "application/json"},
                json={"email": correo},
            )
    except httpx.RequestError:
        raise HTTPException(502, "No se pudo conectar con Supabase.")

    if respuesta.status_code == 429:
        raise HTTPException(429, "Demasiados intentos. Intente más tarde.")
    if respuesta.status_code >= 500:
        raise HTTPException(502, "El servicio de autenticación no está disponible.")
    # Mismo mensaje exista o no el correo, para no revelar qué correos están registrados.
    return {"estado": "OK", "mensaje": MENSAJE_ENVIO}


@router.post("/recuperacion/restablecer")
async def restablecer_contrasena(datos: RestablecerContrasena):
    validar_configuracion()
    correo = validar_correo(datos.correo)
    codigo = datos.codigo.strip()
    if not codigo.isdigit():
        raise HTTPException(400, "El código solo tiene números.")
    if not any(c.isdigit() for c in datos.nueva_contrasena) or datos.nueva_contrasena.isalnum():
        raise HTTPException(400, "La contraseña necesita al menos un número y un símbolo.")

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            # 1) Supabase comprueba el código y entrega una sesión temporal de recuperación.
            verificacion = await client.post(
                f"{SUPABASE_URL}/auth/v1/verify",
                headers={"apikey": SUPABASE_KEY, "Content-Type": "application/json"},
                json={"type": "recovery", "email": correo, "token": codigo},
            )
            if verificacion.status_code == 429:
                raise HTTPException(429, "Demasiados intentos. Intente más tarde.")
            if verificacion.status_code != 200:
                raise HTTPException(400, "El código no es válido o ya venció. Solicita uno nuevo.")
            token = verificacion.json().get("access_token", "")
            sesion = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {token}"}

            # 2) Con esa sesión se guarda la nueva contraseña.
            cambio = await client.put(f"{SUPABASE_URL}/auth/v1/user", headers=sesion,
                                      json={"password": datos.nueva_contrasena})

            # 3) Se cierra la sesión temporal: el usuario entra luego con su nueva contraseña.
            await client.post(f"{SUPABASE_URL}/auth/v1/logout", headers=sesion)
    except httpx.RequestError:
        raise HTTPException(502, "No se pudo conectar con Supabase.")

    if cambio.status_code != 200:
        try:
            codigo_error = cambio.json().get("error_code", "")
        except ValueError:
            codigo_error = ""
        if codigo_error == "same_password":
            raise HTTPException(400, "La nueva contraseña debe ser diferente a la anterior.")
        if codigo_error == "weak_password":
            raise HTTPException(400, "La contraseña es muy débil. Usa letras, números y un símbolo.")
        raise HTTPException(400, "No se pudo cambiar la contraseña. Intenta nuevamente.")

    return {"estado": "OK", "mensaje": "Tu contraseña se actualizó. Ya puedes iniciar sesión."}
