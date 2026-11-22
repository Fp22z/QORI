
package com.qori.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qori.app.ui.screens.RegistroScreen
import com.qori.app.ui.theme.ProyectoQoriTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            ProyectoQoriTheme {
                RegistroScreen()
            }
        }
    }
}