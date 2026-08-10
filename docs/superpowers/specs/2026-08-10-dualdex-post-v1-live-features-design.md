# DualDex Post-v1 Feature Completion Design

| Field | Value |
| --- | --- |
| Status | Approved product direction consolidated for implementation |
| Date | 2026-08-10 |
| Target | Next GitHub-signed release candidate |
| Scope source | Approved live-companion design plus the explicit post-v1 backlog |

## Outcome

The next candidate promotes the already validated Modern Emerald single-battle evidence into the passive production companion and completes the two other explicitly deferred user-facing features: time-of-day markers in the Area-filtered Pokédex and a bounded, gutter-aware resizable overlay.

The normal ROM and SaveRAM Pokédex remains independent. Failure to identify a live battle, time window, or resize state removes only that capability; it never unloads the catalog or replaces validated data with guesses.

## Feature inventory

### Live battle companion

When RetroArch is playing a supported GBA ROM, a read-only production monitor locates the Generation III `BattlePokemon` array by structure shape and validates it against the active parsed catalog. It does not require a ROM-specific user profile or a hard-coded absolute address.

The first production shape promotes the fields already demonstrated by the Modern Emerald evidence:

- automatic battle entry when one valid opposing battler exists;
- opponent species, level, types, and innate IV tier;
- the player's highlighted move;
- ROM-derived move metadata and type-chart effectiveness;
- a direct link from Battle to the target's Pokédex entry;
- Organic move discovery from opponent PP decreases, persisted by species and ranked by frequency; and
- automatic battle exit only after the battle array no longer validates for consecutive monitor heartbeats.

The battle monitor reads memory regardless of whether the diagnostic Memory Mapper Lab is enabled. The lab remains an optional evidence/export tool and does not own production state. Both consumers use separate read-only transports and neither may issue memory, input, cheat, save-state, or content-control writes.

Double battles are attempted automatically from the same structural shape instead of being disabled. The resolver enumerates every catalog-valid active opposing battler from `gBattlersCount`, `gBattlerPositions`, and the contiguous battle array, then competes the nearby target-cursor candidates against side, range, selected-move, and transition invariants. When one cursor validates, the Battle page follows it automatically. When opponents validate but no cursor does, DualDex still shows every opponent and permits manual target switching; diagnostics distinguish `AUTOMATIC_TARGET` from `MANUAL_TARGET_FALLBACK`. A later labeled double-target capture promotes the inferred cursor to independently validated evidence without changing the UI contract. Automatic RAM ruleset selection follows the same evidence rule: `Auto` continues using the parsed primary ruleset until a selector is independently validated.

### Knowledge behavior

- `Discovered` may show complete ROM-derived opponent facts and calculated effectiveness.
- `Organic` shows identity and the qualitative recruitment tier, but an uncaught species exposes only moves it has visibly consumed PP for; move levels and unused move slots stay hidden. Effectiveness is revealed according to the existing Organic matchup policy.
- `Hidden` keeps only minimal target identity and caught state.
- Once captured, the linked Pokédex remains statically omniscient and the observed-use frequency metric is omitted.
- Observations store counts only. No timestamps or move order are retained.

### Area time-of-day markers

Encounter data gains an explicit availability value: `ANY`, `MORNING`, `DAY`, `NIGHT`, or a validated combination. Existing Generation II morning/day/night tables populate it directly. Generations without time-sliced wild tables remain `ANY`; the parser does not infer time restrictions merely because a game has a real-time clock.

The marker is shown only while the Area filter is active:

- sun: the species is available in the selected/current area only during morning/day windows;
- moon: it is available only at night;
- sun and moon: it has separate day and night availability but not a time-independent slot;
- no icon: at least one time-independent slot makes it available regardless of time.

The marker is an accessible inline SVG, not an emoji. Its label describes the exact parsed windows. Encounter detail rows retain their level range and rate.

### Resizable overlay

Overlay mode opens at the existing automatically fitted 4:3 size. The user can drag a dedicated resize handle while the panel is visible. Resizing:

- preserves a 4:3 aspect ratio;
- enforces a readable minimum and a screen/gutter-derived maximum;
- clamps the panel inside the available display bounds;
- preserves the chosen size as a normalized scale so rotation or a different display can refit safely; and
- leaves the floating Poké Ball independently draggable.

The panel preferentially fits unused side gutters on wide displays. If neither gutter can hold the minimum readable panel, it falls back to the centered bounded overlay. No resize action changes RetroArch focus or injects game input.

## Architecture

`battle-memory` is a new pure-Kotlin module. It owns Gen III structure scanning, candidate validation, dynamic battler enumeration, target-cursor competition, polling state, PP-baseline observation, and per-field capability results. It consumes an immutable catalog view and a read-only byte-region source, and emits normalized battle updates. It has no Android, WebView, simulator, or diagnostic-lab dependency.

The Android coordinator schedules bounded reads through RetroArch Network Commands and passes completed regions to `battle-memory`. `ProductionCompanionRuntime` translates validated updates into existing `CompanionAction.BattleStarted`, `BattleEnded`, ledger replacement, and selected-move state. The API and Preact battle pages consume the existing battle model with added capability diagnostics.

Encounter availability remains part of `parser-core`, is serialized by `catalog-store`, and is exposed through the companion API. Overlay size calculation remains a pure Kotlin policy; Android view code only applies its result and stores the normalized user scale.

## Error and compatibility rules

- Ambiguous battle-array candidates publish no battle state.
- A validated multi-opponent array without a validated live cursor publishes every opponent with manual target fallback rather than suppressing the battle.
- Invalid species, move, type, HP, PP, level, ability, record bounds, or catalog mismatch rejects only that candidate.
- A transient missed memory reply does not end a battle. State changes follow validated samples and heartbeat evidence rather than cancellation timeouts.
- Catalog switches clear battle baselines and prevent observations from crossing ROM identities.
- Existing cached catalog documents without encounter availability migrate to `ANY`.
- Unsupported GB/GBC live layouts report battle memory as unavailable while their ROM/SaveRAM Pokédex continues normally.

## Verification contract

Automated tests cover the structure scanner, ambiguity rejection, selected move, PP-decrease counts, baseline reset, knowledge-policy leakage, battle lifecycle, encounter availability serialization, Area icons, overlay bounds, persistence, and legacy settings/catalog migration.

The dedicated `emulator-5556` receives only a debug APK. Physical Thor validation uses only the GitHub-signed candidate and must prove, with RetroArch still focused:

- Modern Emerald automatically enters the Treecko-versus-Weedle battle page;
- Weedle is identified at level 3;
- the highlighted player move and effectiveness are correct;
- Poison Sting and String Shot counts increase only after observable use;
- the linked Pokédex behaves according to capture state;
- leaving battle returns to the prior Pokédex page;
- a synthetic and source-shaped double-battle fixture enumerates both opponents, follows a validated target transition, and exercises the manual fallback when the cursor is ambiguous;
- an Area-filtered Generation II catalog renders the correct time marker; and
- the overlay can resize within bounds and reopen at the retained scale.

No release claim extends beyond the fields and game families proven by these gates.
