package com.tekutekunikki.mizunomi.domain

internal const val DrinkTypeWater = "\u6C34"
internal const val DrinkTypeTea = "\u304A\u8336"
internal const val DrinkTypeCoffee = "\u30B3\u30FC\u30D2\u30FC"
internal const val DrinkTypeJuice = "\u30B8\u30E5\u30FC\u30B9"
internal const val DrinkTypeSportsDrink = "\u30B9\u30DD\u30FC\u30C4\u30C9\u30EA\u30F3\u30AF"
internal const val DrinkTypeMilkDrink = "\u4E73\u98F2\u6599"
internal const val DrinkTypeSoda = "\u70AD\u9178\u98F2\u6599"
internal const val DrinkTypeAlcohol = "\u30A2\u30EB\u30B3\u30FC\u30EB"
internal const val DrinkTypeOther = "\u305D\u306E\u4ED6"

internal val DrinkTypes = listOf(
    DrinkTypeWater,
    DrinkTypeTea,
    DrinkTypeCoffee,
    DrinkTypeJuice,
    DrinkTypeSportsDrink,
    DrinkTypeMilkDrink,
    DrinkTypeSoda,
    DrinkTypeAlcohol,
    DrinkTypeOther,
)

internal val SweetDrinkTypes = setOf(
    DrinkTypeJuice,
    DrinkTypeSportsDrink,
    DrinkTypeMilkDrink,
    DrinkTypeSoda,
)

internal val WaterTeaDrinkTypes = setOf(
    DrinkTypeWater,
    DrinkTypeTea,
)
