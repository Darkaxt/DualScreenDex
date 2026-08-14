package com.enrpau.dualscreendex.parser.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2LandmarkNameCodecTest {
    @Test fun doneStopsDisplayButStillRequiresTheCopiedStringTerminator() {
        assertEquals(
            "PEEL",
            Gen2LandmarkNameCodec.decode(byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x84.toByte(), 0x8b.toByte(), 0x57, 0x99.toByte(), 0x50)),
        )
        assertNull(
            Gen2LandmarkNameCodec.decode(byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x84.toByte(), 0x8b.toByte(), 0x57)),
        )
    }

    @Test fun nullControlFailsClosedInsteadOfPrefixTruncating() {
        assertNull(
            Gen2LandmarkNameCodec.decode(
                byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x84.toByte(), 0x8b.toByte(), 0x00, 0x57, 0x50),
            ),
        )
    }

    @Test fun runtimeTextFlowControlFailsClosed() {
        assertNull(
            Gen2LandmarkNameCodec.decode(byteArrayOf(0x8f.toByte(), 0x84.toByte(), 0x4c, 0x84.toByte(), 0x8b.toByte(), 0x50)),
        )
    }

    @Test fun staticTownMapLineBreakControlsNormalizeToSpaces() {
        assertEquals(
            "LAKE RAGE",
            Gen2LandmarkNameCodec.decode(
                byteArrayOf(
                    0x8b.toByte(), 0x80.toByte(), 0x8a.toByte(), 0x84.toByte(),
                    0x1f,
                    0x91.toByte(), 0x80.toByte(), 0x86.toByte(), 0x84.toByte(),
                    0x50,
                ),
            ),
        )
    }

    @Test fun expandedDialectDecodesShiftedDigitsAndPunctuation() {
        assertEquals(
            "FUKUHARA №.4",
            Gen2LandmarkNameCodec.decode(
                byteArrayOf(
                    0x85.toByte(), 0x94.toByte(), 0x8a.toByte(), 0x94.toByte(),
                    0x87.toByte(), 0x80.toByte(), 0x91.toByte(), 0x80.toByte(), 0x7f,
                    0xcd.toByte(), 0xd8.toByte(), 0xea.toByte(), 0x50,
                ),
                Gen2LandmarkNameEncoding.EXPANDED,
            ),
        )
    }

    @Test fun englishContractionGlyphsAreDecodedInFull() {
        assertEquals(
            "DIGLETT's CAVE",
            Gen2LandmarkNameCodec.decode(
                byteArrayOf(
                    0x83.toByte(), 0x88.toByte(), 0x86.toByte(), 0x8b.toByte(), 0x84.toByte(), 0x93.toByte(), 0x93.toByte(),
                    0xd4.toByte(), 0x1f,
                    0x82.toByte(), 0x80.toByte(), 0x95.toByte(), 0x84.toByte(), 0x50,
                ),
            ),
        )
    }
}
