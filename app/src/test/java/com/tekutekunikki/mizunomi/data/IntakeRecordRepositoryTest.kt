package com.tekutekunikki.mizunomi.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import org.junit.Assert.assertEquals

class IntakeRecordRepositoryTest {
    @Test
    fun observeRecordsForDayUsesClockZoneDayRange() {
        val dao = CapturingDao()
        val zoneId = ZoneId.of("Asia/Tokyo")
        val repository = IntakeRecordRepository(
            dao = dao,
            clock = Clock.fixed(Instant.parse("2026-07-20T10:15:00Z"), zoneId),
        )

        repository.observeRecordsForDay(LocalDate.of(2026, 7, 20))

        assertEquals(Instant.parse("2026-07-19T15:00:00Z").toEpochMilli(), dao.startMillis)
        assertEquals(Instant.parse("2026-07-20T15:00:00Z").toEpochMilli(), dao.endMillis)
    }

    @Test
    fun observeRecordsForWeekContainingUsesMondayToMondayRange() {
        val dao = CapturingDao()
        val zoneId = ZoneId.of("Asia/Tokyo")
        val repository = IntakeRecordRepository(
            dao = dao,
            clock = Clock.fixed(Instant.parse("2026-07-20T10:15:00Z"), zoneId),
        )

        repository.observeRecordsForWeekContaining(LocalDate.of(2026, 7, 22))

        assertEquals(Instant.parse("2026-07-19T15:00:00Z").toEpochMilli(), dao.startMillis)
        assertEquals(Instant.parse("2026-07-26T15:00:00Z").toEpochMilli(), dao.endMillis)
    }

    private class CapturingDao : IntakeRecordDao {
        var startMillis: Long = 0
        var endMillis: Long = 0

        override fun observeRecordsForDay(startMillis: Long, endMillis: Long): Flow<List<IntakeRecord>> {
            capture(startMillis, endMillis)
            return emptyFlow()
        }

        override fun observeRecordsForRange(startMillis: Long, endMillis: Long): Flow<List<IntakeRecord>> {
            capture(startMillis, endMillis)
            return emptyFlow()
        }

        override fun observeTotalAmountForDay(startMillis: Long, endMillis: Long): Flow<Int> {
            capture(startMillis, endMillis)
            return emptyFlow()
        }

        override suspend fun getTotalAmountForDay(startMillis: Long, endMillis: Long): Int {
            capture(startMillis, endMillis)
            return 0
        }

        override suspend fun getAllRecords(): List<IntakeRecord> = emptyList()
        override suspend fun insert(record: IntakeRecord): Long = 0
        override suspend fun insertAll(records: List<IntakeRecord>): List<Long> = emptyList()
        override suspend fun delete(record: IntakeRecord) = Unit
        override suspend fun update(record: IntakeRecord) = Unit
        override suspend fun deleteById(id: Long) = Unit

        private fun capture(startMillis: Long, endMillis: Long) {
            this.startMillis = startMillis
            this.endMillis = endMillis
        }
    }
}
