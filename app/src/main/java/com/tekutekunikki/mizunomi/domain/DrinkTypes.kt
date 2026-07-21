package com.tekutekunikki.mizunomi.domain

internal const val DrinkTypeWater = "水"
internal const val DrinkTypeTea = "お茶"
internal const val DrinkTypeCoffee = "コーヒー"
internal const val DrinkTypeJuice = "ジュース"
internal const val DrinkTypeSportsDrink = "スポーツドリンク"
internal const val DrinkTypeMilkDrink = "乳飲料"
internal const val DrinkTypeSoda = "炭酸飲料"
internal const val DrinkTypeAlcohol = "アルコール"
internal const val DrinkTypeOther = "その他"

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
