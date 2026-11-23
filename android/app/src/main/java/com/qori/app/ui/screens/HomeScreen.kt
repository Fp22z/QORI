package com.qori.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.safeDrawingPadding

@Composable
fun HomeScreen(
    nombre: String,
    correo: String,
    onCerrarSesion: () -> Unit
) {
    var mostrarMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QORI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Box {
                Button(
                    onClick = { mostrarMenu = true },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = nombre.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString() ?: "U"
                    )
                }

                DropdownMenu(
                    expanded = mostrarMenu,
                    onDismissRequest = { mostrarMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Mi perfil") },
                        onClick = { mostrarMenu = false }
                    )

                    DropdownMenuItem(
                        text = { Text("Cerrar sesión") },
                        onClick = {
                            mostrarMenu = false
                            onCerrarSesion()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "¡Bienvenido a QORI!",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Información de mi cuenta",
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Nombre: $nombre")

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Correo: $correo")
    }
}