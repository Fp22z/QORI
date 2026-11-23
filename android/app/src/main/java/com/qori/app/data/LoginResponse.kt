package com.qori.app.data

data class LoginResponse(
    val estado: String,
    val mensaje: String,
    val nombre: String,
    val correo: String,
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int
)
