# Stage 6 audit — Portable Challenge Engine Expansion

Date: 2026-08-27

Specification: `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`
Numeric evidence: `docs/reports/passive-insights-progress/challenge-expansion-compatibility.json`

## Result

Stage 6 has **0 implementation errors and 0 unresolved feature blockers**. Six independently worded role-bound templates extend the six Tier 1 templates. Runtime binding consumes parsed badge, regional Pokédex, and area-collectible roles; unsupported Gym Leader and game-specific mechanic roles generate no challenge. Challenge evaluation remains attached to the unified resolved-snapshot publication path and the exact ROM-save journal.

Organic presentation now retains completed tiers and only the first unfinished tier in each ranked chain. Untouched entity-scoped objectives remain absent away from their scope, while current, started, and completed objectives remain visible. Each visible bounded objective receives a percentage, and the page receives one `completed / applicable` percentage whose denominator includes only knowledge-safe applicable challenges. Discovered mode retains the complete applicable inventory.

The required five numeric measures are:

| Measurement | Covered | Total | Percent | Not applicable | Not found | Error |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Reference semantic classification | 1,003 | 1,003 | 100.00% | 0 | 0 | 0 |
| Classified descriptions expressible | 1,003 | 1,003 | 100.00% | 0 | 0 | 0 |
| Templates applicable per control, aggregate | 110 | 168 | 65.48% | 18 | 40 | 0 |
| Templates fully observable / applicable | 104 | 110 | 94.55% | 0 | 6 | 0 |
| Templates validated / fully observable | 104 | 104 | 100.00% | 0 | 0 | 0 |

Each official Gen I/II control has 5/12 applicable templates (41.67%), 4/5 fully observable (80.00%), and 4/4 validated (100.00%). Each official Gen III and source-backed hack control has 10/12 applicable (83.33%), 10/10 fully observable (100.00%), and 10/10 validated (100.00%). Structural expansion produces 1,093 deterministic concrete definitions across the 14 exact controls because each validated area receives its own stable collectible objective.

## Specification cross-check

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | --- | --- |
| Section 3: one current-state authority | `ProductionCompanionRuntime` builds `ChallengeContext` only from `ResolvedGameSnapshot`, the existing knowledge ledger, and the ROM-save journal; no reader or recovery decoder was added | Runtime integration proves live caught changes complete a bound objective and identical publications do not re-evaluate it | All 14 catalogs bind after their normal parse/reopen path | No parallel current-state pipe | SATISFIED |
| Section 3: Organic discovery and ordinary-copy ban | Entity-specific objectives require knowledge visibility in Organic mode; missing roles generate nothing; user text is independent wording | Engine tests remove unknown areas, unsafe definitions, missing roles, and missing adapters independently | Area challenges appear only after the exact base area enters the active save ledger | No capability, parser, provenance, address, or missing-role text enters ordinary UI | SATISFIED |
| Section 11.1: bounded predicate vocabulary | Boolean, logical, typed comparison, set, ordered, epoch, previous/current, bounded-group progress, and independent reset/pause/miss/completion lifecycles are data predicates | `ChallengeEngineTest` exercises every operator, incremental target retention, lifecycle precedence, and first completion evidence | Shipped cumulative templates use only proven inputs; scoped future rules remain adapter-gated | Required vocabulary is available without implementing the RetroAchievements expression language | SATISFIED |
| Section 11.2: portability tiers | Tier 2 templates require parsed catalog roles; Tier 3 templates additionally require an exact proven adapter and temporal window; Tier 4 is absent from runtime | Catalog decoder and binder reject unsupported tiers, temporal windows, malformed adapters, and missing roles | 449 Tier 1, 470 Tier 2, and 84 Tier 3 references are classified; 120 curated rows remain runtime-gated by their exact facts or adapters | Classification never promotes missing runtime evidence | SATISFIED |
| Section 11.3: dynamic instantiation | `ChallengeCatalogRoleResolver` resolves badge count, navigable regional species, and unambiguous flagged item groups from catalog structures; binder sorts stable keys | Role, reorder, ambiguity, identifier, adapter, and temporal mutation tests | Red through LeafGreen plus Modern Emerald, Unbound, and Odyssey produce exact frozen inventories | No filename, project name, SHA list, fixed offset, or ancestry branch exists in runtime selection | SATISFIED |
| Section 11.3: Atlas objective scope | Area definitions carry exact `AREA:base-N` knowledge scope and Atlas filters objectives to the tracked base area | Runtime test visits two areas and proves each Atlas area receives only its own objective | Every generated area objective is derived from a real parsed flagged-item group | Objectives cannot leak another visited area's target | SATISFIED |
| Section 11.4: Organic disclosure and percentages | Applicability and disclosure are separate engine results; ranked chains, current scopes, and numeric summaries are projected once through the shared API | Engine, projector, runtime, and web tests cover completed-plus-next-tier disclosure, off-scope suppression, current/started/completed scopes, Discovered inventory, bounded percentages, zero denominators, and player-facing copy | The 14-control gate preserves the exact applicable inventory while presentation metadata changes | No undiscovered entity enters the list or denominator; no hidden title or target enters the summary | SATISFIED |
| Sections 11.5 and 11.6: bounded non-goals and acceptance | Templates are packaged offline data, capabilities are available in the offline report, and unsupported inputs remove only dependent definitions | Deterministic evaluation plus missing-role, missing-adapter, ambiguous-name, reordered-ID, and malformed-window controls | 11 official controls and three source-backed hacks have SHA-frozen offline identities and result inventories | No runtime API, downloaded scripts, title matching, or RetroAchievements credit claim | SATISFIED |
| Section 12: navigation and visual scope | Expansion reuses the existing Trainer Progress Challenges list and Atlas drawer; no new route or diagnostic subtitle is introduced | Existing Progress, navigation, layout, theme, and production-copy suites remain in the release gate | Presentation is independent of ROM identity | Suite-wide typography/icon normalization remains owned by Stage 7 | DEFERRED |
| Section 13: exact persistence and identity | Lifecycle progress, target, pause, miss, and first completion evidence live in `PlaythroughJournal` under exact ROM SHA plus save identity; current facts are not mirrored | Codec, sanitation, identity rejection, save-change, first-completion, and compaction tests | Real-control identities are used only by offline tests/reports | Updates cannot merge another ROM or save's challenge state | SATISFIED |
| Section 14: performance and memory | Catalog roles bind once per catalog; immutable contexts skip equal publications and diff predicate dependencies; one bounded journal state per definition is retained | Runtime counters prove equal snapshots do not increment challenge evaluations; performance counters remain Debug-only | Maximum current concrete inventory is 125 for Unbound; aggregate inventory is finite and deterministic | No poller, ROM/save/memory buffer, journal rescan, or render loop was added | SATISFIED |
| Sections 15.1 and 15.2: controls and percentages | Reporter validates exact identities, prior baseline evidence, role inventories, classification counts, and all five required denominators | Reporter rejects missing/duplicate identities, SHA drift, malformed temporal rules, and inventory drift | 14/14 controls evaluated; 1,093 concrete definitions; 0 report errors | `NOT_FOUND`, `NOT_APPLICABLE`, and `ERROR` remain separate numeric fields | SATISFIED |
| Section 17: audit protocol | This table links implementation, automation, real evidence, result, and exact classification | Feature and complete release gates are recorded below | Numeric JSON is committed beside the audit | No current-stage blocker or error remains | SATISFIED |

## Exact non-coverage

- Gym Leader catalog roles: **0/14 controls (0.00%) — 14 `NOT_FOUND`**. The no-item battle template remains absent rather than binding a trainer by name or proximity.
- Proven Tier 3 mechanic adapters: **0/14 controls (0.00%) — 14 `NOT_FOUND`**. The adapter-gated template and lifecycle vocabulary exist, but no control claims an unproven adapter.
- Official Gen I/II live regional-caught observability: **0/6 controls (0.00%) — 6 `NOT_FOUND`**. Their regional role and area objectives still bind; the regional completion challenge remains non-observable until the unified live snapshot resolves caught species.
- Official Gen I/II badge roles: **0/6 controls (0.00%) — 6 `NOT_FOUND`**. The resolver does not substitute retail badge counts.
- Recovered reference semantic classification: **1,003/1,003 (100.00%)**, including 56 persistent source facts, 13 normalized live rules, 43 game-specific adapters, and eight sequence-sensitive objectives.
- Exact runtime equivalents from the recovered set: **0/120 (0.00%) — 120 `NOT_FOUND`**. Classification proves the intended semantic family; it does not prove that the current resolved snapshot exposes every completion, reset, miss, and temporal condition.
- RetroAchievements trigger bytecode or trigger expressions imported: **0**. The committed override registry contains only source IDs, description SHA-256 values, semantic families, portability tiers, and closed recovery-path values.

These are closed evidence outcomes, not promises silently moved to another feature stage. A future parser or adapter change may improve them only with new real structural evidence and its own compatibility report.

## Verification commands

```text
node --test tools/reports/challenge-expansion-compatibility.test.mjs
./gradlew :save-core:test :battle-memory:test :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-daemon --console=plain
npm test
npm run build
node --test tools/release/release-workflow.test.mjs tools/release/release-metadata.test.mjs
```

Final observed results and signed-artifact identity are recorded in the Stage 6 release evidence after the protected workflow completes.

## Observed verification

- Focused challenge engine, catalog, projector, runtime, and web tests: **green**.
- Exact challenge package plus all 14 real controls: **BUILD SUCCESSFUL in 9m 21s**.
- Companion web: **30 files and 227 tests passed**; production TypeScript/Vite build passed.
- Reporter and protected-release workflow: **26 tests passed**, including both challenge-report mutation tests.
- Complete repository command above: **BUILD SUCCESSFUL in 32m**, 103 actionable tasks, including app unit tests, Debug lint, and unsigned Release assembly.
- Compatibility report regeneration produced no numeric or identity drift.

The remaining action is release packaging and protected signing, not feature implementation. The signed-artifact identity is added by the release handoff without rewriting these compatibility results.
