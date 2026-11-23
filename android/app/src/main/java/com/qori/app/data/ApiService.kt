package com.qori.app.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.Response
import retrofit2.http.Header

interface ApiService {

    @GET("cuentas")
    suspend fun listAccounts(@Header("Authorization") token: String): Response<List<AccountResponse>>

    @GET("categorias")
    suspend fun listCategories(
        @Header("Authorization") token: String,
        @Query("tipo") type: String
    ): Response<List<CategoryResponse>>

    @POST("movimientos/ingresos")
    suspend fun createIncome(
        @Header("Authorization") token: String,
        @Body income: CreateIncomeRequest
    ): Response<IncomeResponse>

    @POST("cuentas")
    fun createAccount(
        @Header("Authorization") token: String,
        @Body account: CreateAccountRequest
    ): Call<CreateAccountResponse>

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


    @GET("auth/me")
    fun obtenerUsuario(
        @Header("Authorization") token: String
    ): Call<UsuarioResponse>

}
