# Unified Game-State Decoder Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every feature-specific transient-data path with one `UnifiedGameStateDecoder` instance and one `TransientGameStateSource` interface that resolve live RAM first and validated save/checkpoint recovery second.

**Architecture:** `BattleMemoryCoordinator` remains the sole read-only RetroArch transport owner and `SavePollingMonitor` remains the save discovery/checksum helper. Both feed one application-owned `UnifiedGameStateDecoder`. That class uses stateless generation helpers, owns live-versus-recovery authority, and publishes one immutable `ResolvedGameSnapshot`; `ProductionCompanionRuntime` subscribes once and projects that snapshot into every consumer.

**Tech Stack:** Kotlin/JVM 17, Android/Kotlin, existing `retroarch-session`, `battle-memory`, `save-core`, `companion-core`, JUnit 4, Gradle, Vitest/TypeScript.

**Specification:** `docs/superpowers/specs/2026-08-24-unified-live-memory-decoder-design.md`

---

## Non-negotiable migration rules

- Live RAM is authoritative whenever the corresponding field validates.
- SaveRAM/checkpoints fill only unavailable live fields and never enable live decoding.
- `TransientGameStateSource` is the only transient-game-state interface consumed by DualDex.
- One unavailable field cannot invalidate an unrelated field or section.
- No second polling thread, transport, save monitor, or merge policy is introduced.
- Existing battle privacy, Organic discovery, fog of war, map tracking, loading readiness, and recovery behavior remain intact at every stage.
- Ordinary UI receives no diagnostic reason, provenance label, address, offset, or parser terminology.
- Each stage ends with a plan-to-spec audit. Fix missing requirements before the next stage or register them as blockers; nothing is silently dropped.

## Planned file structure

| File | Responsibility |
| --- | --- |
| `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModels.kt` | Pure live-only values and immutable sample model |
| `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecs.kt` | Stateless Gen III helpers used only by the singleton |
| `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PokedexCodec.kt` | Shared Pokédex flag decoder for saved and live SaveBlock2 |
| `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3TrainerFieldCodec.kt` | Independent Trainer field decoders |
| `app/src/main/java/com/darkaxt/dualdex/live/TransientGameStateSource.kt` | The one read-only interface exposed to DualDex |
| `app/src/main/java/com/darkaxt/dualdex/live/RecoveryProjection.kt` | Validated save/checkpoint recovery input |
| `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt` | Single state owner, decoder façade, recovery merger, and publisher |

Helper files contain no stateful public decoder classes. `UnifiedGameStateDecoder` is the only long-lived decoding/authority instance.

## Stage 1 — Unified contract, singleton, and compatibility bridge

### Task 1: Define live-only values and immutable snapshots

**Files:**
- Create: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModels.kt`
- Create: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModelsTest.kt`
- Modify: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/LivePlayerModels.kt`

- [ ] **Step 1: Write model invariant RED tests**

```kotlin
class LiveMemoryModelsTest {
    @Test fun availableEmptyPartyIsNotUnavailable() {
        val party: LiveValue<List<OwnedIndividual>> = LiveValue.Available(emptyList())
        assertEquals(emptyList<OwnedIndividual>(), party.valueOrNull())
    }

    @Test fun trainerIdDoesNotDependOnPokedexCounts() {
        val trainer = liveTrainer(
            publicTrainerId = LiveValue.Available(12345),
            money = LiveValue.Available(3_000L),
        )
        val pokedex = LivePokedexState(unavailable(), unavailable())

        assertEquals(12345, trainer.publicTrainerId.valueOrNull())
        assertNull(pokedex.seenDexNumbers.valueOrNull())
    }
}
```

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :battle-memory:test --tests "*LiveMemoryModelsTest" --no-daemon --console=plain
```

Expected: compilation fails because the unified live models do not exist.

- [ ] **Step 3: Implement the model boundary**

Define these exact public shapes:

Place `TrainerPlayTime` in `save-core/src/main/kotlin/com/darkaxt/dualdex/save/LivePlayerModels.kt`; place the live availability and snapshot types in `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModels.kt` so live and recovery paths share the same validated time value without reversing module dependencies.

```kotlin
enum class LiveUnavailableCode {
    UNSUPPORTED_LAYOUT, MISSING_REGION, INVALID_POINTER, INVALID_VALUE, AMBIGUOUS_LAYOUT
}

data class LiveUnavailableReason(val code: LiveUnavailableCode, val detail: String)

sealed interface LiveValue<out T> {
    data class Available<T>(val value: T) : LiveValue<T>
    data class Unavailable(val reason: LiveUnavailableReason) : LiveValue<Nothing>
}

fun <T> LiveValue<T>.valueOrNull(): T? = (this as? LiveValue.Available<T>)?.value

data class TrainerPlayTime(val hours: Int, val minutes: Int)

data class LiveTrainerState(
    val identity: LiveValue<TrainerIdentity>,
    val publicTrainerId: LiveValue<Int>,
    val money: LiveValue<Long>,
    val playTime: LiveValue<TrainerPlayTime>,
    val badgeFlags: LiveValue<Int>,
    val stars: LiveValue<Int>,
)

data class LivePokedexState(
    val seenDexNumbers: LiveValue<Set<Int>>,
    val caughtDexNumbers: LiveValue<Set<Int>>,
)

data class LiveLocationState(
    val areaBaseId: LiveValue<Int>,
    val position: LiveValue<RuntimeMapPosition>,
)

data class LiveClockState(val hours: Int, val minutes: Int, val seconds: Int)

data class LiveBattleState(
    val active: Boolean,
    val sample: BattleMemorySample?,
    val encounterKind: BattleEncounterKind,
)

data class LiveGameSnapshot(
    val romIdentity: String,
    val generation: Int,
    val sampleId: Long,
    val trainer: LiveTrainerState,
    val pokedex: LivePokedexState,
    val party: LiveValue<List<OwnedIndividual>>,
    val battle: LiveValue<LiveBattleState>,
    val location: LiveLocationState,
    val clock: LiveValue<LiveClockState>,
    val bag: Map<BagPocket, LiveValue<BagPocketSnapshot>>,
    val eventFlags: LiveValue<Set<Int>>,
)
```

Add constructor invariants for ROM identity, generation, play time, clock, ID, money, and badge masks. Available empty collections remain valid.

- [ ] **Step 4: Run GREEN and commit**

```powershell
.\gradlew.bat :battle-memory:test --tests "*LiveMemoryModelsTest" --no-daemon --console=plain
git add save-core/src/main/kotlin/com/darkaxt/dualdex/save/LivePlayerModels.kt battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModels.kt battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModelsTest.kt
git commit -m "feat: define unified live memory snapshots"
```

### Task 2: Implement the one consumer interface and state owner

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/live/TransientGameStateSource.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/live/RecoveryProjection.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt`

- [ ] **Step 1: Write listener, session, and recovery RED tests**

Require immediate current delivery, semantic-change-only publication, listener removal, ROM mismatch rejection, session clearing, live-over-recovery selection, and field-level fallback:

```kotlin
@Test fun liveMoneyAndRecoveryDexResolveIndependently() {
    val decoder = UnifiedGameStateDecoder()
    decoder.beginSession(context(ROM))
    decoder.acceptRecovery(recovery(ROM, money = 500L, seen = setOf(1, 2), caught = setOf(1)))
    decoder.acceptDecodedLive(
        liveSnapshot(
            rom = ROM,
            money = LiveValue.Available(900L),
            seen = unavailable(),
            caught = unavailable(),
        ),
    )

    assertEquals(900L, decoder.current!!.trainer.money.value)
    assertEquals(ResolvedValueSource.LIVE, decoder.current!!.trainer.money.source)
    assertEquals(setOf(1, 2), decoder.current!!.pokedex.seenDexNumbers.value)
    assertEquals(ResolvedValueSource.RECOVERY, decoder.current!!.pokedex.seenDexNumbers.source)
}

@Test fun clearingRecoveryCannotEraseLiveState() {
    val decoder = decoderWithLiveMoney(900L)
    decoder.clearRecovery()
    assertEquals(900L, decoder.current!!.trainer.money.value)
}
```

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UnifiedGameStateDecoderTest" --no-daemon --console=plain
```

Expected: the interface and singleton do not exist.

- [ ] **Step 3: Define the sole consumer interface and resolved values**

```kotlin
fun interface TransientGameStateListener {
    fun onStateChanged(snapshot: ResolvedGameSnapshot?)
}

interface TransientGameStateSource {
    val current: ResolvedGameSnapshot?
    fun subscribe(listener: TransientGameStateListener): AutoCloseable
}

enum class ResolvedValueSource { LIVE, RECOVERY, UNAVAILABLE }

data class ResolvedValue<T>(val value: T?, val source: ResolvedValueSource) {
    init {
        require((source == ResolvedValueSource.UNAVAILABLE) == (value == null))
    }
}

data class RecoveryApplication(
    val accepted: Boolean,
    val checkpointLedger: KnowledgeLedger? = null,
)
```

Define `ResolvedTrainerState`, `ResolvedPokedexState`, `ResolvedLocationState`, and `ResolvedGameSnapshot` with the same domains as the live snapshot plus one `RecoveryState`. Define `RecoveryProjection` with validated `SaveSnapshot`, `SaveRamView`, optional checkpoint ledger, and `SaveObservation`; never include raw save bytes. `RecoveryState` carries the SaveRAM status and exact checkpoint application event required by the runtime, while technical identity and provenance remain Debug-only.

Replace the battle-specific context as consumers migrate:

```kotlin
data class TransientGameStateContext(
    val romIdentity: String,
    val generation: Int,
    val catalog: BattleCatalogView,
    val gen2TimeOfDayWramOffset: Int? = null,
    val gen3RuntimeMemoryLayout: Gen3RuntimeMemoryLayout? = null,
    val liveAreaMemoryLayout: LiveAreaMemoryLayout? = null,
    val saveParseContext: SaveParseContext? = null,
)
```

This context deliberately has no `savedTrainer`, `SaveSnapshot`, checkpoint, filename, or recovery selection field.

- [ ] **Step 4: Implement one synchronized owner**

`UnifiedGameStateDecoder` stores exactly one session context, one live snapshot, one matching recovery projection, one published resolved snapshot, and one listener set. It exposes integration commands but implements only one consumer interface:

```kotlin
class UnifiedGameStateDecoder : TransientGameStateSource {
    private val listeners = linkedSetOf<TransientGameStateListener>()
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
        return AutoCloseable { synchronized(this) { listeners -= listener } }
    }
}
```

Use one generic `resolve(live, recovery)` helper for every field. Live available wins, then matching recovery, then unavailable. A ROM switch clears both authorities before notifying. Publish only semantic changes; `sampleId` churn alone does not notify.

- [ ] **Step 5: Run GREEN, audit Stage 1, and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UnifiedGameStateDecoderTest" --no-daemon --console=plain
git add app/src/main/java/com/darkaxt/dualdex/live app/src/test/java/com/darkaxt/dualdex/live
git commit -m "feat: own transient live and recovery state"
```

Audit: one stateful implementation, one read-only consumer interface, per-field recovery, matching-ROM gating, and null publication on session end.

### Task 3: Construct one application singleton and retain behavior through an adapter

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateWiringTest.kt`

- [ ] **Step 1: Write construction RED tests**

Prove `DualDexApplication.startLoopback()` creates one decoder, passes it as source to the runtime, and passes the same object as integration target to setup. Prove restart cleanup closes the one runtime subscription.

- [ ] **Step 2: Wire the instance**

```kotlin
val transientState = UnifiedGameStateDecoder()
val runtime = ProductionCompanionRuntime(
    catalogRepository = cache,
    initialSettings = settingsRepository.readForRom(lastCatalogSha256),
    settingsForRom = settingsRepository::readForRom,
    globalSettings = settingsRepository::readGlobal,
    onRomSettingsChanged = settingsRepository::writeForRom,
    onRomDisplayModeChanged = ::requestRomDisplayMode,
    onCatalogCleared = {
        activeCatalogSha256 = null
        preferences.edit().remove(LAST_CATALOG_HASH).remove(LAST_CATALOG_NAME).apply()
    },
    onCatalogCommitted = { sha256, displayName ->
        settingsRepository.migrateLegacyRuleset(sha256)
        activeCatalogSha256 = sha256
        preferences.edit()
            .putString(LAST_CATALOG_HASH, sha256)
            .putString(LAST_CATALOG_NAME, displayName)
            .apply()
    },
    transientGameState = transientState,
)
setupCandidate = RetroArchSetupCoordinator(
    context = this,
    runtime = runtime,
    transientGameState = transientState,
    checkpointCoordinator = checkpointCoordinator,
)
```

Subscribe once in `ProductionCompanionRuntime`. Keep temporary old callbacks for unmigrated features, but mark them compatibility-only and test that they cannot override a migrated field.

- [ ] **Step 3: Verify and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*UnifiedGameStateWiringTest" --tests "*ProductionCompanionRuntimeTest" --no-daemon --console=plain
git add app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateWiringTest.kt
git commit -m "refactor: wire one transient state source"
```

## Stage 2 — Trainer Card, Pokédex, and recovery correctness

### Task 4: Extract shared Gen III Pokédex and Trainer field codecs

**Files:**
- Create: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PokedexCodec.kt`
- Create: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3TrainerFieldCodec.kt`
- Create: `save-core/src/test/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PokedexCodecTest.kt`
- Create: `save-core/src/test/kotlin/com/darkaxt/dualdex/save/gen3/Gen3TrainerFieldCodecTest.kt`
- Modify: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3SaveReader.kt`
- Modify: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PlayerStateCodec.kt`
- Create: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecs.kt`
- Create: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecsTest.kt`

- [ ] **Step 1: Freeze parity and the no-save regression**

Require identical saved/live Pokédex sets from the same SaveBlock2 and require ID, money, play time, badges, seen, and caught from live blocks with no `TrainerSnapshot` or save file input:

```kotlin
val live = Gen3LiveMemoryCodecs.decodePlayer(
    saveBlock1 = block1,
    saveBlock2 = block2,
    extendedSave = null,
    context = parseContext,
    liveParty = LiveValue.Available(party),
)

assertEquals(12345, live.trainer.publicTrainerId.valueOrNull())
assertEquals(3_000L, live.trainer.money.valueOrNull())
assertEquals(TrainerPlayTime(2, 17), live.trainer.playTime.valueOrNull())
assertEquals(savedPokedex.seenDexNumbers, live.pokedex.seenDexNumbers.valueOrNull())
```

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :save-core:test --tests "*Gen3PokedexCodecTest" --tests "*Gen3TrainerFieldCodecTest" :battle-memory:test --tests "*Gen3LiveMemoryCodecsTest" --no-daemon --console=plain
```

- [ ] **Step 3: Extract pure shared functions**

`Gen3PokedexCodec.decode(saveBlock2, context, party)` owns the current bounded aligned candidate scoring, header confidence, caught-subset-of-seen check, optional Party confidence, and deterministic tie-break. `Gen3SaveReader` removes its duplicate private implementation.

`Gen3TrainerFieldCodec` exposes independent functions:

```kotlin
fun decodeIdentity(block2: ByteArray?, abi: Gen3SaveRuntimeAbi): SaveSectionResult<TrainerIdentity>
fun decodePublicTrainerId(block2: ByteArray?, abi: Gen3SaveRuntimeAbi): SaveSectionResult<Int>
fun decodePlayTime(block2: ByteArray?, abi: Gen3SaveRuntimeAbi): SaveSectionResult<TrainerPlayTime>
fun decodeEncryptionKey(block2: ByteArray?, abi: Gen3SaveRuntimeAbi): SaveSectionResult<Long>
fun decodeMoney(block1: ByteArray?, key: Long?, abi: Gen3SaveRuntimeAbi): SaveSectionResult<Long>
fun decodeBadgeFlags(block1: ByteArray?, abi: Gen3SaveRuntimeAbi): SaveSectionResult<Int>
```

The saved codec composes them into complete `TrainerSnapshot`; the live helper maps each result independently. Pokédex failure cannot alter Trainer results.

- [ ] **Step 4: Run GREEN and commit**

```powershell
.\gradlew.bat :save-core:test :battle-memory:test --no-daemon --console=plain
git add save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PokedexCodec.kt save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3TrainerFieldCodec.kt save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3SaveReader.kt save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PlayerStateCodec.kt save-core/src/test/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PokedexCodecTest.kt save-core/src/test/kotlin/com/darkaxt/dualdex/save/gen3/Gen3TrainerFieldCodecTest.kt battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecs.kt battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecsTest.kt
git commit -m "feat: decode live Trainer and Pokedex independently"
```

### Task 5: Feed live Trainer/Pokédex and repair archive recovery matching

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/save/SaveDocumentResolver.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolver.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/save/AndroidSaveDocumentResolver.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/save/SaveDocumentResolverTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolverTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Reproduce both defects**

Add the exact Modern Emerald alias test:

```kotlin
val entry = romEntry(
    sourceName = "Pokemon Modern Emerald (v3.5).7z!Modern Emerald (v3.5).gba",
    archiveEntry = "Modern Emerald (v3.5).gba",
    gameBasename = "Modern Emerald (v3.5)",
)
val candidates = SaveDocumentResolver.matching(
    entry,
    listOf(
        save("Pokemon - Modern Emerald Version v3.5 (USA, Europe).srm"),
        save("Pokemon Modern Emerald (v3.5).srm"),
    ),
    activeGameBasename = "Pokemon Modern Emerald (v3.5)",
)
assertEquals(listOf("Pokemon Modern Emerald (v3.5).srm"), candidates.map { it.name })
```

Add a vertical test where no recovery candidate exists but live Trainer/Pokédex still populates the API.

- [ ] **Step 2: Implement ranked recovery aliases**

Rank exact basenames:

1. active RetroArch `gameBasename`;
2. outer archive/source basename;
3. inner archive-entry basename.

Return only the first nonempty rank. Existing association and structural validation apply within that rank. Never select merely by newest modification time.

- [ ] **Step 3: Make the singleton plan and decode Gen III player regions**

Move pointer/dependent read planning behind `UnifiedGameStateDecoder`. `BattleMemoryCoordinator` supplies one `sampleId` across both phases and never passes `savedTrainer` into a live helper:

```kotlin
val sampleId = nextSampleId++
val pointers = transientState.decodePointers(pointerRegions)
val windows = transientState.valueReadPlan(pointers)
transientState.acceptLiveSample(sampleId, dependentRegions)
```

Switch Trainer Card and Pokédex consumers to the source subscription. Remove `selectedTrainer`, `selectedTrainerIdentity`, and their SaveRAM prerequisite.

- [ ] **Step 4: Verify Stage 2 and commit**

```powershell
.\gradlew.bat :save-core:test :battle-memory:test :companion-core:test :app:testDebugUnitTest --tests "*SaveDocumentResolverTest" --tests "*BattleMemoryCoordinatorTest" --tests "*ProductionCompanionRuntimeTest" --no-daemon --console=plain
git add app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt app/src/main/java/com/darkaxt/dualdex/save/SaveDocumentResolver.kt app/src/main/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolver.kt app/src/main/java/com/darkaxt/dualdex/save/AndroidSaveDocumentResolver.kt app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/save/SaveDocumentResolverTest.kt app/src/test/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolverTest.kt app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "fix: source Trainer and Pokedex from transient state"
```

Audit: no-save live fields work, recovery still works, active archive basename wins, and no diagnostics appear in Trainer/Pokédex UI.

### Implementation audit through Stage 2 — 2026-08-24

| Contract | Evidence | Result |
|---|---|---|
| One application-owned state owner and one read-only consumer interface | `UnifiedGameStateWiringTest`, sole production construction in `DualDexApplication` | PASS |
| Independent live Trainer and Pokédex fields without SaveRAM | shared field codecs, coordinator vertical fixture, API vertical fixture with unavailable money | PASS |
| Live value wins per field; validated recovery fills only unavailable fields | `UnifiedGameStateDecoderTest` and setup recovery projection | PASS |
| Gen III pointer and dependent read planning live behind the unified owner | coordinator delegates both plans and pointer decode to `UnifiedGameStateDecoder` | PASS |
| Archive matching ranks active RetroArch name, outer archive, then inner entry | resolver and direct-resolver regression tests | PASS |
| Compatibility callbacks cannot override migrated Trainer/Pokédex fields | API fixture injects a conflicting legacy live Trainer after the unified snapshot | PASS |
| No diagnostic reason or provenance on normal Trainer/Pokédex surfaces | resolved presentation contains values only; unavailable reasons remain internal | PASS |
| Gen I/II map work remains isolated | Stage 2 changed no map parser, map renderer, Atlas, Gen I, or Gen II file | PASS |

Deferred by the staged specification, not missing from Stage 2: complete battle samples (Stage 3), Party/progression (Stage 4), location/clock/readiness consumption (Stage 5), checkpoint-ledger and bag/event recovery completion (Stage 6), and deletion of the remaining compatibility callbacks (Stage 7). Stage 2 has no blocker for Stage 3.

## Stage 3 — Battle migration

### Task 6: Move battle state and rarity inputs into the same snapshot

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3RuntimeMemoryDecoder.kt`
- Test: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/BattleObservationTrackerTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write battle continuity/privacy RED tests**

Cover overworld → wild → trainer → outcome transitions, first-battle wild classification, double-battle command ownership, target fallback, IV-based rarity availability, explicit tab preservation, and no public opponent move before execution/PP evidence.

- [ ] **Step 2: Decode battle inside the logical sample**

Populate `LiveGameSnapshot.battle` from the same regions/sample ID as the other values. `BattleObservationTracker` remains the temporal privacy helper, but receives only the current snapshot's battle sample. The runtime constructs one Battle screen from that value; compatibility callbacks may carry observations but cannot create a second state authority.

- [ ] **Step 3: Verify Stage 3 and commit**

```powershell
.\gradlew.bat :battle-memory:test :app:testDebugUnitTest --tests "*BattleMemoryCoordinatorTest" --tests "*ProductionCompanionRuntimeTest" --no-daemon --console=plain
git add battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3RuntimeMemoryDecoder.kt battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModels.kt battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/BattleObservationTrackerTest.kt app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "refactor: publish battle through unified state"
```

### Implementation audit through Stage 3 — 2026-08-24

| Contract | Evidence | Result |
|---|---|---|
| Battle lifecycle and full qualified sample share the unified logical sample | coordinator fixtures cover first wild battle, battle-only layouts, live player regions, and end publication | PASS |
| Compatibility callback carries observations but cannot create a second Battle UI authority | conflicting trainer callback leaves the unified wild battle unchanged | PASS |
| Double-battle command owner and automatic target are retained | existing `BattleObservationTrackerTest` ownership/PP regressions and coordinator target tests | PASS |
| Organic opponent moves remain private until execution or PP evidence | raw move slots in the unified sample produce an empty public move list; tracker evidence tests remain green | PASS |
| Wild IV rarity remains usable without area-relative resolution | unified battle API fixture has valid IVs, no encounter areas, and opens a usable Rarity tab | PASS |
| Explicit Battle tab selection survives later unified samples | unified source fixture preserves the manually selected Entry tab | PASS |
| Battle-only Gen III layouts remain supported without SaveRAM/player ABI | battle lifecycle fixture publishes through the singleton with Trainer/Pokédex unavailable | PASS |
| Gen I/II map work remains isolated | Stage 3 changed no map parser, map renderer, Atlas, Gen I, or Gen II file | PASS |

Deferred by the staged specification: Party/progression (Stage 4), Atlas/clock/readiness (Stage 5), passive recovery completion (Stage 6), and Gen I/II callback removal (Stage 7). Stage 3 has no blocker for Stage 4.

## Stage 4 — Party, ownership, and progression

### Task 7: Make Party and Team knowledge consume one value

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Delete after migration: `app/src/main/java/com/darkaxt/dualdex/battle/Gen3LivePartyDecoder.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapper.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapperTest.kt`
- Test: `companion-web/src/pages/PartyPage.test.tsx`

- [ ] **Step 1: Write one-Party-authority RED tests**

Assert Party page, selected detail, Team filter, Trainer license, rarity stars, abilities, nature, HP, PP, and experience use the same ordered list. Valid zero clears both page and Team knowledge. Invalid live Party recovers saved Party without changing live Trainer or battle.

- [ ] **Step 2: Switch consumers atomically**

Remove `liveParty`, `partyPublisher`, `updateLiveParty`, and `selectedParty`. Route `LivePartyKnowledgeMapper` and the Party page from `ResolvedGameSnapshot.party`. Delete the legacy decoder after its cases live in `Gen3LiveMemoryCodecsTest`.

- [ ] **Step 3: Migrate progression inputs**

Use unified Party, Trainer badge flags, and event flags for Trainer license, badges, POI/progression knowledge, and future achievement consumers. Manual Organic discoveries/settings remain companion state but receive game-originating updates only from the source.

- [ ] **Step 4: Verify Stage 4 and commit**

```powershell
.\gradlew.bat :battle-memory:test :companion-core:test :app:testDebugUnitTest --tests "*ProductionCompanionRuntimeTest" --no-daemon --console=plain
Push-Location companion-web
npm.cmd test -- --run src/pages/PartyPage.test.tsx
Pop-Location
git add app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt app/src/main/java/com/darkaxt/dualdex/battle/Gen3LivePartyDecoder.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapper.kt companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapperTest.kt companion-web/src/pages/PartyPage.test.tsx
git commit -m "refactor: unify Party and progression state"
```

### Implementation audit through Stage 4 — 2026-08-24

| Contract | Evidence | Result |
|---|---|---|
| Party, Team, selected details, rarity, Trainer license, and progression use one ordered resolved Party | the runtime vertical test drives recovery, live ordered members, and valid empty Party through every consumer | PASS |
| Live Party wins and recovery fills only an unavailable Party | the same fixture retains live Trainer identity while switching Party authority independently | PASS |
| A valid zero-member Party clears stale Party and Team state | the vertical test publishes an available empty Party and verifies both consumers clear | PASS |
| Corrupt or partial Party windows cannot erase the last valid state | transferred `Gen3LiveGameStateTest` cases reject oversized, incomplete, and corrupt windows | PASS |
| Progression consumes the same Party and independently validated event flags | `applyResolvedPartyAndProgression` merges Party and event-flag knowledge from the unified snapshot only | PASS |
| Legacy Party callbacks cannot compete with the unified source | `partyPublisher`, `updateLiveParty`, `selectedParty`, and the stateful legacy decoder were removed | PASS |
| Gen I/II map work remains isolated | Stage 4 changed no map parser, map renderer, Atlas, Gen I, or Gen II file | PASS |

Deferred by the staged specification: Atlas/clock/readiness (Stage 5), passive recovery completion (Stage 6), and supported Gen I/II non-map callback removal (Stage 7). Stage 4 has no blocker for Stage 5.

## Stage 5 — Atlas, coordinates, clock, and readiness

### Task 8: Move overworld consumers to one location/clock sample

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/GameClockProjection.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/model/GameClockProjectionTest.kt`

- [ ] **Step 1: Write coherent-overworld RED tests**

Prove area/coordinates share one `sampleId`, Recenter uses that position, tracking continues until manual pan/zoom, POI proximity uses it, area changes preserve fog history, missing position never invents `(0, 0)`, and header/day-night use the same clock.

- [ ] **Step 2: Remove direct overworld publishers**

Delete `locationPublisher`, `positionPublisher`, `gen2LightingPublisher`, `updateLiveArea`, `updateLiveMapPosition`, and `updateGen2GameClock` as integration paths. Drive their effects from the single subscription. Map gliding remains presentation behavior driven by resolved coordinate changes.

- [ ] **Step 3: Move readiness to a pure resolved-state policy**

```kotlin
fun ResolvedGameSnapshot.gameAccessReady(): Boolean = when (generation) {
    3 -> location.areaBaseId.source == ResolvedValueSource.LIVE &&
        trainer.identity.source == ResolvedValueSource.LIVE &&
        clock.value?.let { it.hours != 0 || it.minutes != 0 || it.seconds != 0 } == true
    1, 2 -> location.areaBaseId.source == ResolvedValueSource.LIVE
    else -> false
}
```

The application unlocks once per session and does not relock later.

- [ ] **Step 4: Verify Stage 5 and commit**

```powershell
.\gradlew.bat :battle-memory:test :companion-core:test :app:testDebugUnitTest --tests "*BattleMemoryCoordinatorTest" --tests "*ProductionCompanionRuntimeTest" --no-daemon --console=plain
git add app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/GameClockProjection.kt companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/model/GameClockProjectionTest.kt
git commit -m "refactor: unify Atlas clock and readiness state"
```

### Implementation audit through Stage 5 — 2026-08-24

| Contract | Evidence | Result |
|---|---|---|
| Area and coordinates originate in one logical sample and reach companion state atomically | the unified overworld vertical test observes no publication containing a new area with stale coordinates | PASS |
| Atlas tracking, POI proximity, and visited-area history consume the same resolved location | the vertical fixture reveals an adjacent hidden POI and retains both visited areas after movement | PASS |
| Missing coordinates never invent `(0, 0)` | nullable independent location fields remain unavailable when validation fails | PASS |
| Header time and day/night projection consume the unified clock | numeric Gen III time uses the catalog schedule; phase-only Gen II time does not invent hours or minutes | PASS |
| Loading readiness is a pure live-state policy and unlocks only once | Gen III requires live area, live identity, and advancing time; Gen I/II require live area; later zero time cannot relock | PASS |
| Existing Gen I/II battle, area, position, and time-of-day remain available | coordinator fixtures publish all supported Gen I/II values through one snapshot, closing the earlier battle-forwarding gap | PASS |
| Direct overworld publishers are absent from production wiring | area, position, and Gen II lighting publisher fields and setup callbacks were removed; `rg` finds no production wiring | PASS |
| Presentation behavior remains stable | all 23 `MapPage` tests pass; no parser, renderer, Atlas UI, Gen I, or Gen II map file changed | PASS |

The old runtime `updateLiveArea`, `updateLiveMapPosition`, and `updateGen2GameClock` methods remain only as isolated compatibility test seams and have no production caller. Their physical deletion is registered with the complete legacy-surface removal in Stage 7, avoiding unrelated map-test churn while the concurrent Gen I/II map task is active. Stage 5 has no blocker for Stage 6.

## Stage 6 — Save/checkpoint pipes and remaining passive state

### Task 9: Complete recovery routing, bag, and event flags

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinatorTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointRestartIntegrationTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt`

- [ ] **Step 1: Write recovery-pipe RED tests**

Require initial/switch/change/unchanged observations to enter `UnifiedGameStateDecoder.acceptRecovery`. Matching checkpoints seed only the correct playthrough. A changed save freezes the current Organic ledger for sidecar persistence while live values remain authoritative.

- [ ] **Step 2: Reduce checkpoint coordination to storage**

Change the checkpoint coordinator callback to the singleton. Move the existing `SaveKnowledgeApplication` result into the live-state package as `RecoveryApplication`. Give the singleton a bounded `knowledgeLedgerSnapshot` callback during application wiring so a `CHANGED` observation can freeze the pre-application Organic ledger before publishing the new recovery state. The singleton returns `accepted` plus the optional ledger to persist; the coordinator retains only exact checkpoint read/write and atomic sibling behavior.

- [ ] **Step 3: Migrate remaining passive values**

Populate each bag pocket and event flags independently. Keep bag internal—do not add a page. A corrupt pocket affects only itself. POI/progression consumers use the resolved flags.

- [ ] **Step 4: Verify Stage 6 and commit**

```powershell
.\gradlew.bat :save-core:test :battle-memory:test :companion-core:test :app:testDebugUnitTest --tests "*SaveKnowledgeCheckpoint*" --tests "*UnifiedGameStateDecoderTest" --no-daemon --console=plain
git add app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinator.kt app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinatorTest.kt app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointRestartIntegrationTest.kt app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt
git commit -m "refactor: unify transient recovery pipelines"
```

### Implementation audit through Stage 6 — 2026-08-24

| Contract | Evidence | Result |
|---|---|---|
| Initial, switched, changed, and retained unchanged observations enter one recovery owner | setup routes typed monitor results to `SaveKnowledgeCheckpointCoordinator`, which invokes only `acceptRecovery` | PASS |
| Matching checkpoints seed only their exact playthrough | coordinator reads only the observation-derived ROM/save/hash/size/time key for initial or switched observations | PASS |
| A changed save freezes Organic knowledge before applying recovery | the decoder-owned ledger callback is sampled before publication; focused and restart tests verify the exact ledger is persisted/restored | PASS |
| Live fields remain authoritative while recovery fills unavailable fields | unified owner tests retain live money and event flags while recovering independent Pokédex, Party, and bag-pocket values | PASS |
| Disconnect exposes recovery instead of erasing it | `suspendLive` drops only live authority; the recovery-money regression changes source from LIVE to RECOVERY immediately | PASS |
| Save status and restored snapshots use the same transient interface | setup no longer calls runtime save-application/status methods; initial restoration and selection status both enter the singleton | PASS |
| Bag pockets fail independently and remain internal | per-pocket live/recovery tests plus the existing malformed-pocket codec regression keep one corrupt pocket from invalidating the others | PASS |
| Event flags drive progression/POI knowledge from the unified source | live flags win independently over recovery flags and the Stage 4 projection remains the sole game-originating consumer | PASS |
| Gen I/II map work remains isolated | Stage 6 changed no map parser, map renderer, Atlas, Gen I, or Gen II file | PASS |

The old runtime save-application methods remain as isolated compatibility test seams with no production caller. Their physical deletion is registered with the complete legacy-surface removal in Stage 7. Stage 6 has no blocker for Stage 7.

## Stage 7 — Gen I/II parity and legacy removal

### Task 10: Wrap supported Gen I/II state and delete competing authorities

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen1BattleLayoutResolverTest.kt`
- Modify: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen2BattleLayoutResolverTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Delete after migration: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveGameState.kt`

- [x] **Step 1: Freeze Gen I/II behavior**

Record exact supported battle, area, coordinates, Gen II time-of-day, observed moves, and unavailable unsupported sections. Unproven live Trainer/Party/bag/Pokédex fields recover from validated saves when available.

- [x] **Step 2: Move existing resolvers behind the singleton**

The singleton chooses generation and invokes stateless layout helpers. The coordinator supplies bounded regions and publishes no generation-specific state.

- [x] **Step 3: Remove all competing paths**

Remove production uses of:

- `Gen3LiveGameState` as a state authority;
- `Gen3LiveSection` and `Gen3LiveSectionState`;
- Party/location/position/clock/battle state publishers;
- `savedTrainer` from `BattleCatalogContext`;
- `liveGameState`, `liveParty`, and `selected*` runtime methods; and
- feature-specific save fallback outside the singleton.

Stateless helper functions may remain but are callable only through `UnifiedGameStateDecoder`.

- [x] **Step 4: Prove one interface and owner**

```powershell
rg -n "updateLiveParty|updateLiveArea|updateLiveMapPosition|updateGen2GameClock|selectedTrainer|selectedParty|savedTrainer|Gen3LiveSection" app battle-memory companion-core
rg -n "TransientGameStateSource" app/src/main/java
```

Expected: the first command finds no production authority; the second finds the interface, singleton implementation, one construction, and one runtime subscription.

- [x] **Step 5: Verify Stage 7 and commit**

```powershell
.\gradlew.bat :battle-memory:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain
git add battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveGameState.kt battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3RuntimeMemoryDecoder.kt battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen1BattleLayoutResolverTest.kt battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen2BattleLayoutResolverTest.kt app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "refactor: complete unified transient state migration"
```

### Stage 7 specification audit

| Required outcome | Evidence | Result |
| --- | --- | --- |
| Gen I/II retain supported battle, area, coordinates, observed moves, and Gen II phase | coordinator fixtures publish the exact supported values through `acceptExistingGenerationSample`; unsupported sections remain independently unavailable | PASS |
| The coordinator publishes no generation-specific companion state | Gen I/II and Gen III paths now submit bounded samples only to `UnifiedGameStateDecoder` | PASS |
| No legacy aggregate, section, publisher, selector, or saved-Trainer live dependency remains | `Gen3LiveGameState.kt` is deleted; structural `rg` returns no production match for the banned authorities | PASS |
| Stateless Gen III readers are callable only behind the singleton in production | `Gen3LiveMemoryReader` and `Gen3LiveMemoryCodecs` are invoked by `UnifiedGameStateDecoder`; the coordinator requests plans and submits regions through that owner | PASS |
| Exactly one state owner is constructed and one runtime subscription consumes it | `DualDexApplication` is the sole production construction; it injects the same instance into runtime, setup/recovery, and polling; runtime has no default source | PASS |
| Disconnection clears stale live state | `suspendLive` resolves to recovery only, or publishes `null` when no validated recovery exists | PASS |
| Gen I/II map work remains isolated | Stage 7 changed no map parser, map renderer, Atlas, Gen I, or Gen II map file | PASS |
| Affected-module verification is green | `:battle-memory:test :companion-core:test :app:testDebugUnitTest` completed successfully (44 tasks) | PASS |

No Stage 7 requirement is deferred or blocked. Real-ROM identity controls, heap/read measurements, UI/privacy regressions, documentation, and the signed RC remain the Stage 8 gate.

## Stage 8 — Real controls, performance, UI regression, and RC

### Task 11: Verify the complete transient-state contract

**Files:**
- Create: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateRealControlTest.kt`
- Create: `app/src/test/resources/unified-state/official-rom-identities.json`
- Create: `docs/reports/2026-08-24-unified-game-state-verification.md`

- [x] **Step 1: Resolve established real controls without creating ROM copies**

```powershell
$modernCandidates = @(rg --files 'D:\Temp\PokemonHacks\corpus\expanded\roms' | rg 'Modern Emerald.*\.gba$')
if ($modernCandidates.Count -ne 1) { throw "Expected exactly one Modern Emerald ROM, found $($modernCandidates.Count)" }
$env:DUALDEX_MODERN_EMERALD_ROM = $modernCandidates[0]
$env:DUALDEX_UNBOUND_ROM = 'D:\Temp\PokemonHacks\corpus\expanded\roms\0199-a275be0f927e\Unbound (v2.1.1.1).gba'
$env:DUALDEX_ODYSSEY_ROM = 'D:\Temp\PokemonHacks\corpus\expanded\roms\0123-5e7ce46db2ce\Odyssey (v4.1.1).gba'
$env:DUALDEX_OFFICIAL_GB_ROOT = 'D:\Temp\PokemonHacks\Game Boy'
$env:DUALDEX_OFFICIAL_GBA_ROOT = 'D:\Temp\PokemonHacks\Game Boy Advance'
```

Enumerate official Gen I–III candidates from the two exact roots through the existing ROM-source loader, including standard archive containers without extracting persistent ROM copies. Select official base ROM revisions by internal title, game code, and revision, calculate SHA-256, and freeze those identities in `app/src/test/resources/unified-state/official-rom-identities.json`. Fail if an expected identity is absent, duplicated, or resolves to different bytes. Modern Emerald, Unbound, and Odyssey also assert their exact SHA-256 before decode.

- [x] **Step 2: Add the real-control matrix**

For official Gen I–III, Modern Emerald, Unbound, and Odyssey, record per-field live availability. For source-backed Gen III memory fixtures, require Trainer/Pokédex/Party/location/clock without SaveRAM, then apply recovery and prove it fills only intentionally unavailable fields.

- [x] **Step 3: Measure read and heap behavior**

Assert overlapping windows coalesce, requested bytes increase only for a newly migrated field, one live/recovery pair is retained, and no full ROM/SaveRAM/WRAM/EWRAM array survives decode. Record exact before/after window counts, bytes, and retained heap in the report.

- [x] **Step 4: Run the full gate**

```powershell
.\gradlew.bat :save-core:test :battle-memory:test :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest :app:lintRelease --no-daemon --console=plain
Push-Location companion-web
npm.cmd test -- --run
npm.cmd run build
Pop-Location
git diff --check
```

Expected: zero failures and successful web production build.

- [x] **Step 5: Write the sanitized report and commit**

Record exact per-field results, read windows/bytes, heap retention, privacy, UI regression, and unsupported source-backed fields. Exclude player names, IDs, money, save bytes, and personal save paths.

```powershell
git add app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateRealControlTest.kt app/src/test/resources/unified-state/official-rom-identities.json docs/reports/2026-08-24-unified-game-state-verification.md
git commit -m "test: verify unified transient game state"
```

### Task 12: Prepare and publish the next numeric RC

**Files:**
- Modify: `README.md`
- Create: `release/RELEASE_NOTES_1.1.0-rc.54.md`

- [x] **Step 1: Determine the immediate RC successor**

Confirm that RC53 remains the latest protected prerelease, then prepare RC54. Align `1.1.0-rc.54`, version code `1010054`, tag `v1.1.0-rc.54`, APK filename, and release title. Stop and recalculate only if another release has legitimately appeared before execution; never skip a number.

- [x] **Step 2: Run release gates**

```powershell
node --test tools/release/*.test.mjs
.\gradlew.bat :app:lintRelease :app:assembleRelease --no-daemon --console=plain
```

- [x] **Step 3: Commit the release metadata**

```powershell
git add README.md release/RELEASE_NOTES_1.1.0-rc.54.md
git commit -m "release: prepare v1.1.0-rc.54"
```

- [x] **Step 4: Publish through the protected workflow**

```powershell
git push fork HEAD:master
git tag -a v1.1.0-rc.54 -m "v1.1.0-rc.54"
git push fork refs/tags/v1.1.0-rc.54
gh workflow run release.yml --repo Darkaxt/DualScreenDex --ref v1.1.0-rc.54 -f tag=v1.1.0-rc.54
gh run list --repo Darkaxt/DualScreenDex --workflow release.yml --limit 1 --json databaseId,status,conclusion,headSha,url
```

The protected workflow must complete successfully. Do not install, launch, control RetroArch, or perform device UI verification from this task.

- [x] **Step 5: Independently verify the public artifact**

Verify tag/title, APK filename, package ID, numeric version, version code, certificate, SHA-256, and workflow provenance. Record the public artifact hash.

## Final plan-to-spec audit

- [x] Every game-originating transient value reaches DualDex only through `TransientGameStateSource`.
- [x] Exactly one `UnifiedGameStateDecoder` instance exists per application runtime.
- [x] The class owns live decoding, recovery authority, identity gating, and field-level merging.
- [x] Live Trainer ID, money, play time, badges, seen, and caught work without an accessible `.srm`.
- [x] Archive recovery prefers the active RetroArch basename.
- [x] Battle, Party, Pokédex, progression, Atlas, clock, readiness, bag, and event flags consume one resolved snapshot.
- [x] Gen I/II existing support remains available through the same interface.
- [x] Opponent privacy, Organic discovery, fog of war, map tracking, and rarity focus have focused regressions.
- [x] No ordinary UI exposes diagnostic information or provenance.
- [x] Performance/memory and real-control reports contain exact evidence rather than generic labels.
- [x] The next signed RC is published only after every blocker is closed.
