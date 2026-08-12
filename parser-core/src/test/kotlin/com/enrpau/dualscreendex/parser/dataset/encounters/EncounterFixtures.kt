package com.enrpau.dualscreendex.parser.dataset.encounters

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile

internal fun encounterSession(
    bytes: ByteArray,
    references: Map<Int, Int> = emptyMap(),
    referenceSites: Map<Int, List<Int>> = emptyMap(),
    limits: ResolutionLimits = ResolutionLimits(),
    exact: Boolean = false,
    referenceIndex: GbaReferenceIndex? = null,
    onReferenceIndexBuild: () -> Unit = {},
): RomAnalysisSession {
    val rom = RomImage(bytes)
    val exactProfile = if (exact) {
        RomProfile(
            name = "exact encounter fixture",
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            family = EngineFamily.EMERALD,
            platform = Platform.GBA,
            title = "ENCOUNTER TEST",
            revision = 0,
            romSize = rom.size,
            dexSpeciesCount = 100,
            internalSpeciesCount = 100,
            moveCount = 1,
            tables = ProfileTables(),
        )
    } else {
        null
    }
    return RomAnalysisSession(
        rom = rom,
        header = RomHeader(Platform.GBA, "ENCOUNTER TEST"),
        exactProfile = exactProfile,
        limits = limits,
        gbaReferenceIndexFactory = { _, _ ->
            onReferenceIndexBuild()
            referenceIndex ?: if (referenceSites.isEmpty()) {
                GbaReferenceIndex.countsOnlyForTesting(references)
            } else {
                GbaReferenceIndex.fromTargets(
                    targets = references.mapValues { (target, count) ->
                        val sites = referenceSites[target].orEmpty()
                        GbaTargetReferenceEvidence(
                            count = count,
                            instructionSites = sites,
                            observedSites = sites.size,
                            limitSites = limits.maxCompiledReferenceSitesPerCandidate,
                            overflowReason = null,
                            siteEvidenceUnavailableReason = null,
                        )
                    },
                    limitTargets = limits.maxDistinctGbaReferenceTargets,
                )
            }
        },
    )
}

internal fun putStandardEncounterTable(
    bytes: ByteArray,
    root: Int,
    maps: IntRange = 1..3,
    firstInfo: Int = root + 0x200,
    firstSlots: Int = root + 0x400,
    emptyRows: Set<Int> = emptySet(),
    malformedRows: Set<Int> = emptySet(),
) {
    maps.forEachIndexed { index, map ->
        val header = root + index * 20
        bytes[header] = 1
        bytes[header + 1] = map.toByte()
        when (index) {
            in malformedRows -> putEncounterU32(bytes, header + 4, 0x09000000L)
            in emptyRows -> Unit
            else -> {
                val info = firstInfo + index * 8
                val slots = firstSlots + index * 48
                putEncounterGbaPointer(bytes, header + 4, info)
                putEncounterInfo(bytes, info, rateOrEnvironment = 20, slots, 12, 10 + index * 12)
            }
        }
    }
    val sentinel = root + maps.count() * 20
    bytes[sentinel] = 0xFF.toByte()
    bytes[sentinel + 1] = 0xFF.toByte()
}

internal fun putClassicEncounterTable(
    bytes: ByteArray,
    root: Int,
    emptyFirst: Boolean = false,
    hiddenEnvironment: Int = 0,
    firstInfo: Int = root + 0x300,
    firstSlots: Int = root + 0x600,
) {
    repeat(4) { index ->
        val header = root + index * 24
        bytes[header] = 1
        bytes[header + 1] = (index + 1).toByte()
        if (!(emptyFirst && index == 0)) {
            val grassInfo = firstInfo + index * 16
            val grassSlots = firstSlots + index * 64
            putEncounterGbaPointer(bytes, header + 4, grassInfo)
            putEncounterInfo(bytes, grassInfo, 20, grassSlots, 12, 10 + index * 12)
            if (index == 1) {
                val hiddenInfo = grassInfo + 8
                val hiddenSlots = grassSlots + 48
                putEncounterGbaPointer(bytes, header + 16, hiddenInfo)
                putEncounterInfo(bytes, hiddenInfo, hiddenEnvironment, hiddenSlots, 3, 60)
            }
        }
    }
    val sentinel = root + 4 * 24
    bytes[sentinel] = 0xFF.toByte()
    bytes[sentinel + 1] = 0xFF.toByte()
}

internal fun putEmptyClassicDecoy(bytes: ByteArray, root: Int) {
    bytes[root] = 1
    bytes[root + 1] = 1
    bytes[root + 24] = 0xFE.toByte()
    bytes[root + 25] = 1
}

internal fun putEncounterInfo(
    bytes: ByteArray,
    offset: Int,
    rateOrEnvironment: Int,
    slotsOffset: Int,
    slotCount: Int,
    firstSpecies: Int,
) {
    bytes[offset] = rateOrEnvironment.toByte()
    putEncounterGbaPointer(bytes, offset + 4, slotsOffset)
    repeat(slotCount) { slot ->
        val entry = slotsOffset + slot * 4
        bytes[entry] = (5 + slot).toByte()
        bytes[entry + 1] = (7 + slot).toByte()
        putEncounterU16(bytes, entry + 2, firstSpecies + slot)
    }
}

internal fun putEncounterGbaPointer(bytes: ByteArray, offset: Int, targetOffset: Int) {
    putEncounterU32(bytes, offset, 0x08000000L + targetOffset)
}

internal fun putEncounterU16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

internal fun putEncounterU32(bytes: ByteArray, offset: Int, value: Long) {
    repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
}
