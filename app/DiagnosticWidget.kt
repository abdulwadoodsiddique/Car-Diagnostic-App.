package com.example.cardignosticcenter

import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.unit.dp

object DiagnosticWidget : GlanceAppWidget() {
    override suspend fun Content() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Diagnostics Ready")
            Text(text = "Tap to run check")
        }
    }
}
