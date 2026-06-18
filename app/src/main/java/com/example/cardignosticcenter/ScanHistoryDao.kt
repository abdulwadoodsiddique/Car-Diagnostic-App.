package com.example.cardignosticcenter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.example.cardignosticcenter.data.ScanHistory

@Dao
interface ScanHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistory)

    @Query("SELECT * FROM scan_history ORDER BY id DESC")
    fun getAllScans(): Flow<List<ScanHistory>>

    @Query("DELETE FROM scan_history")
    suspend fun deleteAll()
}