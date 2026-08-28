package com.darkaxt.dualdex.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SafProviderResultsTest {
    @Test
    fun `rejects a null child query as a sanitized provider failure`() {
        val failure = assertThrows(SafProviderFailure::class.java) {
            SafProviderResults.requireValue<String>(null, "SAF provider did not return child documents")
        }

        assertEquals("SAF provider did not return child documents", failure.message)
    }
}
