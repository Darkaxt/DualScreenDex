# Passive Insights and Progress Suite Design

| Field | Value |
| --- | --- |
| Status | Approved product direction consolidated for staged implementation |
| Date | 2026-08-26 |
| Scope | Party Analysis, Progress and Challenges, Save Timeline, Pokédex Specimens, Atlas Area Guide, and selected-move Damage Forecast |
| Delivery model | One independently planned, implemented, audited, and accepted feature at a time |
| Primary authority | `UnifiedGameStateDecoder` and its immutable `ResolvedGameSnapshot` |

## 1. Outcome

DualDex shall add interpretation and playthrough context without becoming a save editor or a second game controller. The suite turns already parsed ROM facts and read-only live state into six useful companion features:

1. Party Analysis reached from the existing Party page.
2. A Trainer `PROGRESS` destination containing metrics, portable challenges, and Save Timeline.
3. Pokédex-owned specimen browsing across the live Party and PC storage.
4. An Area Guide drawer inside Atlas.
5. A damage-range forecast attached to the currently selected player move in battle.
6. A semantic challenge engine informed by the titles and descriptions of official Generation I–III RetroAchievements sets, but independent of RetroAchievements at runtime.

The suite remains passive. It does not send game input, write emulator memory, edit saves, claim RetroAchievements unlocks, or require a RetroAchievements account in the APK.

This document is the normative umbrella specification. Each numbered feature receives its own implementation plan and completion audit. A later feature must not be started merely to conceal a blocker in the current one.

## 2. Resolved product decisions

### 2.1 Included

- Party Analysis is a focused analytical page opened from Party.
- Trainer gains a Progress destination rather than a new top-level application mode.
- Save Timeline records validated save-to-save playthrough changes.
- PC Pokémon are exposed as owned specimens inside their Pokédex species entry.
- Area Guide is an Atlas drawer tied to the current or selected area.
- Damage Forecast is embedded beside the current selected move in the existing battle layout.
- Official Pokémon achievement descriptions are a design corpus for portable challenge semantics.

### 2.2 Explicitly excluded

- No standalone read-only Bag page.
- No standalone PC box browser.
- No bag use, item use, Pokémon switching, move selection, navigation, teleportation, or other game commands.
- No save editing, inventory editing, Pokémon editing, or achievement-memory patching.
- No dependence on RetroAchievements availability during ordinary application use.
- No importing or representing DualDex challenge completion as an official RetroAchievements unlock.
- No stock-ROM mechanics, identities, locations, or objectives substituted when a hack cannot resolve them.

Bag state may remain an internal fact when it is needed to prove a challenge such as winning a battle without using items. PC storage may be decoded internally because individual owned Pokémon are valuable within the Pokédex. Neither internal capability justifies a standalone page.

## 3. Existing contracts retained

This specification extends, and does not weaken, these existing contracts:

- `2026-08-25-unified-game-state-single-authority-design.md`: all game-originating live and recovery values enter through one decoder and leave through one immutable resolved snapshot.
- `2026-08-22-save-synchronized-knowledge-checkpoints-design.md`: portable playthrough knowledge is written only after a validated changed save and is keyed to exact ROM and save identity.
- `2026-08-21-pokedex-party-interaction-design.md`: Party remains a 2×3 board and selected-member details use the established focused detail route/dialog.
- `2026-08-21-gen3-local-map-poi-discovery-design.md`: Atlas POIs, Organic discovery, filters, zoom thresholds, and save-scoped knowledge remain authoritative.
- `2026-08-21-cross-page-ui-conformance-design.md`: all new pages use the approved ROM-derived theme, physical font floors, accessibility contract, and ordinary-page diagnostic ban.
- `2026-08-12-organic-rarity-assessments-design.md`: ordinary UI uses concise player-facing language rather than parser or formula provenance.

If an older document conflicts specifically with one of the six features in scope, this specification is authoritative for that feature. It does not supersede unrelated battle, map, parser, theme, loading, or release requirements.

## 4. Reference achievement corpus

### 4.1 Initial source set

The initial design corpus covers the current base achievement sets for the eleven official English Generation I–III releases:

| Generation | Games | Current base achievements |
| --- | --- | ---: |
| I | [Red](https://retroachievements.org/game/724), [Blue](https://retroachievements.org/game/586), [Yellow](https://retroachievements.org/game/723) | 259 |
| II | [Gold](https://retroachievements.org/game/576), [Silver](https://retroachievements.org/game/722), [Crystal](https://retroachievements.org/game/810) | 260 |
| III | [Ruby](https://retroachievements.org/game/790), [Sapphire](https://retroachievements.org/game/791), [Emerald](https://retroachievements.org/game/668), [FireRed](https://retroachievements.org/game/515), [LeafGreen](https://retroachievements.org/game/788) | 484 |
| **Total** | **11 games** | **1,003** |

Counts are extraction evidence, not a permanent application constant. The extraction manifest records its retrieval time and each source game's update value.

The official [`API_GetGameExtended`](https://api-docs.retroachievements.org/v1/get-game-extended.html) response is the preferred input because it exposes achievement ID, title, description, classification, points, display order, author, and modification metadata. Extraction uses a user-supplied API key only in an explicit developer tool invocation. The key must never be committed, copied into a report, logged, sent to the companion, or packaged in an APK.

### 4.2 Extraction boundary

The reference corpus stores only the fields needed for analysis and provenance:

```text
source system
source game ID and title
achievement ID
title
description
official classification when present
display order
author
source modification time
source URL
extraction time
```

It does not download ROMs, badge artwork, user unlocks, leaderboards, user profiles, or raw trigger expressions. It does not scrape logged-in browser state.

The verbatim reference corpus is research input. Before any reference text is distributed in the repository or APK, its redistribution terms must be documented. DualDex challenge titles and descriptions use independently written wording and retain source-level inspiration/provenance in developer documentation rather than presenting copied RetroAchievements text as DualDex-authored content.

### 4.3 Semantic classification

Every reference achievement is classified into one primary semantic family and zero or more constraints:

| Family | Examples of intent |
| --- | --- |
| `PROGRESSION` | earn a badge, defeat a rival, clear a story milestone |
| `COLLECTION` | catch a species, reach a Pokédex total, obtain a key object |
| `EXPLORATION` | enter an area, find its items, defeat its trainers |
| `PARTY` | use a type, species, party size, level range, or composition |
| `BATTLE` | defeat an opponent under defined battle conditions |
| `SESSION` | no reset, one session, no healing or item use |
| `STREAK` | consecutive wins, captures, encounters, or facilities |
| `MINIGAME` | contests, Game Corner, Bug-Catching Contest, fishing, lottery |
| `TIME` | day, night, weekday, elapsed time, or calendar event |
| `COMPLETION` | all trainers, all items, all species, all facility symbols |
| `GAME_SPECIFIC` | mechanics with no safe cross-ROM semantic equivalent |

Constraints include battle mode, maximum level, maximum party size, allowed or forbidden types/species, item-use policy, reset policy, area boundary, ordering, time window, required count, and required sequence.

Classification is fail-closed. An ambiguous description remains `UNCLASSIFIED`; it is not converted into a challenge rule by guessing.

## 5. Shared semantic architecture

### 5.1 Data flow

```text
ROM parser and persisted catalog
        +
UnifiedGameStateDecoder -> ResolvedGameSnapshot
        |
        v
SemanticFactProjector -> current SemanticFactSet
        |
        +--> Party Analysis
        +--> Pokédex Specimens
        +--> Area Guide
        +--> Damage Forecast
        |
        v
SnapshotTransitionEvaluator -> validated GameEvents
        |
        v
ROM-save-scoped PlaythroughJournal
        |
        +--> Progress metrics
        +--> Challenge evaluation
        +--> Save Timeline checkpoint projection
```

No feature may read RetroArch memory, parse SaveRAM, inspect raw event flags, or choose between live and recovery independently. Features consume resolved snapshot fields, parsed catalog facts, or the journal derived exclusively from consecutive resolved snapshots.

### 5.2 Semantic facts

A semantic fact has a stable key, typed value, availability, and knowledge visibility. Examples include:

```text
trainer.badge.<canonical-badge-id>.earned
pokedex.species.<canonical-species-id>.seen
pokedex.species.<canonical-species-id>.caught
party.slot.<index>.individual
storage.box.<index>.slot.<index>.individual
location.area.<canonical-area-id>.visited
poi.<stable-poi-key>.discovered
poi.<stable-poi-key>.collected
battle.active
battle.kind
battle.opponent.<index>.trainer-or-species-id
battle.player.move.selected
battle.item-used
clock.period
```

Raw addresses, offsets, flag numbers, pointer values, parser family labels, and ROM filenames are never semantic fact keys exposed to ordinary UI.

### 5.3 Events and temporal state

`SnapshotTransitionEvaluator` compares validated snapshots and emits typed events only after the existing stability policy accepts the underlying change. Required event families include:

- session started, disconnected, reconnected, reset, and save changed;
- species seen, caught, evolved, joined Party, left Party, entered storage, and left storage;
- area entered and POI discovered, identified, or collected;
- battle started, target selected, move used, item used, Pokémon fainted, battle won, battle lost, and battle ended unresolved;
- badge or story milestone earned;
- metric counter advanced.

An invalid or unavailable intermediate snapshot emits no positive event. Retractions correct current state but do not erase a previously proven historical event unless the entire playthrough identity changes.

### 5.4 Challenge definition

A DualDex challenge is data, not page-specific code:

```text
stable challenge key
independently written title and description
semantic family
applicability predicate
completion predicate
optional progress measure and target
optional start, reset, pause, miss, and session predicates
knowledge visibility
required fact and event capabilities
source-inspiration references
```

The engine supports boolean composition, counters, ordered sequences, value comparisons, bounded sets, session epochs, battle epochs, area epochs, and previous-value comparisons. It does not expose a general script interpreter to downloaded content in the first implementation.

### 5.5 Applicability and failure

A challenge is offered only when:

1. every referenced catalog entity resolves for the active ROM;
2. every required live or recovery field has a proven semantic mapping;
3. every temporal event can be observed at the available sampling cadence; and
4. Organic-mode presentation will not reveal prohibited undiscovered information.

If any condition fails, the challenge is absent. Ordinary pages do not show `unsupported`, `unmapped`, raw capability codes, or parser explanations. Debug Settings and compatibility reports may state the exact missing capability.

## 6. Feature A — Party Analysis

### 6.1 Entry and navigation

- Party retains its six-slot 2×3 board.
- A single clearly recognizable `Analysis` action appears in the Party header/action region.
- The action opens a dedicated `PARTY ANALYSIS` route.
- Back returns to the exact Party roster and selected scroll state.
- Navigating from Analysis into a member, move, nature, ability, or species detail pushes onto the real navigation stack; Back unwinds one destination at a time.

### 6.2 Required presentation

Party Analysis contains four ordered sections:

1. **Team summary** — party size, current level span, fainted/status count, and physical/special/status move distribution when the ROM exposes move categories.
2. **Offensive coverage** — which defending types at least one currently known damaging move can hit super-effectively, which are neutral-only, and which have no effective known option.
3. **Defensive profile** — per-member weaknesses, resistances, immunities, and repeated team weaknesses using current types plus only proven ability effects.
4. **Development** — current evolution opportunities, nearby learned moves, and factual move-role gaps such as no damaging move of a resolved category.

The page uses matrices, compact type chips, and member portraits where they make comparisons easier. It does not assign subjective labels such as `bad team`, recommend a specific replacement Pokémon, or pretend an unknown mechanic is neutral.

### 6.3 Calculation rules

- Use current owned individuals and their actual live moves, levels, status, and abilities.
- Use the active ROM's parsed type chart, moves, evolutions, and mechanics.
- Ability effects participate only when their battle semantics are proven for that ROM.
- Unknown move power/category withholds only calculations requiring that field.
- Duplicate move types are counted once for coverage but may be counted in distribution summaries.
- Fainted Pokémon remain part of structural analysis and are separately marked as unavailable for immediate battle.
- Empty Party slots never create warnings or artificial weaknesses.

### 6.4 Organic policy

Owned Party members and their live moves are player knowledge and may be analyzed fully. The analysis may link to complete owned-member details. It may not use undiscovered opponent, encounter, hidden-item, or future-story information.

### 6.5 Acceptance gate

- Exact 2×3 Party layout and current member-detail behaviour remain unchanged.
- Analysis results are deterministic for the same catalog and snapshot.
- Type-chart mutations in a supported hack change the analysis without code or ROM-name selection.
- Removing a required ability mechanic removes only the affected modifier.
- Empty, partial, fainted, single-member, and six-member parties render without invented values.
- Browser tests prove the approved theme, font floor, 4:3 fit, keyboard navigation, and real Back-stack behaviour.

## 7. Feature B — Trainer Progress, Challenges, and Save Timeline

### 7.1 Entry and information architecture

- Trainer gains two normal destinations: `CARD` and `PROGRESS`.
- `CARD` remains the established Trainer Card.
- `PROGRESS` opens one page with three internal sections: `METRICS`, `CHALLENGES`, and `TIMELINE`.
- The Progress destination becomes available under the same player-initialization/license policy as Trainer Card; restrictive additional unlock conditions are forbidden.
- The last selected Trainer destination and Progress section are remembered for the active ROM-save identity.

### 7.2 Metrics

Metrics distinguish two truthful scopes:

- **Game totals:** values reconstructible from current live state or validated save state, such as play time, badges, seen, caught, money, and current completion totals.
- **Tracked journey:** event counts observed by DualDex for this ROM-save identity, such as battles, wild encounters, trainer battles, captures, evolutions, area visits, POI discoveries, hidden-item discoveries, Party changes, and saves observed.

A tracked metric begins when a matching journal is first established. It is never presented as a lifetime game total when earlier history cannot be reconstructed.

Metrics update from validated semantic events. Polling duplicates, reconnects, cache reloads, transient candidate expansion, and live-to-recovery authority changes must not increment them.

### 7.3 Challenges

- Challenges are grouped by Progress, Collection, Exploration, Battle, Party, and Special.
- Each card shows title, concise description, completion state, and measurable progress when a target exists.
- Completed challenges retain their first proven completion save/time reference.
- A challenge that becomes inapplicable after a catalog update is retained in the journal for integrity but hidden from ordinary UI until it maps again.
- Missable status appears only when the engine can prove both the requirement and the passed point of no return.
- Challenge points may be used for local sorting but must not imply RetroAchievements points or account credit.
- The ordinary product name is `CHALLENGES`; RetroAchievements attribution belongs in About/documentation, not every card.

### 7.4 Save Timeline

Save Timeline is generated from the existing validated `CHANGED` save observation:

1. Freeze the accepted current journal and resolved save projection.
2. Compare it with the latest accepted timeline entry for the same ROM-save identity.
3. Produce at most one entry for that save payload.
4. Store only meaningful user-facing differences and the exact save fingerprint metadata already required by the checkpoint envelope.
5. Atomically replace the sidecar or isolated app-private fallback using the existing checkpoint write path.

An entry may contain:

- in-game play time and current location;
- money and badges;
- seen/caught changes;
- Party membership and evolution changes;
- challenge completions;
- map and POI discovery changes;
- tracked metric deltas.

`INITIAL`, `UNCHANGED`, malformed, identity-mismatched, or recovery-only observations create no entry. Live polling changes are not written before the matching game save changes. The save file itself is never modified.

Timeline retention is bounded to 512 entries per playthrough. The first accepted entry and entries containing badge, evolution, major story, or challenge-completion milestones are preserved preferentially. When compaction is required, the oldest non-milestone entries are removed first. Compaction is deterministic and atomic.

### 7.5 Organic policy

- Completed and currently visible challenges may display their full player-facing text.
- A future challenge that names an undiscovered species, location, trainer, item, or story event is hidden or shown as a generic hidden challenge according to its definition.
- The Timeline records only knowledge already available at the corresponding save.
- Changing to Discovered mode may reveal eligible challenge definitions but does not retroactively fabricate completion.

### 7.6 Acceptance gate

- Trainer Card and Pokédex totals agree because both use the same resolved snapshot.
- One semantic gameplay transition produces one event despite repeated polling samples.
- `48/48 -> 1/1 -> 1/2`-style unstable candidate transitions produce no false captures or metrics.
- A save update writes one checkpoint/timeline entry; reopening the APK without a changed save writes none.
- Switching ROM or save identity exposes no prior journal, timeline, challenge, or metric state.
- An APK update retains the exact matching sidecar and rejects mismatched or stale data.
- No ordinary Progress copy exposes addresses, flag IDs, parser stages, cache provenance, or internal capability labels.

## 8. Feature C — Pokédex Specimens

### 8.1 Purpose and entry

The Pokédex becomes the useful read-only view of PC ownership. There is no standalone storage page.

- A caught species with at least one resolved owned individual gains a `SPECIMENS` section inside its existing detail flow.
- The section is reached from `MORE` or an equivalent detail action that does not overcrowd the five established tabs.
- The section lists Party and PC instances of that canonical species.
- Selecting a specimen opens the same individual-detail component and linked nature, ability, move, and species destinations used by Party.
- Back returns to the same species specimen list and scroll position.

### 8.2 Owned specimen model

The unified snapshot expands with one resolved storage field rather than adding a feature-specific save reader:

```kotlin
data class ResolvedOwnedStorageState(
    val party: List<OwnedIndividual>,
    val boxes: List<ResolvedStorageBox>,
)
```

Each specimen may expose only validated fields:

- sprite, species, nickname, level, gender, and status;
- current/max HP when meaningful for the decoded representation;
- experience progress;
- nature, ability, held item, and moves;
- IV/DV values and the existing rarity-star assessment;
- `PARTY` or a player-facing box/slot location.

The stable specimen key prefers the game's individual identity field where one exists. Where a generation lacks a persistent unique value, the key is scoped to ROM-save identity plus storage location and validated record digest. It is not treated as globally stable after movement.

### 8.3 Authority and refresh

- Live PC memory is preferred whenever structurally validated.
- Exact SaveRAM recovery fills storage only while live storage is unavailable.
- A validated empty live box clears recovered specimens.
- Live Party and storage cannot duplicate the same individual in effective presentation.
- Moving an individual between Party and PC updates its location without changing its identity when the generation exposes a stable identity.
- A caught Pokédex flag without a decodable owned record retains the affirmative caught badge but invents no specimen card.

### 8.4 Organic policy

Owned Pokémon are player knowledge. Their decoded details may be shown fully. Specimens never reveal uncaught catalog Pokémon, hidden encounter locations, opponent moves, or future evolution identities that the existing Pokédex policy withholds.

### 8.5 Acceptance gate

- One canonical species entry enumerates all and only matching Party/PC instances.
- Forms and aliases do not multiply one physical specimen.
- Party-to-PC and PC-to-Party transitions neither duplicate nor lose the individual.
- Empty storage, partial boxes, corrupt records, and unsupported storage layouts leave the Pokédex usable.
- Gen I/II/III record checksum/encryption rules are validated against real official ROM/save or memory controls before hack claims.
- Modern Emerald, Unbound, and Odyssey use source-backed real controls where their exact storage layouts are available.

## 9. Feature D — Atlas Area Guide

### 9.1 Entry and relationship to the map

- Atlas gains one `Area Guide` action consistent with the existing map controls.
- It opens a drawer over the current map rather than navigating away.
- The guide follows the current player area while tracking is active.
- If the user manually selects an Atlas node or local area, the guide describes that selected area until tracking is resumed.
- Closing the drawer preserves map center, zoom, tracking, POI filters, and fog state.

### 9.2 Guide sections

The drawer contains only sections supported by the current catalog and knowledge projection:

1. **Overview** — area name, discovery/completion counts, and connected exits.
2. **Encounters** — available species, time windows, level ranges, and rates permitted by the current knowledge mode.
3. **Places and services** — discovered buildings, Centers, Marts, Gyms, specialist services, and resolved destinations.
4. **Trainers and people** — discovered NPC/trainer entries and completion when validated.
5. **Items** — visible, proximity-discovered hidden, and collected items according to existing POI policy.
6. **Objectives** — applicable local challenges or quest/story cues only when semantically resolved and knowledge-safe.

The drawer reuses the existing POI categories and visibility preferences. It does not introduce a second, contradictory filter store.

### 9.3 Map interaction and performance

- Existing map markers still obey their configured icon and label zoom thresholds.
- Opening the drawer does not force POIs to render below those thresholds.
- The guide may summarize known off-viewport POIs because it is a list, but selecting an entry may center/highlight it only when that POI is already knowledge-visible.
- Fogged raster regions and undiscovered POIs remain absent in Organic mode.
- Hidden map layers and off-viewport markers remain unmounted according to the existing rendering contract.
- The drawer virtualizes long lists and does not trigger a new ROM parse or duplicate map-raster buffer.

### 9.4 Content quality

- Signs use decoded sign text reduced to the first meaningful line after control-code expansion and player-token normalization such as `{PLAYER}` to `Your` when live name substitution is unavailable.
- Buildings use decoded names or service roles; generic labels such as `Place` are forbidden.
- Area name appears once in the established header; the drawer does not overlay a duplicate blue area title on the map.
- Label collision handling and translucent label backing retain the accepted map presentation.

### 9.5 Acceptance gate

- Organic and Discovered projections match the established POI rules exactly.
- Hidden items remain absent until the player enters the item tile or one of its eight neighboring tiles.
- A selected area never causes the UI to report two current locations.
- Drawer operations preserve current zoom and tracking state.
- Official Emerald and FRLG plus source-backed Modern Emerald, Unbound, and Odyssey controls prove names, exits, encounters, POIs, and filtering where applicable.
- Gen I/II adapters reuse the same guide contract while omitting facts their formats cannot prove.

## 10. Feature E — Selected-move Damage Forecast

### 10.1 Placement

- Damage Forecast appears in the existing battle Attack destination adjacent to the currently selected player move.
- It updates when the selected move, player battler, target, battle state, or relevant modifier changes.
- Double battles use the already resolved command owner and selected target. Manual target fallback recalculates against the manually selected opponent.
- No standalone calculator page is required for the first implementation.

### 10.2 Player-facing result

When exact calculation is possible, show:

- damage range in HP;
- range as a percentage of current target HP;
- expected hit range to knock out;
- move accuracy;
- type effectiveness; and
- concise applied conditions such as weather, STAB, item, ability, critical state, or field modifier when those conditions are currently active and player-visible.

The result uses the approved theme and battle composition. It must not show implementation labels such as `compiled source behavior`, `THUMB resolved`, pointer provenance, mechanic IDs, or missing-capability codes.

### 10.3 Calculation authority

The calculator receives one immutable input assembled from:

- generation-specific damage formula and random range;
- live attacker level, stats, types, status, ability, held item, and selected move;
- live target HP, stats, types, status, ability, held item, and field position where knowledge permits;
- parsed move power, category, type, accuracy, hit count, and special rules;
- parsed type chart;
- proven weather, field, ability, item, and mechanic modifiers.

Generation, ROM name, SHA, filename, or presumed ancestry cannot select stock mechanics. Mechanics are admitted by parsed/decoded semantic evidence.

### 10.4 Exact, bounded, and absent results

- **Exact range:** every modifier that can affect the result is known and its semantics are proven.
- **Bounded range:** unknown state has a finite proven set of possible modifiers and the displayed minimum/maximum encloses every permitted outcome. The UI describes the uncertainty in player language.
- **Absent:** an unknown mechanic, stat, target, formula, or multi-hit behaviour could produce an unbounded or misleading result.

An absent forecast leaves the existing move metadata and effectiveness UI intact. It does not replace the panel with a technical error.

### 10.5 Organic policy

- The player's selected move and owned attacker are fully known.
- Target fields obey current Organic battle knowledge. An unobserved opponent move or hidden ability cannot be revealed by the forecast explanation.
- If a hidden target ability could change damage, use a proven bounded range only when doing so does not identify the ability; otherwise omit the forecast.
- Discovered mode may use all ROM-resolved target facts but still requires proven live state and mechanic semantics.

### 10.6 Acceptance gate

- Golden calculations cover the official Gen I, II, and III formula families with real ROM-derived move/type inputs.
- Physical/special rules, STAB, type effectiveness, random factor, status, critical, weather, multi-hit, fixed-damage, immunity, ability, and item paths are independently tested where applicable.
- Source-backed Modern Emerald, Unbound, and Odyssey controls prove that altered mechanics do not silently receive stock results.
- Double-battle command ownership and target changes recompute the correct forecast.
- Polling refresh does not flicker between stale attackers or targets.
- Performance logging shows no new memory read loop and no sustained render loop after the battle state stabilizes.

## 11. Feature F — Portable Challenge Engine Expansion

### 11.1 Initial portable vocabulary

The first engine implementation must support these reusable predicates before game-specific objectives are attempted:

- fact is true/false;
- value equals, differs, exceeds, or falls below a typed threshold;
- set contains an entity or reaches a count;
- event occurs once or reaches a count;
- events occur in order;
- condition holds at battle start, throughout battle, or at battle completion;
- no forbidden event occurs within a battle, area, or session epoch;
- all resolved entities in a bounded catalog group are completed;
- progress is measured as `current / target`;
- reset, pause, miss, and completion predicates are independent.

This vocabulary is deliberately smaller than the full RetroAchievements expression language. New operators require a concrete classified achievement that cannot be expressed safely with the existing vocabulary.

### 11.2 Portability tiers

| Tier | Meaning | Product behaviour |
| --- | --- | --- |
| 1 | Uses universal normalized facts already proven for the ROM | Generate and show automatically |
| 2 | Requires a structurally parsed ROM entity or event mapping | Show only after that mapping validates |
| 3 | Requires a game-specific mechanic adapter | Show only for ROMs proving that adapter |
| 4 | Depends on glitches, external trades, unavailable frame-level transitions, or ambiguous prose | Retain as research inspiration; do not generate |

The goal is broad semantic coverage, not a promise that all 1,003 reference descriptions become active challenges for every ROM.

### 11.3 Dynamic instantiation for hacks

Challenge templates bind to catalog roles rather than retail identities. Examples:

- `Earn {badge.name}` binds to the hack's resolved badge progression.
- `Defeat {gymLeader.name} without using items` binds only when the leader, battle outcome, and item-use events resolve.
- `Discover every item in {area.name}` binds to the active area's proven collectible group.
- `Register {target} species` binds to the hack's resolved regional Pokédex target.

Templates may use player-facing entity names after catalog resolution. Selection logic may not use ROM filenames, project names, SHA lists, fixed retail offsets, or guessed ancestry.

### 11.4 Non-goals

- No arbitrary downloaded rule scripts.
- No remote execution or runtime API requirement.
- No compatibility claim based solely on a matching achievement title.
- No challenge that requires a sampling rate DualDex cannot observe reliably.
- No automatic publication of challenge packs to RetroAchievements.

### 11.5 Acceptance gate

- Every generated challenge can print its required semantic capabilities in Debug Settings or an offline report.
- Removing one capability removes only dependent challenges.
- The eleven official controls produce a deterministic challenge inventory and result set.
- At least Modern Emerald, Unbound, and Odyssey prove dynamic binding without ROM-name selectors for the semantic families their sources expose.
- Mutation controls reject changed flag roles, reordered identifiers, unsupported temporal windows, and ambiguous entities.
- Ordinary UI contains independent DualDex wording and never claims RetroAchievements credit.

## 12. Shared navigation and visual contract

The new destinations extend the existing navigation stack:

```text
Party -> Party Analysis -> Member/Move/Nature/Ability/Species detail
Trainer -> Progress -> Metric/Challenge/Timeline detail
Pokédex -> Species -> Specimens -> Individual detail
Atlas -> Area Guide drawer -> knowledge-visible POI detail
Battle Attack -> selected-move forecast details
```

Back always returns to the immediate prior destination with its selected tab, item, scroll position, zoom, and drawer state. No child detail route may skip its parent, reproduce the earlier Party-to-Ability navigation defect, or reset Atlas tracking.

All new UI follows these rules:

- no debug or diagnostic information outside Settings → Debug;
- no redundant subtitles explaining obvious page names;
- current ROM-derived theme tokens on every surface;
- semantic colors only for established HP, EXP, status, type, rarity, map, and error meanings;
- minimum text size and average text-size gates from the approved 1024×768 AYN Thor audit;
- real buttons, keyboard focus, accessible names, non-color-only status cues, and screen-reader progress labels;
- no document-level horizontal or vertical overflow at the production 4:3 viewport.

## 13. Persistence and identity

One `PlaythroughJournal` belongs to exactly:

```text
ROM SHA-256 + stable save identity
```

It contains companion-owned historical facts only:

- semantic events and tracked counters;
- challenge progress/completions;
- Save Timeline entries;
- Organic discoveries already authorized by their existing ledgers;
- per-feature navigation/preferences explicitly designated as ROM-save-scoped.

It does not mirror current Trainer, Party, PC, Pokédex, money, clock, location, bag, or battle state as an alternate authority. Current values always come from the resolved snapshot. Timeline entries may freeze those values only as historical save evidence.

Checkpoint decoding sanitizes every entity reference against the active catalog. A schema migration that cannot preserve identity rejects the affected historical section without contaminating current live state. Legacy unfingerprinted ledgers are not imported automatically.

## 14. Performance and memory contract

- No feature adds an independent RetroArch poller.
- No feature reads the entire ROM, SaveRAM, or emulated memory into a second buffer.
- Static analysis is cached by catalog identity and invalidated only when its required catalog sections change.
- Current Party, storage, battle, and location projections are replacement values rather than append-only copies.
- Timeline writes reuse the save bytes and fingerprint already obtained by the save monitor.
- Area Guide lists are virtualized when necessary; hidden/off-viewport map markers remain unmounted.
- Damage Forecast recalculates only when a relevant immutable input changes.
- Challenge evaluation is incremental over semantic events; it does not rescan the entire journal every poll.
- Existing load-stage and minute runtime profiling records feature CPU time, heap delta, retained journal size, event rate, and forecast/analysis recomputation count in Debug logs only.

Any stage that materially increases sustained heap, full-memory copying, polling frequency, or UI churn is blocked until the regression is explained and corrected.

## 15. Compatibility and validation corpus

### 15.1 Required controls

Each feature plan identifies which fields are applicable, then validates them against:

- official English Red, Blue, Yellow;
- official English Gold, Silver, Crystal;
- official English Ruby, Sapphire, Emerald, FireRed, LeafGreen;
- source-backed Modern Emerald;
- source-backed Pokémon Unbound; and
- source-backed Pokémon Odyssey.

Real ROM/save/memory/source tuples are primary evidence. Synthetic fixtures are permitted only for malformed, ambiguous, boundary, or mutation cases after the real structure is established.

### 15.2 Percentage reporting

Compatibility reports use numeric percentages per applicable feature and semantic family. `NOT FOUND`, `NOT APPLICABLE`, and `ERROR` remain distinct evidence states and are never collapsed into vague labels. A selected catalog is not equivalent to feature support.

For challenges, report at minimum:

```text
reference descriptions classified / total
templates expressible / classified
templates applicable / total templates for each ROM
templates fully observable / applicable
templates validated / fully observable
```

For Party Analysis, Specimens, Area Guide, and Damage Forecast, report the percentage of required resolved fields and calculations independently.

## 16. Feature-by-feature delivery order

### Stage 0 — Reference corpus and semantic vocabulary

Deliver the authenticated developer extractor, immutable extraction manifest, classification report, independently worded template schema, and no APK/UI changes. This stage is evidence preparation and does not publish a release.

### Stage 1 — Party Analysis

Deliver the Party shortcut, analytical route, calculations, navigation, UI audit, and compatibility report. Do not bundle Progress, PC storage, Atlas, or damage code into this stage.

### Stage 2 — Atlas Area Guide

Deliver the drawer by composing existing encounter, POI, and map projections. Preserve all established local/world map rendering, fog, tracking, zoom, and filter contracts. The Objectives section remains absent until Stage 3 supplies knowledge-safe applicable challenges; its later population is a tracked Stage 3 deferral rather than a reason to delay the otherwise complete guide.

### Stage 3 — Progress foundation and Save Timeline

Deliver semantic facts/events, journal persistence, Trainer Progress sections, baseline portable challenges, metrics, save-synchronized Timeline, and the knowledge-safe Objectives projection consumed by the Stage 2 Area Guide. This stage may use the Stage 0 corpus but must remain functional without network access.

### Stage 4 — Pokédex Specimens

Deliver unified storage resolution, Pokédex specimen enumeration, shared individual detail navigation, and live/recovery authority tests. Do not add a standalone PC or Bag page.

### Stage 5 — Selected-move Damage Forecast

Deliver generation formulas and capability-gated modifiers into the existing Attack page. THUMB or mechanic gaps may withhold specific forecasts but must be quantified; they may not silently substitute retail behaviour.

### Stage 6 — Challenge coverage expansion

Use the classified reference corpus and evidence from the official/hack controls to add Tier 2 and Tier 3 templates incrementally. Tier 4 references remain documented research exclusions.

## 17. Stage audit protocol

After every stage, create one audit table with:

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | --- | --- |

`Classification` is exactly one of:

- `SATISFIED` — the requirement and its evidence are complete;
- `BLOCKER` — required for the current feature and must be fixed before proceeding;
- `DEFERRED` — explicitly outside the current stage and assigned to a named later stage;
- `NOT_APPLICABLE` — the requirement genuinely does not apply to that ROM/feature combination;
- `NOT_FOUND` — required evidence was searched for but not resolved;
- `ERROR` — evaluation failed and must not be represented as missing or inapplicable.

Words such as `good`, `partial`, `green`, `supported`, or `mostly` are not compatibility measurements. Every stage report includes numeric applicable-field or rule coverage.

A stage may proceed with documented `DEFERRED` items only when the current feature's user contract is complete without them. It may not proceed with a `BLOCKER` or `ERROR`. A later stage cannot redefine an unresolved current requirement as optional merely to advance.

## 18. Completion definition

The suite is complete when:

- each of the six features has passed its independent acceptance gate;
- ordinary UI contains no diagnostic/provenance leakage;
- one resolved snapshot remains the only current game-state authority;
- one ROM-save-scoped journal owns historical progress without mirroring current state;
- APK updates preserve only exact matching playthrough history;
- Organic mode reveals no undiscovered entity through analysis, challenges, specimens, guide, timeline, or forecast;
- official and source-backed hack reports quantify applicable coverage per feature;
- performance profiling proves no duplicate pollers, full-memory copies, persistent render loops, or unbounded journal growth; and
- the final release audit links each requirement to code, tests, real-data evidence, documentation, signed artifact, and published compatibility percentages.
