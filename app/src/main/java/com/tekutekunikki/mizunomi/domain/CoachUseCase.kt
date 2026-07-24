package com.tekutekunikki.mizunomi.domain

import com.tekutekunikki.mizunomi.data.IntakeRecord
import com.tekutekunikki.mizunomi.data.remote.GeminiClient
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CoachUseCase(
    private val geminiClient: GeminiClient,
) {
    suspend operator fun invoke(todayRecords: List<IntakeRecord>): Result<String> {
        if (todayRecords.isEmpty()) {
            return Result.success("今日はまだ記録がありません。まずは水やお茶を1杯記録してから、コーチに相談してみましょう。")
        }

        val userInput = todayRecords
            .sortedBy { it.timestamp }
            .joinToString(separator = "\n") { record ->
                "${record.timestamp.toTimeLabel()} ${record.drinkType} ${record.amountMl}ml"
            }

        return geminiClient.generateCoachCard(
            systemInstruction = CoachSystemInstruction,
            userInput = userInput,
        )
    }

    private fun Long.toTimeLabel(): String =
        Instant.ofEpochMilli(this)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(TimeFormatter)

    private companion object {
        private val TimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.JAPAN)

        private const val CoachSystemInstruction = """
あなたは水分補給アプリ「mizunomi」のコーチです。
今日の飲水記録を受け取り、以下の形式でコーチカードを1枚返してください。

【今日の達成度】目標に対する進捗を1行で
【気づき】飲むペースや偏りについての観察を1〜2行
【次の一杯】次にいつ何をどれくらい飲むべきかの具体的な提案

ルール:
- カフェイン飲料も水分としてカウントするが、利尿作用に一言触れる
- 責めない。できている点を必ず1つ拾う
- 医学的な断定や診断はしない
- 全体で150字以内
"""
    }
}
