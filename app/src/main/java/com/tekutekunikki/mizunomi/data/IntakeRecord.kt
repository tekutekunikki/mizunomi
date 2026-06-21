package com.tekutekunikki.mizunomi.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intake_records")
data class IntakeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "drink_type")
    val drinkType: String,
    @ColumnInfo(name = "amount_ml")
    val amountMl: Int,
    val timestamp: Long,
    val memo: String?,
)
