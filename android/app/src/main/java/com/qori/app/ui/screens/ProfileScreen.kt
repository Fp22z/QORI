package com.qori.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qori.app.data.RetrofitClient
import com.qori.app.data.UpdateProfileRequest
import kotlinx.coroutines.launch
import java.io.IOException

// HU12 - Perfil e idioma
// Permite ver y cambiar el nombre, el idioma preferido y la moneda principal.
// El correo se muestra pero no se edita (lo administra Supabase Auth).
// onSaved devuelve el nuevo nombre para actualizar la pantalla de inicio.
@Composable
fun ProfileScreen(token: String, correo: String, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    var idioma by remember { mutableStateOf("es") }
    var moneda by remember { mutableStateOf("PEN") }
    var cargado by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var exito by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }

    BackHandler { if (!guardando) onBack() }
    LaunchedEffect(token, attempt) {
        error = ""
        try {
            val response = RetrofitClient.apiService.getProfile(token)
            val perfil = response.body()
            if (response.isSuccessful && perfil != null) {
                nombre = perfil.display_name
                idioma = perfil.preferred_language
                moneda = perfil.preferred_currency
                cargado = true
            } else {
                error = if (response.code() == 401) "Tu sesión expiró. Vuelve a iniciar sesión."
                else "No se pudo cargar tu perfil."
            }
        } catch (_: IOException) {
            error = "No hay conexión con el servidor."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack, enabled = !guardando) { Text("Volver") }
        Text("Mi perfil", style = MaterialTheme.typography.headlineSmall)

        if (!cargado) {
            if (error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Reintentar") }
            } else {
                CircularProgressIndicator()
            }
        } else {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it; exito = "" },
                label = { Text("Nombre") },
                singleLine = true,
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = correo,
                onValueChange = {},
                label = { Text("Correo electrónico") },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Idioma", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(idioma == "es", { idioma = "es"; exito = "" }, { Text("Español") }, enabled = !guardando)
                FilterChip(idioma == "en", { idioma = "en"; exito = "" }, { Text("English") }, enabled = !guardando)
            }

            Text("Moneda principal", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(moneda == "PEN", { moneda = "PEN"; exito = "" }, { Text("Soles (S/)") }, enabled = !guardando)
                FilterChip(moneda == "USD", { moneda = "USD"; exito = "" }, { Text("Dólares (US$)") }, enabled = !guardando)
            }

            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            if (exito.isNotEmpty()) Text(exito, color = MaterialTheme.colorScheme.primary)

            Button(
                onClick = {
                    val limpio = nombre.trim()
                    error = when {
                        limpio.length < 2 -> "El nombre debe tener al menos 2 caracteres."
                        limpio.length > 80 -> "El nombre puede tener como máximo 80 caracteres."
                        else -> ""
                    }
                    if (error.isEmpty()) {
                        guardando = true
                        scope.launch {
                            try {
                                val response = RetrofitClient.apiService.updateProfile(
                                    token, UpdateProfileRequest(limpio, idioma, moneda)
                                )
                                val perfil = response.body()
                                if (response.isSuccessful && perfil != null) {
                                    nombre = perfil.display_name
                                    exito = "Perfil actualizado."
                                    onSaved(perfil.display_name)
                                } else {
                                    error = when (response.code()) {
                                        401 -> "Tu sesión expiró."
                                        422 -> "Revisa el nombre, el idioma y la moneda."
                                        else -> "No se pudo guardar el perfil. Intenta nuevamente."
                                    }
                                }
                            } catch (_: IOException) {
                                error = "No hay conexión con el servidor."
                            } finally {
                                guardando = false
                            }
                        }
                    }
                },
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (guardando) "Guardando…" else "Guardar cambios") }
        }
    }
}
