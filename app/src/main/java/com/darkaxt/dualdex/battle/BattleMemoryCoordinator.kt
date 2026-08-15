package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.retroarch.CoreMemoryReadSession
import com.darkaxt.dualdex.retroarch.CoreMemoryReadState
import com.darkaxt.dualdex.retroarch.CoreMemoryRegion
import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.darkaxt.dualdex.retroarch.UdpNetworkCommandTransport
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private data class Gen3LiveLocation(val areaBaseId: Int, val position: RuntimeMapPosition?)

data class BattleCatalogContext(
    val romIdentity: String,
    val generation: Int,
    val catalog: BattleCatalogView,
    val gen3SaveBlock1PointerAddress: Long? = null,
    val gen3RuntimeMemoryLayout: Gen3RuntimeMemoryLayout? = null,
    val liveAreaMemoryLayout: LiveAreaMemoryLayout? = null,
    val saveParseContext: SaveParseContext? = null,
)

data class LiveAreaMemoryLayout(
    val wramOffset: Int,
    val byteCount: Int,
    val positionXWramOffset: Int? = null,
    val positionYWramOffset: Int? = null,
) {
    init {
        require(wramOffset in 0 until 0x2000)
        require(byteCount in 1..2 && wramOffset + byteCount <= 0x2000)
        require((positionXWramOffset == null) == (positionYWramOffset == null))
        require(positionXWramOffset == null || positionXWramOffset in 0 until 0x2000)
        require(positionYWramOffset == null || positionYWramOffset in 0 until 0x2000)
    }

    val positionWindowOffset: Int?
        get() = positionXWramOffset?.let { minOf(it, requireNotNull(positionYWramOffset)) }

    val positionWindowBytes: Int
        get() = if (positionXWramOffset == null) {
            0
        } else {
            maxOf(positionXWramOffset, requireNotNull(positionYWramOffset)) -
                requireNotNull(positionWindowOffset) + 1
        }
}

internal fun liveAreaMemoryLayout(family: EngineFamily): LiveAreaMemoryLayout? = when (family) {
    EngineFamily.RED_BLUE -> LiveAreaMemoryLayout(0x135E, 1, 0x1362, 0x1361)
    EngineFamily.YELLOW -> LiveAreaMemoryLayout(0x135D, 1, 0x1361, 0x1360)
    EngineFamily.GOLD_SILVER -> LiveAreaMemoryLayout(0x1A00, 2, 0x1A03, 0x1A02)
    EngineFamily.CRYSTAL -> LiveAreaMemoryLayout(0x1CB5, 2, 0x1CB8, 0x1CB7)
    else -> null
}

internal fun battleHeartbeatDelayMillis(
    eligible: Boolean,
    discovering: Boolean,
    pollingIntervalMs: Int,
): Long = if (eligible) pollingIntervalMs.coerceIn(1, 20).toLong() else 20L

class BattleMemoryCoordinator(
    private val catalogProvider: () -> BattleCatalogContext?,
    private val publisher: (BattleTrackingUpdate) -> Unit,
    private val locationPublisher: (Int?) -> Unit = {},
    private val positionPublisher: (RuntimeMapPosition?) -> Unit = {},
    private val partyPublisher: (List<OwnedIndividual>?) -> Unit = {},
    private val transportFactory: () -> NetworkCommandTransport = { UdpNetworkCommandTransport() },
    private val pollingIntervalProvider: () -> Int = { 5 },
    autoStart: Boolean = true,
) : AutoCloseable {
    private val gen1Resolver = Gen1BattleLayoutResolver()
    private val gen2Resolver = Gen2BattleLayoutResolver()
    private val gen3Resolver = Gen3BattleLayoutResolver()
    private val gen3MainResolver = Gen3MainStateResolver()
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
    private var cachedSaveBlock1Address: Long? = null
    private var requestedSaveBlock1Address: Long? = null
    private var cachedGen3MainLayout: Gen3MainLayout? = null
    private var gen3BattleDiscoveryRequested = false
    private var observedOverworldCallbacks: Gen3MainCallbacks? = null
    private var observedOverworldSample: BattleMemorySample? = null
    private var pendingOverworldCallbacks: Gen3MainCallbacks? = null
    private var pendingOverworldSample: BattleMemorySample? = null
    private var pendingOverworldObservations = 0
    private var awaitingOverworldAfterOutcome = false
    private var lastPublishedLiveParty: List<OwnedIndividual>? = null
    @Volatile private var closed = false

    init {
        if (autoStart) scheduleHeartbeat(0)
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
        if (!nextEligible || sessionIdentity != nextIdentity) {
            locationPublisher(null)
            positionPublisher(null)
            lastPublishedLiveParty = null
            partyPublisher(null)
        }
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
                session.start(buildList {
                    add(
                        CoreMemoryRegion(
                            "battle-window",
                            GEN1_WRAM_BASE + cachedWindowStart,
                            cachedWindowBytes,
                        ),
                    )
                    catalogProvider()?.liveAreaMemoryLayout?.let { location ->
                        add(CoreMemoryRegion("live-location", GEN1_WRAM_BASE + location.wramOffset, location.byteCount))
                        location.positionWindowOffset?.let { positionOffset ->
                            add(
                                CoreMemoryRegion(
                                    "live-position",
                                    GEN1_WRAM_BASE + positionOffset,
                                    location.positionWindowBytes,
                                ),
                            )
                        }
                    }
                })
            }
            reader = session
            return
        }
        if (sessionGeneration == 2) {
            readMode = if (cachedLayout == null) ReadMode.DISCOVERY else ReadMode.CACHED
            cachedWindowStart = 0
            val layout = cachedLayout
            if (layout == null) {
                session.start(listOf(CoreMemoryRegion("wram", GEN1_WRAM_BASE, GEN1_WRAM_BYTES)))
            } else {
                val playerStart = gen2PlayerWindowStart(layout)
                val playerEnd = gen2PlayerWindowEnd(layout)
                val enemyStart = gen2EnemyWindowStart(layout)
                val enemyEnd = gen2EnemyWindowEnd(layout)
                session.start(buildList {
                    add(CoreMemoryRegion("player-state", GEN1_WRAM_BASE + playerStart, playerEnd - playerStart))
                    add(CoreMemoryRegion("enemy-state", GEN1_WRAM_BASE + enemyStart, enemyEnd - enemyStart))
                    catalogProvider()?.liveAreaMemoryLayout?.let { location ->
                        add(CoreMemoryRegion("live-location", GEN1_WRAM_BASE + location.wramOffset, location.byteCount))
                        location.positionWindowOffset?.let { positionOffset ->
                            add(
                                CoreMemoryRegion(
                                    "live-position",
                                    GEN1_WRAM_BASE + positionOffset,
                                    location.positionWindowBytes,
                                ),
                            )
                        }
                    }
                })
            }
            reader = session
            return
        }
        val layout = cachedLayout
        val pointerGlobal = catalogProvider()?.gen3SaveBlock1PointerAddress
        val context = catalogProvider()
        val runtimeLayout = context?.gen3RuntimeMemoryLayout
        if (layout == null && runtimeLayout != null && !gen3BattleDiscoveryRequested) {
            readMode = ReadMode.RUNTIME
            requestedSaveBlock1Address = cachedSaveBlock1Address
            session.start(buildList {
                add(CoreMemoryRegion(
                    "main-state",
                    runtimeLayout.mainAddress,
                    Gen3MainStateResolver.HEADER_BYTES,
                ))
                add(CoreMemoryRegion(
                    "main-lifecycle",
                    runtimeLayout.inBattleAddress,
                    1,
                ))
                runtimeLayout.multiUsePlayerCursorAddress?.let { cursorAddress ->
                    add(CoreMemoryRegion("main-target-cursor", cursorAddress, 1))
                }
                runtimeLayout.battleTypeFlagsAddress?.let { flagsAddress ->
                    add(CoreMemoryRegion(
                        "battle-type-flags",
                        flagsAddress,
                        Gen3RuntimeMemoryDecoder.BATTLE_TYPE_FLAGS_BYTES,
                    ))
                }
                pointerGlobal?.let { add(CoreMemoryRegion("save-block-pointer", it, 4)) }
                requestedSaveBlock1Address?.let { saveBlock ->
                    val decoder = Gen3RuntimeMemoryDecoder(runtimeLayout)
                    add(CoreMemoryRegion(
                        "save-block-location",
                        saveBlock + decoder.locationWindowOffset,
                        decoder.locationWindowBytes,
                    ))
                }
                addAll(livePartyRegions(context))
            })
            reader = session
            return
        }
        if (layout == null) {
            readMode = ReadMode.DISCOVERY
            gen3BattleDiscoveryRequested = false
            requestedSaveBlock1Address = null
            session.start(buildList {
                add(CoreMemoryRegion("ewram", EWRAM_BASE, EWRAM_BYTES))
                add(CoreMemoryRegion("iwram", IWRAM_BASE, IWRAM_BYTES))
                pointerGlobal?.let { add(CoreMemoryRegion("save-block-pointer", it, 4)) }
                addAll(livePartyRegions(context))
            })
        } else {
            readMode = ReadMode.CACHED
            cachedWindowStart = layout.battleMonsOffset - COUNT_DELTA
            requestedSaveBlock1Address = cachedSaveBlock1Address
            session.start(buildList {
                add(CoreMemoryRegion(
                        "battle-window",
                        EWRAM_BASE + cachedWindowStart,
                        CACHED_WINDOW_BYTES,
                    ))
                runtimeLayout?.let { runtime ->
                    add(CoreMemoryRegion(
                        "main-state",
                        runtime.mainAddress,
                        Gen3MainStateResolver.HEADER_BYTES,
                    ))
                    add(CoreMemoryRegion("main-lifecycle", runtime.inBattleAddress, 1))
                    runtime.multiUsePlayerCursorAddress?.let { cursorAddress ->
                        add(CoreMemoryRegion("main-target-cursor", cursorAddress, 1))
                    }
                    runtime.battleTypeFlagsAddress?.let { flagsAddress ->
                        add(CoreMemoryRegion(
                            "battle-type-flags",
                            flagsAddress,
                            Gen3RuntimeMemoryDecoder.BATTLE_TYPE_FLAGS_BYTES,
                        ))
                    }
                } ?: cachedGen3MainLayout?.let { mainLayout ->
                    add(CoreMemoryRegion(
                        "main-state",
                        IWRAM_BASE + mainLayout.offset,
                        Gen3MainStateResolver.HEADER_BYTES,
                    ))
                }
                pointerGlobal?.let { add(CoreMemoryRegion("save-block-pointer", it, 4)) }
                val runtimeDecoder = contextRuntimeLayout()?.let(::Gen3RuntimeMemoryDecoder)
                val locationOffset = runtimeDecoder?.locationWindowOffset ?: SAVE_LOCATION_GROUP_OFFSET
                val locationBytes = runtimeDecoder?.locationWindowBytes ?: Gen3RuntimeMemoryDecoder.MAP_ID_BYTES
                requestedSaveBlock1Address?.let { saveBlock ->
                    add(CoreMemoryRegion(
                        "save-block-location",
                        saveBlock + locationOffset,
                        locationBytes,
                    ))
                }
                addAll(livePartyRegions(context))
            })
        }
        reader = session
    }

    private fun process(regions: Map<String, ByteArray>, context: BattleCatalogContext) {
        val validatedGen2NoBattle = context.generation == 2 && knownGen2NonBattle(regions)
        val resolvedSample = if (context.generation == 1) {
            val source = requireNotNull(regions["wram"] ?: regions["battle-window"])
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
            val source = if (readMode == ReadMode.DISCOVERY) {
                requireNotNull(regions["wram"])
            } else {
                reconstructGen2Wram(regions)
            }
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
            ReadMode.DISCOVERY -> when (val result = gen3Resolver.resolve(requireNotNull(regions["ewram"]), context.catalog)) {
                is LayoutResolution.Resolved -> result.sample.also { cachedLayout = it.layout }
                is LayoutResolution.Ambiguous,
                LayoutResolution.NotFound -> null
            }
            ReadMode.CACHED -> {
                val bytes = requireNotNull(regions["battle-window"])
                val absolute = requireNotNull(cachedLayout)
                val rebased = absolute.rebased(-cachedWindowStart)
                gen3Resolver.resolveKnown(bytes, rebased, context.catalog)?.copy(layout = absolute)
                    .also { if (it == null && !knownGen3NonBattle(bytes, rebased)) cachedLayout = null }
            }
            ReadMode.RUNTIME -> null
        }
        val mainState = resolveGen3MainState(regions, context)
        val liveLocation = if (context.generation == 3 && supportsLiveArea(context)) {
            resolveCurrentGen3Location(regions, context)
        } else {
            null
        }
        val gbMapPosition = if (context.generation in 1..2 && supportsLiveArea(context)) {
            resolveCurrentGbPosition(regions, context)
        } else {
            null
        }
        val gen3Runtime = if (context.generation == 3) {
            val battleActive = resolveGen3BattleActive(regions, context)
            Gen3RuntimeSnapshot(
                battleActive = battleActive,
                areaBaseId = liveLocation?.areaBaseId,
                mapPosition = liveLocation?.position,
                targetBattler = resolveGen3LiveTarget(regions, context),
                encounterKind = if (battleActive == true) {
                    resolveGen3EncounterKind(regions, context)
                } else {
                    BattleEncounterKind.UNKNOWN
                },
            )
        } else {
            null
        }
        publishLiveParty(regions, context)
        val lifecycleActive = gen3Runtime?.battleActive
        if (context.generation == 3 && cachedLayout == null) {
            cachedLayout = if (lifecycleActive == true) parserResolvedGen3BattleLayout(context) else null
            gen3BattleDiscoveryRequested = lifecycleActive == true && cachedLayout == null
        }
        val wasActive = tracker.missed().active
        val lifecycleEnded = context.generation == 3 &&
            wasActive &&
            when (lifecycleActive) {
                false -> true
                true -> false
                null -> mainState != null && mainState.callbacks == observedOverworldCallbacks
        }
        if (supportsLiveArea(context)) {
            locationPublisher(
                if (context.generation == 3) gen3Runtime?.areaBaseId else resolveCurrentArea(regions, context),
            )
            when (context.generation) {
                1, 2 -> positionPublisher(gbMapPosition)
                3 -> positionPublisher(gen3Runtime?.mapPosition)
            }
        }
        val sample = when {
            context.generation != 3 -> resolvedSample
            lifecycleActive == false -> null
            lifecycleActive == true -> resolvedSample.takeUnless { awaitingOverworldAfterOutcome }
            mainState != null -> qualifyGen3BattleSample(mainState, resolvedSample, wasActive)
            else -> resolvedSample
        }?.let { resolved ->
            if (context.generation == 3) {
                resolved.withLiveTargetBattler(gen3Runtime?.targetBattler)
                    .copy(encounterKind = gen3Runtime?.encounterKind ?: BattleEncounterKind.UNKNOWN)
            } else {
                resolved
            }
        }
        val endedByOutcome = sample != null && sample.battleOutcome != 0
        val update = if (lifecycleEnded || endedByOutcome) {
            val final = sample?.let { tracker.update(context.romIdentity, it) } ?: tracker.missed()
            tracker.reset(context.romIdentity)
            if (context.generation == 3) {
                if (lifecycleEnded) {
                    observedOverworldSample = resolvedSample
                    awaitingOverworldAfterOutcome = false
                } else {
                    awaitingOverworldAfterOutcome = true
                }
                clearPendingOverworldObservation()
            }
            BattleTrackingUpdate(
                active = false,
                sample = null,
                observations = final.observations,
                discoveredMatchups = final.discoveredMatchups,
                ended = true,
            )
        } else if (sample != null) {
            tracker.update(context.romIdentity, sample)
        } else if (context.generation == 3 && lifecycleActive == true) {
            tracker.missed()
        } else if (context.generation == 2 && !validatedGen2NoBattle) {
            tracker.missed()
        } else {
            tracker.validatedNoBattle(context.romIdentity)
        }
        if (update.active || update.ended) publisher(update)
    }

    private fun qualifyGen3BattleSample(
        mainState: Gen3MainState,
        sample: BattleMemorySample?,
        wasActive: Boolean,
    ): BattleMemorySample? {
        if (wasActive) return sample
        val callbacks = mainState.callbacks
        val overworld = observedOverworldCallbacks
        if (overworld == null) {
            observedOverworldCallbacks = callbacks
            observedOverworldSample = sample
            clearPendingOverworldObservation()
            return null
        }
        if (callbacks == overworld) {
            observedOverworldSample = sample
            awaitingOverworldAfterOutcome = false
            clearPendingOverworldObservation()
            return null
        }
        if (awaitingOverworldAfterOutcome) return null
        if (sample != null && sample != observedOverworldSample) {
            clearPendingOverworldObservation()
            return sample
        }
        if (pendingOverworldCallbacks == callbacks && pendingOverworldSample == sample) {
            pendingOverworldObservations++
        } else {
            pendingOverworldCallbacks = callbacks
            pendingOverworldSample = sample
            pendingOverworldObservations = 1
        }
        if (pendingOverworldObservations >= REQUIRED_STABLE_OVERWORLD_OBSERVATIONS) {
            observedOverworldCallbacks = callbacks
            observedOverworldSample = sample
            clearPendingOverworldObservation()
        }
        return null
    }

    private fun clearPendingOverworldObservation() {
        pendingOverworldCallbacks = null
        pendingOverworldSample = null
        pendingOverworldObservations = 0
    }

    private fun resolveGen3MainState(
        regions: Map<String, ByteArray>,
        context: BattleCatalogContext,
    ): Gen3MainState? {
        if (context.generation != 3) return null
        regions["main-state"]?.let { bytes ->
            val absolute = context.gen3RuntimeMemoryLayout?.mainAddress
                ?.minus(IWRAM_BASE)
                ?.toInt()
                ?.let(::Gen3MainLayout)
                ?: cachedGen3MainLayout
                ?: return null
            return gen3MainResolver.resolveKnown(bytes, Gen3MainLayout(0))?.copy(layout = absolute)
        }
        val bytes = regions["iwram"] ?: return null
        context.gen3RuntimeMemoryLayout?.let { runtime ->
            val offset = (runtime.mainAddress - IWRAM_BASE).toInt()
            return gen3MainResolver.resolveKnown(bytes, Gen3MainLayout(offset))
        }
        return gen3MainResolver.resolve(bytes)?.also { cachedGen3MainLayout = it.layout }
    }

    private fun resolveGen3BattleActive(
        regions: Map<String, ByteArray>,
        context: BattleCatalogContext,
    ): Boolean? {
        val layout = context.gen3RuntimeMemoryLayout ?: return null
        val bytes = regions["main-lifecycle"] ?: regions["iwram"]?.let { iwram ->
            val offset = (layout.inBattleAddress - IWRAM_BASE).toInt()
            if (offset !in iwram.indices) null else byteArrayOf(iwram[offset])
        }
        return Gen3RuntimeMemoryDecoder(layout).decodeBattleActive(bytes)
    }

    private fun resolveGen3LiveTarget(
        regions: Map<String, ByteArray>,
        context: BattleCatalogContext,
    ): Int? {
        val layout = context.gen3RuntimeMemoryLayout ?: return null
        val cursorAddress = layout.multiUsePlayerCursorAddress ?: return null
        val bytes = regions["main-target-cursor"] ?: regions["iwram"]?.let { iwram ->
            val offset = (cursorAddress - IWRAM_BASE).toInt()
            if (offset !in iwram.indices) null else byteArrayOf(iwram[offset])
        }
        return Gen3RuntimeMemoryDecoder(layout).decodeTargetBattler(bytes)
    }

    private fun resolveGen3EncounterKind(
        regions: Map<String, ByteArray>,
        context: BattleCatalogContext,
    ): BattleEncounterKind {
        val layout = context.gen3RuntimeMemoryLayout ?: return BattleEncounterKind.UNKNOWN
        val address = layout.battleTypeFlagsAddress ?: return BattleEncounterKind.UNKNOWN
        val bytes = regions["battle-type-flags"] ?: regions["ewram"]?.let { ewram ->
            val offset = (address - EWRAM_BASE).toInt()
            if (offset < 0 || offset + Gen3RuntimeMemoryDecoder.BATTLE_TYPE_FLAGS_BYTES > ewram.size) {
                null
            } else {
                ewram.copyOfRange(offset, offset + Gen3RuntimeMemoryDecoder.BATTLE_TYPE_FLAGS_BYTES)
            }
        }
        return Gen3RuntimeMemoryDecoder(layout).decodeBattleEncounterKind(bytes)
    }

    private fun knownGen1NonBattle(bytes: ByteArray): Boolean {
        val layout = cachedLayout ?: return false
        val battleFlagOffset = layout.battlerCountOffset - cachedWindowStart
        return battleFlagOffset in bytes.indices && bytes[battleFlagOffset].toInt() and 0xff == 0
    }

    private fun knownGen2NonBattle(regions: Map<String, ByteArray>): Boolean {
        val layout = cachedLayout ?: return false
        val bytes = regions["wram"] ?: regions["enemy-state"] ?: return false
        val battleFlagOffset = if (regions.containsKey("wram")) {
            layout.battlerCountOffset
        } else {
            layout.battlerCountOffset - gen2EnemyWindowStart(layout)
        }
        return battleFlagOffset in bytes.indices && bytes[battleFlagOffset].toInt() and 0xff == 0
    }

    private fun knownGen3NonBattle(bytes: ByteArray, layout: ResolvedBattleLayout): Boolean {
        val offset = layout.battlerCountOffset
        return offset in bytes.indices && bytes[offset].toInt() and 0xff == 0
    }

    private fun resolveCurrentArea(regions: Map<String, ByteArray>, context: BattleCatalogContext): Int? = when (context.generation) {
        1 -> resolveCurrentGen1Area(regions)
        2 -> resolveCurrentGen2Area(regions)
        3 -> resolveCurrentGen3Area(regions, context)
        else -> null
    }

    private fun supportsLiveArea(context: BattleCatalogContext): Boolean = when (context.generation) {
        1, 2 -> context.liveAreaMemoryLayout != null
        3 -> context.gen3SaveBlock1PointerAddress != null
        else -> false
    }

    private fun resolveCurrentGen1Area(regions: Map<String, ByteArray>): Int? {
        val offset = contextLiveAreaOffset(generation = 1) ?: return null
        val value = regions["live-location"]?.singleOrNull()?.toInt()?.and(0xFF)
            ?: regions["wram"]?.getOrNull(offset)?.toInt()?.and(0xFF)
            ?: return null
        return value.takeUnless { it == 0xFF }
    }

    private fun resolveCurrentGbPosition(
        regions: Map<String, ByteArray>,
        context: BattleCatalogContext,
    ): RuntimeMapPosition? {
        val layout = context.liveAreaMemoryLayout ?: return null
        val xOffset = layout.positionXWramOffset ?: return null
        val yOffset = layout.positionYWramOffset ?: return null
        val positionWindow = regions["live-position"]
        val x = if (positionWindow != null) {
            positionWindow.getOrNull(xOffset - requireNotNull(layout.positionWindowOffset))
        } else {
            regions["wram"]?.getOrNull(xOffset)
        }?.toInt()?.and(0xFF) ?: return null
        val y = if (positionWindow != null) {
            positionWindow.getOrNull(yOffset - requireNotNull(layout.positionWindowOffset))
        } else {
            regions["wram"]?.getOrNull(yOffset)
        }?.toInt()?.and(0xFF) ?: return null
        return RuntimeMapPosition(x, y).takeUnless { x == 0xFF || y == 0xFF }
    }

    private fun resolveCurrentGen2Area(regions: Map<String, ByteArray>): Int? {
        val offset = contextLiveAreaOffset(generation = 2) ?: return null
        val bytes = regions["live-location"] ?: regions["wram"]?.copyOfRange(offset, offset + 2) ?: return null
        if (bytes.size != 2) return null
        val group = bytes[0].toInt() and 0xFF
        val map = bytes[1].toInt() and 0xFF
        return if (group in 0..63 && map != 0xFF) (group shl 8) or map else null
    }

    private fun resolveCurrentGen3Area(regions: Map<String, ByteArray>, context: BattleCatalogContext): Int? =
        resolveCurrentGen3Location(regions, context)?.areaBaseId

    private fun resolveCurrentGen3Location(
        regions: Map<String, ByteArray>,
        context: BattleCatalogContext,
    ): Gen3LiveLocation? {
        if (context.gen3SaveBlock1PointerAddress == null) return null
        val pointerBytes = regions["save-block-pointer"] ?: return null
        if (pointerBytes.size != 4) return null
        val pointer = pointerBytes.foldIndexed(0L) { index, value, byte ->
            value or ((byte.toInt() and 0xFF).toLong() shl (index * 8))
        }
        val runtimeDecoder = context.gen3RuntimeMemoryLayout?.let(::Gen3RuntimeMemoryDecoder)
        val windowOffset = runtimeDecoder?.locationWindowOffset ?: SAVE_LOCATION_GROUP_OFFSET
        val windowBytes = runtimeDecoder?.locationWindowBytes ?: Gen3RuntimeMemoryDecoder.MAP_ID_BYTES
        if (pointer < EWRAM_BASE || pointer + windowOffset + windowBytes > EWRAM_BASE + EWRAM_BYTES) {
            cachedSaveBlock1Address = null
            return null
        }
        val location = if (readMode == ReadMode.DISCOVERY) {
            val ewram = regions["ewram"] ?: return null
            val offset = (pointer - EWRAM_BASE).toInt() + windowOffset
            if (offset < 0 || offset + windowBytes > ewram.size) return null
            ewram.copyOfRange(offset, offset + windowBytes)
        } else {
            if (pointer != requestedSaveBlock1Address) {
                cachedSaveBlock1Address = pointer
                return null
            }
            regions["save-block-location"] ?: return null
        }
        cachedSaveBlock1Address = pointer
        val areaBaseId = runtimeDecoder?.decodeArea(location)
            ?: if (location.size == Gen3RuntimeMemoryDecoder.MAP_ID_BYTES) {
                ((location[0].toInt() and 0xFF) shl 8) or (location[1].toInt() and 0xFF)
            } else {
                null
            }
            ?: return null
        return Gen3LiveLocation(areaBaseId, runtimeDecoder?.decodePosition(location))
    }

    private fun contextLiveAreaOffset(generation: Int): Int? = catalogProvider()
        ?.liveAreaMemoryLayout
        ?.takeIf { it.byteCount == generation.coerceAtMost(2) }
        ?.wramOffset

    private fun contextRuntimeLayout(): Gen3RuntimeMemoryLayout? = catalogProvider()?.gen3RuntimeMemoryLayout

    private fun parserResolvedGen3BattleLayout(context: BattleCatalogContext): ResolvedBattleLayout? {
        val address = context.gen3RuntimeMemoryLayout?.battleMonsAddress ?: return null
        val offset = (address - EWRAM_BASE).toInt()
        if (offset !in COUNT_DELTA until EWRAM_BYTES - CACHED_WINDOW_BYTES) return null
        return ResolvedBattleLayout(
            battleMonsOffset = offset,
            battlerCountOffset = offset - COUNT_DELTA,
            battlerPositionsOffset = offset - POSITIONS_DELTA,
            outcomeOffset = offset + OUTCOME_DELTA,
            moveCursorOffset = offset + MOVE_CURSOR_DELTA,
            targetCursorOffset = offset + TARGET_CURSOR_DELTA,
            battlerCount = 2,
        )
    }

    private fun livePartyRegions(context: BattleCatalogContext?): List<CoreMemoryRegion> {
        val layout = context?.gen3RuntimeMemoryLayout ?: return emptyList()
        if (context.saveParseContext == null) return emptyList()
        val countAddress = layout.playerPartyCountAddress ?: return emptyList()
        val partyAddress = layout.playerPartyAddress ?: return emptyList()
        return listOf(
            CoreMemoryRegion("live-party-count", countAddress, 1),
            CoreMemoryRegion("live-party", partyAddress, Gen3LivePartyDecoder.PARTY_BYTES),
        )
    }

    private fun publishLiveParty(regions: Map<String, ByteArray>, context: BattleCatalogContext) {
        if (context.generation != 3) return
        val parseContext = context.saveParseContext ?: return
        val decoded = Gen3LivePartyDecoder.decode(
            regions["live-party-count"],
            regions["live-party"],
            parseContext,
        ) ?: return
        if (decoded != lastPublishedLiveParty) {
            lastPublishedLiveParty = decoded
            partyPublisher(decoded)
        }
    }

    private fun reconstructGen2Wram(regions: Map<String, ByteArray>): ByteArray {
        val layout = requireNotNull(cachedLayout)
        return ByteArray(GEN1_WRAM_BYTES).also { wram ->
            regions["player-state"]?.copyInto(wram, gen2PlayerWindowStart(layout))
            regions["enemy-state"]?.copyInto(wram, gen2EnemyWindowStart(layout))
        }
    }

    private fun gen2PlayerWindowStart(layout: ResolvedBattleLayout): Int =
        minOf(layout.battleMonsOffset, layout.moveCursorOffset)

    private fun gen2PlayerWindowEnd(layout: ResolvedBattleLayout): Int = maxOf(
        layout.battleMonsOffset + GEN2_BATTLE_MON_BYTES,
        layout.outcomeOffset + 1,
        layout.targetCursorOffset + 1,
    )

    private fun gen2EnemyWindowStart(layout: ResolvedBattleLayout): Int =
        layout.battlerCountOffset - GEN2_ENEMY_FROM_BATTLE_FLAG_DELTA

    private fun gen2EnemyWindowEnd(layout: ResolvedBattleLayout): Int =
        layout.battlerCountOffset + GEN1_BATTLE_TYPE_DELTA + 1

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

    private fun scheduleHeartbeat(delayMillis: Long) {
        heartbeatExecutor.schedule(
            {
                if (closed) return@schedule
                safeHeartbeat()
                if (!closed) scheduleHeartbeat(nextHeartbeatDelay())
            },
            delayMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized
    private fun nextHeartbeatDelay(): Long = battleHeartbeatDelayMillis(
        eligible = eligible,
        discovering = cachedLayout == null || readMode == ReadMode.DISCOVERY,
        pollingIntervalMs = pollingIntervalProvider(),
    )

    @Synchronized
    private fun resetReader() {
        reader = null
        cachedLayout = null
        cachedSaveBlock1Address = null
        requestedSaveBlock1Address = null
        cachedGen3MainLayout = null
        gen3BattleDiscoveryRequested = false
        observedOverworldCallbacks = null
        observedOverworldSample = null
        awaitingOverworldAfterOutcome = false
        clearPendingOverworldObservation()
        closeTransport()
    }

    private fun closeTransport() {
        transport?.close()
        transport = null
    }

    override fun close() {
        closed = true
        heartbeatExecutor.shutdown()
        synchronized(this) {
            resetReader()
            eligible = false
            sessionIdentity = null
            sessionGeneration = 0
            tracker.reset()
        }
    }

    private enum class ReadMode { DISCOVERY, RUNTIME, CACHED }

    companion object {
        private const val GBA_SYSTEM_ID = "game_boy_advance"
        private const val GB_SYSTEM_ID = "game_boy"
        private const val GBC_SYSTEM_ID = "game_boy_color"
        private const val GEN1_WRAM_BASE = 0xc000L
        private const val GEN1_WRAM_BYTES = 0x2000
        private const val GEN1_BATTLE_TYPE_DELTA = 3
        private const val GEN2_ENEMY_FROM_BATTLE_FLAG_DELTA = 0x27
        private const val GEN2_BATTLE_MON_BYTES = 32
        private const val EWRAM_BASE = 0x02000000L
        private const val EWRAM_BYTES = 0x40000
        private const val IWRAM_BASE = 0x03000000L
        private const val IWRAM_BYTES = 0x8000
        private const val SAVE_LOCATION_GROUP_OFFSET = 4
        private const val COUNT_DELTA = 0x1C
        private const val POSITIONS_DELTA = 0x10
        private const val OUTCOME_DELTA = 0x2B2
        private const val MOVE_CURSOR_DELTA = 0x438
        private const val TARGET_CURSOR_DELTA = 0x43C
        private const val CACHED_WINDOW_BYTES = 0x45C
        private const val PRODUCTION_CHUNK_BYTES = 1024
        private const val REQUIRED_STABLE_OVERWORLD_OBSERVATIONS = 2
    }

    private fun supports(generation: Int, systemId: String?): Boolean = when (generation) {
        1 -> systemId.equals(GB_SYSTEM_ID, ignoreCase = true) || systemId.equals(GBC_SYSTEM_ID, ignoreCase = true)
        2 -> systemId.equals(GB_SYSTEM_ID, ignoreCase = true) || systemId.equals(GBC_SYSTEM_ID, ignoreCase = true)
        3 -> systemId.equals(GBA_SYSTEM_ID, ignoreCase = true)
        else -> false
    }
}
