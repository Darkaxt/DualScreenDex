package com.darkaxt.dualdex.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperExportTest {
    @Test
    fun manifestOmitsRawBytesByDefault() {
        val bundle = MapperExport.create(session(), includeRaw = false, privacyAcknowledged = false)

        assertNull(bundle.snapshots.single().regions.single().base64Bytes)
        assertEquals("region-hash", bundle.snapshots.single().regions.single().sha256)
        assertTrue(bundle.diffs.isEmpty())
    }

    @Test
    fun rawExportRequiresExplicitPrivacyConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            MapperExport.create(session(), includeRaw = true, privacyAcknowledged = false)
        }

        val bundle = MapperExport.create(session(), includeRaw = true, privacyAcknowledged = true)

        assertTrue(bundle.snapshots.single().regions.single().base64Bytes!!.isNotBlank())
    }

    @Test
    fun exportCarriesBoundedAddressDiffEvidenceBetweenLabels() {
        val first = session().snapshots.single()
        val second = first.copy(
            id = "snapshot-2", label = MapperLabel.BATTLE_START,
            regions = listOf(first.regions.single().copy(bytes = byteArrayOf(1, 9), sha256 = "changed-hash")),
        )
        val record = session().copy(snapshots = listOf(first, second))

        val manifest = MapperExport.create(record, includeRaw = false, privacyAcknowledged = false)
        val raw = MapperExport.create(record, includeRaw = true, privacyAcknowledged = true)

        assertEquals(1, manifest.diffs.single().changedBytes)
        assertEquals(0x02000001L, manifest.diffs.single().ranges.single().address)
        assertNull(manifest.diffs.single().ranges.single().beforeBase64)
        assertEquals("Ag==", raw.diffs.single().ranges.single().beforeBase64)
        assertEquals("CQ==", raw.diffs.single().ranges.single().afterBase64)
    }

    private fun session() = MapperSessionRecord(
        id = "session",
        coreIdentity = "mGBA",
        contentIdentity = "rom",
        descriptors = listOf(MemoryDescriptor("ewram", "EWRAM", 0x02000000, 2)),
        snapshots = listOf(
            MemorySnapshot(
                id = "snapshot",
                label = MapperLabel.OVERWORLD,
                customLabel = null,
                capturedAtEpochMs = 1,
                coreIdentity = "mGBA",
                contentIdentity = "rom",
                regions = listOf(
                    MemoryRegionSnapshot(
                        MemoryDescriptor("ewram", "EWRAM", 0x02000000, 2),
                        byteArrayOf(1, 2),
                        "region-hash",
                    ),
                ),
            ),
        ),
    )
}
