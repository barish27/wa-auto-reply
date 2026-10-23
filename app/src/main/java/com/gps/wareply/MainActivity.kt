package com.gps.wareply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gps.wareply.ui.MainScreen

private val Teal = Color(0xFF128C7E)
private val DeepTeal = Color(0xFF075E54)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark)
                    darkColorScheme(primary = Teal, secondary = DeepTeal)
                else
                    lightColorScheme(primary = Teal, secondary = DeepTeal)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}
