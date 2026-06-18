package com.example.cardignosticcenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ServiceHomeScreen(navController: NavController) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Car Service Dashboard", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(20.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.navigate("diagnostic") }
        ) {
            Text("Scan Car")
        }

        Spacer(Modifier.height(10.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.navigate("book_service") }
        ) {
            Text("Book Service")
        }

        Spacer(Modifier.height(10.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.navigate("workshops") }
        ) {
            Text("Nearby Workshops")
        }
    }
}
