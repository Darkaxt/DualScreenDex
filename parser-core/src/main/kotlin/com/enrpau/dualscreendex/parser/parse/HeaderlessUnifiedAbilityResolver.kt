package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodec
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableOutcome
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilitySemanticDomain
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedAbilityMetadata
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class HeaderlessUnifiedAbilityResolution(
    val table: TableLayout,
    val metadata: HeaderlessUnifiedAbilityMetadata,
)

/** Resolves the direct ability records referenced by a binary-proven unified species table. */
internal object HeaderlessUnifiedAbilityResolver {
    private const val SPECIES_ABILITY_OFFSET = 24
    private const val SPECIES_ABILITY_SLOTS = 3
    private const val SPECIES_ABILITY_ELEMENT_SIZE = 2
    private const val RECORD_SIZE = 28
    private const val NAME_WIDTH = 17
    private const val DESCRIPTION_POINTER_OFFSET = 20
    private const val RATING_OFFSET = 24
    private const val FLAGS_OFFSET = 25
    private const val MINIMUM_OPTIONAL_COVERAGE = 0.80

    fun resolve(
        session: RomAnalysisSession,
        speciesRoot: Int,
        speciesCount: Int,
        speciesRecordSize: Int,
        activePredicateOffset: Int,
    ): HeaderlessUnifiedAbilityResolution? {
        if (SPECIES_ABILITY_OFFSET + SPECIES_ABILITY_SLOTS * SPECIES_ABILITY_ELEMENT_SIZE > speciesRecordSize) {
            return null
        }
        val activeAbilityIds = abilityIds(
            session.rom,
            speciesRoot,
            speciesCount,
            speciesRecordSize,
            activePredicateOffset,
        )
        val maximumAbilityId = activeAbilityIds.maxOrNull() ?: return null
        val abilityCount = maximumAbilityId + 1
        if (activeAbilityIds.size < 2 || abilityCount !in 2..4096) return null

        val index = session.gbaReferenceIndex ?: return null
        if (index.overflowed) return null
        val domain = AbilitySemanticDomain(activeAbilityIds)
        val ratedRoots = index.targets.mapNotNull { (root, indexed) ->
            if (!plausibleAbilityPrefix(session.rom, root)) return@mapNotNull null
            val references = if (indexed.siteEvidenceAvailable && indexed.instructionSites.isNotEmpty()) {
                indexed
            } else {
                session.nominatedGbaReferenceSites(root)
            }?.takeIf { it.siteEvidenceAvailable } ?: return@mapNotNull null
            root.takeIf { references.instructionSites.any { site -> hasRatingLeafConsumer(session.rom, site) } }
        }
        val candidates = ratedRoots.mapNotNull { root ->
            resolveRoot(session, root, abilityCount, domain)
        }
        return candidates.singleOrNull()
    }

    private fun resolveRoot(
        session: RomAnalysisSession,
        root: Int,
        abilityCount: Int,
        domain: AbilitySemanticDomain,
    ): HeaderlessUnifiedAbilityResolution? {
        when (
            session.limits.checkTableExtent(
                offset = root.toLong(),
                count = abilityCount.toLong(),
                recordSize = RECORD_SIZE.toLong(),
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> Unit
            is ExtentCheck.Invalid, is ExtentCheck.BudgetExceeded -> return null
        }
        val typedLayout = AbilityNameTableLayout(
            offset = root,
            count = abilityCount,
            nameWidth = NAME_WIDTH,
            stride = RECORD_SIZE,
        )
        if (AbilityNameCodec().decode(session, typedLayout, domain) !is AbilityNameTableOutcome.Decoded) {
            return null
        }
        val descriptionsCompatible = optionalCoverage(session.rom, root, abilityCount) { record ->
            val pointer = session.rom.gbaPointer(record + DESCRIPTION_POINTER_OFFSET)
            pointer?.let { plausibleText(session.rom, it, 192) } == true
        }
        val mechanicsCompatible = optionalCoverage(session.rom, root, abilityCount) { record ->
            session.rom.u8(record + FLAGS_OFFSET) and 0x80 == 0
        }
        val descriptionOffset = DESCRIPTION_POINTER_OFFSET.takeIf { descriptionsCompatible }
        return HeaderlessUnifiedAbilityResolution(
            table = TableLayout(
                offset = root,
                count = abilityCount,
                recordSize = NAME_WIDTH,
                stride = RECORD_SIZE,
                pointerOffsets = descriptionOffset?.let(::listOf).orEmpty(),
            ),
            metadata = HeaderlessUnifiedAbilityMetadata(
                speciesAbilityOffset = SPECIES_ABILITY_OFFSET,
                speciesAbilitySlotCount = SPECIES_ABILITY_SLOTS,
                speciesAbilityElementSize = SPECIES_ABILITY_ELEMENT_SIZE,
                abilityRecordSize = RECORD_SIZE,
                abilityNameWidth = NAME_WIDTH,
                abilityDescriptionPointerOffset = descriptionOffset,
                abilityRatingOffset = RATING_OFFSET.takeIf { mechanicsCompatible },
                abilityFlagsOffset = FLAGS_OFFSET.takeIf { mechanicsCompatible },
            ),
        )
    }

    private fun abilityIds(
        rom: RomImage,
        speciesRoot: Int,
        speciesCount: Int,
        speciesRecordSize: Int,
        activePredicateOffset: Int,
    ): Set<Int> = buildSet {
        repeat(speciesCount) { speciesId ->
            val record = speciesRoot + speciesId * speciesRecordSize
            if (rom.u8(record + activePredicateOffset) == 0) return@repeat
            repeat(SPECIES_ABILITY_SLOTS) { slot ->
                add(rom.u16le(record + SPECIES_ABILITY_OFFSET + slot * SPECIES_ABILITY_ELEMENT_SIZE))
            }
        }
    }

    private fun plausibleAbilityPrefix(rom: RomImage, root: Int): Boolean {
        if (root < 0 || root.toLong() + RECORD_SIZE + NAME_WIDTH > rom.size.toLong()) return false
        val rowZero = rom.slice(root, NAME_WIDTH)
        val terminator = rowZero.indexOfFirst { it.toInt() and 0xFF == 0xFF }
        if (terminator <= 0 || rowZero.take(terminator).any { it.toInt() and 0xFF !in 0xAD..0xB0 }) {
            return false
        }
        val first = PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(root + RECORD_SIZE, NAME_WIDTH))
        return first.terminated && first.validRatio >= 0.80 && first.text.any(Char::isLetterOrDigit)
    }

    private fun optionalCoverage(
        rom: RomImage,
        root: Int,
        count: Int,
        validate: (Int) -> Boolean,
    ): Boolean {
        val valid = (0 until count).count { id ->
            runCatching { validate(root + id * RECORD_SIZE) }.getOrDefault(false)
        }
        return valid.toDouble() / count >= MINIMUM_OPTIONAL_COVERAGE
    }

    private fun plausibleText(rom: RomImage, offset: Int, maximumLength: Int): Boolean {
        if (offset !in 0 until rom.size) return false
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(
            rom.slice(offset, minOf(maximumLength, rom.size - offset)),
        )
        return decoded.terminated && decoded.validRatio >= 0.80 && decoded.text.any(Char::isLetterOrDigit)
    }

    /** Complete Thumb leaf: `id * 28`, signed byte load at +24, compare, and return. */
    private fun hasRatingLeafConsumer(rom: RomImage, site: Int): Boolean {
        if (site !in 2 until rom.size - 16) return false
        val expected = intArrayOf(
            0x00C2,
            0x4B00,
            0x1A12,
            0x0092,
            0x189B,
            0xB510,
            0x7E1B,
            0x061B,
            0x161B,
        )
        return expected.indices.all { index ->
            val actual = rom.u16le(site - 2 + index * 2)
            if (index == 1) actual and 0xFF00 == expected[index] else actual == expected[index]
        }
    }
}
