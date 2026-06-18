package com.example.cardignosticcenter

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import android.content.Intent

class DiagnosticWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Loop through each widget and update them
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_diagnostic)

            // Set your desired text or other updates here
            views.setTextViewText(R.id.widget_title, "Diagnostics Ready")
            views.setTextViewText(R.id.widget_description, "Tap to run check")

            // Apply the changes
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    // Optionally: override other methods like onEnabled(), onDisabled(), etc.
}
