package com.qori.app.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header

interface ApiService {

    @GET("salud")
    fun verificarSalud(): Call<Map<String, String>>

    @POST("auth/registro")
    fun registrarUsuario(
        @Body datos: RegistroRequest
    ): Call<RegistroResponse>


    @POST("auth/login")
    fun iniciarSesion(
        @Body datos: LoginRequest
    ): Call<LoginResponse>


    @POST("auth/logout")
    fun cerrarSesion(
        @Header("Authorization") token: String
    ): Call<Map<String, String>>


}
