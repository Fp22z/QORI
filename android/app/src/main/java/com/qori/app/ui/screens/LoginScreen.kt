package com.qori.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    onIniciarSesion: (String, String) -> Unit,
    onRegistrarse: () -> Unit,
    onOlvideContrasena: () -> Unit = {} // HU11
) {
    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "QORI",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Iniciar sesión")

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onIniciarSesion(correo.trim(), contrasena)
            },
            enabled = correo.isNotBlank() &&
                    contrasena.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Iniciar sesión")
        }


        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onRegistrarse
        ) {
            Text("¿Aún no tienes cuenta? Regístrate")
        }

        // HU11 - Recuperar contraseña
        TextButton(
            onClick = onOlvideContrasena
        ) {
            Text("¿Olvidaste tu contraseña?")
        }

    }
}
