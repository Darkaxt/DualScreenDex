package com.enrpau.dualscreendex.parser.analysis

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserCancellationTest {
    @Test
    fun sourceCancelsItsTokenAndInterruptedThreadsAlsoCancel() {
        val source = ParserCancellationSource()

        source.cancel()

        assertTrue(source.isCancellationRequested)
        assertThrows(ParserCancellationException::class.java) {
            source.token.throwIfCancellationRequested()
        }

        val interrupted = ParserCancellationSource()
        Thread.currentThread().interrupt()
        try {
            assertThrows(ParserCancellationException::class.java) {
                interrupted.token.throwIfCancellationRequested()
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun gbaReferenceScanChecksCancellationAtFixedByteIntervals() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 2) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            SafeGbaReferenceIndexBuilder.build(
                RomImage(ByteArray(8_192)),
                ResolutionLimits(),
                cancellation,
            )
        }

        assertEquals(2, checks)
    }

    @Test
    fun pngEncodingChecksCancellationDuringRasterRows() {
        var checks = 0
        val cancellation = ParserCancellationToken {
            checks++
            if (checks == 4) throw ParserCancellationException()
        }

        assertThrows(ParserCancellationException::class.java) {
            PngEncoder.encode(
                RgbaSprite(width = 8, height = 8, argb = IntArray(64)),
                cancellation,
            )
        }

        assertEquals(4, checks)
    }
}
