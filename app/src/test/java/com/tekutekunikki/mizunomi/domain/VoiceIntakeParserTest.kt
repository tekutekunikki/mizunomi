package com.tekutekunikki.mizunomi.domain

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class VoiceIntakeParserTest {
    @Test
    fun parsesSupportedVoiceInputs() {
        parseVoiceIntake("\u6C34300")!!.also {
            assertEquals(DrinkTypeWater, it.drinkType)
            assertEquals(300, it.amountMl)
        }
        parseVoiceIntake("\u30DD\u30AB\u30EA500")!!.also {
            assertEquals(DrinkTypeSportsDrink, it.drinkType)
            assertEquals(500, it.amountMl)
        }
        parseVoiceIntake("\u30B3\u30FC\u30D2\u30FC\u725B\u4E73200")!!.also {
            assertEquals(DrinkTypeMilkDrink, it.drinkType)
            assertEquals(200, it.amountMl)
        }
        parseVoiceIntake("\u304A\u8336\uFF15\uFF10\uFF10")!!.also {
            assertEquals(DrinkTypeTea, it.drinkType)
            assertEquals(500, it.amountMl)
        }
    }

    @Test
    fun skipsTimeAndUsesFirstValidAmount() {
        assertEquals(300, parseVoiceIntake("13\u6642\u306B300\u98F2\u3093\u3060")!!.amountMl)
    }

    @Test
    fun returnsNullForMissingOrOutOfRangeAmount() {
        assertNull(parseVoiceIntake("\u6C34\u3092\u98F2\u3093\u3060"))
        assertNull(parseVoiceIntake("\u6C3430"))
        assertNull(parseVoiceIntake("\u6C343000000"))
    }
}
