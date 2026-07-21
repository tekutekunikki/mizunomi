package com.tekutekunikki.mizunomi.domain

internal const val VoiceAmountMinMl = 50
internal const val VoiceAmountMaxMl = 2_000

internal data class VoiceIntakeCandidate(
    val drinkType: String,
    val amountMl: Int,
    val rawText: String,
)

internal fun parseVoiceIntake(text: String): VoiceIntakeCandidate? {
    val normalizedText = text.normalizeDigits()
    val amountMl = Regex("\\d+")
        .findAll(normalizedText)
        .mapNotNull { match -> match.value.toIntOrNull() }
        .firstOrNull { amount -> amount in VoiceAmountMinMl..VoiceAmountMaxMl }
        ?: return null

    return VoiceIntakeCandidate(
        drinkType = classifyVoiceDrinkType(normalizedText),
        amountMl = amountMl,
        rawText = text,
    )
}

internal fun classifyVoiceDrinkType(text: String): String =
    when {
        text.containsAny(
            "ポカリ",
            "ポカリスエット",
            "アクエリアス",
            "スポーツドリンク",
        ) -> DrinkTypeSportsDrink
        text.containsAny("コーラ", "炭酸") -> DrinkTypeSoda
        text.containsAny(
            "牛乳",
            "カツゲン",
            "コーヒー牛乳",
            "乳飲料",
        ) -> DrinkTypeMilkDrink
        text.containsAny(
            "ビール",
            "酒",
            "ワイン",
            "ハイボール",
        ) -> DrinkTypeAlcohol
        text.containsAny(
            "午後ティー",
            "午後の紅茶",
            "ジュース",
            "オレンジ",
            "りんご",
        ) -> DrinkTypeJuice
        text.containsAny("お茶", "緑茶", "麦茶") -> DrinkTypeTea
        text.containsAny("コーヒー", "ブラック") -> DrinkTypeCoffee
        text.contains("水") -> DrinkTypeWater
        text.contains("その他") -> DrinkTypeOther
        else -> DrinkTypeOther
    }

internal fun String.containsAny(vararg keywords: String): Boolean =
    keywords.any { contains(it) }

internal fun String.normalizeDigits(): String =
    map { char ->
        when (char) {
            in '０'..'９' -> '0' + (char - '０')
            else -> char
        }
    }.joinToString(separator = "")
