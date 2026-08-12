package com.enrpau.dualscreendex.parser.dataset.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpriteMaterializationProjectionTest {
    @Test
    fun projectsOnlyTheFramesAlreadyDecodedByTheValidatedTypedOutcome() {
        val bytes = ByteArray(0x300)
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        bytes[0x100] = 1
        putGbaPointer(bytes, 16, 0x2F8)
        putU16(bytes, 20, 32)
        val table = GbaSpriteTableLayout(0, 3, 8, GbaGraphicsMode.RAW_4BPP)
        val decoded = SpriteCodec().decode(spriteSession(bytes), table) as SpriteTableOutcome.Decoded
        val resolved = ResolvedSpriteLayout(
            table = table,
            rows = decoded.rows,
            semanticDomain = SpriteSemanticDomain(3, setOf(0, 2)),
        )

        val materialized = SpriteMaterializationProjection.materialize(resolved)

        assertEquals((decoded.rows[0] as SpriteRowOutcome.Decoded).frame, materialized.getValue(0))
        assertFalse(1 in materialized)
        assertFalse(2 in materialized)
    }
}
