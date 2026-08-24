package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.battle.LiveGameSnapshot
import com.darkaxt.dualdex.battle.LiveValue
import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.TrainerPlayTime
import java.util.Collections
import java.util.IdentityHashMap

class UnifiedGameStateDecoder : TransientGameStateSource {
    private val listeners: MutableSet<TransientGameStateListener> =
        Collections.newSetFromMap(IdentityHashMap())
    private var context: TransientGameStateContext? = null
    private var live: LiveGameSnapshot? = null
    private var recovery: RecoveryProjection? = null
    private var published: ResolvedGameSnapshot? = null

    override val current: ResolvedGameSnapshot?
        @Synchronized get() = published

    @Synchronized
    override fun subscribe(listener: TransientGameStateListener): AutoCloseable {
        listeners += listener
        listener.onStateChanged(published)
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
        published = null
        if (hadPublishedState) notifyListeners(null)
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

    @Synchronized
    fun acceptRecovery(projection: RecoveryProjection): RecoveryApplication {
        val active = context ?: return RecoveryApplication(false)
        if (!projection.snapshot.romIdentity.equals(active.romIdentity, ignoreCase = true)) {
            return RecoveryApplication(false)
        }
        if (projection.snapshot.saveGeneration != active.generation) return RecoveryApplication(false)
        recovery = projection
        publishResolved()
        return RecoveryApplication(accepted = true)
    }

    @Synchronized
    fun clearRecovery(saveIdentity: String? = null): ResolvedGameSnapshot? {
        val existing = recovery ?: return published
        if (saveIdentity != null && !existing.snapshot.saveIdentity.equals(saveIdentity, ignoreCase = true)) {
            return published
        }
        recovery = null
        publishResolved()
        return published
    }

    @Synchronized
    fun endSession() {
        val hadPublishedState = published != null
        context = null
        live = null
        recovery = null
        published = null
        if (hadPublishedState) notifyListeners(null)
    }

    private fun publishResolved() {
        val next = resolveSnapshot() ?: return
        val previous = published
        published = next
        if (!previous.semanticallyEquals(next)) notifyListeners(next)
    }

    private fun resolveSnapshot(): ResolvedGameSnapshot? {
        val active = context ?: return null
        val live = live
        val saved = recovery?.snapshot
        val savedTrainer = saved?.trainer
        val savedBag = saved?.bag.orEmpty().associateBy(BagPocketSnapshot::pocket)
        return ResolvedGameSnapshot(
            romIdentity = active.romIdentity,
            generation = active.generation,
            sampleId = live?.sampleId,
            trainer = ResolvedTrainerState(
                identity = resolve(live?.trainer?.identity, savedTrainer?.let { com.darkaxt.dualdex.save.TrainerIdentity(it.name, it.gender) }),
                publicTrainerId = resolve(live?.trainer?.publicTrainerId, savedTrainer?.publicTrainerId),
                money = resolve(live?.trainer?.money, savedTrainer?.money),
                playTime = resolve(
                    live?.trainer?.playTime,
                    savedTrainer?.let { TrainerPlayTime(it.playTimeHours, it.playTimeMinutes) },
                ),
                badgeFlags = resolve(live?.trainer?.badgeFlags, savedTrainer?.badgeFlags),
                stars = resolve(live?.trainer?.stars, savedTrainer?.stars),
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
                saveIdentity = saved?.saveIdentity,
                checkpointLedger = recovery?.checkpointLedger,
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

    private fun ResolvedGameSnapshot?.semanticallyEquals(other: ResolvedGameSnapshot): Boolean {
        if (this == null) return false
        return copy(sampleId = null) == other.copy(sampleId = null)
    }

    private fun notifyListeners(snapshot: ResolvedGameSnapshot?) {
        listeners.toList().forEach { it.onStateChanged(snapshot) }
    }
}
