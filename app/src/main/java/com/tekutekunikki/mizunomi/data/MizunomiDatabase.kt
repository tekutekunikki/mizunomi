package com.tekutekunikki.mizunomi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IntakeRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class MizunomiDatabase : RoomDatabase() {
    abstract fun intakeRecordDao(): IntakeRecordDao

    companion object {
        @Volatile
        private var instance: MizunomiDatabase? = null

        fun getInstance(context: Context): MizunomiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MizunomiDatabase::class.java,
                    "mizunomi.db",
                ).build().also { instance = it }
            }
    }
}
