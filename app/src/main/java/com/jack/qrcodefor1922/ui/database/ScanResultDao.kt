package com.jack.qrcodefor1922.ui.database

import androidx.room.*

@Dao
interface ScanResultDao {
    @Query("SELECT * FROM scanresult ORDER BY id DESC")
    suspend fun getAll(): List<ScanResult>

    @Insert
    suspend fun insertAll(vararg results: ScanResult)


    @Delete
    suspend fun delete(result: ScanResult)
}