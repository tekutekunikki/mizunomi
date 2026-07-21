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
            "\u30DD\u30AB\u30EA",
            "\u30DD\u30AB\u30EA\u30B9\u30A8\u30C3\u30C8",
            "\u30A2\u30AF\u30A8\u30EA\u30A2\u30B9",
            "\u30B9\u30DD\u30FC\u30C4\u30C9\u30EA\u30F3\u30AF",
        ) -> DrinkTypeSportsDrink
        text.containsAny("\u30B3\u30FC\u30E9", "\u70AD\u9178") -> DrinkTypeSoda
        text.containsAny(
            "\u725B\u4E73",
            "\u30AB\u30C4\u30B2\u30F3",
            "\u30B3\u30FC\u30D2\u30FC\u725B\u4E73",
            "\u4E73\u98F2\u6599",
        ) -> DrinkTypeMilkDrink
        text.containsAny(
            "\u30D3\u30FC\u30EB",
            "\u9152",
            "\u30EF\u30A4\u30F3",
            "\u30CF\u30A4\u30DC\u30FC\u30EB",
        ) -> DrinkTypeAlcohol
        text.containsAny(
            "\u5348\u5F8C\u30C6\u30A3\u30FC",
            "\u5348\u5F8C\u306E\u7D05\u8336",
            "\u30B8\u30E5\u30FC\u30B9",
            "\u30AA\u30EC\u30F3\u30B8",
            "\u308A\u3093\u3054",
        ) -> DrinkTypeJuice
        text.containsAny("\u304A\u8336", "\u7DD1\u8336", "\u9EA6\u8336") -> DrinkTypeTea
        text.containsAny("\u30B3\u30FC\u30D2\u30FC", "\u30D6\u30E9\u30C3\u30AF") -> DrinkTypeCoffee
        text.contains("\u6C34") -> DrinkTypeWater
        text.contains("\u305D\u306E\u4ED6") -> DrinkTypeOther
        else -> DrinkTypeOther
    }

internal fun String.containsAny(vararg keywords: String): Boolean =
    keywords.any { contains(it) }

internal fun String.normalizeDigits(): String =
    map { char ->
        when (char) {
            in '\uFF10'..'\uFF19' -> '0' + (char - '\uFF10')
            else -> char
        }
    }.joinToString(separator = "")
