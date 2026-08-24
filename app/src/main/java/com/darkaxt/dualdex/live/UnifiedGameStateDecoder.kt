package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.LiveGameSnapshot
import com.darkaxt.dualdex.battle.LiveBattleState
import com.darkaxt.dualdex.battle.LiveClockState
import com.darkaxt.dualdex.battle.LiveLocationState
import com.darkaxt.dualdex.battle.LiveUnavailableCode
import com.darkaxt.dualdex.battle.LiveUnavailableReason
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.battle.Gen3LiveMemoryReader
import com.darkaxt.dualdex.battle.Gen3LiveMemoryCodecs
import com.darkaxt.dualdex.battle.Gen3LivePointers
import com.darkaxt.dualdex.battle.Gen3LiveReadWindow
import com.darkaxt.dualdex.battle.Gen3RuntimeMemoryLayout
import com.darkaxt.dualdex.battle.RuntimeMapPosition
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.TrainerPlayTime
import com.darkaxt.dualdex.save.SaveObservationKind
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import java.util.Collections
import java.util.IdentityHashMap

class UnifiedGameStateDecoder(
    private val knowledgeLedgerSnapshot: () -> KnowledgeLedger = { KnowledgeLedger() },
) : TransientGameStateSource {
    private val listeners: MutableSet<TransientGameStateListener> =
        Collections.newSetFromMap(IdentityHashMap())
    private var context: TransientGameStateContext? = null
    private var live: LiveGameSnapshot? = null
    private var recovery: RecoveryProjection? = null
    private var recoveryStatus: SaveRamView? = null
    private var recoverySourceId: String? = null
    private var recoveryApplicationId = 0L
    private var recoveryResetKnowledge = false
    private var published: ResolvedGameSnapshot? = null

    override val current: ResolvedGameSnapshot?
        @Synchronized get() = published

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
        val hadPublishedState = published != null
        this.context = context
        live = null
        recovery = null
        recoveryStatus = null
        recoverySourceId = null
        recoveryApplicationId = 0L
        recoveryResetKnowledge = false
        published = null
        if (hadPublishedState) notifyListeners(null, ResolvedGameSection.entries.toSet())
    }

    @Synchronized
    fun acceptDecodedLive(snapshot: LiveGameSnapshot): ResolvedGameSnapshot? {
        val active = context ?: return null
        if (!snapshot.romIdentity.equals(active.romIdentity, ignoreCase = true)) return published
        if (snapshot.generation != active.generation) return published
        if (live != null && snapshot.sampleId < requireNotNull(live).sampleId) return published
        live = snapshot
        publishResolved()
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
    ): ResolvedGameSnapshot? {
        val active = context ?: return published
        if (active.generation !in 1..2) return published
        val unavailable = unavailable<Nothing>("field is not supported by the current live adapter")
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
    ): ResolvedGameSnapshot? {
        val active = context ?: return published
        if (active.generation != 3) return published
        val layout = active.gen3RuntimeMemoryLayout
        val parseContext = active.saveParseContext
        val memory = layout?.let { memoryLayout ->
            Gen3LiveMemoryReader.decode(
                regions = regions,
                layout = memoryLayout,
                saveContext = parseContext,
            )
        }
        val party = memory?.party
            ?: unavailable("live Party layout was unavailable")
        val player = parseContext?.let { saveContext ->
            Gen3LiveMemoryCodecs.decodePlayer(
                saveBlock1 = regions[Gen3LiveMemoryReader.SAVE_BLOCK1_ID],
                saveBlock2 = regions[Gen3LiveMemoryReader.SAVE_BLOCK2_ID],
                extendedSave = regions[Gen3LiveMemoryReader.EXTENDED_SAVE_ID],
                context = saveContext,
                liveParty = party,
            )
        } ?: unavailablePlayer()
        return acceptDecodedLive(
            LiveGameSnapshot(
                romIdentity = active.romIdentity,
                generation = 3,
                sampleId = sampleId,
                trainer = player.trainer,
                pokedex = player.pokedex,
                party = party,
                battle = LiveValue.Available(battle),
                location = LiveLocationState(
                    areaBaseId = areaBaseId?.let { LiveValue.Available(it) }
                        ?: memory?.location
                        ?: unavailable("live area layout was unavailable"),
                    position = mapPosition?.let { LiveValue.Available(it) }
                        ?: unavailable("live map position was unavailable"),
                ),
                clock = memory?.clock ?: unavailable("live game clock layout was unavailable"),
                bag = player.bag,
                eventFlags = memory?.eventFlags
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
        val observation = projection.observation
        val previous = recovery
        val samePlaythrough = previous != null &&
            previous.snapshot.romIdentity.equals(projection.snapshot.romIdentity, ignoreCase = true) &&
            previous.snapshot.saveIdentity.equals(projection.snapshot.saveIdentity, ignoreCase = true) &&
            observation?.source?.id?.let { sourceId -> recoverySourceId == sourceId } != false
        if (
            observation?.kind == SaveObservationKind.UNCHANGED &&
            samePlaythrough &&
            previous.snapshot == projection.snapshot &&
            previous.observation?.fingerprint == observation.fingerprint
        ) {
            recovery = projection
            recoveryStatus = projection.saveRam
            recoveryResetKnowledge = false
            publishResolved()
            return RecoveryApplication(accepted = true)
        }
        val frozenLedger = if (observation?.kind == SaveObservationKind.CHANGED && samePlaythrough) {
            knowledgeLedgerSnapshot()
        } else {
            null
        }
        recoveryResetKnowledge = when (observation?.kind) {
            SaveObservationKind.INITIAL, SaveObservationKind.SWITCHED -> true
            SaveObservationKind.CHANGED, SaveObservationKind.UNCHANGED -> !samePlaythrough
            null -> previous == null
        }
        recovery = projection
        recoveryStatus = projection.saveRam
        observation?.source?.id?.let { recoverySourceId = it }
        recoveryApplicationId++
        publishResolved()
        return RecoveryApplication(accepted = true, checkpointLedger = frozenLedger)
    }

    @Synchronized
    fun acceptRecoveryStatus(saveRam: SaveRamView): ResolvedGameSnapshot? {
        if (context == null) return published
        recoveryStatus = saveRam
        publishResolved()
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
        publishResolved()
        return published
    }

    @Synchronized
    fun suspendLive(): ResolvedGameSnapshot? {
        if (context == null) return published
        live = null
        publishResolved()
        return published
    }

    @Synchronized
    fun endSession() {
        val hadPublishedState = published != null
        context = null
        live = null
        recovery = null
        recoveryStatus = null
        recoverySourceId = null
        recoveryApplicationId = 0L
        recoveryResetKnowledge = false
        published = null
        if (hadPublishedState) notifyListeners(null, ResolvedGameSection.entries.toSet())
    }

    private fun publishResolved() {
        val previous = published
        val next = resolveSnapshot()
        if (next == null) {
            if (previous != null) {
                published = null
                notifyListeners(null, ResolvedGameSection.entries.toSet())
            }
            return
        }
        published = next
        val changedSections = previous.changedSections(next)
        if (changedSections.isNotEmpty()) notifyListeners(next, changedSections)
    }

    private fun resolveSnapshot(): ResolvedGameSnapshot? {
        val active = context ?: return null
        val live = live
        val saved = recovery?.snapshot
        if (live == null && saved == null) return null
        val recoveryTrainer = saved?.trainer
        val savedBag = saved?.bag.orEmpty().associateBy(BagPocketSnapshot::pocket)
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
                seenDexNumbers = resolve(live?.pokedex?.seenDexNumbers, saved?.seenDexNumbers),
                caughtDexNumbers = resolve(live?.pokedex?.caughtDexNumbers, saved?.caughtDexNumbers),
            ),
            party = resolve(live?.party, saved?.party),
            battle = resolve(live?.battle, null),
            location = ResolvedLocationState(
                areaBaseId = resolve(live?.location?.areaBaseId, saved?.currentArea?.baseId),
                position = resolve(live?.location?.position, null),
            ),
            clock = resolve(live?.clock, null),
            bag = BagPocket.entries.associateWith { pocket ->
                resolve(live?.bag?.get(pocket), savedBag[pocket])
            },
            eventFlags = resolve(live?.eventFlags, saved?.eventFlagIds),
            recovery = RecoveryState(
                applicationId = recoveryApplicationId.takeIf { recovery != null },
                saveIdentity = saved?.saveIdentity,
                snapshot = saved,
                saveRam = recoveryStatus,
                observationKind = recovery?.observation?.kind,
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
            if (party != other.party || bag != other.bag || eventFlags != other.eventFlags) {
                add(ResolvedGameSection.PARTY)
            }
            if (location != other.location || clock != other.clock) add(ResolvedGameSection.OVERWORLD)
            if (battle != other.battle) add(ResolvedGameSection.BATTLE)
        }
    }

    private fun notifyListeners(snapshot: ResolvedGameSnapshot?, changedSections: Set<ResolvedGameSection>) {
        val update = ResolvedGameStateUpdate(snapshot, changedSections)
        listeners.toList().forEach { it.onStateChanged(update) }
    }
}
