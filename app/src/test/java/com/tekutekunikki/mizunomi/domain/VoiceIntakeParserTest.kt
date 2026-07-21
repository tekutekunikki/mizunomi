package com.tekutekunikki.mizunomi.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class VoiceIntakeParserTest {
    @Test
    fun parsesSupportedVoiceInputs() {
        parseVoiceIntake("水300")!!.also {
            assertEquals(DrinkTypeWater, it.drinkType)
            assertEquals(300, it.amountMl)
        }
        parseVoiceIntake("ポカリ500")!!.also {
            assertEquals(DrinkTypeSportsDrink, it.drinkType)
            assertEquals(500, it.amountMl)
        }
        parseVoiceIntake("コーヒー牛乳200")!!.also {
            assertEquals(DrinkTypeMilkDrink, it.drinkType)
            assertEquals(200, it.amountMl)
        }
        parseVoiceIntake("お茶５００")!!.also {
            assertEquals(DrinkTypeTea, it.drinkType)
            assertEquals(500, it.amountMl)
        }
    }

    @Test
    fun skipsTimeAndUsesFirstValidAmount() {
        assertEquals(300, parseVoiceIntake("13時に300飲んだ")!!.amountMl)
    }

    @Test
    fun returnsNullForMissingOrOutOfRangeAmount() {
        assertNull(parseVoiceIntake("水を飲んだ"))
        assertNull(parseVoiceIntake("水30"))
        assertNull(parseVoiceIntake("水3000000"))
    }
}
