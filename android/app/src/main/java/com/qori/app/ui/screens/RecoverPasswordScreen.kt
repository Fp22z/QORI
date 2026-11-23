package com.qori.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.qori.app.data.RecoveryRequest
import com.qori.app.data.ResetPasswordRequest
import com.qori.app.data.RetrofitClient
import kotlinx.coroutines.launch
import java.io.IOException

// HU11 - Recuperar contraseña
// Paso 1: correo → Supabase envía un código. Paso 2: código + nueva contraseña.
// onListo regresa al inicio de sesión para entrar con la nueva contraseña.
@Composable
fun RecoverPasswordScreen(onBack: () -> Unit, onListo: () -> Unit) {
    val scope = rememberCoroutineScope()
    var paso by remember { mutableIntStateOf(1) }
    var correo by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }

    BackHandler { if (!cargando) onBack() }

    fun solicitarCodigo() {
        val correoLimpio = correo.trim()
        error = when {
            correoLimpio.isEmpty() -> "Ingresa tu correo."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches() -> "Ingresa un correo válido."
            else -> ""
        }
        if (error.isNotEmpty()) return
        cargando = true
        scope.launch {
            try {
                val response = RetrofitClient.apiService.requestRecoveryCode(RecoveryRequest(correoLimpio))
                if (response.isSuccessful) {
                    aviso = response.body()?.mensaje ?: ""
                    paso = 2
                } else {
                    error = when (response.code()) {
                        429 -> "Se alcanzó el límite de correos. Intenta en unos minutos."
                        else -> "No se pudo enviar el código. Intenta nuevamente."
                    }
                }
            } catch (_: IOException) {
                error = "No hay conexión con el servidor."
            } finally {
                cargando = false
            }
        }
    }

    fun restablecer() {
        error = when {
            !codigo.trim().matches(Regex("[0-9]{6,10}")) -> "Escribe el código que llegó a tu correo."
            nueva.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
            !nueva.any { it.isDigit() } || nueva.all { it.isLetterOrDigit() } ->
                "La contraseña necesita al menos un número y un símbolo."
            nueva != confirmar -> "Las contraseñas no coinciden."
            else -> ""
        }
        if (error.isNotEmpty()) return
        cargando = true
        scope.launch {
            try {
                val response = RetrofitClient.apiService.resetPassword(
                    ResetPasswordRequest(correo.trim(), codigo.trim(), nueva)
                )
                if (response.isSuccessful) {
                    onListo()
                } else {
                    // El backend devuelve un mensaje claro en "detail" (código vencido, misma contraseña…).
                    error = when (response.code()) {
                        429 -> "Demasiados intentos. Intenta en unos minutos."
                        400 -> leerDetalle(response.errorBody()?.string()) ?: "No se pudo cambiar la contraseña."
                        else -> "No se pudo cambiar la contraseña. Intenta nuevamente."
                    }
                }
            } catch (_: IOException) {
                error = "No hay conexión con el servidor."
            } finally {
                cargando = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Recuperar contraseña", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(if (paso == 1) "Paso 1 de 2: te enviaremos un código a tu correo."
             else "Paso 2 de 2: escribe el código y tu nueva contraseña.")
        Spacer(Modifier.height(24.dp))

        if (paso == 1) {
            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo electrónico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            if (aviso.isNotEmpty()) {
                Text(aviso, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = codigo,
                onValueChange = { codigo = it.filter { c -> c.isDigit() }.take(10) },
                label = { Text("Código") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = nueva,
                onValueChange = { nueva = it },
                label = { Text("Nueva contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmar,
                onValueChange = { confirmar = it },
                label = { Text("Confirmar contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !cargando,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { if (paso == 1) solicitarCodigo() else restablecer() },
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(when {
                cargando -> "Enviando…"
                paso == 1 -> "Enviar código"
                else -> "Cambiar contraseña"
            })
        }
        if (paso == 2) {
            TextButton(onClick = { solicitarCodigo() }, enabled = !cargando) { Text("Reenviar código") }
        }
        TextButton(onClick = onBack, enabled = !cargando) { Text("Volver al inicio de sesión") }
    }
}

// Lee {"detail": "..."} de una respuesta de error de FastAPI.
private fun leerDetalle(cuerpo: String?): String? = try {
    JsonParser.parseString(cuerpo ?: "").asJsonObject.get("detail")?.asString
} catch (_: Exception) {
    null
}
