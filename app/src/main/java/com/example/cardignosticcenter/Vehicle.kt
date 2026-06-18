package com.example.cardignosticcenter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(

    @PrimaryKey
    val vin: String,     // Unique Vehicle ID

    val ownerName: String,

    val model: String,

    val year: String
)