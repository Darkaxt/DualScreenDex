package com.enrpau.dualscreendex.parser.parse

/** Invented catalogs and independently assembled Thumb consumers for both physical strides. */
internal class Gen3CompiledWideMoveFixture(val width: Int = 56, relocation: Int = 0) {
    val core = Gen3CompiledWideCoreFixture(relocation)
    val bytes get() = core.bytes
    val acquisition = 0x900 + relocation
    val copier = 0xb00 + relocation
    val nameConsumer = 0xc00 + relocation
    val fieldConsumers = (0..10).map { 0x1000 + relocation + it * 0x40 }
    val pointerRoot = 0x4000 + relocation
    val listRoot = 0x4200 + relocation
    val nameRoot = 0x4400 + relocation
    val detailRoot = 0x4800 + relocation

    init {
        require(width == 20 || width == 56)
        putAcquisition(acquisition, pointerRoot, detailRoot)
        core.putWords(copier, 0xb500, 0x1c03, 0xe002, 0x701a, 0x3301, 0x3101, 0x780a,
            0x1c10, 0x28ff, 0xd1f8, 0x20ff, 0x7018, 0x1c18, 0xbc02, 0x4708)
        putNameConsumer(nameConsumer, nameRoot, detailRoot)
        val fields = listOf(0 to 2, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1,
            7 to 1, 8 to -1, 12 to 4, 16 to 1, 17 to 1)
        fields.forEachIndexed { index, (offset, size) -> putFieldConsumer(fieldConsumers[index], offset, size, detailRoot) }
        for (native in listOf(1, 2, 4)) core.putU32(pointerRoot + native * 4, 0x08000000 + listRoot + native * 16)
        core.putU32(pointerRoot + 3 * 4, 0x02000000)
        putList(1, listOf(2 to 0, 7 to 100))
        putList(2, listOf(4 to 17))
        putList(4, emptyList())
        for (id in 0..7) {
            putName(id, "MOVE$id")
            if (id != 0) {
                val at = detailRoot + id * width
                core.putU16(at, 513)
                bytes[at + 2] = 60
                bytes[at + 3] = 18
                bytes[at + 4] = 90
                bytes[at + 5] = 10
                bytes[at + 7] = 64
                bytes[at + 8] = (-5).toByte()
                core.putU32(at + 12, 0x10203040)
                bytes[at + 16] = if (id == 7) 3 else 1
                bytes[at + 17] = 42
            }
        }
    }

    fun putAcquisition(at: Int, pointers: Int, details: Int) {
        val shift = if (width == 56) 3 else 2
        core.putWords(at,
            0x0600, 0x0e07, 0x4c20, 0x4640, 0x0082, 0x1910, 0x6801, 0x8808,
            0x4b1e, 0x4298, 0xd031, 0x8848, 0x42b8, 0xdc2e, 0x4699, 0x2500,
            0x4b1b, 0x469a, 0x1914, 0x6820, 0x1828, 0x8801, 0x1c30, 0xf000,
            0xf800, 0x0400, 0x0c00, 0x4548, 0xd112, 0x6820, 0x1828, 0x8801,
            (shift shl 6) or 8, if (width == 56) 0x1a40 else 0x1840, shift shl 6,
            0x4653, 0x18c2, 0x7c10, 0x2802, 0xd007, 0x8810, 0x28a7, 0xd004,
            0x285b, 0xd002, 0x1c30, 0xf000, 0xf800, 0x3504, 0x4c09, 0x4640,
            0x0082, 0x1910, 0x6800, 0x1829, 0x8808, 0x4548, 0xd002, 0x8848,
            0x42b8, 0xddd4, 0xbc38, 0x4698, 0x46a1, 0x46aa, 0xbcf0, 0xbc01, 0x4700)
        putBl(at + 0x2e, copier)
        putBl(at + 0x5c, copier)
        core.putU32(at + 0x88, 0x08000000 + pointers)
        core.putU32(at + 0x8c, 65535)
        core.putU32(at + 0x90, 0x08000000 + details)
    }

    fun putNameConsumer(at: Int, names: Int, details: Int) {
        val shift = if (width == 56) 3 else 2
        core.putWords(at, 0x880a, 0x0111, 0x1889, 0x4a0e, 0x1889, 0xf000, 0xf800,
            0x480d, 0x4b0d, 0x4652, 0x6811, 0x7949, 0x0049, 0x1989, 0x880a,
            (shift shl 6) or 0x11, if (width == 56) 0x1a89 else 0x1889,
            (shift shl 6) or 9, 0x18c9, 0x7889, 0x4770)
        putBl(at + 10, copier)
        core.putU32(at + 64, 0x08000000 + names)
        core.putU32(at + 68, 0x02000000)
        core.putU32(at + 72, 0x08000000 + details)
    }

    fun putFieldConsumer(at: Int, offset: Int, size: Int, root: Int) {
        val shift = if (width == 56) 3 else 2
        core.putWords(at, 0x4907, (shift shl 6) or 0x10,
            if (width == 56) 0x1a80 else 0x1880, shift shl 6, 0x1840)
        when (size) {
            -1 -> core.putWords(at + 10, 0x2308, 0x56c3, 0x4770)
            1 -> core.putWords(at + 10, 0x7803 or (offset shl 6), 0x4770)
            2 -> core.putWords(at + 10, 0x8803 or ((offset / 2) shl 6), 0x4770)
            4 -> core.putWords(at + 10, 0x6803 or ((offset / 4) shl 6), 0x4770)
        }
        core.putU32(at + 32, 0x08000000 + root)
    }

    fun putList(native: Int, entries: List<Pair<Int, Int>>) {
        val at = listRoot + native * 16
        entries.forEachIndexed { index, (move, level) ->
            core.putU16(at + index * 4, move)
            core.putU16(at + index * 4 + 2, level)
        }
        core.putU16(at + entries.size * 4, 65535)
    }

    fun putName(id: Int, value: String) {
        val at = nameRoot + id * 17
        bytes.fill(0, at, at + 17)
        value.forEachIndexed { i, c -> bytes[at + i] = when (c) {
            in 'A'..'Z' -> (0xbb + c.code - 'A'.code).toByte()
            in '0'..'9' -> (0xa1 + c.code - '0'.code).toByte()
            else -> error("unsupported synthetic character")
        } }
        bytes[at + value.length] = 0xff.toByte()
    }

    private fun putBl(at: Int, target: Int) {
        val displacement = target - at - 4
        core.putU16(at, 0xf000 or ((displacement shr 12) and 0x7ff))
        core.putU16(at + 2, 0xf800 or ((displacement shr 1) and 0x7ff))
    }
}
