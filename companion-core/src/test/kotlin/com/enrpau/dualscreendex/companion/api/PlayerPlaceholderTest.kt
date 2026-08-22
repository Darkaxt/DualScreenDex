package com.enrpau.dualscreendex.companion.api

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPlaceholderTest {
    @Test
    fun replacesPossessivePlayerPlaceholderWithoutARegularExpression() {
        assertEquals("Your House", resolvePlayerPlaceholder("{PLAYER}'s House", null))
        assertEquals("May's House", resolvePlayerPlaceholder("{PLAYER}'s House", "May"))
    }
}
