package com.darkaxt.dualdex.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredSafIndexEligibilityTest {
    @Test
    fun onlyTheExactCurrentReadGrantMakesAStoredIndexEligible() {
        val stored = "content://provider/tree/roms"

        assertFalse(StoredSafIndexEligibility.isEligible(stored, emptySet()))
        assertFalse(
            StoredSafIndexEligibility.isEligible(
                stored,
                setOf("content://provider/tree/roms-child", "content://other/tree/roms"),
            ),
        )
        assertTrue(StoredSafIndexEligibility.isEligible(stored, setOf(stored)))
    }
}
