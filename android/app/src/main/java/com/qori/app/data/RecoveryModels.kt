package com.qori.app.data

// HU11 - Recuperar contraseña: POST /recuperacion/solicitar y /recuperacion/restablecer
data class RecoveryRequest(
    val correo: String
)

data class ResetPasswordRequest(
    val correo: String,
    val codigo: String,
    val nueva_contrasena: String
)

data class RecoveryResponse(
    val estado: String,
    val mensaje: String
)
