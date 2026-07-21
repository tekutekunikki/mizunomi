package com.tekutekunikki.mizunomi.data

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

const val MaxCsvImportBytes = 5 * 1024 * 1024
private const val MaxCsvDataRows = 10_000
private const val MaxMemoLength = 100
private const val MaxVisibleErrors = 20
private val StrictDateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    .withResolverStyle(ResolverStyle.STRICT)
private val StrictTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withResolverStyle(ResolverStyle.STRICT)
private val KnownDrinkTypes = setOf(
    "水",
    "お茶",
    "コーヒー",
    "ジュース",
    "スポーツドリンク",
    "乳飲料",
    "炭酸飲料",
    "アルコール",
    "その他",
)

data class CsvImportRowError(
    val rowNumber: Int,
    val reason: String,
)

data class CsvImportPreview(
    val records: List<IntakeRecord>,
    val skippedRowCount: Int,
    val duplicateCount: Int,
    val unknownDrinkTypeCount: Int,
    val totalErrorCount: Int,
    val errors: List<CsvImportRowError>,
) {
    val hiddenErrorCount: Int = (totalErrorCount - errors.size).coerceAtLeast(0)
}

class CsvImportException(message: String) : IllegalArgumentException(message)

fun decodeUtf8Csv(bytes: ByteArray): String = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: Exception) {
    throw CsvImportException("UTF-8形式のCSVではありません")
}

fun parseIntakeRecordsCsv(
    csvText: String,
    existingRecords: List<IntakeRecord>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CsvImportPreview {
    val rows = parseCsvRows(csvText)
    if (rows.isEmpty()) throw CsvImportException("ヘッダーがありません")

    val header = rows.first().mapIndexed { index, value ->
        if (index == 0) value.removePrefix("﻿") else value
    }
    val columnIndexes = header.withIndex().associate { it.value to it.index }
    listOf("date", "time", "drinkType", "amountMl").forEach { requiredColumn ->
        if (requiredColumn !in columnIndexes) {
            throw CsvImportException("必須列 $requiredColumn がありません")
        }
    }

    val dataRows = rows.drop(1)
        .mapIndexed { index, row -> IndexedCsvRow(index + 2, row) }
        .filterNot { indexedRow -> indexedRow.values.all { it.isBlank() } }
    if (dataRows.size > MaxCsvDataRows) {
        throw CsvImportException("データ行が10,000行を超えています")
    }

    val existingKeys = existingRecords.mapTo(mutableSetOf()) { record ->
        val dateTime = Instant.ofEpochMilli(record.timestamp).atZone(zoneId).toLocalDateTime()
        DuplicateKey(
            date = dateTime.toLocalDate(),
            time = dateTime.toLocalTime().withSecond(0).withNano(0),
            drinkType = record.drinkType,
            amountMl = record.amountMl,
        )
    }
    val csvKeys = mutableSetOf<DuplicateKey>()
    val records = mutableListOf<IntakeRecord>()
    val visibleErrors = mutableListOf<CsvImportRowError>()
    var totalErrorCount = 0
    var duplicateCount = 0
    var unknownDrinkTypeCount = 0

    fun addRowError(rowNumber: Int, reason: String) {
        totalErrorCount += 1
        if (visibleErrors.size < MaxVisibleErrors) {
            visibleErrors += CsvImportRowError(rowNumber, reason)
        }
    }

    dataRows.forEach { indexedRow ->
        val rowNumber = indexedRow.rowNumber
        val row = indexedRow.values
        fun value(column: String): String? = columnIndexes[column]?.let { columnIndex ->
            row.getOrNull(columnIndex)
        }

        if (row.size > header.size) {
            addRowError(rowNumber, "列数がヘッダーと一致しません")
            return@forEach
        }

        val date = value("date")?.let(::parseDateOrNull)
        if (date == null) {
            addRowError(rowNumber, "date の形式が不正です")
            return@forEach
        }
        val time = value("time")?.let(::parseTimeOrNull)
        if (time == null) {
            addRowError(rowNumber, "time の形式が不正です")
            return@forEach
        }

        val amountText = value("amountMl").orEmpty()
        val amountMl = amountText.toIntOrNull()
        if (amountMl == null || amountMl !in 1..5000 || amountText.contains('.')) {
            val reason = when {
                amountText.isBlank() -> "amountMl が空です"
                amountMl != null && amountMl > 5000 -> "amountMl が 5000ml を超えています"
                else -> "amountMl は1〜5000の整数で入力してください"
            }
            addRowError(rowNumber, reason)
            return@forEach
        }

        val memo = value("memo").orEmpty()
        if (memo.length > MaxMemoLength) {
            addRowError(rowNumber, "memo が100文字を超えています")
            return@forEach
        }

        val originalDrinkType = value("drinkType").orEmpty().trim()
        val isUnknownDrinkType = originalDrinkType !in KnownDrinkTypes
        val drinkType = originalDrinkType.takeIf { !isUnknownDrinkType } ?: "その他"
        val key = DuplicateKey(date, time, drinkType, amountMl)
        if (key in existingKeys || !csvKeys.add(key)) {
            duplicateCount += 1
            return@forEach
        }

        val timestamp = LocalDateTime.of(date, time)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        records += IntakeRecord(
            drinkType = drinkType,
            amountMl = amountMl,
            timestamp = timestamp,
            memo = memo.ifBlank { null },
        )
        if (isUnknownDrinkType) unknownDrinkTypeCount += 1
    }

    return CsvImportPreview(
        records = records,
        skippedRowCount = totalErrorCount + duplicateCount,
        duplicateCount = duplicateCount,
        unknownDrinkTypeCount = unknownDrinkTypeCount,
        totalErrorCount = totalErrorCount,
        errors = visibleErrors,
    )
}

private fun parseDateOrNull(value: String): LocalDate? = try {
    LocalDate.parse(value.trim(), StrictDateFormatter)
} catch (_: DateTimeParseException) {
    null
}

private fun parseTimeOrNull(value: String): LocalTime? = try {
    LocalTime.parse(value.trim(), StrictTimeFormatter)
} catch (_: DateTimeParseException) {
    null
}

private fun parseCsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var closedQuote = false
    var index = 0

    fun finishField() {
        row += field.toString()
        field.clear()
        closedQuote = false
    }

    fun finishRow() {
        finishField()
        rows += row
        row = mutableListOf()
    }

    while (index < text.length) {
        val char = text[index]
        if (inQuotes) {
            if (char == '"') {
                if (index + 1 < text.length && text[index + 1] == '"') {
                    field.append('"')
                    index += 1
                } else {
                    inQuotes = false
                    closedQuote = true
                }
            } else {
                field.append(char)
            }
        } else {
            when (char) {
                '"' -> {
                    if (field.isNotEmpty() || closedQuote) {
                        throw CsvImportException("CSVの引用符の形式が不正です")
                    }
                    inQuotes = true
                }

                ',' -> finishField()
                '\n' -> finishRow()
                '\r' -> {
                    finishRow()
                    if (index + 1 < text.length && text[index + 1] == '\n') index += 1
                }

                else -> {
                    if (closedQuote) throw CsvImportException("CSVの引用符の後に不正な文字があります")
                    field.append(char)
                }
            }
        }
        index += 1
    }

    if (inQuotes) throw CsvImportException("CSVの引用符が閉じられていません")
    if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) finishRow()
    return rows
}

private data class DuplicateKey(
    val date: LocalDate,
    val time: LocalTime,
    val drinkType: String,
    val amountMl: Int,
)

private data class IndexedCsvRow(
    val rowNumber: Int,
    val values: List<String>,
)
