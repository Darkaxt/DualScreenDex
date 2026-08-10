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
    val catalog: BattleCatalogView,
)

class BattleMemoryCoordinator(
    private val catalogProvider: () -> BattleCatalogContext?,
    private val publisher: (BattleTrackingUpdate) -> Unit,
    private val transportFactory: () -> NetworkCommandTransport = { UdpNetworkCommandTransport() },
    autoStart: Boolean = true,
) : AutoCloseable {
    private val resolver = Gen3BattleLayoutResolver()
    private val tracker = BattleObservationTracker()
    private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "dualdex-battle-memory").apply { isDaemon = true }
    }
    private var sessionIdentity: String? = null
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
            systemId.equals(GBA_SYSTEM_ID, ignoreCase = true) &&
            romIdentity != null &&
            context?.romIdentity.equals(romIdentity, ignoreCase = true)
        val nextIdentity = if (nextEligible) romIdentity else null
        if (eligible == nextEligible && sessionIdentity == nextIdentity) return

        val hadBattle = tracker.missed().active
        resetReader()
        tracker.reset(nextIdentity)
        eligible = nextEligible
        sessionIdentity = nextIdentity
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
                process(state.regions.values.single(), context)
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

    private fun process(bytes: ByteArray, context: BattleCatalogContext) {
        val sample = when (readMode) {
            ReadMode.DISCOVERY -> when (val result = resolver.resolve(bytes, context.catalog)) {
                is LayoutResolution.Resolved -> result.sample.also { cachedLayout = it.layout }
                is LayoutResolution.Ambiguous,
                LayoutResolution.NotFound -> null
            }
            ReadMode.CACHED -> {
                val absolute = requireNotNull(cachedLayout)
                val rebased = absolute.rebased(-cachedWindowStart)
                resolver.resolveKnown(bytes, rebased, context.catalog)?.copy(layout = absolute)
                    .also { if (it == null) cachedLayout = null }
            }
        }
        val update = if (sample != null && sample.battleOutcome != 0) {
            val final = tracker.update(context.romIdentity, sample)
            tracker.reset(context.romIdentity)
            cachedLayout = null
            BattleTrackingUpdate(active = false, sample = null, observations = final.observations, ended = true)
        } else if (sample != null) {
            tracker.update(context.romIdentity, sample)
        } else {
            tracker.validatedNoBattle(context.romIdentity)
        }
        if (update.active || update.ended) publisher(update)
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
            tracker.reset()
        }
    }

    private enum class ReadMode { DISCOVERY, CACHED }

    companion object {
        private const val GBA_SYSTEM_ID = "game_boy_advance"
        private const val EWRAM_BASE = 0x02000000L
        private const val EWRAM_BYTES = 0x40000
        private const val COUNT_DELTA = 0x1C
        private const val CACHED_WINDOW_BYTES = 0x45C
        private const val PRODUCTION_CHUNK_BYTES = 1024
        private const val HEARTBEAT_MILLIS = 20L
    }
}
