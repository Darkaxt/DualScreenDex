# Stage 3 audit — Trainer Progress, Challenges, Save Timeline, and Atlas Objectives

Date: 2026-08-27  
Specification: `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`  
Numeric evidence: `docs/reports/passive-insights-progress/progress-timeline-compatibility.json`

## Result

Stage 3 has **0 blockers** and **0 errors**. Trainer now has `CARD` and `PROGRESS` destinations; Progress provides current Game totals, ROM-save-scoped Tracked journey metrics, six offline Tier 1 Challenges, and a save-synchronized Timeline. The same applicable Organic-safe Exploration challenges populate Atlas Objectives. One `ResolvedGameSnapshot` remains the current-state authority, while the journal owns historical facts only.

The exact 14-control evidence reports:

| Measurement | Covered | Total | Percent |
| --- | ---: | ---: | ---: |
| Current total fields | 40 | 70 | 57.14% |
| Observable event families | 108 | 126 | 85.71% |
| Baseline applicable templates | 66 | 84 | 78.57% |
| Fully observable / applicable templates | 66 | 66 | 100.00% |
| Validated / fully observable templates | 66 | 66 | 100.00% |
| Reference descriptions classified | 883 | 1,003 | 88.04% |
| Classified descriptions expressible | 883 | 883 | 100.00% |

All eight Gen III controls expose 5/5 current total fields, 9/9 event families, and 6/6 baseline templates. The six official Gen I/II controls expose 0/5 current total fields through the currently proven live path, 6/9 event families, and 3/6 baseline templates. Those 30 current fields and 18 capture/evolution/Party event slots remain numeric `NOT_FOUND` evidence; the report does not credit unvalidated recovery data or replace it with a stock layout.

## Specification cross-check

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | --- | --- |
| Section 3: one current-state authority and ordinary-page diagnostic ban | `ResolvedSemanticFactProjector` translates the accepted unified snapshot; current Trainer/Pokédex/Party/location/battle values are not copied into journal authority. Progress views expose only player-facing values and text. | Projection equality tests prove live/recovery source changes do not change accepted facts; UI tests reject parser/capability copy. | Existing 14-control unified-state matrix is the sole live-field input to this report. | No second poller, reader, current-state cache, provenance label, address, or parser stage | SATISFIED |
| Section 5: stable semantic facts and deduplicated transitions | Immutable facts use ROM-save identity, species personality when available, accepted caught/Party/area/POI/battle/save state, and deterministic transition order. | `SnapshotTransitionEvaluatorTest` covers captures, evolutions, areas, POIs, battle start/end, Party, save changes, repeats, reconnects, unavailable candidates, authority changes, and identity switches. | Every event family is admitted only when its required exact-control live/catalog capability exists. | One accepted transition produces one event; rejected candidates produce none | SATISFIED |
| Section 7.1: Trainer destinations, license policy, and remembered selection | `TrainerPage` composes the established Card and new Progress destination; section/destination preferences live in the exact playthrough journal, with an ephemeral pre-identity selection only until the real identity appears. | Browser tests cover CARD/PROGRESS and METRICS/CHALLENGES/TIMELINE actions; runtime/projector tests cover defaults and persisted selection. | The destination is available under the same Trainer Card first-owned-Pokémon policy for every control. | No additional restrictive unlock | SATISFIED |
| Section 7.2: truthful Game totals and Tracked journey scopes | `TrainerProgressProjector` separates five resolved current values from battles, wild/trainer battles, captures, evolutions, areas, POIs, Party changes, saves, and challenge completions observed by DualDex. | Projector and journal tests verify labels, values, deduplication, and user-facing Timeline deltas. | 40/70 current fields and 108/126 event families are independently measured. | Missing current fields render unavailable; tracked counts never claim lifetime totals | SATISFIED |
| Section 7.3: baseline Challenges | `PortableChallengeCatalog` loads six offline, independently worded, Organic-safe Tier 1 templates; `ChallengeEngine` retains prior state, first completion time/save, and hides inapplicable definitions. | Predicate, applicability, Organic, incremental dependency, first-completion, save-reference, and UI grouping/progress tests. | 66/84 template slots apply; 66/66 applicable templates are fully observable and validated. | Baseline is offline, capability-gated, and does not claim RetroAchievements credit | SATISFIED |
| Section 7.4 and 13: exact save-synchronized Timeline and identity | Checkpoint schema 2 embeds one sanitized journal under ROM SHA plus save identity and existing save-file envelope; only `CHANGED` observations freeze pending deltas and atomically write. | Identity mismatch, schema migration, malformed input, initial/unchanged no-write, restart, atomic store, duplicate fingerprint, same-save challenge delta, and 512-entry compaction tests. | Save observations use the generation-neutral validated monitor/fingerprint path; no ROM-specific offset or identity branch was added. | APK reopen/update restores only an exact checkpoint; game saves remain untouched | SATISFIED |
| Section 7.5: Organic visibility | Ordinary Challenges come only from definitions marked Organic-safe whose semantic capabilities exist; Objectives receive only incomplete visible Exploration results. | Engine and Area Guide tests prove unsafe/inapplicable definitions and empty objective sections remain absent. | POI capability is credited only from materialized exact-control POI evidence. | No undiscovered species, location, trainer, item, or story identity is introduced | SATISFIED |
| Section 7.6: acceptance regressions | Trainer/Pokédex totals derive from the same snapshot; baseline switching resets without events; changed save freezes at most once; current and history authority remain separate. | Focused progress, recovery, runtime, and web suites plus the full affected suites. | Exact required controls are keyed by SHA and carry no private paths or save/player values. | All current Stage 3 acceptance clauses have direct evidence | SATISFIED |
| Sections 11.1–11.2: bounded portable vocabulary and tiers | Runtime supports boolean/logical predicates, typed comparisons, counts, sets, ordered events, bounded epochs, and previous/current comparisons. Stage 3 instantiates only count-based Tier 1 templates. | `ChallengeEngineTest` exercises the bounded operators and rejects missing capabilities. Reporter validates every shipped template shape. | Reference corpus: 883/1,003 classified; Tier 1 baseline applies dynamically without filenames, names, hashes, or stock offsets. | Tier 1 baseline complete; structural/game-specific packs remain outside this stage | SATISFIED |
| Section 11 expansion: scoped game-specific temporal bindings and explicit reset/pause/miss lifecycle | Research vocabulary retains these requirements, but no shipped Tier 1 template depends on them. | Stage 0 classification and current engine tests preserve the boundary. | No exact-control evidence is claimed for unimplemented adapters. | Assigned to Stage 6 with concrete evidence gates | DEFERRED |
| Section 12: navigation, theme, accessibility, and 4:3 presentation | Progress is an internal Trainer destination, so Back returns to the immediate prior app route; tabs preserve selection; theme tokens, bounded scroll regions, labelled buttons, and non-color completion copy are used. | Trainer Progress, Trainer Card, Area Guide, production navigation, and layout suites. | Same browser contract applies independently of ROM identity. | No parent-skipping route, debug subtitle, or document overflow | SATISFIED |
| Section 14: no poller/buffer and bounded profiling | Evaluation runs only on unified-state publications; challenge dependencies are incremental; journal Timeline is capped at 512. Existing minute/load profiling now reports semantic evaluations/CPU, events, challenge evaluations/CPU, Timeline entries, retained journal items, and process heap/CPU metrics. | Performance component and production runtime counter tests; journal compaction and repeated-poll tests. | Exact controls reuse the existing unified reader and save monitor; the reporter reads committed sanitized evidence only. | No ROM/save/memory buffer, timer, polling loop, or normal-page metric was added | SATISFIED |
| Sections 15 and 17: required corpus, numeric report, and audit protocol | Reporter validates exact identities across live, Area Guide, baseline, and Stage 0 classification artifacts and emits separate field/template denominators and absence counts. | Reporter tests fail closed on missing controls, identity mismatch, and malformed templates. | 11 official + Modern Emerald + Unbound + Odyssey: 14/14 exact controls, 0 report errors. | Numeric evidence and classifications remain distinct | SATISFIED |

## Explicit non-coverage and tracked deferrals

- Official Gen I/II current Progress totals: **0/30 (0.00%) — 30 fields NOT_FOUND in the currently proven live path**. Validated save recovery may populate an unavailable live field during a real playthrough, but this report does not count recovery without a matching real save control.
- Official Gen I/II capture, evolution, and Party-change event slots: **0/18 (0.00%) — 18 event slots NOT_FOUND**. Area, POI, battle, wild/trainer encounter, and changed-save observation families remain observable.
- Tier 2 structurally bound and Tier 3 game-specific challenge packs, including their explicit reset/pause/miss and scoped temporal adapters, remain **DEFERRED to Stage 6**.
- Tier 4 glitch/trade/unavailable-frame/ambiguous references remain research exclusions and are not generated.

None of these evidence states enables a fallback live layout, fabricated game total, hidden objective, or stock challenge binding.

## Verification commands

```text
node --test tools/reports/progress-timeline-compatibility.test.mjs
./gradlew :parser-core:test :parser-cli:test :catalog-store:test :save-core:test :battle-memory:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain
npm test -- --run
npm run build
```

Observed results: report transformers 7/7; affected JVM/Android suites 1,798 tests with 0 failures and 0 errors; browser suite 212/212 across 29 files; production TypeScript/Vite build successful.
