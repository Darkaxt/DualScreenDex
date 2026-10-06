# Pokescape World Map acceptance — October 6, 2026

**Accepted boundary: host/static World Map.** One retained Pokescape v1.0.4 build was evaluated; no other ROM, full-corpus rerun, Android/device, save-state or release acceptance is implied. [Machine-readable evidence](2026-10-06-pokescape-world-map-acceptance.json).

## Verified gain

| Measure | Before | Accepted |
|---|---:|---:|
| Weighted applicable-capability coverage | 74.97% | 79.14% |
| World Map | NOT_FOUND | AVAILABLE |
| Regions / locations | 0 / 0 | 1 / 64 |
| Independently checked encounter/base-area bindings | 0 | 116 |
| Local Maps | 520 | 520, unchanged |
| Named area identities | 0 | 270 |
| Required static Local Map names | 0 / 484 | 270 / 484 |
| Encounter-area names | 0 / 202 | 201 / 202 |
| World region / location labels | 0 / 0 | 1 / 64 |

The static raster is **224 × 120 pixels**, with **28 × 15 semantic cells**. All **26,880 pixels**, palette placement, crop geometry, 64 location geometries/native section identities and 116 encounter/base-area joins matched independent private checks. No raster or decoded bulk data is published.

The working core is unchanged: 1,235 canonical named/stat/sprite species, 538 evolution edges, 19,537 level-up links, 828 moves and 202 encounter areas. Whole stored-section comparisons preserve species, moves, types, abilities, natures, type chart, encounters, capture balls, acquisition rulesets, trainer assets, Local Maps, language manifest and diagnostics—not merely their counters. Related text gains are corroborated against native compiled names; existing title disposition is unchanged.

## Diagnosis and generic implementation

The baseline already found and composed the affine 8bpp assets. It failed at the region-entry/map-header join: one encountered section ordinal falls outside the **213-row domain proven by the compiled guarded name consumer**. Exhaustively reading that ordinal as another table row rejected the valid map.

The new generic helper proves the compare/BHI bound, literal root, eight-byte stride, name-field displacement and indirect read with register flow. Complete indexed reference sites and the full bounded table extent are mandatory. Conflicting complete consumers, incomplete evidence, malformed rows and exhausted budgets fail closed. Map/encounter identities outside the represented region domain remain intact but do not acquire invented map geometry or text. Existing validation thresholds, optional isolation and legacy fallback are retained; no ROM identity, fixed address or project profile enters production.

The public structural oracle is [Demonheadge/pokescape_rom](https://github.com/Demonheadge/pokescape_rom) at `a42a3eab7c9ca458ec86d3287f90b669afe02318`. Its checked assets did **not** exactly match this retained build. It is not the accepted pixel or exact-build oracle. Independent private checks instead traced compiled name/geometry/palette-loader consumers and rendered the admitted original in memory.

## Source, references and persistence

- Accepted packaged CLI source: `36b495c541215761ee20786284dda156a5b47ac3`; exact runtime JAR manifest and source stamp checked before execution.
- Parser schema **87**; storage schema remains **2**.
- Report schema 16; one-input execution receipt and report/generator digests are retained in the JSON evidence.
- **83,623 complete decoded-reference checks, zero errors**, including all persisted acquisition rulesets and map/scene/POI/asset relationships.
- Complete SQLite write/reopen structural equality, two integrity checks and equal versioned logical digests. Accepted before/after digest: `8f5ad9651886458f08571d5ea50e3f8f4e34740a6a425202988f741b1bf04550`.
- Five separately admitted original reads and two production parser executions across diagnosis/baseline/acceptance. Two preflight reservations were abandoned before consumption; neither was reused. No ROM copies or real control inputs were created.

## Host verification and review

The initial eight focused regression cases produced **four intentional failures** before implementation. Final observed host results:

| Suite | Cases | Failures | Opt-in skips |
|---|---:|---:|---:|
| Affected parser/catalog, map compositor and compiled title | 93 | 0 | 0 |
| Complete catalog-store suite | 119 | 0 | 6 |
| Complete parser-cli suite | 76 | 0 | 1 |

The final parser run executed the additional title cases; CLI was up-to-date on that invocation, following its earlier actual complete execution at the unchanged production source. One expanded intermediate test failed because its fixture omitted a second compiled reference site; the fixture was corrected without weakening production evidence requirements.

Synthetic controls cover relocated/duplicate/competing consumers, decoys, missing or truncated references, malformed pointers/names/register flow, extent and nomination budgets, cancellation, optional-catalog isolation, affine/tiled 8bpp and text 4bpp composition, and title authority. Raster production code is unchanged; no separately admitted real control was necessary. Inline review checked register preservation, branch arithmetic, full extents, candidate-local evidence, fail-closed conflicts, cancellation propagation and the unchanged legacy path.

Every required Gradle execution acquired the installed shared Windows gate. Observed settings were two workers, no parallel projects and a 3 GB Gradle heap; Kotlin's 3 GB daemon budget was requested with the project strategy preserved, not independently measured as a total-host limit. Test configuration used one 512 MB fork. No diagnosed memory failure or increased budget occurred.

## Explicit field-level limits

This is not whole-ROM completion. Named follow-ups in the [library ledger](2026-10-03-library-refresh.md#follow-up-ledger):

- `LIB26-G3-POKESCAPE-SECTION-DOMAIN`: one encountered section is not represented by the compiled static region table; do not invent a location for it.
- `LIB26-G3-POKESCAPE-TEXT`: 214 required Local Map names, one encounter-area name and 1,097 POI text obligations remain unresolved. Coverage is 1,052/2,149 POI text obligations; the guide's 652 records with content measure something different. Context-dependent map labels remain under their existing explicit applicability contract.
- `LIB26-G3-POKESCAPE-OPTIONAL`: missing Dex text and move details/categories, five move-description gaps, ambiguous abilities and unresolved ability text/mechanics/native type label remain open.

`LIB26-G3-POKESCAPE-WORLD` closes this bounded static World Map round. Other targets remain separate. Only this matrix row is superseded; **341 other row objects and their provenance are preserved**, including historical controls. The composite retains 309 full342-baseline rows alongside 33 independently focused rows.

## Retention and closure

Immutable private admissions, consumed/abandoned receipts, source/runtime/report bindings, independent verification, RED/GREEN XML and gate evidence are retained outside Temp/Git. Original archives, old/new corpus snapshots, public source oracles and canonical source are protected. On October 7, **three exact-root reviewed cleanup transactions were applied**: 3,481 files and 331 empty directories removed, **160,685,952 logical bytes** reclaimed, all eight disposable roots verified absent and zero residuals. Cleanup did not acquire the Gradle mutex. Logical deleted bytes are not a promise of identical volume free-space change during concurrent host activity. Final reconciliation has **zero specification blockers and zero required tracked deferrals**, with unrelated optional limits remaining explicitly ledgered. See the [staged plan](../../superpowers/plans/2026-10-06-pokescape-world-map-plan.md).
