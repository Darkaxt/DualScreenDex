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

    @Test
    fun `dispatches matching tree once and consumes duplicate or missing extras`() {
        val picker = RecordingPicker()
        val dispatcher = SetupPickerRequestDispatcher(picker)

        dispatch(dispatcher, SetupPickerRequest.RETROARCH.encoded)
        dispatch(dispatcher, SetupPickerRequest.ROMS.encoded)
        dispatch(dispatcher, null)
        dispatch(dispatcher, SetupPickerRequest.ROMS.encoded)

        assertEquals(listOf("config", "rom", "rom"), picker.opened)
    }

    private fun dispatch(dispatcher: SetupPickerRequestDispatcher, value: String?) {
        var pending = value
        dispatcher.consume(read = { pending }, clear = { pending = null })
        dispatcher.consume(read = { pending }, clear = { pending = null })
    }

    private class RecordingPicker : SetupPickerDispatch {
        val opened = mutableListOf<String>()

        override fun openConfigTree() {
            opened += "config"
        }

        override fun openRomTree() {
            opened += "rom"
        }
    }
}
