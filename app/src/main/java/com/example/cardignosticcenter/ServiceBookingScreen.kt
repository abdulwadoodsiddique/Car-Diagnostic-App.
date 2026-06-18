package com.example.cardignosticcenter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ServiceBookingScreen(navController: NavController) {

    var selectedServices by remember { mutableStateOf(setOf<String>()) }
    var bookingConfirmed by remember { mutableStateOf(false) }

    fun toggleService(service: String) {
        selectedServices = if (selectedServices.contains(service)) {
            selectedServices - service
        } else {
            selectedServices + service
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Book Car Service",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(30.dp))

        ServiceItem("Oil Service", selectedServices, ::toggleService)
        ServiceItem("Battery Replacement", selectedServices, ::toggleService)
        ServiceItem("General Service", selectedServices, ::toggleService)

        Spacer(Modifier.height(30.dp))

        if (selectedServices.isNotEmpty()) {

            Text("Selected Services:")
            selectedServices.forEach {
                Text("• $it")
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    bookingConfirmed = true
                    navController.navigate("workshops")
                }
            ) {
                Text("Confirm Booking")
            }
        }

        Spacer(Modifier.height(30.dp))

        if (bookingConfirmed) {

            Text("Booking Successful ✅")

            Spacer(Modifier.height(20.dp))

            Button(onClick = { navController.navigate("diagnostic") }) {
                Text("Back to Diagnostic")
            }
        }
    }
}

@Composable
fun ServiceItem(
    serviceName: String,
    selectedServices: Set<String>,
    onToggle: (String) -> Unit
) {

    val isSelected = selectedServices.contains(serviceName)

    Button(
        onClick = { onToggle(serviceName) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.secondary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(serviceName)
    }
}