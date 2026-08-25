# Unified Game-State Single-Authority Design

| Field | Value |
| --- | --- |
| Status | Corrective implementation contract |
| Date | 2026-08-25 |
| Supersedes | Consumer and recovery portions of `2026-08-24-unified-live-memory-decoder-design.md` |
| Scope | Every game-originating transient value consumed by DualDex |

## Objective

DualDex shall expose one long-lived `UnifiedGameStateDecoder` instance and one immutable, translated `ResolvedGameSnapshot` as the sole authority for the active playthrough. Live memory, validated SaveRAM, and exact checkpoint recovery enter that class. All normal UI flows consume fields projected directly from its snapshot.

The runtime may adapt types for the companion API, but it may not choose an authority, translate identifiers, merge a recovery snapshot into gameplay knowledge, or maintain a feature-specific copy whose lifecycle differs from the resolved snapshot.

## Corrected problem statement

The August 24 migration achieved one decoder instance and one runtime subscription, but that was insufficient. `ProductionCompanionRuntime` retained section-specific authority logic:

- recovery was eagerly merged into `KnowledgeLedger`;
- live Pokédex flags were translated outside the decoder and added to that ledger;
- live replacement could add missing entries but could not retract stale recovery entries;
- Trainer Card read snapshot counts while the Pokédex read the independently merged ledger;
- Party, progression, battle observations, and Atlas still passed through feature-specific projection and mutation pipelines; and
- structural checks proved one subscription without proving that each rendered value originated from the corresponding snapshot field.

This produced the observed contradiction: one live party member and Trainer Card count `1`, while the Pokédex displayed `52` caught entries. The exact pre-starter Modern Emerald save parses as `0` caught. The post-starter expansion came from the old inverse mapping publishing every form/alias row that shared the starter's single Pokédex flag, then retaining those additions in the separate Pokédex ledger.

## Non-negotiable invariants

1. `UnifiedGameStateDecoder` is the only stateful translator and authority router for game-originating transient data.
2. `TransientGameStateSource.current` and its subscription publish the same immutable `ResolvedGameSnapshot`.
3. The resolved boundary uses canonical catalog identifiers. Pokédex values are species IDs, not raw regional, National, or engine flag numbers.
   One raw Pokédex flag resolves to exactly one base-form catalog representative; forms and aliases require their own live ownership or observation evidence.
4. Live values replace recovery values for the same field. Replacement must retract stale recovery values, including values already exposed by an earlier snapshot.
5. SaveRAM and checkpoint recovery fill only fields whose live value is unavailable. Recovery may never be eagerly unioned into an unrelated live field.
6. A validated empty set or empty party is authoritative and clears stale state. It is not treated as unavailable.
7. Organic discoveries are a separate knowledge layer. They may augment presentation according to Organic-mode policy, but recovery data may not masquerade as an organic discovery.
8. Every game-originating update—Trainer, Pokédex, Party, battle, observations, location, coordinates, clock, bag, event flags, and readiness—enters through the unified decoder and leaves through the resolved snapshot.
9. Navigation, UI preferences, manual map pan/zoom, and Organic/Discovered mode settings remain companion-owned because they are not game-originating transient data.
10. Technical provenance and failure reasons remain restricted to Debug Settings.
11. The migration adds no poller, transport, timeout, raw-memory retention, or full-memory copy.

## Normalized public snapshot

The resolved model is the only public transient interface:

```kotlin
data class ResolvedPokedexState(
    val seenSpeciesIds: ResolvedValue<Set<Int>>,
    val caughtSpeciesIds: ResolvedValue<Set<Int>>,
)

data class ResolvedGameSnapshot(
    val romIdentity: String,
    val generation: Int,
    val sampleId: Long?,
    val trainer: ResolvedTrainerState,
    val pokedex: ResolvedPokedexState,
    val party: ResolvedValue<List<OwnedIndividual>>,
    val battle: ResolvedValue<LiveBattleState>,
    val battleKnowledge: ResolvedBattleKnowledge,
    val location: ResolvedLocationState,
    val clock: ResolvedValue<LiveClockState>,
    val bag: Map<BagPocket, ResolvedValue<BagPocketSnapshot>>,
    val eventFlags: ResolvedValue<Set<Int>>,
    val recovery: RecoveryState,
)
```

Raw Pokédex flag numbers remain an internal input type. The decoder translates them using the active `SaveParseContext.speciesById[*].pokedexFlagNumber` mapping before publication. A shared flag deterministically selects the base-form representative and never expands into every alias. An absent mapping makes only that Pokédex field unavailable.

`ResolvedBattleKnowledge` is a translated immutable result of the existing observation tracker: observed move increments, matchup discoveries, and organically encountered species. The tracker may remain a stateless/temporal helper, but its output is accepted by the decoder and published with the battle section. It is not delivered directly to the runtime.

## Companion projection boundary

The runtime performs one deterministic projection:

```text
ResolvedGameSnapshot -> Companion transient projection -> StateView
```

This projection may format a clock, create display models, or join canonical IDs to static catalog records. It must be a replacement projection keyed by the snapshot and must not perform fallback selection.

The companion model explicitly separates:

- current resolved game state;
- current-session Organic observations;
- save-synchronized checkpoint knowledge; and
- user settings/navigation.

Effective Pokédex presentation follows this policy:

- while `seenSpeciesIds` or `caughtSpeciesIds` is available, use that current resolved field plus legitimate current-session Organic observations;
- when the field is live, no recovery-origin contribution for that field survives;
- when live is unavailable and recovery is selected by the decoder, use the recovery value;
- Party ownership may imply caught only through the same snapshot projection; and
- clearing/switching a session clears the transient projection before another ROM can publish.

## Required consumer trace

| Flow | Snapshot input | Forbidden alternate authority |
| --- | --- | --- |
| Trainer Card | `trainer`, `pokedex` | saved Trainer selector, ledger counts |
| Pokédex tabs/counters | `pokedex`, `party`, current Organic observations | eager SaveRAM merge, additive legacy action |
| Party/details/Team/license | `party`, `trainer.badgeFlags` | Party callback or saved-party selector |
| Battle and encounter title | `battle` | coordinator-to-runtime battle callback |
| Battle observations/privacy | `battleKnowledge` | direct tracker mutation of ledger |
| Atlas/current area/position | `location` | area or coordinate publisher |
| Clock/day-night/readiness | `clock`, `location`, `trainer` | independent clock/readiness callback |
| POIs/progression | `location`, `eventFlags` | direct event/save merge |
| Bag/future passive pages | `bag` | direct SaveRAM access |

Encounter page titles are data-derived normal UI copy:

- wild: `WILD ENCOUNTER`;
- trainer: `TRAINER BATTLE`;
- unresolved: `ENCOUNTER`.

## Recovery and checkpoint contract

Storage helpers discover and validate save files and sidecars, then submit typed `RecoveryProjection` values to `UnifiedGameStateDecoder`. They do not mutate companion state.

The decoder owns ROM/save identity gating and per-field authority. A changed save may freeze the Organic/checkpoint ledger for persistence, but that persistence event is distinct from the current resolved gameplay values. Checkpoints never store or restore mirrored Pokédex seen/caught, owned/Team, Trainer-license, or current-area fields; those values come from the typed save snapshot. Existing checkpoints containing those fields are stripped on input. Applying a recovery projection must not write its Pokédex, Party, Trainer, area, bag, or event data directly into a live UI ledger. The coordinator writes a checkpoint only for a validated `CHANGED` save observation.

On disconnect, live fields become unavailable and matching recovery can become visible through a newly published resolved snapshot. On reconnect, the next valid live field replaces and retracts the corresponding recovery field.

## Verification contract

Every stage must publish a spec audit containing the exact requirement, code path, automated evidence, actual result, and any blocker or explicit deferral. A passing build alone is not evidence of single authority.

Required structural controls:

- one production construction of `UnifiedGameStateDecoder`;
- one production subscription to `TransientGameStateSource`;
- no feature-specific transient publisher into `ProductionCompanionRuntime`;
- no SaveRAM-to-UI merge outside the decoder;
- no raw Pokédex flag translation outside the decoder; and
- no production dispatch of legacy live/resolved section actions after migration.

Required behavioral controls:

- recovery `52 caught` followed by live `1 caught` yields exactly `1` in both Trainer Card and Pokédex;
- a pre-starter Modern Emerald save resolves to `0`, and a checkpoint containing `52` mirrored caught entries cannot change that result;
- one caught flag shared by 51 aliases resolves to one canonical caught species, not 52;
- live empty seen/caught clears stale recovery entries;
- live unavailable exposes matching recovery without changing unrelated live Trainer/Party fields;
- Pokédex, Party, Trainer license, and owned/team indicators agree for the same snapshot;
- battle lifecycle, encounter title, rarity, targeting, and privacy agree for the same battle snapshot;
- area, coordinates, POIs, clock, and readiness agree for the same overworld snapshot;
- session switch cannot leak prior-ROM transient state; and
- Gen I/II unsupported fields remain unavailable while their supported fields continue through the same interface.

Required final gates:

- affected Kotlin/JVM tests at every stage;
- complete Gradle unit/lint suite and web tests/build;
- static consumer trace with zero forbidden production paths;
- exact official Gen I–III plus Modern Emerald, Unbound, and Odyssey controls;
- read-window, retained-byte, and sustained-heap checks;
- ordinary-UI diagnostic-string audit; and
- protected signed next numeric RC only after every blocker is closed.

## Completion criteria

The work is complete only when deleting any legacy runtime action/callback cannot change gameplay state because no production consumer uses it, and every displayed transient value can be traced from one field of the current `ResolvedGameSnapshot` without encountering another authority or fallback decision.
