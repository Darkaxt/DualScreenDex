package com.darkaxt.dualdex.setup

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GuideLoadFaultTest {
    @Test
    fun `default fault is inert and one shot fault clears after one activation`() {
        val entry = RomIndexEntry(
            sourceId = "file:///qa-guide.gb",
            sourceName = "qa-guide.gb",
            archiveEntry = null,
            platform = RomPlatform.GB,
            gameBasename = "qa-guide",
            crc32 = "00000000",
            sha256 = "0".repeat(64),
        )
        val failure = IllegalStateException("qa failure")
        val fault = OneShotGuideLoadFault()

        assertNull(NoGuideLoadFault.beforeLoad(entry))
        fault.failNext(failure)

        assertSame(failure, fault.beforeLoad(entry))
        assertNull(fault.beforeLoad(entry))
    }
}
