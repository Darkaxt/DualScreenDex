package com.darkaxt.dualdex.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ThorFocusCommandTest {
    @Test
    fun readUsesTheSecureScreenFocusSetting() {
        assertEquals(
            listOf("/system/bin/settings", "get", "secure", "screen_focus_lock"),
            ThorFocusCommand.read(),
        )
    }

    @Test
    fun writeUsesTheSecureScreenFocusSettingForEveryAllowedMode() {
        for (mode in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM) {
            assertEquals(
                listOf(
                    "/system/bin/settings",
                    "put",
                    "secure",
                    "screen_focus_lock",
                    mode.toString(),
                ),
                ThorFocusCommand.write(mode),
            )
        }
    }

    @Test
    fun writeRejectsModesOutsideTheAllowlist() {
        assertThrows(IllegalArgumentException::class.java) {
            ThorFocusCommand.write(3)
        }
    }

    @Test
    fun parseAcceptsOnlyTrimmedAllowedModes() {
        assertEquals(ThorFocusMode.AUTO, ThorFocusCommand.parse(" 0\n"))
        assertEquals(ThorFocusMode.TOP, ThorFocusCommand.parse("\t1 "))
        assertEquals(ThorFocusMode.BOTTOM, ThorFocusCommand.parse("2\r\n"))
        assertNull(ThorFocusCommand.parse("3"))
        assertNull(ThorFocusCommand.parse("1 extra"))
        assertNull(ThorFocusCommand.parse(""))
    }
}
