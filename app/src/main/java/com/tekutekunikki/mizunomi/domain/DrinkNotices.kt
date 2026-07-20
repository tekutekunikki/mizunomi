package com.tekutekunikki.mizunomi.domain

import com.tekutekunikki.mizunomi.data.IntakeRecord

internal const val SweetDrinkWarningThresholdMl = 500
internal const val BalancedDrinkTotalThresholdMl = 1_000
internal const val WaterTeaMinimumThresholdMl = 500

internal data class DrinkNotice(
    val title: String,
    val message: String,
)

internal fun buildDrinkNotices(
    records: List<IntakeRecord>,
    todayTotalMl: Int,
): List<DrinkNotice> {
    val sweetDrinkTotalMl = records
        .filter { it.drinkType in SweetDrinkTypes }
        .sumOf { it.amountMl }
    val hasAlcohol = records.any { it.drinkType == DrinkTypeAlcohol }
    val waterTeaTotalMl = records
        .filter { it.drinkType in WaterTeaDrinkTypes }
        .sumOf { it.amountMl }

    return buildList {
        if (sweetDrinkTotalMl >= SweetDrinkWarningThresholdMl) {
            add(
                DrinkNotice(
                    title = "\u7518\u3044\u98F2\u307F\u7269\u304C\u5C11\u3057\u591A\u3081\u3067\u3059",
                    message = "\u6B21\u306E\u4E00\u676F\u306F\u6C34\u304B\u304A\u8336\u3092\u9078\u3093\u3067\u307F\u307E\u3057\u3087\u3046\u3002",
                ),
            )
        }
        if (hasAlcohol) {
            add(
                DrinkNotice(
                    title = "\u30A2\u30EB\u30B3\u30FC\u30EB\u306E\u8A18\u9332\u304C\u3042\u308A\u307E\u3059",
                    message = "\u4ECA\u65E5\u306F\u6C34\u3082\u4E00\u7DD2\u306B\u3068\u3063\u3066\u304A\u304D\u307E\u3057\u3087\u3046\u3002",
                ),
            )
        }
        if (
            todayTotalMl >= BalancedDrinkTotalThresholdMl &&
            waterTeaTotalMl < WaterTeaMinimumThresholdMl
        ) {
            add(
                DrinkNotice(
                    title = "\u6C34\u30FB\u304A\u8336\u304C\u5C11\u306A\u3081\u3067\u3059",
                    message = "\u6B21\u306F\u3084\u3055\u3057\u304F\u4E00\u676F\u8DB3\u3057\u3066\u304A\u304D\u307E\u3057\u3087\u3046\u3002",
                ),
            )
        }
    }
}
