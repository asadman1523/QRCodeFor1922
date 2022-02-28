package com.jack.qrcodefor1922

import android.content.Context
import androidx.room.Room
import com.jack.qrcodefor1922.ui.database.AppDatabase
import com.jack.qrcodefor1922.ui.database.ScanResultDao

object Utils {
    private val DB_NAME: String = "qrcode1922.db"

    fun getDatabaseDao(applicationContext: Context): ScanResultDao {
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, DB_NAME).build()
        return db.resultDao()
    }
}