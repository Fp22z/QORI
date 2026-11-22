package com.qori.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import com.qori.app.data.RetrofitClient
import com.qori.app.ui.theme.ProyectoQoriTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mensaje = mutableStateOf("Conectando con FastAPI...")

        setContent {
            ProyectoQoriTheme {
                Text(text = mensaje.value)
            }
        }

        RetrofitClient.apiService.verificarSalud()
            .enqueue(object : Callback<Map<String, String>> {

                override fun onResponse(
                    call: Call<Map<String, String>>,
                    response: Response<Map<String, String>>
                ) {
                    if (response.isSuccessful) {
                        val estado = response.body()?.get("estado")
                        mensaje.value = "Conexión con FastAPI: $estado"
                    } else {
                        mensaje.value = "Error HTTP: ${response.code()}"
                    }
                }

                override fun onFailure(
                    call: Call<Map<String, String>>,
                    t: Throwable
                ) {
                    mensaje.value = "Error de conexión: ${t.message}"
                }
            })
    }
}