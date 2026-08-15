package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TextEncoding
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3PlayerRuntimeLayoutResolverRealControlTest {
    @Test
    fun officialEmeraldPublishesTheCompleteSourceVerifiedPlayerRuntimeDescriptor() {
        val configured = System.getenv("DUALDEX_OFFICIAL_EMERALD_ROM")
        assumeTrue("set DUALDEX_OFFICIAL_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))

        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(OFFICIAL_EMERALD_SHA256, parsed.analysis.sha256)
        val runtime = requireNotNull(requireNotNull(parsed.catalog).runtimeMetadata.gen3RuntimeMemoryLayout)

        assertEquals(0x03005D8CL, runtime.saveBlock1PointerAddress)
        assertEquals(0x03005D90L, runtime.saveBlock2PointerAddress)
        val save = requireNotNull(runtime.saveRuntimeAbi)
        assertEquals(0x3D88, save.saveBlock1Size)
        assertEquals(0x0F2C, save.saveBlock2Size)
        assertEquals(CatalogGen3TextEncoding.ENGLISH, save.textEncoding)
        assertEquals(0x00, save.trainer.playerNameOffset)
        assertEquals(8, save.trainer.playerNameLength)
        assertEquals(0x08, save.trainer.genderOffset)
        assertEquals(0x0A, save.trainer.trainerIdOffset)
        assertEquals(0x0E, save.trainer.playTimeHoursOffset)
        assertEquals(0x10, save.trainer.playTimeMinutesOffset)
        assertEquals(0xAC, save.trainer.encryptionKeyOffset)
        assertEquals(0x490, save.trainer.moneyOffset)
        assertEquals(999_999L, save.trainer.maximumMoney)
        assertEquals(
            listOf(
                0x137C to 0x80,
                0x137D to 0x01,
                0x137D to 0x02,
                0x137D to 0x04,
                0x137D to 0x08,
                0x137D to 0x10,
                0x137D to 0x20,
                0x137D to 0x40,
            ),
            save.trainer.badgeFlags.map { it.byteOffset to it.mask },
        )
        assertEquals(
            listOf(
                Triple(CatalogGen3BagPocket.ITEMS, 0x560, 30),
                Triple(CatalogGen3BagPocket.KEY_ITEMS, 0x5D8, 30),
                Triple(CatalogGen3BagPocket.BALLS, 0x650, 16),
                Triple(CatalogGen3BagPocket.TM_HM, 0x690, 64),
                Triple(CatalogGen3BagPocket.BERRIES, 0x790, 46),
            ),
            save.bag.pockets.map { Triple(it.pocket, it.byteOffset, it.capacity) },
        )

        val party = requireNotNull(runtime.partyAbi)
        assertEquals(0x020244E9L, party.countAddress)
        assertEquals(0x020244ECL, party.partyAddress)
        assertEquals(6, party.capacity)
        assertEquals(100, party.recordSize)

        val battleUi = requireNotNull(runtime.battleUiAbi) { runtime.toString() }
        assertEquals(0x02024064L, battleUi.activeBattlerAddress)
        assertEquals(0x020244ACL, battleUi.actionCursorAddress)
        assertEquals(0x020244B0L, battleUi.moveCursorAddress)
        assertEquals(0x0202420CL, battleUi.targetCursorAddress)
    }

    private companion object {
        const val OFFICIAL_EMERALD_SHA256 =
            "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"
    }
}
