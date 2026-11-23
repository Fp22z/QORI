
package com.qori.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qori.app.ui.screens.RegistroScreen
import com.qori.app.ui.theme.ProyectoQoriTheme
import com.qori.app.data.LoginRequest
import com.qori.app.data.LoginResponse
import com.qori.app.data.RetrofitClient
import com.qori.app.ui.screens.LoginScreen
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.widget.Toast


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            ProyectoQoriTheme {
                LoginScreen(
                    onIniciarSesion = { correo, contrasena ->
                        iniciarSesion(correo, contrasena)
                    }
                )

            }
        }
    }


    private fun iniciarSesion(
        correo: String,
        contrasena: String
    ) {
        val datos = LoginRequest(correo, contrasena)

        RetrofitClient.apiService.iniciarSesion(datos)
            .enqueue(object : Callback<LoginResponse> {

                override fun onResponse(
                    call: Call<LoginResponse>,
                    response: Response<LoginResponse>
                ) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@MainActivity,
                            "Inicio de sesión exitoso",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "No se pudo iniciar sesión",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(
                    call: Call<LoginResponse>,
                    t: Throwable
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error de conexión",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

}