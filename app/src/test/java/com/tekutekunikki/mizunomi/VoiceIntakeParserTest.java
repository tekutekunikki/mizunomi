package com.tekutekunikki.mizunomi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VoiceIntakeParserTest {
    @Test
    public void parsesWaterAmount() {
        VoiceIntakeCandidate candidate = MainActivityKt.parseVoiceIntake("\u6C34300");

        assertEquals("\u6C34", candidate.getDrinkType());
        assertEquals(300, candidate.getAmountMl());
    }

    @Test
    public void skipsTimeAndUsesFirstValidAmount() {
        VoiceIntakeCandidate candidate = MainActivityKt.parseVoiceIntake("13\u6642\u306B300\u98F2\u3093\u3060");

        assertEquals(300, candidate.getAmountMl());
    }

    @Test
    public void returnsNullWhenAmountIsTooLarge() {
        assertNull(MainActivityKt.parseVoiceIntake("\u6C343000000"));
    }

    @Test
    public void returnsNullWhenAmountIsBelowMinimum() {
        assertNull(MainActivityKt.parseVoiceIntake("\u6C3430"));
    }
}
