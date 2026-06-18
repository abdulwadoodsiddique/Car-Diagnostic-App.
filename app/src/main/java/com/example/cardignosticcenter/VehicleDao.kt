package com.example.cardignosticcenter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VehicleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehicles(): List<Vehicle>

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)
}