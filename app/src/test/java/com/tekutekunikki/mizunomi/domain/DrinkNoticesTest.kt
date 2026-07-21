package com.tekutekunikki.mizunomi.domain

import com.tekutekunikki.mizunomi.data.IntakeRecord
import org.junit.Test
import org.junit.Assert.assertEquals

class DrinkNoticesTest {
    @Test
    fun sweetDrinkNoticeStartsAtFiveHundredMl() {
        assertEquals(
            1,
            buildDrinkNotices(
                records = listOf(record(DrinkTypeJuice, 500)),
                todayTotalMl = 500,
            ).size,
        )
    }

    @Test
    fun alcoholNoticeIsShown() {
        assertEquals(
            1,
            buildDrinkNotices(
                records = listOf(record(DrinkTypeAlcohol, 100)),
                todayTotalMl = 100,
            ).size,
        )
    }

    @Test
    fun waterTeaNoticeStartsAtThousandMlWhenWaterTeaIsLow() {
        assertEquals(
            2,
            buildDrinkNotices(
                records = listOf(
                    record(DrinkTypeJuice, 500),
                    record(DrinkTypeCoffee, 500),
                ),
                todayTotalMl = 1_000,
            ).size,
        )
    }

    private fun record(drinkType: String, amountMl: Int) =
        IntakeRecord(id = 0, drinkType = drinkType, amountMl = amountMl, timestamp = 0, memo = null)
}
