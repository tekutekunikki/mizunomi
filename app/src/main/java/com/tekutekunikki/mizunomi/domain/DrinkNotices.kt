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
                    title = "甘い飲み物が少し多めです",
                    message = "次の一杯は水かお茶を選んでみましょう。",
                ),
            )
        }
        if (hasAlcohol) {
            add(
                DrinkNotice(
                    title = "アルコールの記録があります",
                    message = "今日は水も一緒にとっておきましょう。",
                ),
            )
        }
        if (
            todayTotalMl >= BalancedDrinkTotalThresholdMl &&
            waterTeaTotalMl < WaterTeaMinimumThresholdMl
        ) {
            add(
                DrinkNotice(
                    title = "水・お茶が少なめです",
                    message = "次はやさしく一杯足しておきましょう。",
                ),
            )
        }
    }
}
