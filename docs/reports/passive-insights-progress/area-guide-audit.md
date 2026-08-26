# Stage 2 audit — Atlas Area Guide

Date: 2026-08-26  
Specification: `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`  
Numeric evidence: `docs/reports/passive-insights-progress/area-guide-compatibility.json`

## Result

Stage 2 has **0 blockers** and **0 errors**. The Area Guide is one knowledge-safe projection over the same resolved area, encounter, POI, discovery, and filter inputs already used by Atlas and Local maps. Missing catalog facts remove only their rows; they do not become stock names, exits, services, or objectives.

The real-data gate selected, persisted, and reopened all 14 exact controls. Aggregate field coverage is:

| Input | Covered | Total | Percent |
| --- | ---: | ---: | ---: |
| Area names | 3,973 | 4,979 | 79.80% |
| Named exit targets | 7,848 | 10,513 | 74.65% |
| Encounter species names | 23,167 | 23,167 | 100.00% |
| Encounter availability windows | 2,775 | 2,775 | 100.00% |
| Encounter level ranges | 23,152 | 23,167 | 99.94% |
| Encounter rates | 23,167 | 23,167 | 100.00% |
| Local-map records | 4,978 | 4,978 | 100.00% |
| Local maps containing parsed POIs | 4,737 | 4,978 | 95.16% |
| POIs with resolved content | 5,881 | 25,003 | 23.52% |
| Shared category filters | 70 | 70 | 100.00% |

`Local maps containing parsed POIs` measures observed content density; an empty map is not automatically an error. `POIs with resolved content` requires an actual name, gender-specific name, service role, or item identity. Unresolved records remain absent or use the established knowledge-safe unidentified presentation where the point itself is legitimately known.

## Specification cross-check

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | --- | --- |
| Section 3: one current-state authority | `ProductionCompanionRuntime` builds one cached `AreaGuideProjection` from the resolved snapshot and passes it to both State and map presentation; the guide reads no memory or SaveRAM independently | Runtime cache test proves one projection for repeated polling clients; `ApiViewBuilderTest` proves the immutable API view | All 14 reports are generated from persisted/reopened catalogs, never a second runtime reader | One projection and no alternate state pipe | SATISFIED |
| Section 3: established map/knowledge/filter contracts remain authoritative | `AreaGuideBuilder` receives the same projected points, visibility mode, discovery ledger, current/selected area, and `LocalMapPoiPreferences` used by the map | Organic/Discovered, hidden-item proximity, filter, tracking, fog, zoom, and raster-mount tests | Official Gen I–III plus the three source-backed hacks use one contract | No second POI/filter/discovery store | SATISFIED |
| Section 3: theme, accessibility, and ordinary-page diagnostic ban | Drawer uses existing map controls and ROM-derived surface/type tokens; player-facing rows contain no parser, capability, source, signature, or hash provenance | Drawer, layout, and production UI tests; production TypeScript/Vite build | 1024×768 constraints are enforced by the shared layout suite | No new diagnostic UI or redundant area title | SATISFIED |
| Sections 9.1 and 9.5: drawer entry, tracking, selection, close continuity, and single-current reporting | One `Area Guide` map control opens an overlay; tracked area follows live state, manual selection holds, Recenter resumes tracking, and Back/close dismiss only the drawer | `MapPage.test.tsx` proves tracked/manual transitions, Back ownership, preserved zoom/pan/tracking/fog/filter/raster state, and one current header | Required controls all expose the same Area Guide API contract | The map remains mounted and its interaction state is preserved | SATISFIED |
| Section 9.2: conditional guide sections | Overview, Encounters, Places/Services, Trainers/People, Items, and Objectives are separate optional projections; empty unsupported sections are not rendered | Builder/API/drawer tests cover supported and omitted sections | Encounter fields are independently measured; unresolved place/person/item facts are withheld | No false empty tables or stock substitutes | SATISFIED |
| Section 9.2: Objectives | `objectives` is an empty immutable section until knowledge-safe local challenge facts exist | Builder and drawer tests prove an empty section stays absent | Stage 2 has no applicable challenge journal from which to derive objectives | Assigned to Stage 3; current drawer remains truthful without it | DEFERRED |
| Sections 9.2 and 9.4: names, signs, services, and area-title quality | Sign text uses the first meaningful expanded line; `{PLAYER}` becomes the live name or `Your`; `Place` and duplicate current-area labels are rejected | Builder and drawer content-quality tests | Area-name and POI-content percentages expose unresolved ROM facts without replacing them | Only proven player-facing content is shown | SATISFIED |
| Sections 9.2–9.3: encounters and knowledge visibility | Encounter groups project ROM-derived species, time windows, levels, and rates only when allowed by the active knowledge mode | Organic/Discovered mutation tests; drawer row tests | Species/windows/rates are 100.00%; levels are 99.94%, with 15 records withheld | Unresolved values do not become defaults | SATISFIED |
| Sections 9.2–9.3: POIs, items, filters, and selection | Guide rows reuse map categories/preferences; an entry may highlight only an already knowledge-visible and currently mounted/above-threshold marker | POI visibility, threshold, filter, selection, collision, fog, and eight-neighbor hidden-item tests | Five filters resolve for all 14 controls; Local maps resolve 4,978/4,978 | Drawer selection cannot reveal or force-render hidden content | SATISFIED |
| Section 9.3: raster and marker continuity | The drawer overlays the existing stage; it allocates no map image/canvas and does not alter mounted placements | Tests compare mounted raster URLs/bytes, fog nodes, viewport, and tracking before/after open/close | Real catalogs provide the connected Local-map inputs used by the controls | No raster reload or duplicate buffer | SATISFIED |
| Section 12: navigation stack | Companion Back is consumed by the open drawer before the parent map route; drawer close leaves map selection, viewport, and tracking intact | Back regression test plus production root/map Back tests | Same browser route contract is independent of ROM identity | Back returns to the immediate map destination | SATISFIED |
| Section 12: visual/accessibility/4:3 contract | Drawer uses real labelled buttons, keyboard Escape/Back support, focusable selectable rows, windowed lists, and bounded overlay CSS | Full browser suite and layout assertions | Production bundle compiled with the same 1024×768 layout tokens | No document-level overflow or color-only state | SATISFIED |
| Section 14: no new reader, parser, buffer, poller, or render loop | Projection is cached with the presentation snapshot; browser drawer has no fetch, interval, timeout, animation frame, or retained raster copy | Runtime recomputation test, no-loop source assertion, full performance collector tests | One real corpus parse produced the committed report; opening the drawer does not parse | Existing global poller remains the only live source | SATISFIED |
| Section 14: bounded lists and metrics | Long sections retain only a viewport plus overscan; projection CPU/count/retained items are added to the existing Debug-only performance stream; browser render/retained-row metric is production-console only | Windowing tests, collector serialization tests, production-build check | Corpus totals exercise large encounter/POI denominators without copying the catalog into UI state | Performance evidence is diagnostic-only and bounded | SATISFIED |
| Section 15.1: required corpus | Reporter validates exact SHA-256 identities, selection, reference integrity, persistence, and reopen evidence | Reporter fails on missing, duplicate, unexpected, or erroneous controls | 11 official + Modern Emerald + Unbound + Odyssey: 14/14 selected, 14/14 reopened, 0 errors | Exact required corpus used | SATISFIED |
| Section 15.2: independent percentages and absence states | Report schema separates ten map/guide input families and retains raw covered/total counts | `area-guide-compatibility.test.mjs` | Per-ROM and aggregate numbers are committed without private paths | No generic compatibility label replaces numeric evidence | SATISFIED |
| Section 17: audit protocol | This table records implementation, automated, and real evidence using the required classifications | Full Kotlin/Android and browser suites plus report tests | Public report contains `errors: []` | 0 blockers and 0 errors | SATISFIED |

## Explicit non-coverage

These are unresolved field records, not softened compatibility labels:

- Area names: **3,973 / 4,979 (79.80%) — 1,006 records NOT_FOUND**.
- Named exit targets: **7,848 / 10,513 (74.65%) — 2,665 records NOT_FOUND**.
- Encounter level ranges: **23,152 / 23,167 (99.94%) — 15 records NOT_FOUND**.
- POIs with resolved content: **5,881 / 25,003 (23.52%) — 19,122 records NOT_FOUND**.
- Objectives: **DEFERRED to Stage 3**. The section stays absent; no story or challenge cue is guessed.

The guide omits dependent rows or presents an already-known point as unidentified according to the established Organic policy. None of these states permits a stock-ROM name, service, exit, or objective fallback.

## Verification commands

```text
node --test tools/reports/area-guide-compatibility.test.mjs
./gradlew :parser-core:test :parser-cli:test :catalog-store:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain
npm test
npm run build
```

Observed results: report tests 3/3; full Gradle build successful; browser tests 209/209 across 28 files; production Vite build successful.
