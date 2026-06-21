package com.tekutekunikki.mizunomi.data

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class IntakeRecordRepository(
    private val dao: IntakeRecordDao,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeTodayRecords(): Flow<List<IntakeRecord>> =
        observeRecordsForDay(LocalDate.now(clock))

    fun observeRecordsForDay(date: LocalDate): Flow<List<IntakeRecord>> {
        val range = date.toMillisRange(clock.zone)
        return dao.observeRecordsForDay(range.startMillis, range.endMillis)
    }

    fun observeTotalAmountForDay(date: LocalDate): Flow<Int> {
        val range = date.toMillisRange(clock.zone)
        return dao.observeTotalAmountForDay(range.startMillis, range.endMillis)
    }

    suspend fun addRecord(
        drinkType: String,
        amountMl: Int,
        timestamp: Long = clock.millis(),
        memo: String? = null,
    ): Long =
        dao.insert(
            IntakeRecord(
                drinkType = drinkType,
                amountMl = amountMl,
                timestamp = timestamp,
                memo = memo,
            ),
        )

    suspend fun deleteRecord(record: IntakeRecord) {
        dao.delete(record)
    }

    suspend fun deleteRecordById(id: Long) {
        dao.deleteById(id)
    }

    private fun LocalDate.toMillisRange(zoneId: ZoneId): DayMillisRange {
        val start = atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return DayMillisRange(startMillis = start, endMillis = end)
    }

    private data class DayMillisRange(
        val startMillis: Long,
        val endMillis: Long,
    )
}
