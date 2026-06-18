package com.example.cardignosticcenter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiveRpmGraph(rpm: Int?) {

    val rpmValues = remember { mutableStateListOf<Float>() }

    rpm?.let {
        if (rpmValues.size > 50) {
            rpmValues.removeAt(0)   // ✅ FIXED
        }
        rpmValues.add(it.toFloat())
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {

        if (rpmValues.size < 2) return@Canvas

        val maxRpm = 8000f
        val widthStep = size.width / (rpmValues.size - 1)

        for (i in 0 until rpmValues.size - 1) {

            val startX = i * widthStep
            val endX = (i + 1) * widthStep

            val startY = size.height - (rpmValues[i] / maxRpm * size.height)
            val endY = size.height - (rpmValues[i + 1] / maxRpm * size.height)

            drawLine(
                color = Color.Red,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 4f
            )
        }
    }
}