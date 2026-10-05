package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Gen3CompiledCanonicalSpeciesMetadata
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideCoreFixture
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideCoreOutcome
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledWideCoreResolver
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompiledWideSpriteMaterializationTest {
    @Test
    fun onlyCanonicalNativeSlotsRenderWithTheirNativePalettes() {
        val (rom, layout) = fixture()
        val sprites = SpriteMaterializer.pokemon(rom, layout, gbaPaletteTableOffset = PALETTES)
        assertEquals(setOf(1, 2, 4), sprites.keys)
        for (sprite in sprites.values) {
            assertEquals(8, sprite.width)
            assertEquals(8, sprite.height)
            assertEquals(0xFFFF0000.toInt(), sprite.argb[0])
        }
    }

    @Test
    fun missingCanonicalAuthorityOrMismatchedPhysicalSpanCannotRenderAliases() {
        val (rom, layout) = fixture()
        for (variant in listOf(
            layout.copy(compiledCanonicalSpecies = null),
            layout.copy(tables = layout.tables.copy(sprites = layout.tables.sprites!!.copy(count = 5))),
            layout.copy(resolvedDatasets = ResolvedDatasetLayouts()),
        )) {
            assertTrue(SpriteMaterializer.pokemon(rom, variant, gbaPaletteTableOffset = PALETTES).isEmpty())
        }
    }

    @Test
    fun canonicalGbaMaterializationPreservesCancellation() {
        val (rom, layout) = fixture()
        assertThrows(ParserCancellationException::class.java) {
            SpriteMaterializer.pokemon(
                rom, layout, gbaPaletteTableOffset = PALETTES,
                cancellation = ParserCancellationToken { throw ParserCancellationException() },
            )
        }
    }

    private fun fixture(): Pair<RomImage, ResolvedRomLayout> {
        val f = Gen3CompiledWideCoreFixture()
        val resolved = Gen3CompiledWideCoreResolver.resolve(f.session(), PokemonTextCodec.gbaEnglish)
            as Gen3CompiledWideCoreOutcome.Resolved
        repeat(resolved.speciesCount) { id ->
            f.putU32(SPRITES + id * 8, 0x08005800)
            f.putU16(SPRITES + id * 8 + 4, 32)
            f.putU32(PALETTES + id * 8, 0x08005900)
        }
        val graphics = ByteArray(32).also { it[0] = 1 }
        val palette = ByteArray(32).also { it[2] = 0x1f }
        literalLz77(graphics).copyInto(f.bytes, 0x5800)
        literalLz77(palette).copyInto(f.bytes, 0x5900)
        val stats = resolved.baseStats.table
        return RomImage(f.bytes) to ResolvedRomLayout(
            family = EngineFamily.EMERALD, generation = 3, platform = Platform.GBA,
            speciesCount = resolved.speciesCount, moveCount = null,
            tables = ProfileTables(
                speciesNames = resolved.speciesNames,
                baseStats = TableLayout(stats.offset.toInt(), stats.count.toInt(), 64, format = TableRecordFormat.WIDE_STATS_64),
                sprites = TableLayout(SPRITES, resolved.speciesCount, 8),
            ),
            resolvedDatasets = ResolvedDatasetLayouts(baseStats = resolved.baseStats),
            compiledCanonicalSpecies = Gen3CompiledCanonicalSpeciesMetadata(
                resolved.speciesNames, stats, resolved.nativeToDex,
            ),
        )
    }

    private fun literalLz77(bytes: ByteArray): ByteArray = byteArrayOf(0x10, bytes.size.toByte(), 0, 0) +
        bytes.toList().chunked(8).flatMap { listOf(0.toByte()) + it }.toByteArray()

    companion object {
        private const val SPRITES = 0x5000
        private const val PALETTES = 0x5100
    }
}
