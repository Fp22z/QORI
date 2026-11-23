package com.qori.app.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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

}
