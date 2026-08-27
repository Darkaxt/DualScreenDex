package com.darkaxt.dualdex.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupPickerRequestTest {
    @Test
    fun `decodes supported requests and consumes each value once`() {
        var pending: String? = SetupPickerRequest.ROMS.encoded

        val first = SetupPickerRequest.consume(
            read = { pending },
            clear = { pending = null },
        )
        val second = SetupPickerRequest.consume(
            read = { pending },
            clear = { pending = null },
        )

        assertEquals(SetupPickerRequest.ROMS, first)
        assertNull(second)
        assertEquals(SetupPickerRequest.RETROARCH, SetupPickerRequest.parse("retroarch"))
        assertNull(SetupPickerRequest.parse("unknown"))
    }
}
