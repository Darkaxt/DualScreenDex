package com.darkaxt.dualdex.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class PrivacySafeDiagnosticsTest {
    @Test
    fun `log formatting keeps only bounded categories and coarse failure classes`() {
        val privateFailure = IllegalStateException(
            "trainer=12345 money=3000 x=8 y=9 flags=255 sha256=${"a".repeat(64)} path=D:/private/game.gba",
        )

        val rendered = PrivacySafeDiagnostics.message(
            category = "CATALOG_CACHE",
            outcome = "REJECTED_EXCEPTION",
            failure = privateFailure,
        )

        assertEquals(
            "category=CATALOG_CACHE outcome=REJECTED_EXCEPTION failure=INVALID_STATE",
            rendered,
        )
        assertFalse(rendered.contains("12345"))
        assertFalse(rendered.contains("3000"))
        assertFalse(rendered.contains("D:/private"))
        assertFalse(rendered.contains("a".repeat(64)))
    }

    @Test
    fun `invalid labels cannot inject raw console or source text`() {
        val rendered = PrivacySafeDiagnostics.message(
            category = "console D:/private/app.js",
            outcome = "money=3000",
            failure = IOException("/data/user/0/private.db"),
        )

        assertEquals("category=UNKNOWN outcome=UNKNOWN failure=IO_FAILURE", rendered)
    }
}
