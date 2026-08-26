# Stage 1 audit — Party Analysis

Date: 2026-08-26  
Specification: `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`  
Numeric evidence: `docs/reports/passive-insights-progress/party-analysis-compatibility.json`

## Result

Stage 1 has **0 blockers** and **0 errors**. The feature is complete and capability-gated: absent live fields, move categories, or typed ability semantics remove only the dependent result and never become a neutral/default mechanic.

The real-data gate selected, persisted, and reopened all 14 exact controls. Aggregate coverage is:

| Input | Covered | Total | Percent |
| --- | ---: | ---: | ---: |
| Live Party fields | 48 | 84 | 57.14% |
| Move detail records | 4,785 | 4,785 | 100.00% |
| Move category records | 4,737 | 4,785 | 99.00% |
| Type-chart records | 1,470 | 1,470 | 100.00% |
| Evolution records | 5,551 | 5,551 | 100.00% |
| Abilities with proven incoming-type modifiers | 30 | 849 | 3.53% |

The ability denominator excludes generations without abilities. The 30 covered abilities are five abilities in each official Gen III catalog plus Modern Emerald; each catalog exposes six typed modifier records because Thick Fat has two affected types.

## Specification cross-check

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | --- | --- |
| Section 3: one live/recovery authority | `ApiViewBuilder.state` receives `AppSnapshot.party`; Party Analysis reads no memory or SaveRAM path | `ApiViewBuilderTest`, `ProductionCompanionRuntimeTest`, and `UnifiedGameStateRealControlTest` | Fresh 14-control unified-state run passed | Current Party facts have one authority | SATISFIED |
| Section 3: retain the approved Party interaction | `PartyPage` keeps the six-slot 2×3 board and its member action; Analysis is a header action | `PartyPage.test.tsx`, `App.production.test.tsx`, `party-artwork.spec.ts` | 1024×768 browser capture inspected without horizontal overflow | Existing Party composition remains intact | SATISFIED |
| Section 3: retain theme, Organic, map, and diagnostic boundaries | Analysis uses existing theme/type tokens; no map, POI, discovery, save, or battle routing was changed | `PartyAnalysisPage.test.tsx`, `layoutStyles.test.ts`, full browser suite | Production 4:3 capture used the ROM-derived theme | No ordinary-page diagnostic/provenance text | SATISFIED |
| Section 5.1: Party Analysis uses resolved facts plus catalog semantics | `PartyAnalyzer` consumes immutable owned individuals, active ruleset, moves, types, type chart, evolutions, and typed mechanics | Determinism, mutation, empty/partial/fainted/single/six-member tests | Every catalog input came from the exact real-ROM controls | No filename, SHA, family, or retail fallback selects calculations | SATISFIED |
| Sections 5.2–5.4: shared fact/event projection for temporal features | Stage 1 needs current Party facts only and remains on the resolved snapshot; shared event projection is not duplicated locally | No Party-specific poller, journal, or event evaluator exists | Not required to calculate the current Party page | Assigned to Stage 3 before Progress/Challenges/Timeline | DEFERRED |
| Section 6.1: route and Back stack | `PARTY_ANALYSIS` and pushed member/species detail routes preserve the Party selection and scroll position | Production navigation test proves Analysis → member → species → member → Analysis → Party | Production browser route exercised | Back unwinds one destination at a time | SATISFIED |
| Sections 6.2–6.3: four factual sections and fail-closed calculations | Team summary, offensive coverage, defensive profile, and development are pure projections; empty type charts withhold coverage | `PartyAnalyzerTest` and `PartyAnalysisPage.test.tsx` | Type charts and evolutions are 100%; categories are reported independently | Unknown inputs withhold only dependent facts | SATISFIED |
| Section 6.3: proven ability effects only | Source-backed incoming modifiers carry a typed name resolved against the active ROM's own type table; the analyzer accepts only typed incoming multipliers | Resolver, live-ROM materializer, analyzer mutation, and report-writer tests | Official Gen III and Modern Emerald expose 5 abilities / 6 records each; Unbound and Odyssey expose 0 and are withheld | No outgoing boost is misapplied as incoming damage | SATISFIED |
| Section 6.4: Organic knowledge | Analysis receives owned Party individuals and their live moves only; unknown evolution target identity is masked and unlinked | Page projection tests cover unknown targets | No encounter, opponent, hidden item, or story fact is an input | Owned-member analysis does not reveal undiscovered entities | SATISFIED |
| Section 6.5: empty through six-member rendering and ROM mutation | Analyzer models absent sections explicitly and uses catalog IDs/matchups | Empty, partial, fainted, single, six-member, and type-chart mutation tests | Official/hack charts are read from each persisted catalog | Deterministic and data-driven | SATISFIED |
| Section 12: visual and accessibility contract | Compact matrices/type chips, existing portraits, real buttons, focus names, 4:3 CSS bounds | 200 browser tests across 27 files; production build; layout and keyboard assertions | 1024×768 E2E capture inspected | No debug labels, redundant subtitle, or document-level overflow | SATISFIED |
| Section 14: no extra polling/copying and bounded recomputation | Analysis is computed only on an uncached presentation snapshot; no bytes or append-only state are retained | Repeated state reads return the same object and report exactly one recomputation; profiler records `analysis.party.recomputations` and `analysis.party.cpuNanos` | Full Kotlin/Android regression passed | Existing global heap sampling remains authoritative; analysis adds no reader or buffer | SATISFIED |
| Section 15.1: required corpus | Report transformer validates exact SHA-256 identities, selection, reference integrity, persistence, and reopen evidence | Report tests fail on missing, duplicate, unexpected, or erroneous controls | 11 official + Modern Emerald + Unbound + Odyssey: 14/14 selected, 14/14 reopened, 0 errors | Exact required corpus used | SATISFIED |
| Section 15.2: independent percentages | Report schema separates live Party fields, moves, categories, charts, evolutions, and modifiers | `party-analysis-compatibility.test.mjs` | Per-ROM counts and percentages are committed without private paths | No generic compatibility label substitutes for a number | SATISFIED |
| Section 17: audit protocol | This table records implementation, automated, and real evidence with exact classifications | Full required Gradle suite, 200 browser tests, production web build, report tests | Public report contains `errors: []` | 0 blockers and 0 errors | SATISFIED |

## Explicit non-coverage

These are evidence states, not softened labels:

- Gen I/II live Party fields: **0 / 36 (0.00%) — NOT_FOUND**. Static moves, categories, type charts, and evolutions are 100% for those six controls, but the unified live decoder does not currently expose their Party individuals.
- Modern Emerald move categories: **365 / 368 (99.18%) — 3 records NOT_FOUND**.
- Pokémon Unbound move categories: **898 / 922 (97.40%) — 24 records NOT_FOUND**.
- Pokémon Odyssey move categories: **456 / 477 (95.60%) — 21 records NOT_FOUND**.
- Pokémon Unbound typed defensive ability modifiers: **0 / 254 (0.00%) — NOT_FOUND**.
- Pokémon Odyssey typed defensive ability modifiers: **0 / 129 (0.00%) — NOT_FOUND**.
- Gen I/II typed ability modifiers: **0 / 0 — NOT_APPLICABLE**, because these generations have no ability table.

The UI reports category-dependent values as unresolved and omits unproven ability modifiers. None of these states creates a Stage 1 error or permits a stock fallback.

## Verification commands

```text
node --test tools/reports/party-analysis-compatibility.test.mjs
./gradlew :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain
npm test
npm run build
```

Observed results: report tests 2/2; full Gradle build successful in 16m37s; browser tests 200/200 across 27 files; production Vite build successful.
