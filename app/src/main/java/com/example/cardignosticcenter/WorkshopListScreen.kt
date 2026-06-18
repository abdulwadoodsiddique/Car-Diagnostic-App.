package com.example.cardignosticcenter

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class Workshop(
    val name: String,
    val phone: String,
    val latitude: Double,
    val longitude: Double
)
@Composable
fun WorkshopListScreen() {

    val context = LocalContext.current

    val workshops = listOf(
        Workshop(
            "Farooque Motor Garage",
            "9004943239",
            19.0760,
            72.8777
        ),
        Workshop(
            "Mumbai Auto Care",
            "9123456789",
            19.0820,
            72.8410
        ),
        Workshop(
            "nearby workshop",
            "9876543210",
            19.0750,
            72.8780
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Nearby Workshops",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        workshops.forEach { workshop ->

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = workshop.name,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.height(8.dp))

                    // 📍 Open Maps Button
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {

                            val uri = Uri.parse(
                                "geo:${workshop.latitude},${workshop.longitude}?q=${workshop.latitude},${workshop.longitude}(${workshop.name})"
                            )

                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)

                            context.startActivity(mapIntent)
                        }
                    ) {
                        Text("Open in Maps 📍")
                    }

                    Spacer(Modifier.height(8.dp))

                    // 📞 Call Button
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {

                            val intent = Intent(Intent.ACTION_DIAL)
                            intent.data = Uri.parse("tel:${workshop.phone}")
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Call ${workshop.phone} 📞")
                    }
                }
            }
        }
    }
}