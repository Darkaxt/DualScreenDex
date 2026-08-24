package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.darkaxt.dualdex.save.gen3.Gen3BagAbi
import com.darkaxt.dualdex.save.gen3.Gen3BagPocketAbi
import com.darkaxt.dualdex.save.gen3.Gen3BitFlag
import com.darkaxt.dualdex.save.gen3.Gen3EventFlagAbi
import com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi
import com.darkaxt.dualdex.save.gen3.Gen3TextEncoding
import com.darkaxt.dualdex.save.gen3.Gen3TrainerCardAbi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3LiveSectionFingerprintsTest {
    @Test
    fun `only the fingerprint owning a changed ABI slice changes`() {
        val regions = fixtureRegions()
        val baseline = Gen3LiveSectionFingerprints.compute(regions, layout(), context())

        assertOnlyChanged(regions, baseline, Gen3LiveMemoryReader.SAVE_BLOCK2_ID, 0, Gen3LiveDecodedSection.PLAYER)
        assertOnlyChanged(regions, baseline, Gen3LiveMemoryReader.PARTY_ID, 40, Gen3LiveDecodedSection.PARTY)
        assertOnlyChanged(regions, baseline, Gen3LiveMemoryReader.CLOCK_ID, 4, Gen3LiveDecodedSection.OVERWORLD)
        assertOnlyChanged(regions, baseline, Gen3LiveMemoryReader.SAVE_BLOCK1_ID, 0x20, Gen3LiveDecodedSection.PROGRESSION)
    }

    @Test
    fun `translated cache reuses values and never retains source arrays`() {
        val cache = Gen3LiveTranslatedSectionCache()
        val raw = ByteArray(64) { it.toByte() }
        val fingerprints = Gen3LiveSectionFingerprints.compute(fixtureRegions(), layout(), context())
        val first = cache.resolve(Gen3LiveDecodedSection.PARTY, 1, fingerprints.party) { listOf("decoded") }
        raw.fill(0)
        val second = cache.resolve<List<String>>(Gen3LiveDecodedSection.PARTY, 1, fingerprints.party) {
            error("must reuse")
        }

        assertSame(first, second)
        assertEquals(1L, cache.counters().getValue("live.decode.party"))
        assertEquals(1L, cache.counters().getValue("live.reuse.party"))
        assertTrue(cache.retainedValueTypes().none { it == ByteArray::class.java })

        cache.clearEntries()
        val third = cache.resolve(Gen3LiveDecodedSection.PARTY, 2, fingerprints.party) { listOf("new") }
        assertNotEquals(first, third)
        assertEquals(2L, cache.counters().getValue("live.decode.party"))
    }

    private fun assertOnlyChanged(
        baselineRegions: Map<String, ByteArray>,
        baseline: Gen3LiveSectionFingerprintSet,
        regionId: String,
        offset: Int,
        expected: Gen3LiveDecodedSection,
    ) {
        val changed = baselineRegions.mapValues { (_, value) -> value.copyOf() }.toMutableMap()
        changed.getValue(regionId)[offset] = (changed.getValue(regionId)[offset].toInt() xor 1).toByte()
        val next = Gen3LiveSectionFingerprints.compute(changed, layout(), context())
        val changedSections = Gen3LiveDecodedSection.entries.filterTo(mutableSetOf()) { baseline[it] != next[it] }
        assertEquals(setOf(expected), changedSections)
    }

    private fun fixtureRegions() = mapOf(
        Gen3LiveMemoryReader.SAVE_BLOCK1_ID to ByteArray(0x100),
        Gen3LiveMemoryReader.SAVE_BLOCK2_ID to ByteArray(0x280),
        Gen3LiveMemoryReader.PARTY_COUNT_ID to byteArrayOf(1),
        Gen3LiveMemoryReader.PARTY_ID to ByteArray(600),
        Gen3LiveMemoryReader.CLOCK_ID to byteArrayOf(0, 0, 12, 30, 10),
    )

    private fun layout() = Gen3RuntimeMemoryLayout(
        mainAddress = 0x03002000,
        inBattleAddress = 0x03002040,
        inBattleMask = 1,
        saveBlock1MapGroupOffset = 4,
        saveBlock1MapNumberOffset = 5,
        liveClockAddress = 0x030039E8,
        playerPartyCountAddress = 0x02000200,
        playerPartyAddress = 0x02000300,
        playerPartyCapacity = 6,
        playerPartyRecordSize = 100,
        saveBlock1PointerAddress = 0x03001000,
        saveBlock2PointerAddress = 0x03001004,
        saveBlock1Size = 0x100,
        saveBlock2Size = 0x280,
    )

    private fun context() = SaveParseContext(
        romIdentity = "rom",
        speciesById = mapOf(1 to SaveSpeciesContext(1, 1, 0)),
        gen3SaveRuntimeAbi = Gen3SaveRuntimeAbi(
            saveBlock1Size = 0x100,
            saveBlock2Size = 0x280,
            textEncoding = Gen3TextEncoding.ENGLISH,
            trainer = Gen3TrainerCardAbi(
                playerNameOffset = 0,
                playerNameLength = 8,
                genderOffset = 8,
                trainerIdOffset = 10,
                playTimeHoursOffset = 14,
                playTimeMinutesOffset = 16,
                encryptionKeyOffset = 0x240,
                moneyOffset = 0x10,
                maximumMoney = 999_999,
                badgeFlags = listOf(Gen3BitFlag(0x11, 1)),
            ),
            bag = Gen3BagAbi(listOf(Gen3BagPocketAbi(BagPocket.ITEMS, 0x30, 2))),
            eventFlags = Gen3EventFlagAbi(0x20, 4),
        ),
    )
}
