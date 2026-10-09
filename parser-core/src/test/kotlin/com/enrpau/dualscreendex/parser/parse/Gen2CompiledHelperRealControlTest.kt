package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.OriginalRomTestAccess
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.catalog.TypeSemanticRole
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.text.Gen2PlainNameCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2CompiledHelperRealControlTest {
    @Test fun polishedCrystalHasCompleteModernHelperAuthority() {
        verifyHelpers(
            "DUALDEX_POLISHED_CRYSTAL_ROM",
            "469d08907d0ef474d174d87d84d25e108b86ca481131b41fa291bd8aae97c29b",
            255,
        )
    }

    @Test fun crystalInheritanceHasIndependentModernHelperAuthority() {
        verifyHelpers(
            "DUALDEX_CRYSTAL_INHERITANCE_ROM",
            "8a7f102ec2f04572bb0bcf95e44279ea5f3e113f73ce217f499c514c14dbd4a4",
            127,
        )
    }

    private fun verifyIndexes(rom: RomImage, maximumBank: Int) {
        val bases = (0 until 0x4000 - 35).filter { offset ->
            rom.u8(offset) == 0xE5 && rom.u8(offset + 1) == 0xD5 && rom.u8(offset + 2) == 0xC5 &&
                rom.u8(offset + 3) == 0xFA && rom.u8(offset + 6) == 0x4F &&
                rom.u8(offset + 7) == 0xFA && rom.u8(offset + 10) == 0x47 && rom.u8(offset + 11) == 0xCD &&
                if (maximumBank == 255) rom.u8(offset + 14) == 0x3E else rom.u8(offset + 14) == 0x0B
        }.map { rom.u16le(it + 12) }
        val names = (0 until 0x4000 - 45).mapNotNull { offset ->
            if (maximumBank == 255) {
                if (rom.u8(offset) == 0xE5 && rom.u8(offset + 1) == 0x21 &&
                    rom.u8(offset + 4) == 0x2A && rom.u8(offset + 5) == 0x5F &&
                    rom.u8(offset + 6) == 0x7E && rom.u8(offset + 7) == 0xCD
                ) rom.u16le(offset + 8) else null
            } else {
                if (rom.u8(offset) == 0xE5 && rom.u8(offset + 1) == 0xC5 &&
                    rom.u8(offset + 2) == 0xFA && rom.u8(offset + 5) == 0x4F &&
                    rom.u8(offset + 6) == 0xFA && rom.u8(offset + 9) == 0x47 && rom.u8(offset + 10) == 0xCD
                ) rom.u16le(offset + 11) else null
            }
        }
        val indexes = bases.flatMap { base -> names.mapNotNull { name ->
            if (maximumBank == 255) Gen2CompactIndexResolver.zeroBased(rom, base, name)
            else Gen2CompactIndexResolver.oneBased(rom, base, name)
        } }.distinct()
        val resolved = requireNotNull(indexes.singleOrNull())
        val slots = resolved.metadata.slots
        assertEquals(if (maximumBank == 255) 289 else 254, slots.size)
        assertEquals(if (maximumBank == 255) 292 else 256, resolved.nameSlots)
        assertTrue(slots.none { (it.id and 0xFF) in setOf(0, 255) })
        assertTrue(slots.all { it.nameIndex < resolved.nameSlots && it.baseIndex < resolved.baseSlots })
        assertEquals(1, slots.first().id)
        assertEquals(0, slots.first().baseIndex)
        assertEquals(if (maximumBank == 255) 291 else 254, slots.last().id)
    }

    private fun verifyHelpers(environment: String, expectedSha: String, maximumBank: Int) {
        val configured = System.getenv(environment)
        assumeTrue("set $environment to run this real helper control", !configured.isNullOrBlank())
        val rom = RomImage(OriginalRomTestAccess.readAllBytes(Path.of(requireNotNull(configured))))
        assertEquals(expectedSha, rom.sha256)
        val copySites = (0 until minOf(0x4000, rom.size) - 3).mapNotNull { offset ->
            GbCompiledFarCopy.resolve(rom, offset)?.let { offset to it }
        }
        assertEquals("complete far-copy routine must be unambiguous", 1, copySites.size)
        assertEquals(maximumBank, copySites.single().second.maximumBank)
        val copyAddress = copySites.single().first
        val linkedCopies = (0 until 0x4000 - 3).filter { offset ->
            rom.u8(offset) == 0xCD && rom.u16le(offset + 1) == copyAddress
        }
        assertTrue("multiple compiled callers must use the proven copy helper", linkedCopies.size >= 2)
        val multiplyVector = 0x18
        val multiplyAddress = if (rom.u8(multiplyVector) == 0xC3) {
            rom.u16le(multiplyVector + 1)
        } else {
            multiplyVector
        }
        assertTrue(GbCompiledBankCalls.repeatedAdd(rom, multiplyAddress))
        verifyIndexes(rom, maximumBank)
        val codec = if (maximumBank == 255) Gen2PlainNameCodec.english53 else Gen2PlainNameCodec.english53Ngrams
        val typeNames = requireNotNull(CompiledTypeNameResolver.resolve(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)), 2, codec,
        ))
        assertEquals(if (maximumBank == 255) 1 else 2, typeNames.recordSize)
        val types = requireNotNull(CompiledTypeNameResolver.decode(rom, 2, typeNames, codec))
        assertEquals(19, types.size)
        assertEquals(TypeSemanticRole.entries.toSet(), types.values.map { it.semanticRole }.toSet())
        assertEquals(TypeSemanticRole.FIRE, types[9]?.semanticRole)
        assertEquals(TypeSemanticRole.GRASS, types[11]?.semanticRole)
        assertEquals(TypeSemanticRole.FAIRY, types[17]?.semanticRole)
        assertEquals(TypeSemanticRole.MYSTERY, types[18]?.semanticRole)
        val core = requireNotNull(Gen2CompactCoreResolver.resolve(RomAnalysisSession(rom, RomHeaderReader.read(rom))))
        val compiledMoves = requireNotNull(Gen2CompactMoveNamesResolver.resolve(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)), core.codec, types.keys,
        ))
        val localized = LocalizedTableLayout(speciesNames = core.tables.speciesNames,
            moveNames = compiledMoves.moveNames, typeNames = core.typeNames)
        val manifest = RomLanguageManifest(LanguageTag.ENGLISH, listOf(RomLanguageProjection(
            LanguageTag.ENGLISH, core.codec.id, core.codec.version, localized, emptyList(), LanguageResolutionStatus.RESOLVED,
        )), LanguageResolutionStatus.RESOLVED)
        val species = RecordMaterializers.species(rom, ResolvedRomLayout(
            EngineFamily.CRYSTAL, 2, Platform.GBC, core.metadata.slots.size, null, core.tables,
            languageManifest = manifest, gen2CompactCore = core.metadata,
        ))
        assertEquals(core.metadata.slots.map { it.id }.toSet(), species.keys)
        assertEquals(if (maximumBank == 255) "Bulbasaur" else "Cyndaquil", species[1]?.name?.value)
        assertEquals(if (maximumBank == 255) 45 else 39, species[1]?.baseStats?.value?.hp)
        assertEquals(if (maximumBank == 255) 45 else 65, species[1]?.baseStats?.value?.speed)
        assertEquals(if (maximumBank == 255) 65 else 60, species[1]?.baseStats?.value?.specialAttack)
        assertTrue(species.values.all { it.name.value?.isNotBlank() == true && it.baseStats.value != null })
        assertTrue(species.values.all { it.typeIds.value?.all(types::containsKey) == true })
        assertTrue(species.values.all { it.dexNumber.value == null })
        val moveData = compiledMoves.moveData
        assertEquals(8, moveData.recordSize)
        assertEquals(TableRecordFormat.GEN2_SPLIT_MOVE_8, moveData.format)
        val moves = RecordMaterializers.moves(rom, ResolvedRomLayout(
            EngineFamily.CRYSTAL, 2, Platform.GBC, null, 255,
            ProfileTables(moveNames = compiledMoves.moveNames, moveData = moveData), languageManifest = manifest,
        ))
        assertEquals((1..255).toSet(), moves.keys)
        assertTrue(moves.values.all { it.name.value?.isNotBlank() == true })
        assertTrue(moves.values.all { it.typeId.value in types.keys })
        assertEquals(setOf(MoveCategory.PHYSICAL, MoveCategory.SPECIAL, MoveCategory.STATUS),
            moves.values.map { it.category.value }.toSet())
        moves.forEach { (id, move) ->
            val rawCategory = rom.u8(moveData.offset + (id - 1) * 8 + 7)
            assertEquals(listOf(MoveCategory.PHYSICAL, MoveCategory.SPECIAL, MoveCategory.STATUS)[rawCategory],
                move.category.value)
        }
    }
}
