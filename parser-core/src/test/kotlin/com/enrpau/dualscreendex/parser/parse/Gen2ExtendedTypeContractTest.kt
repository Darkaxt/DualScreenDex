package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2ExtendedTypeContractTest {
    @Test fun derivesCompletePointerExtentAndRetainsUnknownNativeLabel() {
        val bytes = fixture()
        val table = requireNotNull(resolve(bytes))
        assertEquals(29, table.count)
        val decoded = requireNotNull(CompiledTypeNameResolver.decode(RomImage(bytes), 2, table, PokemonTextCodec.gbEnglish))
        assertEquals("CUSTOM", decoded[28]?.name)
        assertNull(decoded[28]?.semanticRole)
        assertEquals("FAIRY", decoded[6]?.semanticRole?.name)
    }

    @Test fun rejectsMalformedExtentCopyAndNativeLabelWithoutLegacyFallback() {
        val bytes = fixture()
        assertNull(resolve(bytes.copyOf().also { it[0x4000 + 18] = 0 }))
        assertNull(resolve(bytes.copyOf().also { it[0x5000] = 0x3B }))
        assertNull(resolve(bytes.copyOf().also { it[0x5300] = 0x01 }))
    }

    @Test fun materializesExtendedStatsOnlyWithDecodedNativeTypeAuthority() {
        val bytes = fixture()
        bytes.put(0x6000, "01 50 50 50 50 50 50 1c 1c")
        bytes.put(0x6100, "80 50 50 50 50 50 50 50 50 50")
        val typeNames = requireNotNull(resolve(bytes))
        val manifest = RomLanguageManifest(LanguageTag.ENGLISH, listOf(RomLanguageProjection(
            LanguageTag.ENGLISH, PokemonTextCodec.gbEnglish.id, PokemonTextCodec.gbEnglish.version,
            LocalizedTableLayout(speciesNames = TableLayout(0x6100, 1, 10), typeNames = typeNames),
            emptyList(), LanguageResolutionStatus.RESOLVED,
        )), LanguageResolutionStatus.RESOLVED)
        val layout = ResolvedRomLayout(EngineFamily.CRYSTAL, 2, Platform.GBC, 1, null,
            ProfileTables(speciesNames = TableLayout(0x6100, 1, 10), baseStats = TableLayout(0x6000, 1, 32)),
            languageManifest = manifest)
        val records = RecordMaterializers.species(RomImage(bytes), layout)
        assertEquals(listOf(28, 28), records[1]?.typeIds?.value)
        assertEquals(80, records[1]?.baseStats?.value?.hp)
        assertNull(RecordMaterializers.species(RomImage(bytes.copyOf().also { it[0x5300] = 1 }), layout)[1]?.baseStats?.value)
    }

    private fun resolve(bytes: ByteArray) = CompiledTypeNameResolver.resolve(
        RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "CRYSTAL")), 2, PokemonTextCodec.gbEnglish,
    )

    private fun fixture(): ByteArray = ByteArray(0x8000).also { bytes ->
        bytes.put(0x4000, "fa 00 c1 21 00 50 5f 16 00 19 19 2a 66 6f 11 00 c2 01 0d 00 c3 00 03")
        bytes.put(0x300, "2a 12 13 0b 79 b0 20 f8 c9")
        val labels = listOf("NORMAL", "FIGHTING", "FLYING", "POISON", "GROUND", "ROCK", "FAIRY", "BUG", "GHOST", "STEEL") +
            List(9) { "NORMAL" } + listOf("???", "FIRE", "WATER", "GRASS", "ELECTRIC", "PSYCHIC", "ICE", "DRAGON", "DARK", "CUSTOM")
        var cursor = 0x503A
        labels.forEachIndexed { id, label ->
            val root = if (id == 28) 0x5300 else cursor
            bytes[0x5000 + id * 2] = root.toByte()
            bytes[0x5001 + id * 2] = (root ushr 8).toByte()
            val encoded = label.map { if (it == '?') 0xE6.toByte() else (it.code - 'A'.code + 0x80).toByte() } + 0x50.toByte()
            encoded.toByteArray().copyInto(bytes, root)
            cursor += encoded.size
        }
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }
}
