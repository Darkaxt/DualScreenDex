package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ModernEmeraldEncounterLiveRomTest {
    @Test
    fun selectsTheMainOverworldTableInsteadOfTheBattlePyramidFacilityTable() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            parsed.analysis.sha256,
        )
        val catalog = requireNotNull(parsed.catalog)
        val capability = catalog.capabilities.getValue(RomCapability.AREA_ENCOUNTERS)

        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(0xBD34E0, capability.offset)
        assertEquals(20, capability.recordSize)
        assertEquals(231, catalog.encounterAreas.size)
        assertEquals(133, catalog.encounterAreas.map { it.id / 10 }.distinct().size)
        val capableAreas = catalog.encounterAreas.filter { area ->
            area.slots.any { slot ->
                slot.speciesId == 290 && 2 in slot.minimumLevel..slot.maximumLevel
            }
        }
        assertEquals(listOf(161), capableAreas.map { it.id })
        assertEquals(listOf(16), capableAreas.map { it.id / 10 })
        assertTrue(requireNotNull(capableAreas.single().name.value).startsWith("Map ").not())
        assertTrue(requireNotNull(catalog.runtimeMetadata.gen3SaveBlock1PointerAddress) in 0x02000000L..0x03FFFFFFL)
        assertEquals(
            CatalogGen3RuntimeMemoryLayout(
                mainAddress = 0x03001574,
                inBattleAddress = 0x030019AD,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                liveClockAddress = 0x030039E8,
                liveClockSchedule = CatalogGameClockSchedule(dayStartHour = 6, nightStartHour = 21),
                multiUsePlayerCursorAddress = null,
                multiUsePlayerCursorEvidence = null,
                playerPartyCountAddress = 0x0201D9C5,
                playerPartyAddress = 0x0201D9C8,
                battleMonsAddress = 0x0200143C,
                battleTypeFlagsAddress = 0x020003A0,
                trainerBattleMask = 1 shl 3,
                nonWildBattleMask = 0x8FFF8B72.toInt(),
                saveBlock1PointerAddress = 0x030036F0,
                saveBlock2PointerAddress = 0x030036F4,
                saveRuntimeAbi = CatalogGen3SaveRuntimeAbi(
                    saveBlock1Size = 0x3D88,
                    saveBlock2Size = 0x0F2C,
                    textEncoding = CatalogGen3TextEncoding.ENGLISH,
                    trainer = CatalogGen3TrainerCardAbi(
                        playerNameOffset = 0,
                        playerNameLength = 8,
                        genderOffset = 8,
                        trainerIdOffset = 0x0A,
                        playTimeHoursOffset = 0x0E,
                        playTimeMinutesOffset = 0x10,
                        encryptionKeyOffset = 0xAC,
                        moneyOffset = 0x490,
                        maximumMoney = 999_999,
                        badgeFlags = listOf(
                            CatalogGen3BitFlag(0x137C, 0x80),
                            CatalogGen3BitFlag(0x137D, 0x01),
                            CatalogGen3BitFlag(0x137D, 0x02),
                            CatalogGen3BitFlag(0x137D, 0x04),
                            CatalogGen3BitFlag(0x137D, 0x08),
                            CatalogGen3BitFlag(0x137D, 0x10),
                            CatalogGen3BitFlag(0x137D, 0x20),
                            CatalogGen3BitFlag(0x137D, 0x40),
                        ),
                    ),
                    bag = CatalogGen3BagAbi(
                        listOf(
                            CatalogGen3BagPocketAbi(CatalogGen3BagPocket.ITEMS, 0x560, 30),
                            CatalogGen3BagPocketAbi(CatalogGen3BagPocket.KEY_ITEMS, 0x5D8, 30),
                            CatalogGen3BagPocketAbi(CatalogGen3BagPocket.BALLS, 0x650, 16),
                            CatalogGen3BagPocketAbi(CatalogGen3BagPocket.TM_HM, 0x690, 64),
                            CatalogGen3BagPocketAbi(CatalogGen3BagPocket.BERRIES, 0x790, 46),
                        ),
                    ),
                    eventFlags = CatalogGen3EventFlagAbi(0x1270, 0x12C),
                ),
                partyAbi = CatalogGen3PartyAbi(0x0201D9C5, 0x0201D9C8, 6, 100),
                battleUiAbi = CatalogGen3BattleUiAbi(0x0200141C, 0x02001864, 0x02001868, 0x020015C4),
            ),
            catalog.runtimeMetadata.gen3RuntimeMemoryLayout,
        )
        assertEquals("Oldale Town", catalog.runtimeMetadata.areaNamesByBaseId[0x0202])
        assertTrue(capability.reasons.single().contains("headers=272"))
        assertTrue(capability.reasons.single().contains("references=11"))
        assertTrue(capability.reasons.single().contains("candidates="))
    }

    @Test
    fun selectsTheReferencedBlazedGlazedOverworldTableInsteadOfFacilityTables() {
        val configured = System.getenv("DUALDEX_BLAZED_GLAZED_ROM")
        assumeTrue("set DUALDEX_BLAZED_GLAZED_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(
            "0b55d44bfd32a350202c0878754cfcacbbaee128de3b59297ee669b69269199f",
            parsed.analysis.sha256,
        )
        val catalog = requireNotNull(parsed.catalog)
        val capability = catalog.capabilities.getValue(RomCapability.AREA_ENCOUNTERS)

        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertEquals(0x11DE94C, capability.offset)
        assertEquals(20, capability.recordSize)
        assertEquals(328, catalog.encounterAreas.size)
        assertEquals(187, catalog.encounterAreas.map { it.id / 10 }.distinct().size)
        assertTrue(capability.reasons.single().contains("headers=195"))
        assertTrue(capability.reasons.single().contains("populatedMethods=336"))
        assertTrue(capability.reasons.single().contains("references=13"))
        assertTrue(capability.reasons.single().contains("authority=compiled-reference-and-structural-dominance"))
    }
}
