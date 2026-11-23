from fastapi import FastAPI
from database import probar_conexion
from routers import auth, categorias, cuentas, movimientos, perfil, recuperacion, saldos

app = FastAPI()

# Conectar el módulo de autenticación
app.include_router(auth.router)
app.include_router(cuentas.router)
app.include_router(categorias.router)
app.include_router(movimientos.router)
app.include_router(saldos.router)        # HU10
app.include_router(recuperacion.router)  # HU11
app.include_router(perfil.router)        # HU12


# Página de bienvenida
@app.get("/")
def inicio():
    return {"mensaje": "Bienvenido a la API de QORI"}


# Verificar que FastAPI funciona
@app.get("/salud")
def verificar_salud():
    return {"estado": "OK"}


# Comprobar conexión con PostgreSQL
@app.get("/probar-bd")
def verificar_base_datos():
    return probar_conexion()
