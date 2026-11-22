import os

from dotenv import load_dotenv
from sqlalchemy import create_engine, text

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL")

engine = create_engine(DATABASE_URL)


def probar_conexion():
    try:
        with engine.connect() as conexion:
            resultado = conexion.execute(
                text("SELECT COUNT(*) FROM qori.profiles")
            )
            cantidad = resultado.scalar()

            return {
                "conexion": "exitosa",
                "perfiles": cantidad
            }

    except Exception as error:
        print("Error de conexión:", type(error).__name__)
        return {"conexion": "fallida"}
