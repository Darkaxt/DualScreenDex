package com.darkaxt.dualdex.battle

data class Gen3MainLayout(val offset: Int)

data class Gen3MainCallbacks(
    val callback1: Long,
    val callback2: Long,
)

data class Gen3MainState(
    val layout: Gen3MainLayout,
    val callbacks: Gen3MainCallbacks,
)

class Gen3MainStateResolver {
    fun resolve(iwram: ByteArray): Gen3MainState? = buildList {
        var offset = 0
        while (offset + HEADER_BYTES <= iwram.size) {
            decode(iwram, Gen3MainLayout(offset))?.let(::add)
            offset += ALIGNMENT
        }
    }.singleOrNull()

    fun resolveKnown(bytes: ByteArray, layout: Gen3MainLayout): Gen3MainState? = decode(bytes, layout)

    private fun decode(bytes: ByteArray, layout: Gen3MainLayout): Gen3MainState? {
        val offset = layout.offset
        if (offset < 0 || offset + HEADER_BYTES > bytes.size) return null
        val callbacks = CALLBACK_OFFSETS.map { bytes.u32(offset + it) }
        if (callbacks[1] == 0L || callbacks.any { !it.isCallbackPointer() }) return null
        val firstCounter = bytes.u32(offset + VBLANK_COUNTER_1_OFFSET)
        val secondCounter = bytes.u32(offset + VBLANK_COUNTER_2_OFFSET)
        if (firstCounter == 0L || firstCounter != secondCounter) return null
        if (KEY_OFFSETS.any { bytes.u16(offset + it) !in 0..KEY_MASK }) return null
        if (bytes.u16(offset + WATCHED_KEYS_PRESSED_OFFSET) !in 0..1) return null
        return Gen3MainState(
            layout = layout,
            callbacks = Gen3MainCallbacks(callbacks[0], callbacks[1]),
        )
    }

    private fun Long.isCallbackPointer(): Boolean = this == 0L || this in ROM_START until ROM_END

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.u32(offset: Int): Long =
        (this[offset].toInt() and 0xFF).toLong() or
            ((this[offset + 1].toInt() and 0xFF).toLong() shl 8) or
            ((this[offset + 2].toInt() and 0xFF).toLong() shl 16) or
            ((this[offset + 3].toInt() and 0xFF).toLong() shl 24)

    companion object {
        const val HEADER_BYTES = 0x38
        private const val ALIGNMENT = 4
        private const val ROM_START = 0x08000000L
        private const val ROM_END = 0x0A000000L
        private const val KEY_MASK = 0x03FF
        private const val VBLANK_COUNTER_1_OFFSET = 0x20
        private const val VBLANK_COUNTER_2_OFFSET = 0x24
        private const val WATCHED_KEYS_PRESSED_OFFSET = 0x34
        private val CALLBACK_OFFSETS = listOf(0x00, 0x04, 0x08, 0x0C, 0x10, 0x14, 0x18)
        private val KEY_OFFSETS = listOf(0x28, 0x2A, 0x2C, 0x2E, 0x30, 0x36)
    }
}
