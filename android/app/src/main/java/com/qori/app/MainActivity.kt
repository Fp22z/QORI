package com.qori.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.qori.app.data.LoginRequest
import com.qori.app.data.LoginResponse
import com.qori.app.data.RetrofitClient
import com.qori.app.ui.screens.AccountsScreen
import com.qori.app.ui.screens.BalancesScreen
import com.qori.app.ui.screens.ExpenseScreen
import com.qori.app.ui.screens.ProfileScreen
import com.qori.app.ui.screens.RecoverPasswordScreen
import com.qori.app.ui.screens.CategoriesScreen
import com.qori.app.ui.screens.IncomeScreen
import com.qori.app.ui.screens.CreateAccountScreen
import com.qori.app.ui.screens.HomeScreen
import com.qori.app.ui.screens.LoginScreen
import com.qori.app.ui.screens.RegistroScreen
import com.qori.app.ui.theme.ProyectoQoriTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.qori.app.data.UsuarioResponse

class MainActivity : ComponentActivity() {

    private val usuarioActual = mutableStateOf<LoginResponse?>(null)
    private val pantallaActual = mutableStateOf("login")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            ProyectoQoriTheme {

                val usuario = usuarioActual.value

                if (usuario != null && pantallaActual.value == "expense") {
                    // HU09 - Registrar gasto
                    ExpenseScreen(
                        token = "Bearer ${usuario.access_token}",
                        onBack = { pantallaActual.value = "home" },
                        onSaved = {
                            pantallaActual.value = "home"
                            Toast.makeText(this@MainActivity, "Gasto registrado", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else if (usuario != null && pantallaActual.value == "balances") {
                    // HU10 - Consultar saldos
                    BalancesScreen(
                        token = "Bearer ${usuario.access_token}",
                        onBack = { pantallaActual.value = "home" }
                    )
                } else if (usuario != null && pantallaActual.value == "profile") {
                    // HU12 - Perfil e idioma
                    ProfileScreen(
                        token = "Bearer ${usuario.access_token}",
                        correo = usuario.correo,
                        onBack = { pantallaActual.value = "home" },
                        onSaved = { nuevoNombre ->
                            usuarioActual.value = usuario.copy(nombre = nuevoNombre)
                            Toast.makeText(this@MainActivity, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else if (usuario != null && pantallaActual.value == "accounts") {
                    AccountsScreen(
                        token = "Bearer ${usuario.access_token}",
                        onBack = { pantallaActual.value = "home" },
                        onCreate = { pantallaActual.value = "createAccount" },
                        onIncome = { pantallaActual.value = "income" },
                        onCategories = { pantallaActual.value = "categories" }
                    )
                } else if (usuario != null && pantallaActual.value == "categories") {
                    CategoriesScreen("Bearer ${usuario.access_token}") { pantallaActual.value = "accounts" }
                } else if (usuario != null && pantallaActual.value == "income") {
                    IncomeScreen(
                        token = "Bearer ${usuario.access_token}",
                        onBack = { pantallaActual.value = "accounts" },
                        onSaved = {
                            pantallaActual.value = "accounts"
                            Toast.makeText(this@MainActivity, "Ingreso registrado", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else if (usuario != null && pantallaActual.value == "createAccount") {
                    CreateAccountScreen(
                        token = "Bearer ${usuario.access_token}",
                        onBack = { pantallaActual.value = "accounts" },
                        onSaved = {
                            pantallaActual.value = "accounts"
                            Toast.makeText(this@MainActivity, "Cuenta creada", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else if (usuario != null) {

                    HomeScreen(
                        nombre = usuario.nombre,
                        correo = usuario.correo,
                        onCerrarSesion = {
                            cerrarSesion()
                        },
                        onOpenAccounts = { pantallaActual.value = "accounts" },
                        onOpenProfile = { pantallaActual.value = "profile" },
                        onOpenExpense = { pantallaActual.value = "expense" },
                        onOpenBalances = { pantallaActual.value = "balances" }
                    )

                } else if (pantallaActual.value == "recuperar") {
                    // HU11 - Recuperar contraseña
                    RecoverPasswordScreen(
                        onBack = { pantallaActual.value = "login" },
                        onListo = {
                            pantallaActual.value = "login"
                            Toast.makeText(this@MainActivity, "Contraseña actualizada. Inicia sesión.", Toast.LENGTH_LONG).show()
                        }
                    )
                } else if (pantallaActual.value == "registro") {

                    RegistroScreen(
                        onIniciarSesion = {
                            pantallaActual.value = "login"
                        }
                    )

                } else {

                    LoginScreen(
                        onIniciarSesion = { correo, contrasena ->
                            iniciarSesion(correo, contrasena)
                        },
                        onRegistrarse = {
                            pantallaActual.value = "registro"
                        },
                        onOlvideContrasena = {
                            pantallaActual.value = "recuperar"
                        }
                    )
                }
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

                        val usuario = response.body()

                        if (usuario != null) {
                            verificarUsuario(usuario)
                        }

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


    private fun verificarUsuario(sesion: LoginResponse) {

        val token = "Bearer ${sesion.access_token}"

        RetrofitClient.apiService.obtenerUsuario(token)
            .enqueue(object : Callback<UsuarioResponse> {

                override fun onResponse(
                    call: Call<UsuarioResponse>,
                    response: Response<UsuarioResponse>
                ) {
                    if (response.isSuccessful) {

                        val usuario = response.body()

                        if (usuario != null) {
                            usuarioActual.value = sesion.copy(
                                nombre = usuario.nombre,
                                correo = usuario.correo
                            )

                            Toast.makeText(
                                this@MainActivity,
                                "Usuario verificado correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            mostrarErrorVerificacion()
                        }

                    } else {
                        mostrarErrorVerificacion()
                    }
                }

                override fun onFailure(
                    call: Call<UsuarioResponse>,
                    t: Throwable
                ) {
                    mostrarErrorVerificacion()
                }
            })
    }

    private fun mostrarErrorVerificacion() {
        Toast.makeText(
            this,
            "No se pudo verificar la sesión",
            Toast.LENGTH_LONG
        ).show()
    }



    private fun cerrarSesion() {

        val usuario = usuarioActual.value ?: return

        val token = "Bearer ${usuario.access_token}"

        RetrofitClient.apiService.cerrarSesion(token)
            .enqueue(object : Callback<Map<String, String>> {

                override fun onResponse(
                    call: Call<Map<String, String>>,
                    response: Response<Map<String, String>>
                ) {
                    if (response.isSuccessful) {

                        usuarioActual.value = null
                        pantallaActual.value = "login"

                        Toast.makeText(
                            this@MainActivity,
                            "Sesión cerrada correctamente",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "No se pudo cerrar la sesión",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(
                    call: Call<Map<String, String>>,
                    t: Throwable
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error de conexión al cerrar sesión",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

}
