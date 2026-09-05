package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.parse.GbaPublishedHeaderResolver
import com.enrpau.dualscreendex.parser.text.LanguageTextPlausibility
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import kotlin.math.abs

data class AbilityDescriptionResult(
    val sourceOffset: Int,
    val confidence: Double,
    val descriptions: Map<Int, String>,
)

object AbilityDescriptionMaterializer {

    fun materialize(
        rom: RomImage,
        layout: ResolvedRomLayout,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): AbilityDescriptionResult? {
        cancellation.throwIfCancellationRequested()
        if (layout.generation != 3) return null
        val codec = layout.defaultTextCodec() ?: return null
        val names = layout.tables.abilities ?: return null
        if (names.count < 2) return null
        val pointerTableBytes = names.count.toLong() * 4
        val nameStride = names.stride ?: names.recordSize
        if (pointerTableBytes > rom.size.toLong() || nameStride <= 0 ||
            names.offset.toLong() + names.count.toLong() * nameStride > rom.size.toLong()
        ) return null
        val inlineRoot = names.offset.toLong() + names.count.toLong() * nameStride
        val inlineCandidates = layout.compiledGbaReferences?.takeUnless { it.overflowed }?.siteEvidence?.let { index ->
            com.enrpau.dualscreendex.parser.parse.compiledInlineAbilityTexts(rom, index, cancellation)
                .filter { it.offset.toLong() == inlineRoot }
        }.orEmpty()
        if (inlineCandidates.isNotEmpty()) {
            val inline = inlineCandidates.singleOrNull() ?: return null
            val decoded = inline.decode(rom, names.count, codec, cancellation) ?: return null
            return AbilityDescriptionResult(inline.offset, 1.0, decoded)
        }
        val embeddedDescription = layout.pokeemeraldExpansion?.let { expansion ->
            expansion.abilityRecordSize to expansion.abilityDescriptionPointerOffset
        } ?: layout.headerlessUnifiedSpecies?.abilities?.let { abilities ->
            abilities.abilityDescriptionPointerOffset?.let { pointerOffset ->
                abilities.abilityRecordSize to pointerOffset
            }
        }
        embeddedDescription?.let { (recordSize, pointerOffset) ->
            val descriptions = buildMap {
                repeat(names.count - 1) { index ->
                    cancellation.throwIfCancellationRequested()
                    val id = index + 1
                    val record = names.offset + id * (names.stride ?: recordSize)
                    val textOffset = rom.gbaPointer(record + pointerOffset) ?: return@repeat
                    val length = minOf(192, rom.size - textOffset)
                    val decoded = decodeDetailedOrNull(
                        codec = codec,
                        rom = rom,
                        offset = textOffset,
                        maximumBytes = length,
                        cancellation = cancellation,
                    ) ?: return@repeat
                    val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
                    if (
                        decoded.terminated && decoded.validRatio >= 0.85 &&
                        looksLikeNaturalDescription(normalized, codec)
                    ) {
                        put(id, normalized)
                    }
                }
            }
            val expected = names.count - 1
            val confidence = descriptions.size.toDouble() / expected.coerceAtLeast(1)
            return AbilityDescriptionResult(names.offset, confidence, descriptions).takeIf {
                descriptions.size >= maxOf(2, (expected * 0.8).toInt())
            }
        }
        val tableBytes = pointerTableBytes.toInt()
        val expectedOffset = align4((names.offset.toLong() + names.count.toLong() * names.recordSize).toInt())
        val candidates = linkedSetOf<Int>()
        val publishedRoot = GbaPublishedHeaderResolver.resolve(rom, codec).abilityDescriptions
        publishedRoot?.let(candidates::add)
        val referenceIndex = layout.compiledGbaReferences
        if (publishedRoot == null && (referenceIndex == null || referenceIndex.overflowed)) return null
        val references = referenceIndex?.takeUnless { it.overflowed }?.counts.orEmpty()
        if (publishedRoot == null) {
            var eligibleCandidates = 0
            references.keys.forEach { offset ->
                cancellation.throwIfCancellationRequested()
                if (!isCompletePointerSpanCandidate(rom, offset, names.count)) return@forEach
                eligibleCandidates++
                if (eligibleCandidates > MAX_DESCRIPTION_CANDIDATES) return null
                candidates += offset
            }
        }
        val prefix = abilityNamePrefix(rom, names, codec, cancellation)

        return candidates.asSequence()
            .mapNotNull { offset ->
                cancellation.throwIfCancellationRequested()
                val published = offset == publishedRoot
                val referenceCount = references[offset] ?: 0
                if (!published && referenceCount == 0) return@mapNotNull null
                val minimumCoverage = if (published) 0.70 else 0.80
                decodeCandidate(
                    rom = rom,
                    codec = codec,
                    offset = offset,
                    count = names.count,
                    minimumCoverage = minimumCoverage,
                    cancellation = cancellation,
                )?.let { result ->
                    DescriptionCandidate(
                        result = result,
                        references = referenceCount,
                        published = published,
                        semanticallyAligned = prefix == null || leadingPointersAreAligned(rom, offset, prefix),
                    )
                }
            }
            .filter(DescriptionCandidate::semanticallyAligned)
            .maxWithOrNull(
                compareBy<DescriptionCandidate> { if (it.published) 1 else 0 }
                    .thenBy { it.references }
                    .thenBy { it.result.confidence }
                    .thenByDescending { abs(it.result.sourceOffset - expectedOffset) },
            )
            ?.result
    }

    private fun decodeCandidate(
        rom: RomImage,
        codec: PokemonTextCodec,
        offset: Int,
        count: Int,
        minimumCoverage: Double,
        cancellation: ParserCancellationToken,
    ): AbilityDescriptionResult? {
        val noneOffset = runCatching { rom.gbaPointer(offset) }.getOrNull() ?: return null
        val noneLength = minOf(64, rom.size - noneOffset)
        val none = decodeDetailedOrNull(
            codec = codec,
            rom = rom,
            offset = noneOffset,
            maximumBytes = noneLength,
            cancellation = cancellation,
        ) ?: return null
        val validNone = none.text.isBlank() || (none.validRatio >= 0.85 && none.text.length >= 5)
        if (!none.terminated || !validNone) return null

        val descriptions = linkedMapOf<Int, String>()
        for (id in 1 until count) {
            cancellation.throwIfCancellationRequested()
            val textOffset = runCatching { rom.gbaPointer(offset + id * 4) }.getOrNull() ?: break
            val length = minOf(192, rom.size - textOffset)
            val decoded = decodeDetailedOrNull(
                codec = codec,
                rom = rom,
                offset = textOffset,
                maximumBytes = length,
                cancellation = cancellation,
            ) ?: break
            if (!decoded.terminated || decoded.validRatio < 0.85) break
            val normalized = decoded.text.replace(Regex("\\s+"), " ").trim()
            if (normalized.length >= 5) {
                descriptions[id] = normalized
            }
        }
        val expectedDescriptions = count - 1
        val decodedRatio = descriptions.size.toDouble() / expectedDescriptions
        val naturalDescriptionCount = descriptions.values.count {
            looksLikeNaturalDescription(it, codec)
        }
        val naturalRatio = naturalDescriptionCount.toDouble() / descriptions.size.coerceAtLeast(1)
        val confidence = minOf(decodedRatio, naturalRatio)
        val minimum = maxOf(2, kotlin.math.ceil(expectedDescriptions * minimumCoverage).toInt())
        return if (descriptions.size >= minimum && naturalRatio >= 0.75) {
            AbilityDescriptionResult(offset, confidence, descriptions)
        } else {
            null
        }
    }

    private fun decodeDetailedOrNull(
        codec: PokemonTextCodec,
        rom: RomImage,
        offset: Int,
        maximumBytes: Int,
        cancellation: ParserCancellationToken,
    ) = try {
        codec.decodeDetailed(rom, offset, maximumBytes, cancellation)
    } catch (failure: ParserCancellationException) {
        throw failure
    } catch (_: RuntimeException) {
        null
    }

    private fun abilityNamePrefix(
        rom: RomImage,
        names: TableLayout,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): AbilityNamePrefix? {
        if (names.count < 2 || names.variableLength) return null
        fun read(index: Int): String {
            val record = names.offset + index * (names.stride ?: names.recordSize)
            val value = if (names.valuesArePointers) rom.gbaPointer(record) else record
            if (value == null || value < 0 || value >= rom.size) return ""
            val width = if (names.valuesArePointers) minOf(64, rom.size - value) else names.recordSize
            return decodeDetailedOrNull(
                codec = codec,
                rom = rom,
                offset = value,
                maximumBytes = width,
                cancellation = cancellation,
            )?.text.orEmpty()
        }
        val sentinel = read(0)
        val first = read(1)
        return AbilityNamePrefix(
            sentinelIsStructural = sentinel.isNotBlank() && sentinel.none(Char::isLetterOrDigit),
            firstIsNamed = first.any(Char::isLetterOrDigit),
        )
    }

    private fun leadingPointersAreAligned(
        rom: RomImage,
        offset: Int,
        prefix: AbilityNamePrefix,
    ): Boolean {
        if (!prefix.sentinelIsStructural || !prefix.firstIsNamed) return true
        val sentinelDescription = rom.gbaPointer(offset) ?: return false
        val firstDescription = rom.gbaPointer(offset + 4) ?: return false
        return sentinelDescription != firstDescription
    }

    private fun isCompletePointerSpanCandidate(rom: RomImage, offset: Int, count: Int): Boolean {
        if (offset < 0 || (offset and 3) != 0 || count < 2) return false
        val lastEntry = offset.toLong() + (count.toLong() - 1L) * 4L
        if (lastEntry + 4L > rom.size.toLong()) return false
        return runCatching {
            rom.gbaPointer(offset) != null &&
                rom.gbaPointer(offset + 4) != null &&
                rom.gbaPointer(lastEntry.toInt()) != null
        }.getOrDefault(false)
    }

    private fun looksLikeNaturalDescription(value: String, codec: PokemonTextCodec): Boolean =
        LanguageTextPlausibility.looksLikeNaturalDescription(
            value = value,
            language = codec.language,
            minimumLength = 8,
            minimumWords = 2,
        )

    private fun align4(value: Int): Int = (value + 3) and 3.inv()

    private const val MAX_DESCRIPTION_CANDIDATES = 512

    private data class AbilityNamePrefix(
        val sentinelIsStructural: Boolean,
        val firstIsNamed: Boolean,
    )

    private data class DescriptionCandidate(
        val result: AbilityDescriptionResult,
        val references: Int,
        val published: Boolean,
        val semanticallyAligned: Boolean,
    )
}
