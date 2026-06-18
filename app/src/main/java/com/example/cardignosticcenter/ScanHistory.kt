package com.example.cardignosticcenter.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistory(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val vin: String,
    val date: String,
    val healthScore: Int,
    val severity: String,
    val faultSummary: String
)