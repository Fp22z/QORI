package com.qori.app.data

import retrofit2.Call
import retrofit2.http.GET

interface ApiService {

    @GET("salud")
    fun verificarSalud(): Call<Map<String, String>>

}
