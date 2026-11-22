
package com.qori.app.ui.screens

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qori.app.data.RegistroRequest
import com.qori.app.data.RegistroResponse
import com.qori.app.data.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Composable
fun RegistroScreen() {

    val context = LocalContext.current

    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var confirmarContrasena by remember { mutableStateOf("") }

    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Crear cuenta en QORI",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Completa tus datos para registrarte.")

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it },
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmarContrasena,
            onValueChange = { confirmarContrasena = it },
            label = { Text("Confirmar contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {

                val nombreLimpio = nombre.trim()
                val correoLimpio = correo.trim()

                mensaje = when {
                    nombreLimpio.isEmpty() ||
                            correoLimpio.isEmpty() ||
                            contrasena.isEmpty() ||
                            confirmarContrasena.isEmpty() ->
                        "Todos los campos son obligatorios."

                    !Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches() ->
                        "Ingrese un correo electrónico válido."

                    contrasena.length < 8 ->
                        "La contraseña debe tener al menos 8 caracteres."

                    contrasena != confirmarContrasena ->
                        "Las contraseñas no coinciden."

                    else -> ""
                }

                if (mensaje.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        mensaje,
                        Toast.LENGTH_LONG
                    ).show()
                } else {

                    cargando = true

                    val datos = RegistroRequest(
                        nombre = nombreLimpio,
                        correo = correoLimpio,
                        contrasena = contrasena
                    )

                    RetrofitClient.apiService
                        .registrarUsuario(datos)
                        .enqueue(object : Callback<RegistroResponse> {

                            override fun onResponse(
                                call: Call<RegistroResponse>,
                                response: Response<RegistroResponse>
                            ) {
                                cargando = false


                                mensaje = when {
                                    response.isSuccessful ->
                                        response.body()?.mensaje
                                            ?: "Solicitud de registro recibida."

                                    response.code() == 429 ->
                                        "Se alcanzó el límite de correos de confirmación. " +
                                                "Inténtalo más tarde."

                                    else ->
                                        "No se pudo completar el registro."
                                }
                            }

                            override fun onFailure(
                                call: Call<RegistroResponse>,
                                t: Throwable
                            ) {
                                cargando = false
                                mensaje = "Error de conexión con el servidor."
                            }
                        })
                }
            },
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (cargando) "Registrando..."
                else "Registrarse"
            )
        }

        if (cargando) {
            CircularProgressIndicator()
        }

        if (mensaje.isNotEmpty()) {
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}