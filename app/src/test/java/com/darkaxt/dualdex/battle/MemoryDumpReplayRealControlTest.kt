package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.live.ResolvedValueSource
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Replays every retained raw mapper session through the production live-memory pipeline. */
class MemoryDumpReplayRealControlTest {
    @Test
    fun everyRetainedMemoryDumpMapsThroughItsGenerationSpecificProductionPipeline() {
        val root = Path.of(
            System.getenv("DUALDEX_MEMORY_DUMP_ROOT")
                ?: "D:/Temp/PokemonHacks/validation/memory-dumps",
        )
        assumeTrue("memory-dump corpus does not exist: $root", Files.isDirectory(root))
        val dumps = Files.list(root).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter { it.fileName.toString().endsWith(".json", ignoreCase = true) }
                .sorted()
                .map(::readDump)
                .toList()
        }
        assertEquals("the retained mapper corpus changed without updating this control", 4, dumps.size)

        dumps.forEach { dump ->
            assertTrue("${dump.contentIdentity} omitted raw memory", dump.containsRawMemory)
            val control = controlFor(dump.contentIdentity)
            val loaded = RomSourceLoader.load(control.romPath)
            val catalog = requireNotNull(CatalogParser.parse(loaded.rom).catalog)
            val context = ProductionCompanionRuntime().use { runtime ->
                runtime.loadCatalog(loaded.displayName, catalog)
                requireNotNull(runtime.battleCatalogContext())
            }
            assertEquals(control.generation, context.generation)
            if (control.generation == 3) {
                assertEquals(0xBC, context.saveParseContext?.gen3SaveRuntimeAbi?.trainer?.encryptionKeyOffset)
            }

            dump.snapshots.forEachIndexed { index, snapshot ->
                val memory = validateAndDecodeRegions(dump, snapshot)
                val state = UnifiedGameStateDecoder()
                val transport = ReplayMemoryTransport(memory)
                val resolved = BattleMemoryCoordinator(
                    catalogProvider = { context },
                    transientGameState = state,
                    transportFactory = { transport },
                    autoStart = false,
                ).use { coordinator ->
                    coordinator.updateSession(
                        connected = true,
                        systemId = dump.coreIdentity,
                        romIdentity = context.romIdentity,
                    )
                    repeat(REPLAY_HEARTBEATS) { coordinator.heartbeat() }
                    requireNotNull(state.current) {
                        "${dump.contentIdentity} snapshot $index (${snapshot.label}) produced no unified state"
                    }
                }

                assertEquals(context.generation, resolved.generation)
                assertEquals(ResolvedValueSource.LIVE, resolved.battle.source)
                assertEquals(ResolvedValueSource.LIVE, resolved.location.areaBaseId.source)
                assertEquals(ResolvedValueSource.LIVE, resolved.location.position.source)
                assertTrue("snapshot $index made no production memory reads", transport.commands.isNotEmpty())

                when (control.generation) {
                    1 -> {
                        assertEquals(ResolvedValueSource.UNAVAILABLE, resolved.clock.source)
                        assertEquals(ResolvedValueSource.UNAVAILABLE, resolved.trainer.money.source)
                    }
                    2 -> {
                        assertEquals(ResolvedValueSource.LIVE, resolved.clock.source)
                        assertEquals(ResolvedValueSource.UNAVAILABLE, resolved.trainer.money.source)
                    }
                    3 -> {
                        assertEquals(ResolvedValueSource.LIVE, resolved.trainer.identity.source)
                        assertEquals(ResolvedValueSource.LIVE, resolved.trainer.publicTrainerId.source)
                        assertEquals(ResolvedValueSource.LIVE, resolved.trainer.playTime.source)
                        assertEquals(ResolvedValueSource.LIVE, resolved.trainer.money.source)
                        assertEquals(3_300L, resolved.trainer.money.value)
                        assertEquals(ResolvedValueSource.LIVE, resolved.pokedex.seenSpeciesIds.source)
                        assertEquals(ResolvedValueSource.LIVE, resolved.pokedex.caughtSpeciesIds.source)
                        assertEquals(ResolvedValueSource.LIVE, resolved.party.source)
                    }
                }
            }
        }
    }

    private fun validateAndDecodeRegions(
        dump: DumpBundle,
        snapshot: DumpSnapshot,
    ): Map<Long, ByteArray> {
        val descriptors = dump.descriptors.associateBy(DumpDescriptor::id)
        return snapshot.regions.associate { region ->
            val descriptor = requireNotNull(descriptors[region.descriptorId])
            assertEquals(descriptor.baseAddress, region.baseAddress)
            assertEquals(descriptor.size, region.size)
            val bytes = Base64.getDecoder().decode(requireNotNull(region.base64Bytes))
            assertEquals(region.size, bytes.size)
            assertEquals(region.sha256.lowercase(), sha256(bytes))
            region.baseAddress to bytes
        }
    }

    private fun controlFor(contentIdentity: String): ReplayControl = when {
        "Modern Emerald" in contentIdentity -> ReplayControl(
            generation = 3,
            romPath = Path.of(
                System.getenv("DUALDEX_MODERN_EMERALD_ROM")
                    ?: "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
            ),
        )
        "Yellow Version" in contentIdentity -> ReplayControl(
            generation = 1,
            romPath = Path.of(
                "D:/Temp/PokemonHacks/roms/official/Gen I-II/" +
                    "Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb",
            ),
        )
        "Crystal Version" in contentIdentity -> ReplayControl(
            generation = 2,
            romPath = Path.of(
                "D:/Temp/PokemonHacks/roms/official/Gen I-II/" +
                    "Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc",
            ),
        )
        else -> error("unregistered mapper dump identity: $contentIdentity")
    }.also { control ->
        assumeTrue("control ROM does not exist: ${control.romPath}", Files.isRegularFile(control.romPath))
    }

    private fun readDump(path: Path): DumpBundle =
        Files.newBufferedReader(path).use { Gson().fromJson(it, DumpBundle::class.java) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class ReplayControl(val generation: Int, val romPath: Path)

    private data class DumpBundle(
        val coreIdentity: String,
        val contentIdentity: String,
        val descriptors: List<DumpDescriptor>,
        val snapshots: List<DumpSnapshot>,
        val containsRawMemory: Boolean,
    )

    private data class DumpDescriptor(val id: String, val baseAddress: Long, val size: Int)
    private data class DumpSnapshot(val label: String, val regions: List<DumpRegion>)
    private data class DumpRegion(
        val descriptorId: String,
        val baseAddress: Long,
        val size: Int,
        val sha256: String,
        val base64Bytes: String?,
    )

    private class ReplayMemoryTransport(private val memory: Map<Long, ByteArray>) : NetworkCommandTransport {
        val commands = mutableListOf<String>()
        private val replies = ArrayDeque<ByteArray>()

        override fun send(payload: ByteArray) {
            val command = payload.toString(Charsets.US_ASCII)
            commands += command
            val parts = command.split(' ')
            val address = parts[1].toLong(16)
            val length = parts[2].toInt()
            val source = requireNotNull(memory.entries.singleOrNull { (base, bytes) ->
                address >= base && address + length <= base + bytes.size
            }) { "mapper requested uncaptured memory 0x${address.toString(16)}+$length" }
            val offset = (address - source.key).toInt()
            val encoded = (0 until length).joinToString(" ") { index ->
                "%02X".format(source.value[offset + index].toInt() and 0xFF)
            }
            replies += "READ_CORE_MEMORY ${parts[1]} $encoded".toByteArray(Charsets.US_ASCII)
        }

        override fun poll(): ByteArray? = replies.pollFirst()
        override fun close() = Unit
    }

    private companion object {
        const val REPLAY_HEARTBEATS = 6
    }
}
