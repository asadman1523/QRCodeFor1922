package com.jack.qrcodefor1922.ui.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

enum class TYPE {SMS_1922, TEXT, REDIRECT}
@Entity
data class ScanResult (
    @PrimaryKey(autoGenerate = true) val uid: Int,
    @ColumnInfo(defaultValue = "0") val timestamp: Date,
    @ColumnInfo val content: String,
    @ColumnInfo val type: TYPE
        ) {
    constructor(date:Date, content: String, type: TYPE) : this(0, date, content, type)
}