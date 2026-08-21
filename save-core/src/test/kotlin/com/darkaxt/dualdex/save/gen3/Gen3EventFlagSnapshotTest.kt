package com.darkaxt.dualdex.save.gen3

import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3EventFlagSnapshotTest {
    @Test
    fun `decodes set flag ids from the declared save block window`() {
        val saveBlock1 = ByteArray(0x40)
        saveBlock1[0x10] = 0b0000_0101
        saveBlock1[0x12] = 0b1000_0000.toByte()

        assertEquals(
            setOf(0, 2, 23),
            Gen3EventFlagSnapshot.decode(saveBlock1, Gen3EventFlagAbi(0x10, 3)),
        )
    }

    @Test
    fun `rejects an incomplete declared flag window`() {
        assertEquals(
            null,
            Gen3EventFlagSnapshot.decode(ByteArray(0x12), Gen3EventFlagAbi(0x10, 3)),
        )
    }
}
