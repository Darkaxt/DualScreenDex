package com.darkaxt.dualdex.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelUpRulesetDetectionFingerprintTest {
    private val selectors = listOf(
        SaveByteSelector("original", 0x3DA6, 0x02, 0x00),
        SaveByteSelector("modern", 0x3DA6, 0x02, 0x02),
    )

    @Test
    fun isStableAcrossDescriptorOrderButBoundToDescriptorsAndOutcome() {
        val current = LevelUpRulesetDetectionFingerprint.create(selectors, "modern")

        assertEquals(current, LevelUpRulesetDetectionFingerprint.create(selectors.reversed(), "modern"))
        assertNotEquals(
            current,
            LevelUpRulesetDetectionFingerprint.create(
                selectors.map { it.copy(saveBlock1ByteOffset = it.saveBlock1ByteOffset + 1) },
                "modern",
            ),
        )
        assertNotEquals(current, LevelUpRulesetDetectionFingerprint.create(selectors, "original"))
    }

    @Test
    fun refusesIncompleteOrMalformedProvenanceInputs() {
        assertNull(LevelUpRulesetDetectionFingerprint.create(emptyList(), "modern"))
        assertNull(LevelUpRulesetDetectionFingerprint.create(selectors, "missing"))
        assertNull(LevelUpRulesetDetectionFingerprint.create(selectors + selectors.first(), "modern"))
        assertNull(
            LevelUpRulesetDetectionFingerprint.create(
                selectors.map { it.copy(mask = 0x03) },
                "modern",
            ),
        )
    }
}
