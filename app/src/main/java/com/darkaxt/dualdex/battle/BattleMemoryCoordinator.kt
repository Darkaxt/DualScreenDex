package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.retroarch.CoreMemoryReadSession
import com.darkaxt.dualdex.retroarch.CoreMemoryReadState
import com.darkaxt.dualdex.retroarch.CoreMemoryRegion
import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.darkaxt.dualdex.retroarch.UdpNetworkCommandTransport
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class BattleCatalogContext(
    val romIdentity: String,
    val generation: Int,
    val catalog: BattleCatalogView,
)

class BattleMemoryCoordinator(
    private val catalogProvider: () -> BattleCatalogContext?,
    private val publisher: (BattleTrackingUpdate) -> Unit,
    private val transportFactory: () -> NetworkCommandTransport = { UdpNetworkCommandTransport() },
    autoStart: Boolean = true,
) : AutoCloseable {
    private val gen1Resolver = Gen1BattleLayoutResolver()
    private val gen2Resolver = Gen2BattleLayoutResolver()
    private val gen3Resolver = Gen3BattleLayoutResolver()
    private val tracker = BattleObservationTracker()
    private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "dualdex-battle-memory").apply { isDaemon = true }
    }
    private var sessionIdentity: String? = null
    private var sessionGeneration = 0
    private var eligible = false
    private var transport: NetworkCommandTransport? = null
    private var reader: CoreMemoryReadSession? = null
    private var cachedLayout: ResolvedBattleLayout? = null
    private var readMode = ReadMode.DISCOVERY
    private var cachedWindowStart = 0

    init {
        if (autoStart) heartbeatExecutor.scheduleWithFixedDelay(
            ::safeHeartbeat,
            0,
            HEARTBEAT_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized
    fun updateSession(connected: Boolean, systemId: String?, romIdentity: String?) {
        val context = catalogProvider()
        val nextEligible = connected &&
            romIdentity != null &&
            context != null &&
            context.romIdentity.equals(romIdentity, ignoreCase = true) &&
            supports(context.generation, systemId)
        val nextIdentity = if (nextEligible) romIdentity else null
        val nextGeneration = if (nextEligible) context.generation else 0
        if (eligible == nextEligible && sessionIdentity == nextIdentity && sessionGeneration == nextGeneration) return

        val hadBattle = tracker.missed().active
        resetReader()
        tracker.reset(nextIdentity)
        eligible = nextEligible
        sessionIdentity = nextIdentity
        sessionGeneration = nextGeneration
        if (hadBattle && !nextEligible) publisher(BattleTrackingUpdate(active = false, sample = null, ended = true))
    }

    @Synchronized
    fun heartbeat() {
        if (!eligible) return
        val context = catalogProvider() ?: return
        if (!context.romIdentity.equals(sessionIdentity, ignoreCase = true)) {
            updateSession(false, null, null)
            return
        }
        val current = reader
        if (current == null) {
            startRead()
            return
        }
        when (val state = current.heartbeat()) {
            CoreMemoryReadState.Idle,
            is CoreMemoryReadState.Reading -> Unit
            is CoreMemoryReadState.Complete -> {
                reader = null
                process(state.regions, context)
            }
            is CoreMemoryReadState.Failed -> {
                reader = null
                closeTransport()
                tracker.missed().takeIf(BattleTrackingUpdate::active)?.let(publisher)
            }
        }
    }

    private fun startRead() {
        val connection = transport ?: transportFactory().also { transport = it }
        val session = CoreMemoryReadSession(connection::send, connection::poll, PRODUCTION_CHUNK_BYTES)
        if (sessionGeneration == 1) {
            val layout = cachedLayout
            if (layout == null) {
                readMode = ReadMode.DISCOVERY
                session.start(listOf(CoreMemoryRegion("wram", GEN1_WRAM_BASE, GEN1_WRAM_BYTES)))
            } else {
                readMode = ReadMode.CACHED
                cachedWindowStart = layout.moveCursorOffset
                val cachedWindowBytes = layout.battlerCountOffset + GEN1_BATTLE_TYPE_DELTA + 1 - cachedWindowStart
                session.start(
                    listOf(
                        CoreMemoryRegion(
                            "battle-window",
                            GEN1_WRAM_BASE + cachedWindowStart,
                            cachedWindowBytes,
                        ),
                    ),
                )
            }
            reader = session
            return
        }
        if (sessionGeneration == 2) {
            readMode = if (cachedLayout == null) ReadMode.DISCOVERY else ReadMode.CACHED
            cachedWindowStart = 0
            session.start(listOf(CoreMemoryRegion("wram", GEN1_WRAM_BASE, GEN1_WRAM_BYTES)))
            reader = session
            return
        }
        val layout = cachedLayout
        if (layout == null) {
            readMode = ReadMode.DISCOVERY
            session.start(listOf(CoreMemoryRegion("ewram", EWRAM_BASE, EWRAM_BYTES)))
        } else {
            readMode = ReadMode.CACHED
            cachedWindowStart = layout.battleMonsOffset - COUNT_DELTA
            session.start(
                listOf(
                    CoreMemoryRegion(
                        "battle-window",
                        EWRAM_BASE + cachedWindowStart,
                        CACHED_WINDOW_BYTES,
                    ),
                ),
            )
        }
        reader = session
    }

    private fun process(regions: Map<String, ByteArray>, context: BattleCatalogContext) {
        val validatedGen2NoBattle = context.generation == 2 && knownGen2NonBattle(regions)
        val sample = if (context.generation == 1) {
            val source = requireNotNull(regions.values.singleOrNull())
            if (readMode == ReadMode.CACHED) {
                val absolute = requireNotNull(cachedLayout)
                val rebased = absolute.rebased(-cachedWindowStart)
                gen1Resolver.resolveKnown(source, rebased, context.catalog)?.copy(layout = absolute)
                    .also { if (it == null && !knownGen1NonBattle(source)) cachedLayout = null }
            } else {
                when (val result = gen1Resolver.resolve(source, context.catalog)) {
                    is LayoutResolution.Resolved -> result.sample.also { cachedLayout = it.layout }
                    is LayoutResolution.Ambiguous,
                    LayoutResolution.NotFound -> null
                }
            }
        } else if (context.generation == 2) {
            val source = requireNotNull(regions.values.singleOrNull())
            val layout = cachedLayout
            if (layout == null) {
                when (val result = gen2Resolver.resolve(source, context.catalog)) {
                    is LayoutResolution.Resolved -> result.sample.also { cachedLayout = it.layout }
                    is LayoutResolution.Ambiguous,
                    LayoutResolution.NotFound -> null
                }
            } else {
                gen2Resolver.resolveKnown(source, layout, context.catalog)
            }
        } else when (readMode) {
            ReadMode.DISCOVERY -> when (val result = gen3Resolver.resolve(requireNotNull(regions.values.singleOrNull()), context.catalog)) {
                is LayoutResolution.Resolved -> result.sample.also { cachedLayout = it.layout }
                is LayoutResolution.Ambiguous,
                LayoutResolution.NotFound -> null
            }
            ReadMode.CACHED -> {
                val bytes = requireNotNull(regions.values.singleOrNull())
                val absolute = requireNotNull(cachedLayout)
                val rebased = absolute.rebased(-cachedWindowStart)
                gen3Resolver.resolveKnown(bytes, rebased, context.catalog)?.copy(layout = absolute)
                    .also { if (it == null) cachedLayout = null }
            }
        }
        val update = if (sample != null && sample.battleOutcome != 0) {
            val final = tracker.update(context.romIdentity, sample)
            tracker.reset(context.romIdentity)
            cachedLayout = null
            BattleTrackingUpdate(
                active = false,
                sample = null,
                observations = final.observations,
                discoveredMatchups = final.discoveredMatchups,
                ended = true,
            )
        } else if (sample != null) {
            tracker.update(context.romIdentity, sample)
        } else if (context.generation == 2 && !validatedGen2NoBattle) {
            tracker.missed()
        } else {
            tracker.validatedNoBattle(context.romIdentity)
        }
        if (update.active || update.ended) publisher(update)
    }

    private fun knownGen1NonBattle(bytes: ByteArray): Boolean {
        val layout = cachedLayout ?: return false
        val battleFlagOffset = layout.battlerCountOffset - cachedWindowStart
        return battleFlagOffset in bytes.indices && bytes[battleFlagOffset].toInt() and 0xff == 0
    }

    private fun knownGen2NonBattle(regions: Map<String, ByteArray>): Boolean {
        val layout = cachedLayout ?: return false
        val bytes = regions.values.singleOrNull() ?: return false
        val battleFlagOffset = layout.battlerCountOffset
        return battleFlagOffset in bytes.indices && bytes[battleFlagOffset].toInt() and 0xff == 0
    }

    private fun ResolvedBattleLayout.rebased(delta: Int): ResolvedBattleLayout = copy(
        battleMonsOffset = battleMonsOffset + delta,
        battlerCountOffset = battlerCountOffset + delta,
        battlerPositionsOffset = battlerPositionsOffset + delta,
        outcomeOffset = outcomeOffset + delta,
        moveCursorOffset = moveCursorOffset + delta,
        targetCursorOffset = targetCursorOffset + delta,
    )

    private fun safeHeartbeat() {
        runCatching(::heartbeat)
    }

    @Synchronized
    private fun resetReader() {
        reader = null
        cachedLayout = null
        closeTransport()
    }

    private fun closeTransport() {
        transport?.close()
        transport = null
    }

    override fun close() {
        heartbeatExecutor.shutdown()
        synchronized(this) {
            resetReader()
            eligible = false
            sessionIdentity = null
            sessionGeneration = 0
            tracker.reset()
        }
    }

    private enum class ReadMode { DISCOVERY, CACHED }

    companion object {
        private const val GBA_SYSTEM_ID = "game_boy_advance"
        private const val GB_SYSTEM_ID = "game_boy"
        private const val GBC_SYSTEM_ID = "game_boy_color"
        private const val GEN1_WRAM_BASE = 0xc000L
        private const val GEN1_WRAM_BYTES = 0x2000
        private const val GEN1_BATTLE_TYPE_DELTA = 3
        private const val EWRAM_BASE = 0x02000000L
        private const val EWRAM_BYTES = 0x40000
        private const val COUNT_DELTA = 0x1C
        private const val CACHED_WINDOW_BYTES = 0x45C
        private const val PRODUCTION_CHUNK_BYTES = 1024
        private const val HEARTBEAT_MILLIS = 20L
    }

    private fun supports(generation: Int, systemId: String?): Boolean = when (generation) {
        1 -> systemId.equals(GB_SYSTEM_ID, ignoreCase = true) || systemId.equals(GBC_SYSTEM_ID, ignoreCase = true)
        2 -> systemId.equals(GB_SYSTEM_ID, ignoreCase = true) || systemId.equals(GBC_SYSTEM_ID, ignoreCase = true)
        3 -> systemId.equals(GBA_SYSTEM_ID, ignoreCase = true)
        else -> false
    }
}
