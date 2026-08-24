# Unified Game-State Decoder Design

| Field | Value |
| --- | --- |
| Status | Approved architecture, ready for staged planning |
| Date | 2026-08-24 |
| Scope | Read-only live state for supported Game Boy, Game Boy Color, and Game Boy Advance Pokémon engines |

## Objective

DualDex shall have one long-lived `UnifiedGameStateDecoder` instance as the sole translator and authority router for current gameplay state. It shall decode active RetroArch memory, accept validated save/checkpoint recovery inputs, and produce one immutable `ResolvedGameSnapshot` containing every value it can validate. Pokédex, Party, Battle, Trainer Card, Atlas, loading readiness, progression, and future passive features shall consume that same snapshot.

Live memory is the primary authority while a matching game session is connected. SaveRAM and save-synchronized checkpoints are recovery sources only. A missing, renamed, stale, inaccessible, or invalid `.sav`/`.srm` must not suppress valid live values.

## Current problem

The application already has one RetroArch memory transport owner, `BattleMemoryCoordinator`, but translation and publication are fragmented:

- `Gen3LiveGameState` decodes SaveBlock-derived Trainer, Party, bag, clock, location, and flags.
- `Gen3RuntimeMemoryDecoder` separately decodes battle lifecycle, position, target, encounter kind, and command ownership.
- `Gen3LivePartyDecoder` provides a legacy Party-only path.
- Gen I/II location, position, battle, and time-of-day use separate coordinator branches and publishers.
- `ProductionCompanionRuntime` receives independent battle, area, position, party, clock, and Gen III snapshot callbacks and then applies feature-specific selection rules.
- `TrainerSnapshot` is an all-fields save-oriented object. The Gen III live path currently requires a saved Trainer merely to obtain Pokédex counts, causing valid live ID, money, play time, badges, name, and gender to disappear when SaveRAM matching fails.

This fragmentation allows different pages to observe different authorities and sampling moments. It also makes a SaveRAM failure capable of disabling live information that is already present in WRAM.

## Product contract

### Authority order

For current gameplay values, the authority order is:

1. A validated value from the latest matching `LiveGameSnapshot`.
2. The matching save-synchronized checkpoint or parsed SaveRAM value, only when the live value is unavailable.
3. Unavailable.

The parsed ROM catalog remains the authority for static definitions, layouts, labels, sprites, tables, and joins. It is not a source of current playthrough state.

Save recovery is applied per field or independently meaningful section. A missing live Pokédex count may fall back to the checkpoint without replacing live Trainer ID, money, play time, badges, Party, or location.

### One game-state decoder

The application owns exactly one `UnifiedGameStateDecoder` instance for the active session. Its public contract is conceptually:

```kotlin
class UnifiedGameStateDecoder {
    fun beginSession(context: TransientGameStateContext)

    fun pointerReadPlan(context: TransientGameStateContext): List<LiveReadWindow>

    fun valueReadPlan(
        context: TransientGameStateContext,
        pointers: LivePointerSnapshot,
    ): List<LiveReadWindow>

    fun acceptLiveSample(
        sampleId: Long,
        regions: Map<String, ByteArray>,
    ): ResolvedGameSnapshot

    fun acceptRecovery(projection: RecoveryProjection): RecoveryApplication

    fun clearRecovery(saveIdentity: String? = null): ResolvedGameSnapshot

    fun endSession(): ResolvedGameSnapshot
}
```

`RecoveryApplication` reports whether the matching recovery input was accepted and carries an optional frozen `KnowledgeLedger` for the storage coordinator to persist. The current resolved state remains available through `TransientGameStateSource`; recovery integration does not create a second consumer interface.

The class may use small stateless helper codecs for generation-specific and save structures. Those helpers are internal implementation units; they are not independently owned polling services, public state authorities, recovery authorities, or feature-specific decoders.

### Single DualDex interface

All downstream DualDex code sees one read-only interface:

```kotlin
fun interface TransientGameStateListener {
    fun onStateChanged(snapshot: ResolvedGameSnapshot?)
}

interface TransientGameStateSource {
    val current: ResolvedGameSnapshot?

    fun subscribe(listener: TransientGameStateListener): AutoCloseable
}
```

`UnifiedGameStateDecoder` is the sole implementation. Its session, live-sample, and recovery-input methods are integration commands used by the existing coordinators; they are not feature-specific consumer APIs. `ProductionCompanionRuntime` subscribes once and projects the resolved snapshot into companion state. No feature receives a direct Party, battle, Trainer, area, position, clock, event-flag, or save callback.

Subscription immediately supplies the current snapshot, publishes only semantic changes, and publishes `null` when the active session ends or switches before a replacement snapshot exists. Closing the returned handle removes the listener without changing decoder state.

`BattleMemoryCoordinator` initially remains the sole live transport and scheduling owner. It asks the decoder for bounded read windows, performs the pointer and dependent read phases, and passes the resulting byte regions back to that same decoder instance. `SavePollingMonitor` and the checkpoint coordinator remain bounded storage/checksum helpers and deliver only validated recovery inputs to the same decoder instance. They do not select UI authority. A later coordinator rename is allowed only after all consumers use the unified snapshot; the migration must not introduce a second scheduler, transport, recovery owner, or competing merge policy.

### One logical sample

A logical sample may require two physical read phases because SaveBlock pointers must be validated before dependent EWRAM windows can be read. Both phases share one monotonically increasing `sampleId`. The published snapshot contains only data from that logical sample; the decoder must not silently combine raw regions from different samples.

Overlapping bounded read windows are coalesced before transport. Raw whole-WRAM/EWRAM discovery remains allowed only where an existing generation-specific layout resolver genuinely requires it. Once a layout is known, normal polling uses bounded regions.

### Independent availability

One malformed or unsupported field must not invalidate an unrelated value. The normalized primitive is:

```kotlin
sealed interface LiveValue<out T> {
    data class Available<T>(val value: T) : LiveValue<T>
    data class Unavailable(val reason: LiveUnavailableReason) : LiveValue<Nothing>
}

enum class LiveUnavailableCode {
    UNSUPPORTED_LAYOUT,
    MISSING_REGION,
    INVALID_POINTER,
    INVALID_VALUE,
    AMBIGUOUS_LAYOUT,
}

data class LiveUnavailableReason(
    val code: LiveUnavailableCode,
    val detail: String,
)
```

An available empty collection is represented as `Available(emptyList())` or `Available(emptySet())`; it is not treated as missing. Technical reasons remain internal and may be exposed only in Debug Settings.

Trainer state is field-granular because its fields have different byte and validation dependencies:

```kotlin
data class LiveTrainerState(
    val identity: LiveValue<TrainerIdentity>,
    val publicTrainerId: LiveValue<Int>,
    val money: LiveValue<Long>,
    val playTime: LiveValue<TrainerPlayTime>,
    val badgeFlags: LiveValue<Int>,
    val stars: LiveValue<Int>,
)
```

This replaces the live use of the all-or-nothing `TrainerSnapshot`. The save parser may continue producing a complete `TrainerSnapshot` after all required save fields validate.

## Normalized snapshot

The decoder retains one internal `LiveGameSnapshot` plus at most one matching recovery snapshot. It publishes:

```kotlin
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

data class ResolvedGameSnapshot(
    val romIdentity: String,
    val generation: Int,
    val sampleId: Long?,
    val trainer: ResolvedTrainerState,
    val pokedex: ResolvedPokedexState,
    val party: ResolvedValue<List<OwnedIndividual>>,
    val battle: ResolvedValue<LiveBattleState>,
    val location: ResolvedLocationState,
    val clock: ResolvedValue<LiveClockState>,
    val bag: Map<BagPocket, ResolvedValue<BagPocketSnapshot>>,
    val eventFlags: ResolvedValue<Set<Int>>,
    val recovery: RecoveryState,
)
```

`LivePokedexState` contains independently available seen and caught species/Dex-number sets and their counts. `LiveLocationState` contains independently available `areaBaseId` and `position` fields under the same `sampleId`, so Atlas never combines an area from one sample with coordinates from another. `LiveBattleState` contains the existing battle sample, lifecycle, encounter classification, command owner, selected move, and target evidence without exposing unobserved opponent moves to public companion state.

The snapshot is immutable. Feature code cannot mutate it or retain private raw memory regions.

`RecoveryState` carries the current validated SaveRAM status and any exact checkpoint seed/application event required by the companion runtime. It contains no raw save bytes. Normal pages use only resolved gameplay values; recovery identity, source, reasons, and provenance are available exclusively to Debug Settings.

The interface covers all game-originating transient state and its recovery projection. Navigation history, display settings, map filter settings, and other user preferences remain ordinary companion configuration rather than decoded game state. Organic knowledge remains companion-owned, but every game-originating update to it—Party ownership, Pokédex flags, battle observations, areas, coordinates, and event flags—must originate from this one interface.

## Generation adapters

### Generation III

The Gen III adapter uses catalog-derived runtime layouts and `SaveParseContext`:

- validate `gSaveBlock1Ptr` and `gSaveBlock2Ptr` within the declared GBA work-RAM ranges;
- decode Trainer identity, public ID, money, play time, badges, Pokédex flags, bag, event flags, area, and position from live SaveBlock bytes;
- decode Party from the resolved live Party globals and complete records;
- decode battle lifecycle, battlers, encounter kind, command ownership, selected move, and target from the existing battle layouts;
- decode the live clock from the resolved schedule/global; and
- use extended save bytes only for fields whose typed ABI requires them.

The Pokédex flag resolver currently embedded in `Gen3SaveReader` becomes a shared pure codec. Live Party is an optional confidence input, never a dependency for Trainer core fields. Save parsing and live decoding use the same flag and Trainer field codecs so their interpretation cannot drift.

### Generations I and II

The initial Gen I/II adapter wraps the already supported live battle, location, position, and Gen II time-of-day paths into the same snapshot. Unsupported sections are explicitly unavailable. Existing live values must not regress merely because these generations expose fewer structures.

Later source-backed Gen I/II Trainer, Party, Pokédex, inventory, or progression mappings plug into the same snapshot fields without adding new public decoders or publishers.

## Consumer migration

Every feature migrates from direct callbacks or private selectors to the snapshot exposed by `TransientGameStateSource`:

| Consumer | Unified authority |
| --- | --- |
| Trainer Card | `trainer` resolved per field, live first |
| Pokédex counts and knowledge | `pokedex`, then checkpoint recovery; current-session organic observations remain additive |
| Party overview/details and Team filter | the same `party` value |
| Battle UI and rarity focus | `battle`; opponent-move privacy remains enforced by `BattleObservationTracker` |
| Atlas, local-map tracking, POI proximity, and area discovery | the same `location` value |
| Header clock and day/night projection | `clock` |
| Trainer license, badges, POI flags, and future progression views | `party`, `trainer.badgeFlags`, and `eventFlags` |
| Loading game-readiness gate | a policy over the unified snapshot, not an independent memory read |
| Future bag view | `bag` |

During staged migration, a compatibility adapter may translate `LiveGameSnapshot` into old callbacks. An individual consumer switches once, after its focused tests pass. The adapter is deleted at the final gate.

## Recovery merge

The unified decoder manages recovery authority and per-field merging. Storage helpers may read `.sav`, `.srm`, SQLite, or sidecar checkpoint files, validate them, and parse them into typed inputs; they then deliver those inputs through `acceptRecovery`. The unified decoder itself performs no filesystem discovery and never accepts unvalidated bytes.

```kotlin
data class ResolvedValue<T>(
    val value: T?,
    val source: ResolvedValueSource,
)

enum class ResolvedValueSource { LIVE, RECOVERY, UNAVAILABLE }
```

The decoder's internal recovery helper follows these rules:

- live available always wins for current state;
- checkpoint values fill only unavailable live fields;
- a checkpoint is eligible only after ROM and save identity validation;
- changing, renaming, or losing a save cannot erase available live data;
- disconnecting clears live authority and permits the matching checkpoint to recover the UI;
- switching ROM identity clears both the prior live snapshot and any nonmatching recovery state; and
- recovery provenance is internal/Debug-only and never appears in ordinary UI copy.

## Failure behavior

- Invalid pointer: only dependent fields become unavailable.
- Invalid Trainer field: only that field becomes unavailable.
- Invalid Pokédex layout: seen/caught become unavailable; Trainer core remains live.
- Invalid Party count or record: Party becomes unavailable; Trainer, battle, location, and clock remain live.
- Valid Party count zero: publish an available empty Party.
- Invalid battle structure: Battle becomes unavailable without interrupting overworld state.
- Missing map coordinates with a valid area: location exposes the area while coordinates remain unavailable inside its normalized structure; Atlas must not invent coordinates.
- Read failure: do not publish fabricated zeros or values from another ROM/sample.
- Unsupported generation field: unavailable with a typed internal reason, never `NOT_APPLICABLE` masquerading as `NOT_FOUND`.

No ordinary page may display addresses, offsets, source labels, parser labels, availability reasons, or provenance. Those details belong only to Debug Settings.

## Performance and lifecycle

- One live coordinator, one transport, one unified decoder instance, one latest live snapshot, and at most one matching recovery snapshot exist per active session.
- Each selected byte range is read at most once per logical sample after overlap coalescing.
- Decoding is pure and bounded; it retains no ROM or memory dump.
- Snapshot equality suppresses redundant downstream publication, but `sampleId` is excluded from semantic equality used for UI dispatch.
- Existing configurable battle polling remains the single cadence authority. This migration adds no timeout and no additional polling cadence.
- Disconnect and ROM switch clear pointer caches, generation layouts, decoder session state, and the last published snapshot atomically.

## Privacy and passive-operation contract

- All memory operations remain read-only.
- No controller input, cheat, memory write, SaveRAM write, game order, or RetroArch configuration mutation is introduced.
- Raw opponent move slots may be decoded internally for structural validation and PP-delta observation, but public state receives only moves proven observed under the existing policy.
- Player names, IDs, money, party, and save-derived recovery data remain on-device and are excluded from compatibility reports.

## Staged migration order

1. **Unified contract, recovery authority, and compatibility adapter:** introduce `UnifiedGameStateDecoder`, `LiveGameSnapshot`, and `ResolvedGameSnapshot` without changing visible behavior.
2. **Trainer Card and Pokédex:** remove the SaveRAM prerequisite, decode live Trainer fields and Pokédex flags independently, and resolve the current null-field defect while retaining save/checkpoint recovery.
3. **Battle:** move lifecycle, encounter kind, command ownership, rarity inputs, target, and observation tracking onto the unified snapshot.
4. **Party and progression:** make Party page, Team filter, Trainer license, badges, event flags, and current-session knowledge consume the same snapshot.
5. **Atlas and clock:** move area, position, POI proximity, tracking, readiness, header clock, and day/night projection.
6. **Bag and remaining internal state:** migrate bag pockets and any remaining passive live fields, without adding a bag page.
7. **Gen I/II parity and legacy removal:** wrap supported Gen I/II live values, remove old publishers/selectors, and rename the coordinator only if the final code reads more clearly.
8. **Integration and release gate:** run focused, affected-module, real-ROM, performance, memory, privacy, and UI regressions before preparing the next RC.

Each stage must keep the application functional and commit-ready. A stage may retain the compatibility adapter but may not create a second source of truth.

## Verification contract

### Required automated controls

- `LiveValue` and snapshot invariants, including available empty collections.
- One `UnifiedGameStateDecoder` instance plans and decodes all supported regions and owns recovery selection.
- One logical sample cannot mix regions from different `sampleId` values.
- Missing saved Trainer/Pokédex data does not suppress live ID, money, play time, badges, identity, Party, location, clock, or battle.
- Invalid Pokédex flags leave Trainer core available.
- The recovery resolver chooses live per field and uses a matching checkpoint only for unavailable values.
- Party page and Team knowledge receive identical Party membership.
- Atlas area and coordinates originate from the same snapshot.
- Battle rarity receives the same encounter kind and opponent IV data as the battle UI.
- No opponent move reaches public state before observation evidence.
- Gen I/II supported live battle/location/time behavior remains unchanged.
- No ordinary UI string contains technical availability or provenance text.

### Required real controls

The final gate uses the available official Gen I–III ROMs plus Modern Emerald, Pokémon Unbound, and Pokémon Odyssey where the corresponding source-backed runtime layouts exist. For each control, record per-field live availability and compare the unified path against the pre-migration behavior. A SaveRAM-absent Gen III run is mandatory and must still populate every live-supported Trainer Card and Pokédex value.

Real controls remain read-only. Automated validation may use source-backed memory fixtures. Physical-device gameplay verification belongs to the signed RC gate and is not simulated by synthetic-only evidence.

### Performance and memory controls

- Compare read-window count and total requested bytes before and after each migration stage.
- Assert no duplicate overlapping window survives coalescing.
- Assert no full ROM, save, WRAM, or EWRAM copy is retained by the decoder or snapshot.
- Measure sustained heap use across overworld, map movement, Party navigation, and battle transitions; the live snapshot count retained by production owners must remain one.

## Completion criteria

The migration is complete when:

1. All live-memory translation and recovery authority enter through one `UnifiedGameStateDecoder` instance.
2. `TransientGameStateSource` is the only transient-state consumer interface exposed to DualDex.
3. All user-facing flows consume one current `ResolvedGameSnapshot` published through that interface.
4. No generation-specific live helper accepts `savedTrainer`, `SaveSnapshot`, checkpoint state, or a save filename as an input; only the unified façade accepts validated typed recovery state.
5. Save absence or filename mismatch cannot suppress valid live state.
6. Removing the legacy publishers and selectors causes no feature, privacy, performance, or official/hack compatibility regression.
7. The full verification matrix passes and the signed next RC is independently versioned and published.
