package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogRuntimeMemoryLayoutTest {
    @Test
    fun `player runtime descriptor preserves every typed field`() {
        val saveAbi = saveAbi()
        val partyAbi = CatalogGen3PartyAbi(
            countAddress = 0x020244E9,
            partyAddress = 0x020244EC,
            capacity = 6,
            recordSize = 100,
        )
        val battleUiAbi = CatalogGen3BattleUiAbi(
            activeBattlerAddress = 0x02023BC4,
            actionCursorAddress = 0x02023DCC,
            moveCursorAddress = 0x02024264,
            targetCursorAddress = 0x02024268,
        )

        val layout = CatalogGen3RuntimeMemoryLayout(
            mainAddress = 0x030022C0,
            inBattleAddress = 0x03002748,
            inBattleMask = 0x02,
            saveBlock1MapGroupOffset = 4,
            saveBlock1MapNumberOffset = 5,
            saveBlock1PointerAddress = 0x03005D8C,
            saveBlock2PointerAddress = 0x03005D90,
            saveRuntimeAbi = saveAbi,
            partyAbi = partyAbi,
            battleUiAbi = battleUiAbi,
            liveClockAddress = 0x030039E8,
            liveClockSchedule = CatalogGameClockSchedule(dayStartHour = 6, nightStartHour = 21),
        )

        assertEquals(0x03005D8CL, layout.saveBlock1PointerAddress)
        assertEquals(0x03005D90L, layout.saveBlock2PointerAddress)
        assertEquals(saveAbi, layout.saveRuntimeAbi)
        assertEquals(CatalogGen3EventFlagAbi(0x1270, 0x12C), layout.saveRuntimeAbi?.eventFlags)
        assertEquals(partyAbi, layout.partyAbi)
        assertEquals(battleUiAbi, layout.battleUiAbi)
        assertEquals(CatalogGameClockSchedule(6, 21), layout.liveClockSchedule)
    }

    @Test
    fun `save pointer and ABI descriptor is all or nothing`() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogGen3RuntimeMemoryLayout(
                mainAddress = 0x030022C0,
                inBattleAddress = 0x03002748,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                saveBlock1PointerAddress = 0x03005D8C,
                saveBlock2PointerAddress = null,
                saveRuntimeAbi = saveAbi(),
            )
        }
    }

    @Test
    fun `clock schedule requires a validated live clock address`() {
        assertThrows(IllegalArgumentException::class.java) {
            CatalogGen3RuntimeMemoryLayout(
                mainAddress = 0x030022C0,
                inBattleAddress = 0x03002748,
                inBattleMask = 0x02,
                saveBlock1MapGroupOffset = 4,
                saveBlock1MapNumberOffset = 5,
                liveClockSchedule = CatalogGameClockSchedule(6, 21),
            )
        }
    }

    @Test
    fun `extended save descriptor requires a matching EWRAM window`() {
        val expanded = saveAbi().copy(
            extendedSaveDataSize = 0x2EA4,
            bag = CatalogGen3BagAbi(
                listOf(
                    CatalogGen3BagPocketAbi(
                        CatalogGen3BagPocket.ITEMS,
                        0x09AC,
                        450,
                        dataSource = CatalogGen3BagDataSource.EXTENDED_SAVE,
                    ),
                ),
            ),
        )
        val layout = CatalogGen3RuntimeMemoryLayout(
            mainAddress = 0x030022C0,
            inBattleAddress = 0x03002748,
            inBattleMask = 0x02,
            saveBlock1MapGroupOffset = 4,
            saveBlock1MapNumberOffset = 5,
            saveBlock1PointerAddress = 0x03005008,
            saveBlock2PointerAddress = 0x0300500C,
            extendedSaveAddress = 0x0203B174,
            saveRuntimeAbi = expanded,
        )

        assertEquals(0x0203B174L, layout.extendedSaveAddress)
        assertThrows(IllegalArgumentException::class.java) { layout.copy(extendedSaveAddress = null) }
    }

    private fun saveAbi() = CatalogGen3SaveRuntimeAbi(
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
            badgeFlags = (0 until 8).map { CatalogGen3BitFlag(0x1270 + it / 8, 1 shl (it % 8)) },
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
    )
}
