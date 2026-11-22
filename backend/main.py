import os

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from database import probar_conexion

load_dotenv()

app = FastAPI()

SUPABASE_URL = os.getenv("SUPABASE_URL", "").rstrip("/")
SUPABASE_KEY = os.getenv("SUPABASE_PUBLISHABLE_KEY", "")


# Modelo de los datos de registro
class RegistroUsuario(BaseModel):
    nombre: str
    correo: str
    contrasena: str


# Endpoint de bienvenida
@app.get("/")
def inicio():
    return {"mensaje": "Bienvenido a la API de QORI"}


# Endpoint de salud
@app.get("/salud")
def verificar_salud():
    return {"estado": "OK"}


# Endpoint para comprobar PostgreSQL
@app.get("/probar-bd")
def verificar_base_datos():
    return probar_conexion()


# HU01 - Registro de usuarios
@app.post("/auth/registro")
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
