package com.tekutekunikki.mizunomi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeRecordDao {
    @Insert
    suspend fun insert(record: IntakeRecord): Long

    @Query(
        """
        SELECT *
        FROM intake_records
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
        ORDER BY timestamp DESC
        """,
    )
    fun observeRecordsForDay(startMillis: Long, endMillis: Long): Flow<List<IntakeRecord>>

    @Query(
        """
        SELECT *
        FROM intake_records
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
        ORDER BY timestamp ASC
        """,
    )
    fun observeRecordsForRange(startMillis: Long, endMillis: Long): Flow<List<IntakeRecord>>

    @Query(
        """
        SELECT *
        FROM intake_records
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getAllRecords(): List<IntakeRecord>

    @Query(
        """
        SELECT COALESCE(SUM(amount_ml), 0)
        FROM intake_records
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
        """,
    )
    fun observeTotalAmountForDay(startMillis: Long, endMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(amount_ml), 0)
        FROM intake_records
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
        """,
    )
    suspend fun getTotalAmountForDay(startMillis: Long, endMillis: Long): Int

    @Delete
    suspend fun delete(record: IntakeRecord)

    @Update
    suspend fun update(record: IntakeRecord)

    @Query("DELETE FROM intake_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
