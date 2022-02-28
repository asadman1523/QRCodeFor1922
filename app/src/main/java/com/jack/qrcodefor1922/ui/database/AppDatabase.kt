package com.jack.qrcodefor1922.ui.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = 2,
    entities = [ScanResult::class],
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
                     ],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resultDao(): ScanResultDao
}