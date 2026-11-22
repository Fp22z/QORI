from fastapi import FastAPI
from database import probar_conexion

app = FastAPI()


@app.get("/")
def inicio():
    return {"mensaje": "Bienvenido a la API de QORI"}


@app.get("/salud")
def verificar_salud():
    return {"estado": "OK"}


@app.get("/probar-bd")
def verificar_base_datos():
    return probar_conexion()
