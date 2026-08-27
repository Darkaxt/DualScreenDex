package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.gen3.Gen3BagDataSource
import java.security.MessageDigest
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicLong

enum class Gen3LiveDecodedSection(val metricName: String) {
    PLAYER("player"),
    PARTY("party"),
    STORAGE("storage"),
    OVERWORLD("overworld"),
    PROGRESSION("progression"),
}

data class Gen3LiveSectionFingerprintSet(
    val player: String,
    val party: String,
    val storage: String,
    val overworld: String,
    val progression: String,
) {
    operator fun get(section: Gen3LiveDecodedSection): String = when (section) {
        Gen3LiveDecodedSection.PLAYER -> player
        Gen3LiveDecodedSection.PARTY -> party
        Gen3LiveDecodedSection.STORAGE -> storage
        Gen3LiveDecodedSection.OVERWORLD -> overworld
        Gen3LiveDecodedSection.PROGRESSION -> progression
    }
}

/** Hashes only the live ABI slices consumed by each translated section, without retaining source arrays. */
object Gen3LiveSectionFingerprints {
    fun compute(
        regions: Map<String, ByteArray>,
        layout: Gen3RuntimeMemoryLayout,
        context: SaveParseContext,
    ): Gen3LiveSectionFingerprintSet {
        val abi = context.gen3SaveRuntimeAbi
        val saveBlock1 = regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID]
        val saveBlock2 = regions[Gen3LiveMemoryReader.SAVE_BLOCK2_ID]
        val extended = regions[Gen3LiveMemoryReader.EXTENDED_SAVE_ID]

        val party = digest {
            slice("party-count", regions[Gen3LiveMemoryReader.PARTY_COUNT_ID], 0, 1)
            slice(
                "party-records",
                regions[Gen3LiveMemoryReader.PARTY_ID],
                0,
                (layout.playerPartyCapacity ?: 0) * (layout.playerPartyRecordSize ?: 0),
            )
        }
        val storage = digest {
            slice(
                "storage-records",
                regions[Gen3LiveMemoryReader.STORAGE_ID],
                0,
                (layout.pokemonStorageBoxCount ?: 0) * (layout.pokemonStorageBoxCapacity ?: 0) *
                    (layout.pokemonStorageRecordSize ?: 0),
            )
        }
        val player = digest {
            if (abi == null) {
                missing("save-abi")
            } else {
                val trainer = abi.trainer
                slice("name", saveBlock2, trainer.playerNameOffset, trainer.playerNameLength)
                slice("gender", saveBlock2, trainer.genderOffset, 1)
                slice("trainer-id", saveBlock2, trainer.trainerIdOffset, 4)
                slice("play-hours", saveBlock2, trainer.playTimeHoursOffset, 2)
                slice("play-minutes", saveBlock2, trainer.playTimeMinutesOffset, 1)
                trainer.encryptionKeyOffset?.let { slice("encryption-key", saveBlock2, it, 4) }
                slice("money", saveBlock1, trainer.moneyOffset, 4)
                trainer.badgeFlags.forEachIndexed { index, flag ->
                    slice("badge-$index", saveBlock1, flag.byteOffset, 1)
                }
                val flagBytes = (context.internalSpeciesCount + 7) / 8
                val pokedexEnd = minOf(saveBlock2?.size ?: 0, MAX_POKEDEX_OWNED_OFFSET + flagBytes * 2)
                slice("pokedex-search", saveBlock2, POKEDEX_HEADER_START, pokedexEnd - POKEDEX_HEADER_START)
            }
        }
        val overworld = digest {
            slice("map-group", saveBlock1, layout.saveBlock1MapGroupOffset, 1)
            slice("map-number", saveBlock1, layout.saveBlock1MapNumberOffset, 1)
            slice("clock", regions[Gen3LiveMemoryReader.CLOCK_ID], 0, CLOCK_BYTES)
        }
        val progression = digest {
            if (abi == null) {
                missing("save-abi")
            } else {
                abi.trainer.encryptionKeyOffset?.let { slice("encryption-key", saveBlock2, it, 4) }
                abi.bag.pockets.forEach { pocket ->
                    val source = when (pocket.dataSource) {
                        Gen3BagDataSource.SAVE_BLOCK1 -> saveBlock1
                        Gen3BagDataSource.EXTENDED_SAVE -> extended
                    }
                    slice("bag-${pocket.pocket}", source, pocket.byteOffset, pocket.capacity * pocket.slotSize)
                }
                abi.eventFlags?.let { flags ->
                    slice("event-flags", saveBlock1, flags.byteOffset, flags.byteCount)
                }
            }
        }
        return Gen3LiveSectionFingerprintSet(player, party, storage, overworld, progression)
    }

    fun combine(first: String, second: String): String = digest {
        text("first", first)
        text("second", second)
    }

    private fun digest(block: DigestBuilder.() -> Unit): String {
        val builder = DigestBuilder(DIGEST.get().also(MessageDigest::reset))
        builder.block()
        return builder.finish()
    }

    private class DigestBuilder(private val digest: MessageDigest) {
        fun slice(label: String, bytes: ByteArray?, offset: Int, length: Int) {
            text("label", label)
            if (bytes == null || offset < 0 || length < 0 || offset.toLong() + length > bytes.size.toLong()) {
                missing("$label:$offset:$length")
                return
            }
            int(offset)
            int(length)
            if (length > 0) digest.update(bytes, offset, length)
        }

        fun missing(label: String) = text("missing", label)

        fun text(label: String, value: String) {
            digest.update(label.encodeToByteArray())
            digest.update(0)
            digest.update(value.encodeToByteArray())
            digest.update(0)
        }

        private fun int(value: Int) {
            repeat(4) { shift -> digest.update((value ushr (shift * 8)).toByte()) }
        }

        fun finish(): String = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val CLOCK_BYTES = 5
    private const val POKEDEX_HEADER_START = 0x18
    private const val MAX_POKEDEX_OWNED_OFFSET = 0x200
    private val DIGEST = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
}

/** Stores translated section objects only. Fingerprints are immutable hex strings. */
class Gen3LiveTranslatedSectionCache {
    private data class Entry(val generation: Int, val fingerprint: String, val value: Any)

    private val entries = EnumMap<Gen3LiveDecodedSection, Entry>(Gen3LiveDecodedSection::class.java)
    private val decodes = Gen3LiveDecodedSection.entries.associateWith { AtomicLong() }
    private val reuses = Gen3LiveDecodedSection.entries.associateWith { AtomicLong() }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(
        section: Gen3LiveDecodedSection,
        generation: Int,
        fingerprint: String,
        decode: () -> T,
    ): T {
        val existing = entries[section]
        if (existing?.generation == generation && existing.fingerprint == fingerprint) {
            reuses.getValue(section).incrementAndGet()
            return existing.value as T
        }
        val value = decode()
        entries[section] = Entry(generation, fingerprint, value)
        decodes.getValue(section).incrementAndGet()
        return value
    }

    @Synchronized
    fun clearEntries() = entries.clear()

    fun counters(): Map<String, Long> = buildMap {
        Gen3LiveDecodedSection.entries.forEach { section ->
            put("live.decode.${section.metricName}", decodes.getValue(section).get())
            put("live.reuse.${section.metricName}", reuses.getValue(section).get())
        }
    }

    @Synchronized
    internal fun retainedValueTypes(): List<Class<*>> = entries.values.map { it.value.javaClass }
}
