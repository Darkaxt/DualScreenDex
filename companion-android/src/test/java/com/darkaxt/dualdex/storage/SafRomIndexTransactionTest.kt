package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class SafRomIndexTransactionTest {
    @Test
    fun `returns terminal failure when initial index persistence throws`() {
        val transaction = SafRomIndexTransaction { _: List<RomIndexEntry> ->
            throw IOException("storage unavailable")
        }

        assertEquals(SafRomIndexCommitResult.Failed, transaction.commit(emptyList()))
    }
}
