package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.LiveGameSnapshot
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.LiveLocationState
import com.darkaxt.dualdex.battle.LiveUnavailableCode
import com.darkaxt.dualdex.battle.LiveUnavailableReason
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.battle.Gen3LiveMemoryReader
import com.darkaxt.dualdex.battle.Gen3LiveMemoryCodecs
import com.darkaxt.dualdex.battle.Gen3LiveDecodedSection
import com.darkaxt.dualdex.battle.Gen3LivePlayerOverview
import com.darkaxt.dualdex.battle.Gen3LiveSectionFingerprints
import com.darkaxt.dualdex.battle.Gen3LiveTranslatedSectionCache
import com.darkaxt.dualdex.battle.Gen3LivePointers
import com.darkaxt.dualdex.battle.Gen3LiveReadWindow
import com.darkaxt.dualdex.battle.Gen3RuntimeMemoryLayout
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.TrainerPlayTime
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.LevelUpRulesetDetectionFingerprint
import com.darkaxt.dualdex.save.OwnedIndividual
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

class UnifiedGameStateDecoder(
    private val stateTraceSink: ResolvedStateTraceSink = ResolvedStateTraceSink { },
    private val knowledgeLedgerSnapshot: () -> KnowledgeLedger = { KnowledgeLedger() },
) : TransientGameStateSource {
    private val listeners: MutableSet<TransientGameStateListener> =
        Collections.newSetFromMap(IdentityHashMap())
    private var context: TransientGameStateContext? = null
    private var live: LiveGameSnapshot? = null
    private var liveEstablished = false
    private var recovery: RecoveryProjection? = null
    private var recoveryStatus: SaveRamView? = null
    private var recoverySourceId: String? = null
    private var recoveryApplicationId = 0L
    private var recoveryResetKnowledge = false
    private var battleKnowledge = ResolvedBattleKnowledge()
    private var published: ResolvedGameSnapshot? = null
    private var traceRevision = 0L
    private val translatedSectionCache = Gen3LiveTranslatedSectionCache()
    private val gen3PokedexStabilizer = Gen3LivePokedexStabilizer()
    private var contextEpoch = 0
    private val liveMemoryPackets = AtomicLong()
    private val liveMemoryBytes = AtomicLong()
    private val liveMemorySamples = AtomicLong()
    private val liveMemoryScratchBuffers = AtomicLong()
    private val liveMemoryRegionBuffers = AtomicLong()
    private val liveMemoryCompletionClones = AtomicLong()

    override val current: ResolvedGameSnapshot?
        @Synchronized get() = published

    override fun performanceCounters(): Map<String, Long> = translatedSectionCache.counters() + mapOf(
        "liveMemory.packets" to liveMemoryPackets.get(),
        "liveMemory.bytes" to liveMemoryBytes.get(),
        "liveMemory.samples" to liveMemorySamples.get(),
        "liveMemory.scratchBuffers" to liveMemoryScratchBuffers.get(),
        "liveMemory.regionBuffers" to liveMemoryRegionBuffers.get(),
        "liveMemory.completionRegionClones" to liveMemoryCompletionClones.get(),
    )

    fun recordLiveMemoryRead(
        packets: Long,
        bytes: Long,
        completedSamples: Long,
        scratchBuffers: Long,
        regionBuffers: Long,
        completionRegionClones: Long,
    ) {
        liveMemoryPackets.addAndGet(packets)
        liveMemoryBytes.addAndGet(bytes)
        liveMemorySamples.addAndGet(completedSamples)
        liveMemoryScratchBuffers.addAndGet(scratchBuffers)
        liveMemoryRegionBuffers.addAndGet(regionBuffers)
        liveMemoryCompletionClones.addAndGet(completionRegionClones)
    }

    @Synchronized
    override fun subscribe(listener: TransientGameStateListener): AutoCloseable {
        listeners += listener
        listener.onStateChanged(
            ResolvedGameStateUpdate(
                snapshot = published,
                changedSections = if (published == null) emptySet() else ResolvedGameSection.entries.toSet(),
            ),
        )
        return AutoCloseable {
            synchronized(this@UnifiedGameStateDecoder) {
                listeners.remove(listener)
            }
        }
    }

    @Synchronized
    fun beginSession(context: TransientGameStateContext) {
        if (this.context == context) return
        val previous = published
        contextEpoch++
        translatedSectionCache.clearEntries()
        gen3PokedexStabilizer.reset()
        this.context = context
        live = null
        liveEstablished = false
        recovery = null
        recoveryStatus = null
        recoverySourceId = null
        recoveryApplicationId = 0L
        recoveryResetKnowledge = false
        battleKnowledge = ResolvedBattleKnowledge()
        published = null
        if (previous != null) {
            publishCleared(previous, ResolvedStateTraceTrigger.SESSION_BEGIN)
        }
    }

    @Synchronized
    fun acceptDecodedLive(snapshot: LiveGameSnapshot): ResolvedGameSnapshot? {
        val active = context ?: return null
        if (!snapshot.romIdentity.equals(active.romIdentity, ignoreCase = true)) return published
        if (snapshot.generation != active.generation) return published
        if (live != null && snapshot.sampleId < requireNotNull(live).sampleId) return published
        live = snapshot
        liveEstablished = true
        publishResolved(ResolvedStateTraceTrigger.LIVE_SAMPLE)
        return published
    }

    fun gen3PointerReadPlan(layout: Gen3RuntimeMemoryLayout): List<Gen3LiveReadWindow> =
        Gen3LiveMemoryReader.pointerWindows(layout)

    fun decodeGen3Pointers(
        regions: Map<String, ByteArray>,
        layout: Gen3RuntimeMemoryLayout,
    ): Gen3LivePointers = Gen3LiveMemoryReader.decodePointers(regions, layout)

    fun gen3ValueReadPlan(
        layout: Gen3RuntimeMemoryLayout,
        pointers: Gen3LivePointers,
    ): List<Gen3LiveReadWindow> = Gen3LiveMemoryReader.dependentWindows(layout, pointers)

    fun gen3IndependentValueReadPlan(
        layout: Gen3RuntimeMemoryLayout,
        includeParty: Boolean,
    ): List<Gen3LiveReadWindow> = Gen3LiveMemoryReader.independentWindows(layout).filter { window ->
        includeParty || (window.id != Gen3LiveMemoryReader.PARTY_COUNT_ID && window.id != Gen3LiveMemoryReader.PARTY_ID)
    }

    fun isGen3IndependentValueRegion(id: String): Boolean =
        id == Gen3LiveMemoryReader.PARTY_COUNT_ID ||
            id == Gen3LiveMemoryReader.PARTY_ID ||
            id == Gen3LiveMemoryReader.CLOCK_ID

    fun gen3SaveBlock1(regions: Map<String, ByteArray>): ByteArray? =
        regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID]

    @Synchronized
    fun acceptExistingGenerationSample(
        sampleId: Long,
        battle: LiveBattleState,
        areaBaseId: Int?,
        mapPosition: RuntimeMapPosition?,
        clock: LiveClockState?,
        trackingUpdate: BattleTrackingUpdate? = null,
    ): ResolvedGameSnapshot? {
        val active = context ?: return published
        if (active.generation !in 1..2) return published
        val unavailable = unavailable<Nothing>("field is not supported by the current live adapter")
        trackingUpdate?.let { mergeBattleTracking(it, areaBaseId) }
        return acceptDecodedLive(
            LiveGameSnapshot(
                romIdentity = active.romIdentity,
                generation = active.generation,
                sampleId = sampleId,
                trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
                    unavailable,
                    unavailable,
                    unavailable,
                    unavailable,
                    unavailable,
                    unavailable,
                ),
                pokedex = com.darkaxt.dualdex.battle.LivePokedexState(unavailable, unavailable),
                party = unavailable,
                storedIndividuals = unavailable,
                battle = LiveValue.Available(battle),
                location = LiveLocationState(
                    areaBaseId = areaBaseId?.let { LiveValue.Available(it) } ?: unavailable,
                    position = mapPosition?.let { LiveValue.Available(it) } ?: unavailable,
                ),
                clock = clock?.let { LiveValue.Available(it) } ?: unavailable,
                bag = BagPocket.entries.associateWith { unavailable },
                eventFlags = unavailable,
            ),
        )
    }

    @Synchronized
    fun acceptGen3LiveSample(
        sampleId: Long,
        regions: Map<String, ByteArray>,
        battle: LiveBattleState,
        areaBaseId: Int?,
        mapPosition: RuntimeMapPosition?,
        trackingUpdate: BattleTrackingUpdate? = null,
    ): ResolvedGameSnapshot? {
        val active = context ?: return published
        if (active.generation != 3) return published
        val layout = active.gen3RuntimeMemoryLayout
        val parseContext = active.saveParseContext
        val cached = if (layout != null && parseContext != null) {
            decodeCachedGen3Sections(regions, layout, parseContext)
        } else {
            null
        }
        val memory = if (cached == null) layout?.let { memoryLayout ->
            Gen3LiveMemoryReader.decode(regions, memoryLayout, parseContext)
        } else null
        val party = cached?.party ?: memory?.party ?: unavailable("live Party layout was unavailable")
        val decodedPlayer = cached?.let { sections ->
            com.darkaxt.dualdex.battle.Gen3LivePlayerState(
                trainer = sections.player.trainer,
                pokedex = sections.player.pokedex,
                bag = sections.progression.bag,
            )
        } ?: parseContext?.let { saveContext ->
            Gen3LiveMemoryCodecs.decodePlayer(
                regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID],
                regions[Gen3LiveMemoryReader.SAVE_BLOCK2_ID],
                regions[Gen3LiveMemoryReader.EXTENDED_SAVE_ID],
                saveContext,
                party,
            )
        } ?: unavailablePlayer()
        val player = decodedPlayer.copy(
            pokedex = gen3PokedexStabilizer.accept(decodedPlayer.pokedex, party),
        )
        trackingUpdate?.let { mergeBattleTracking(it, areaBaseId) }
        return acceptDecodedLive(
            LiveGameSnapshot(
                romIdentity = active.romIdentity,
                generation = 3,
                sampleId = sampleId,
                trainer = player.trainer,
                pokedex = player.pokedex,
                party = party,
                storedIndividuals = unavailable("live owned storage layout was unavailable"),
                battle = LiveValue.Available(battle),
                location = LiveLocationState(
                    areaBaseId = areaBaseId?.let { LiveValue.Available(it) }
                        ?: cached?.overworld?.location
                        ?: memory?.location
                        ?: unavailable("live area layout was unavailable"),
                    position = mapPosition?.let { LiveValue.Available(it) }
                        ?: unavailable("live map position was unavailable"),
                ),
                clock = cached?.overworld?.clock
                    ?: memory?.clock
                    ?: unavailable("live game clock layout was unavailable"),
                bag = player.bag,
                eventFlags = cached?.progression?.eventFlags
                    ?: memory?.eventFlags
                    ?: unavailable("live event-flag layout was unavailable"),
            ),
        )
    }

    @Synchronized
    fun acceptRecovery(projection: RecoveryProjection): RecoveryApplication {
        val active = context ?: return RecoveryApplication(false)
        if (!projection.snapshot.romIdentity.equals(active.romIdentity, ignoreCase = true)) {
            return RecoveryApplication(false)
        }
        if (projection.snapshot.saveGeneration != active.generation) return RecoveryApplication(false)
        val safeProjection = projection.copy(
            checkpointLedger = projection.checkpointLedger?.transientCheckpointOnly(),
        )
        val observation = safeProjection.observation
        val previous = recovery
        val samePlaythrough = previous != null &&
            previous.snapshot.romIdentity.equals(safeProjection.snapshot.romIdentity, ignoreCase = true) &&
            previous.snapshot.saveIdentity.equals(safeProjection.snapshot.saveIdentity, ignoreCase = true) &&
            observation?.source?.id?.let { sourceId -> recoverySourceId == sourceId } != false
        if (
            observation?.kind == SaveObservationKind.UNCHANGED &&
            samePlaythrough &&
            previous.snapshot == safeProjection.snapshot &&
            previous.observation?.fingerprint == observation.fingerprint
        ) {
            recovery = safeProjection
            recoveryStatus = safeProjection.saveRam
            recoveryResetKnowledge = false
            publishResolved(ResolvedStateTraceTrigger.RECOVERY_APPLIED)
            return RecoveryApplication(accepted = true)
        }
        val frozenLedger = if (observation?.kind == SaveObservationKind.CHANGED && samePlaythrough) {
            knowledgeLedgerSnapshot().transientCheckpointOnly()
        } else {
            null
        }
        recoveryResetKnowledge = when (observation?.kind) {
            SaveObservationKind.INITIAL, SaveObservationKind.SWITCHED -> true
            SaveObservationKind.CHANGED, SaveObservationKind.UNCHANGED -> !samePlaythrough
            null -> previous == null
        }
        if (recoveryResetKnowledge) {
            battleKnowledge = safeProjection.checkpointLedger?.toResolvedBattleKnowledge()
                ?: ResolvedBattleKnowledge()
        }
        recovery = safeProjection
        recoveryStatus = safeProjection.saveRam
        observation?.source?.id?.let { recoverySourceId = it }
        recoveryApplicationId++
        publishResolved(ResolvedStateTraceTrigger.RECOVERY_APPLIED)
        return RecoveryApplication(accepted = true, checkpointLedger = frozenLedger)
    }

    @Synchronized
    fun acceptRecoveryStatus(saveRam: SaveRamView): ResolvedGameSnapshot? {
        if (context == null) return published
        recoveryStatus = saveRam
        publishResolved(ResolvedStateTraceTrigger.RECOVERY_STATUS)
        return published
    }

    @Synchronized
    fun clearRecovery(saveIdentity: String? = null): ResolvedGameSnapshot? {
        val existing = recovery ?: return published
        if (saveIdentity != null && !existing.snapshot.saveIdentity.equals(saveIdentity, ignoreCase = true)) {
            return published
        }
        recovery = null
        recoverySourceId = null
        recoveryResetKnowledge = false
        publishResolved(ResolvedStateTraceTrigger.RECOVERY_CLEARED)
        return published
    }

    @Synchronized
    fun suspendLive(): ResolvedGameSnapshot? {
        if (context == null) return published
        translatedSectionCache.clearEntries()
        live = null
        publishResolved(ResolvedStateTraceTrigger.LIVE_SUSPENDED)
        return published
    }

    @Synchronized
    fun acceptBattleTracking(update: BattleTrackingUpdate): ResolvedGameSnapshot? {
        val areaBaseId = (live?.location?.areaBaseId as? LiveValue.Available<Int>)?.value
        mergeBattleTracking(update, areaBaseId)
        publishResolved(ResolvedStateTraceTrigger.BATTLE_TRACKING)
        return published
    }

    @Synchronized
    fun endSession() {
        val previous = published
        translatedSectionCache.clearEntries()
        gen3PokedexStabilizer.reset()
        context = null
        live = null
        liveEstablished = false
        recovery = null
        recoveryStatus = null
        recoverySourceId = null
        recoveryApplicationId = 0L
        recoveryResetKnowledge = false
        battleKnowledge = ResolvedBattleKnowledge()
        published = null
        if (previous != null) {
            publishCleared(previous, ResolvedStateTraceTrigger.SESSION_END)
        }
    }

    private fun publishResolved(trigger: ResolvedStateTraceTrigger) {
        val previous = published
        val next = resolveSnapshot()
        if (next == null) {
            if (previous != null) {
                published = null
                publishCleared(previous, trigger)
            }
            return
        }
        published = next
        val changedSections = previous.changedSections(next)
        if (changedSections.isNotEmpty()) {
            trace(previous, next, changedSections, trigger)
            notifyListeners(next, changedSections)
        }
    }

    private fun publishCleared(
        previous: ResolvedGameSnapshot,
        trigger: ResolvedStateTraceTrigger,
    ) {
        val changedSections = ResolvedGameSection.entries.toSet()
        trace(previous, null, changedSections, trigger)
        notifyListeners(null, changedSections)
    }

    private fun trace(
        previous: ResolvedGameSnapshot?,
        next: ResolvedGameSnapshot?,
        changedSections: Set<ResolvedGameSection>,
        trigger: ResolvedStateTraceTrigger,
    ) {
        val event = resolvedStateTraceEvent(
            revision = ++traceRevision,
            trigger = trigger,
            previous = previous,
            next = next,
            changedSections = changedSections,
        )
        runCatching { stateTraceSink.append(event) }
    }

    private fun decodeCachedGen3Sections(
        regions: Map<String, ByteArray>,
        layout: Gen3RuntimeMemoryLayout,
        parseContext: com.darkaxt.dualdex.save.SaveParseContext,
    ): CachedGen3Sections {
        val fingerprints = Gen3LiveSectionFingerprints.compute(regions, layout, parseContext)
        val party = translatedSectionCache.resolve(
            Gen3LiveDecodedSection.PARTY,
            contextEpoch,
            fingerprints.party,
        ) {
            Gen3LiveMemoryReader.decodeParty(
                regions[Gen3LiveMemoryReader.PARTY_COUNT_ID],
                regions[Gen3LiveMemoryReader.PARTY_ID],
                layout,
                parseContext,
            )
        }
        val player = translatedSectionCache.resolve(
            Gen3LiveDecodedSection.PLAYER,
            contextEpoch,
            Gen3LiveSectionFingerprints.combine(fingerprints.player, fingerprints.party),
        ) {
            Gen3LiveMemoryCodecs.decodePlayerOverview(
                regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID],
                regions[Gen3LiveMemoryReader.SAVE_BLOCK2_ID],
                parseContext,
                party,
            )
        }
        val overworld = translatedSectionCache.resolve(
            Gen3LiveDecodedSection.OVERWORLD,
            contextEpoch,
            fingerprints.overworld,
        ) {
            CachedGen3Overworld(
                location = Gen3LiveMemoryReader.decodeLocation(
                    regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID],
                    layout,
                ),
                clock = Gen3LiveMemoryReader.decodeClock(regions[Gen3LiveMemoryReader.CLOCK_ID]),
            )
        }
        val progression = translatedSectionCache.resolve(
            Gen3LiveDecodedSection.PROGRESSION,
            contextEpoch,
            fingerprints.progression,
        ) {
            CachedGen3Progression(
                bag = Gen3LiveMemoryCodecs.decodeBag(
                    regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID],
                    regions[Gen3LiveMemoryReader.SAVE_BLOCK2_ID],
                    regions[Gen3LiveMemoryReader.EXTENDED_SAVE_ID],
                    parseContext,
                ),
                eventFlags = Gen3LiveMemoryReader.decodeEventFlags(
                    regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID],
                    parseContext.gen3SaveRuntimeAbi,
                ),
            )
        }
        return CachedGen3Sections(player, party, overworld, progression)
    }

    private fun resolveSnapshot(): ResolvedGameSnapshot? {
        val active = context ?: return null
        val live = live
        val saved = recovery?.snapshot
        if (live == null && saved == null) return null
        val playerRecovery = saved.takeIf { liveEstablished }
        val recoveryTrainer = playerRecovery?.trainer
        val savedBag = playerRecovery?.bag.orEmpty().associateBy(BagPocketSnapshot::pocket)
        val resolvedParty = resolve(live?.party, playerRecovery?.party)
        val resolvedStored = resolve(live?.storedIndividuals, playerRecovery?.storedIndividuals)
        val boxes = resolvedStorageBoxes(
            generation = active.generation,
            stored = resolvedStored,
            party = resolvedParty.value.orEmpty(),
        )
        return ResolvedGameSnapshot(
            romIdentity = active.romIdentity,
            generation = active.generation,
            sampleId = live?.sampleId,
            trainer = ResolvedTrainerState(
                identity = resolve(live?.trainer?.identity, recoveryTrainer?.let { com.darkaxt.dualdex.save.TrainerIdentity(it.name, it.gender) }),
                publicTrainerId = resolve(live?.trainer?.publicTrainerId, recoveryTrainer?.publicTrainerId),
                money = resolve(live?.trainer?.money, recoveryTrainer?.money),
                playTime = resolve(
                    live?.trainer?.playTime,
                    recoveryTrainer?.let { TrainerPlayTime(it.playTimeHours, it.playTimeMinutes) },
                ),
                badgeFlags = resolve(live?.trainer?.badgeFlags, recoveryTrainer?.badgeFlags),
                stars = resolve(live?.trainer?.stars, recoveryTrainer?.stars),
            ),
            pokedex = ResolvedPokedexState(
                seenSpeciesIds = resolvePokedexSpecies(
                    live?.pokedex?.seenDexNumbers,
                    playerRecovery?.seenDexNumbers,
                    active.saveParseContext,
                ),
                caughtSpeciesIds = resolvePokedexSpecies(
                    live?.pokedex?.caughtDexNumbers,
                    playerRecovery?.caughtDexNumbers,
                    active.saveParseContext,
                ),
            ),
            ownedStorage = ResolvedOwnedStorageState(
                party = resolvedParty,
                boxes = boxes,
            ),
            battle = resolve(live?.battle, null),
            battleKnowledge = battleKnowledge,
            location = ResolvedLocationState(
                areaBaseId = resolve(live?.location?.areaBaseId, saved?.currentArea?.baseId),
                position = resolve(live?.location?.position, null),
            ),
            clock = resolve(live?.clock, null),
            bag = BagPocket.entries.associateWith { pocket ->
                resolve(live?.bag?.get(pocket), savedBag[pocket])
            },
            eventFlags = resolve(live?.eventFlags, saved?.eventFlagIds),
            levelUpRulesetId = resolve(null, validatedLevelUpRulesetId(saved, active.saveParseContext)),
            recovery = RecoveryState(
                applicationId = recoveryApplicationId.takeIf { recovery != null },
                saveIdentity = saved?.saveIdentity,
                saveRam = recoveryStatus,
                observationKind = recovery?.observation?.kind,
                saveFileFingerprint = recovery?.observation?.fingerprint?.sha256,
                checkpointLedger = recovery?.checkpointLedger,
                resetKnowledge = recoveryResetKnowledge,
            ),
        )
    }

    private fun <T> resolve(live: LiveValue<T>?, recovery: T?): ResolvedValue<T> = when (live) {
        is LiveValue.Available -> ResolvedValue.live(live.value)
        is LiveValue.Unavailable, null -> if (recovery != null) {
            ResolvedValue.recovery(recovery)
        } else {
            ResolvedValue.unavailable()
        }
    }

    private fun resolvedStorageBoxes(
        generation: Int,
        stored: ResolvedValue<List<OwnedIndividual>>,
        party: List<OwnedIndividual>,
    ): ResolvedValue<List<ResolvedStorageBox>> {
        val individuals = stored.value ?: return ResolvedValue.unavailable()
        val partyIdentities = party.mapNotNull(OwnedIndividual::individualIdentity).toSet()
        val partyFallbacks = party.filter { it.individualIdentity == null }
            .groupingBy(OwnedIndividual::validatedRecordDigest)
            .eachCount()
            .toMutableMap()
        val deduplicated = individuals.filter { individual ->
            val identity = individual.individualIdentity
            if (identity != null) return@filter identity !in partyIdentities
            val digest = individual.validatedRecordDigest()
            val remaining = partyFallbacks[digest] ?: 0
            if (remaining <= 0) true else {
                partyFallbacks[digest] = remaining - 1
                false
            }
        }
        val boxes = deduplicated.mapNotNull { individual ->
            storageCoordinates(individual.stableLocation, generation)?.let { (box, slot) ->
                Triple(box, slot, individual)
            }
        }.groupBy { it.first }
            .toSortedMap()
            .map { (boxIndex, entries) ->
                ResolvedStorageBox(
                    index = boxIndex,
                    slots = entries.sortedBy { it.second }.map { (_, slot, individual) ->
                        ResolvedStorageSlot(slot, individual)
                    },
                )
            }
        return when (stored.source) {
            ResolvedValueSource.LIVE -> ResolvedValue.live(boxes)
            ResolvedValueSource.RECOVERY -> ResolvedValue.recovery(boxes)
            ResolvedValueSource.UNAVAILABLE -> ResolvedValue.unavailable()
        }
    }

    private fun storageCoordinates(location: String, generation: Int): Pair<Int, Int>? {
        BOX_SLOT_LOCATION.matchEntire(location)?.let { match ->
            val box = match.groupValues[1].toIntOrNull()?.minus(1) ?: return null
            val slot = match.groupValues[2].toIntOrNull() ?: return null
            return (box to slot).takeIf { box >= 0 && slot >= 0 }
        }
        FLAT_BOX_LOCATION.matchEntire(location)?.let { match ->
            val flat = match.groupValues[1].toIntOrNull() ?: return null
            val capacity = if (generation == 3) GEN3_BOX_CAPACITY else GB_BOX_CAPACITY
            return (flat / capacity) to (flat % capacity)
        }
        return null
    }

    private fun resolvePokedexSpecies(
        live: LiveValue<Set<Int>>?,
        recovery: Set<Int>?,
        parseContext: com.darkaxt.dualdex.save.SaveParseContext?,
    ): ResolvedValue<Set<Int>> {
        val resolvedFlags = resolve(live, recovery)
        val flagNumbers = resolvedFlags.value ?: return ResolvedValue.unavailable()
        if (flagNumbers.isEmpty()) return ResolvedValue(emptySet(), resolvedFlags.source)
        val speciesByFlag = parseContext?.speciesById.orEmpty().values
            .mapNotNull { species ->
                species.pokedexFlagNumber?.takeIf { it > 0 }?.let { flag -> flag to species }
            }
            .groupBy({ it.first }, { it.second })
        if (flagNumbers.any { it !in speciesByFlag }) return ResolvedValue.unavailable()
        val speciesIds = flagNumbers.mapTo(linkedSetOf()) { flag ->
            speciesByFlag.getValue(flag).minWith(
                compareBy<com.darkaxt.dualdex.save.SaveSpeciesContext> { it.formId != 0 }
                    .thenBy { it.speciesId },
            ).speciesId
        }
        return ResolvedValue(speciesIds, resolvedFlags.source)
    }

    private fun validatedLevelUpRulesetId(
        saved: com.darkaxt.dualdex.save.SaveSnapshot?,
        parseContext: com.darkaxt.dualdex.save.SaveParseContext?,
    ): String? {
        val snapshot = saved ?: return null
        val id = snapshot.detectedLevelUpRulesetId ?: return null
        val selectors = parseContext?.levelUpRulesetSelectors.orEmpty()
        if (selectors.isEmpty() || selectors.none { it.rulesetId == id }) return null
        val expected = LevelUpRulesetDetectionFingerprint.create(selectors, id)
        return id.takeIf {
            snapshot.levelUpRulesetDetectionResolved &&
                snapshot.levelUpRulesetDetectionFingerprint == expected
        }
    }

    private fun KnowledgeLedger.transientCheckpointOnly(): KnowledgeLedger = copy(
        seenSpecies = emptySet(),
        caughtSpecies = emptySet(),
        owned = emptyList(),
        teamSpecies = emptySet(),
        trainerCardUnlocked = false,
        currentAreaBaseId = null,
    )

    private fun mergeBattleTracking(update: BattleTrackingUpdate, areaBaseId: Int?) {
        val observed = battleKnowledge.observedMoves.mapValuesTo(linkedMapOf()) { (_, moves) ->
            moves.toMutableMap()
        }
        update.observations.forEach { (speciesId, increments) ->
            val frequencies = observed.getOrPut(speciesId, ::linkedMapOf)
            increments.forEach { (moveId, count) ->
                if (speciesId > 0 && moveId > 0 && count > 0) frequencies.merge(moveId, count, Int::plus)
            }
        }
        val encountered = update.sample?.opponents.orEmpty().mapTo(linkedSetOf()) { it.speciesId }
        val seenByArea = battleKnowledge.seenSpeciesByArea.mapValuesTo(linkedMapOf()) { (_, species) ->
            species.toSet()
        }
        if (areaBaseId != null && encountered.isNotEmpty()) {
            seenByArea[areaBaseId] = seenByArea[areaBaseId].orEmpty() + encountered
        }
        battleKnowledge = ResolvedBattleKnowledge(
            observedMoves = observed.mapValues { (_, moves) -> moves.toMap() },
            seenSpeciesIds = battleKnowledge.seenSpeciesIds + encountered,
            seenSpeciesByArea = seenByArea,
            recoveredMatchups = battleKnowledge.recoveredMatchups,
            discoveredMatchups = battleKnowledge.discoveredMatchups + update.discoveredMatchups,
            latestUpdate = update,
        )
    }

    private fun KnowledgeLedger.toResolvedBattleKnowledge() = ResolvedBattleKnowledge(
        observedMoves = observedMoves.mapValues { (_, observations) ->
            observations.associate { it.moveId to it.frequency }
        },
        seenSpeciesIds = seenSpecies,
        seenSpeciesByArea = seenSpeciesByArea,
        recoveredMatchups = discoveredMatchups,
        discoveredMatchups = emptySet(),
    )

    private fun <T> unavailable(detail: String): LiveValue<T> = LiveValue.Unavailable(
        LiveUnavailableReason(LiveUnavailableCode.MISSING_REGION, detail),
    )

    private fun unavailablePlayer() = com.darkaxt.dualdex.battle.Gen3LivePlayerState(
        trainer = com.darkaxt.dualdex.battle.LiveTrainerState(
            unavailable("live Trainer layout was unavailable"),
            unavailable("live Trainer ID layout was unavailable"),
            unavailable("live money layout was unavailable"),
            unavailable("live play-time layout was unavailable"),
            unavailable("live badge layout was unavailable"),
            unavailable("live Trainer stars layout was unavailable"),
        ),
        pokedex = com.darkaxt.dualdex.battle.LivePokedexState(
            unavailable("live Pokédex layout was unavailable"),
            unavailable("live Pokédex layout was unavailable"),
        ),
        bag = BagPocket.entries.associateWith { unavailable("live Bag layout was unavailable") },
    )

    private fun ResolvedGameSnapshot?.changedSections(other: ResolvedGameSnapshot): Set<ResolvedGameSection> {
        if (this == null || romIdentity != other.romIdentity || generation != other.generation) {
            return ResolvedGameSection.entries.toSet()
        }
        return buildSet {
            if (recovery != other.recovery) add(ResolvedGameSection.RECOVERY)
            if (trainer != other.trainer || pokedex != other.pokedex) add(ResolvedGameSection.PLAYER)
            if (
                party != other.party ||
                storedIndividuals != other.storedIndividuals ||
                bag != other.bag ||
                eventFlags != other.eventFlags ||
                levelUpRulesetId != other.levelUpRulesetId
            ) {
                add(ResolvedGameSection.PARTY)
            }
            if (location != other.location || clock != other.clock) add(ResolvedGameSection.OVERWORLD)
            if (battle != other.battle || battleKnowledge != other.battleKnowledge) add(ResolvedGameSection.BATTLE)
        }
    }

    private fun notifyListeners(snapshot: ResolvedGameSnapshot?, changedSections: Set<ResolvedGameSection>) {
        val update = ResolvedGameStateUpdate(snapshot, changedSections)
        listeners.toList().forEach { it.onStateChanged(update) }
    }

    private data class CachedGen3Sections(
        val player: Gen3LivePlayerOverview,
        val party: LiveValue<List<com.darkaxt.dualdex.save.OwnedIndividual>>,
        val overworld: CachedGen3Overworld,
        val progression: CachedGen3Progression,
    )

    private data class CachedGen3Overworld(
        val location: LiveValue<Int>,
        val clock: LiveValue<LiveClockState>,
    )

    private data class CachedGen3Progression(
        val bag: Map<BagPocket, LiveValue<BagPocketSnapshot>>,
        val eventFlags: LiveValue<Set<Int>>,
    )

    private companion object {
        val BOX_SLOT_LOCATION = Regex("box-(\\d+)-(\\d+)")
        val FLAT_BOX_LOCATION = Regex("box-(\\d+)")
        const val GB_BOX_CAPACITY = 20
        const val GEN3_BOX_CAPACITY = 30
    }
}
