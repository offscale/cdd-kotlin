package com.example.auto

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

@Serializable
data class ExampleDto(val name: String)

class ApiException(message: String) : Exception(message)

@Composable
/** Auto generated docs */
fun App() {
    MaterialTheme {
        Text("Hello from KMP Auto-Admin Scaffold")
    }
}