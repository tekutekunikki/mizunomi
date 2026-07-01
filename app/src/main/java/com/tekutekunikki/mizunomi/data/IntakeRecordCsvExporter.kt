package com.tekutekunikki.mizunomi.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CsvHeader = "date,time,drinkType,amountMl,memo"
private val CsvTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun buildIntakeRecordsCsv(
    records: List<IntakeRecord>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = buildString {
    appendLine(CsvHeader)
    records.sortedBy { it.timestamp }.forEach { record ->
        val localDateTime = Instant.ofEpochMilli(record.timestamp)
            .atZone(zoneId)
            .toLocalDateTime()
        append(localDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
        append(',')
        append(localDateTime.toLocalTime().format(CsvTimeFormatter))
        append(',')
        append(record.drinkType.toCsvField())
        append(',')
        append(record.amountMl)
        append(',')
        append(record.memo.orEmpty().toCsvField())
        append('\n')
    }
}

private fun String.toCsvField(): String {
    if (none { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        return this
    }
    return "\"${replace("\"", "\"\"")}\""
}
