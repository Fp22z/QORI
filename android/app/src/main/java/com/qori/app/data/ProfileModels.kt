package com.qori.app.data

// HU12 - Perfil: GET y PATCH /perfil
data class ProfileResponse(
    val display_name: String,
    val preferred_language: String,
    val preferred_currency: String,
    val timezone: String
)

data class UpdateProfileRequest(
    val display_name: String,
    val preferred_language: String,
    val preferred_currency: String
)
