# Gen I/II Map Parity Checkpoint A Audit

Specification: `docs/superpowers/specs/2026-08-24-gen1-gen2-local-map-parity-design.md`

| Stage | Requirement | Evidence | Classification | Target / acceptance |
|---|---|---|---|---|
| Baseline | Reconcile with `fork/master` before every commit | Corpus gate rebased HEAD `8cf81da` onto master `2ed446c`; final audit gate has HEAD/master/remote branch and merge base all at `a8579cc` | PASS | Re-run and record refs at every commit |
| Shared solver | Gen III output remains unchanged | Shared normalized builder plus complete `LocalMapSceneBuilderTest` and `Gen3MapSceneResolverTest` pass | PASS | Re-run with every generation adapter change |
| Gen I scenes | Compiled connections produce bounded scenes | 11-byte decoder, fail-closed integration, synthetic ABI suite, and Red/Blue/Yellow exact controls pass | PASS | Red/Blue/Yellow strict controls pass |
| Gen II scenes | Compiled connections preserve four palettes | 12-byte decoder, fail-closed integration, synthetic ABI suite, and Gold/Silver/Crystal exact controls pass | PASS | Gold/Silver/Crystal strict controls pass |
| Live player | Existing area and X/Y drive shared scene marker | Gen II scene API projection and existing Android live-map publication tests pass | PASS | Android and API tests pass |
| Overworld marker | Structurally resolved frame or compact-dot fallback | Native contracts, sole-appearance API, structural GB/GBC resolver, and six official exact controls pass | PASS | Official controls and fail-closed tests pass |
| Discovery / Atlas | RC53 hidden-image and fallback contract remains intact | Organic scenes omit undiscovered raster URLs and Atlas underlays; Atlas remains the unavailable-Local fallback | PASS | Web tests pass |
| Persistence | Existing catalogs rebuild once and round-trip | Parser schema 35, stale-revision rejection, synthetic section coverage, and official Red/Crystal round trips pass | PASS | Parser schema 35 cache tests pass |
| GB/GBC corpus | No accepted Local raster regresses | 334/334 hashes; 102/102 deterministic selected rows; exact pre-stage raster preservation; 69 current scenes; three source-backed strict controls | PASS | Zero parser errors, raster regressions, and strict-control failures |
| Signed artifact | Publish and independently verify a production-signed APK | `v1.1.0-rc.54`; package/version/code/hash and one v3 signer match protected provenance | PASS | Live ADB validation still requires explicit device ownership |

## Baseline characterization

`Gen3MapSceneResolverTest.partitionsContradictoryBranchesDeterministically` now freezes the exact safe-greedy branch membership and `scene/0001` key before solver extraction. Validation passed with:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen3MapSceneResolverTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

## Shared scene solver

Gen III connection decoding remains generation-owned while normalized constraint canonicalization, deterministic placement, overlap exclusion, partitioning, and scene bounds now live in `LocalMapSceneBuilder`. Missing-map constraints are discarded before topology construction. Validation passed with:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*LocalMapSceneBuilderTest' --tests '*Gen3MapSceneResolverTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

The pre-extraction exact scene membership and key characterization remained unchanged.

## Gen I scene adapter

`Gen1MapSceneResolver` now decodes each set cardinal flag in the compiled north/south/west/east order and advances exactly 11 bytes per record. Target strip, WRAM, width, sentinel, and signed-alignment evidence is validated independently before conversion to shared grid constraints. A malformed record contributes only a bounded diagnostic; an unexpected adapter failure produces an empty scene list and retains every accepted Gen I raster.

The synthetic suite covers all four directions, reciprocal agreement, flag-record ordering, invalid WRAM evidence, out-of-range target strips, wrong connected widths, unknown targets, bank-truncated records, odd tile alignment, and continuation after a malformed record:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen1MapSceneResolverTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

The official Red, Blue, and Yellow controls were run with their SHA-locked environment inputs and `--rerun-tasks`. They retained 226, 226, and 227 maps respectively; all existing names, dimensions, and ARGB raster hashes remained exact. Every compiled connection passed structural validation. Pallet Town (`0x00`) and Route 1 (`0x0c`) share `scene/0000` with the frozen displacement `(0, -36)`, and global scene membership and overlap invariants pass:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen1LocalMapResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL
```

Post-stage comparison against the specification found no missing or partial Gen I scene requirement. There are no Gen I scene blockers or deferrals.

## Gen II scene adapter

`Gen2MapSceneResolver` now decodes the compiled 12-byte group/map connection ABI in north/south/west/east flag order. It validates target block-bank strip pointers, WRAM pointers, connected widths, cardinal sentinels, and signed metatile alignment per record before producing shared constraints. Unexpected resolver or cross-catalog validation failures fail closed to individual Local maps, without changing indexed assets or runtime lighting metadata.

The synthetic suite covers all four directions, cross-bank strip resolution, reciprocal agreement and conflict rejection, flag-record ordering, invalid WRAM evidence, out-of-range target strips, wrong connected widths, unknown targets, bank-truncated records, odd tile alignment, and continuation after malformed evidence:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen2MapSceneResolverTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

The official Gold, Silver, and Crystal controls were run from their SHA-locked inputs with `--rerun-tasks`. They retained 368, 368, and 388 maps, 364, 364, and 382 named maps, and the structurally resolved WRAM offsets `0x1568`, `0x1568`, and `0x1841`. Existing exact day-raster controls and all four New Bark Town morning/day/night/dark hashes remained unchanged. Every compiled connection passed structural validation. New Bark Town (`0x1804`) and Route 29 (`0x1803`) share a scene with the frozen displacement `(-60, 0)` from New Bark to Route 29; global scene membership and overlap invariants pass:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen2LocalMapResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 10m 11s
```

Post-stage comparison against the specification found no missing or partial Gen II scene or lighting-preservation requirement. There are no Gen II scene blockers or deferrals.

## Native overworld-asset contract

`TrainerAssetCatalog` now accepts distinct non-empty subsets of the supported gender-key domain for overworld sprites while retaining the exact dual-gender requirement for Trainer Card portraits. Supported overworld dimensions are exactly 16×16, 16×32, and 32×32; invalid gender keys, duplicate references, and unsupported dimensions remain rejected. `ApiViewBuilder` selects the sole overworld asset when runtime trainer gender is unavailable, without applying that fallback to portraits.

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*CatalogModelsTest' --tests '*Gen3TrainerAssetResolverRealControlTest' :companion-core:test --tests '*ApiViewBuilderTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

The existing dual-gender Gen III API behavior remains exact.

## GB/GBC overworld-frame adapters

`GbTrainerAssetResolver` resolves Gen I walking graphics from compiled loader/copy contracts, including the shared Red/Blue form and Yellow's banked state-loader form. It requires distinct walking/bike/alternate-state graphics authority, bank-bounded 12-tile sheets, a validated VRAM copy target, and a bounded occupied-pixel frame before publishing the first 16×16 walking frame. Gen II resolution supports both compiled `GetSprite` consumer forms and both object-palette copy forms, validates six-byte rows and 12-tile walking semantics, uses the day block of the structurally resolved four-time-block `MapObjectPals`, and reads only relative rows 0 and Crystal `0x5f`.

Red, Blue, Yellow, Gold, and Silver source-tree builds are byte-identical to their SHA-locked controls. Crystal's row and palette contracts were independently cross-checked against the public source before the compiled control was treated as authority. Exact rendered hashes pass for all six titles: one neutral-DMG frame for Red/Blue/Yellow, one day-palette frame for Gold/Silver, and male plus female day-palette frames for Crystal. Empty or malformed supported ROMs return no trainer assets without throwing.

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*GbTrainerAssetResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL
```

`CatalogParser` dispatches the GB/GBC resolver under one `runCatching` boundary. The official Local-map controls prove that trainer materialization coexists with all accepted rasters, scenes, Gen II indexed assets, four lighting hashes, and runtime clock metadata:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen1LocalMapResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 3m 48s

D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen2LocalMapResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 7m 43s
```

Post-stage comparison against the specification closes blocker Task #147. There are no remaining overworld-marker blockers or deferrals.

## Catalog persistence

Parser schema 35 invalidates revision 34 catalogs exactly once without changing the SQLite schema or section formats. Synthetic section coverage retains static PNG maps, indexed four-palette maps, timed maps, scenes, POIs, runtime lighting metadata, and trainer assets; the stale-revision control preserves the independent save snapshot while clearing incompatible catalog metadata and sections.

Official Red and Crystal cache controls additionally prove that generated Gen I/II scenes and native walking frames survive a complete SQLite round trip. Crystal retains indexed raster bytes, all morning/day/night/dark palettes, the `0x1841` time-of-day WRAM offset, and both trainer appearances. `KnowledgeLedgerSanitizerTest` proves that a catalog rebuild removes stale area/POI keys while retaining every valid revealed area and the exact save-scoped Local-map POI preferences.

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :catalog-store:test --tests '*CatalogStoreTest' --no-daemon --console=plain
BUILD SUCCESSFUL in 55s

DUALDEX_POKERED_ROM=<official-control> D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :catalog-store:test --tests '*official Gen I local map assets survive a complete cache round trip*' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 58s

DUALDEX_POKECRYSTAL_ROM=<official-control> D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :catalog-store:test --tests '*official Gen II local map assets survive a complete cache round trip*' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 3m
```

Post-stage comparison against the specification closes blocker Task #148. There are no persistence blockers or deferrals.

## Shared map presentation

The shared catalog API now has exact Generation I static-scene and Generation II indexed-scene regressions. They prove scene pixel geometry, static versus dynamic-lighting flags, live area/X/Y publication, and a sole 16×16 native trainer marker without runtime gender. Existing Android memory-coordinator tests continue to prove that generation-owned area and coordinate bytes reach the shared state model.

Web regressions prove that a native 16×16 marker remains at least its ROM dimensions, recentering retains zoom, and the compact dot remains present when no native frame resolves. Every placement in a connected timed scene changes from `?lighting=DAY` to `?lighting=NIGHT` without changing placement geometry, transform, pan, or scale. Organic mode never mounts or references an undiscovered raster URL and never places Atlas beneath Local; Atlas remains the fail-closed surface when no current Local map exists.

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :companion-core:test --tests '*ApiViewBuilderTest' --no-daemon --console=plain
BUILD SUCCESSFUL in 45s

D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :app:testDebugUnitTest --tests '*BattleMemoryCoordinatorTest' --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 39s

npm --prefix D:/Temp/dualdex-gen2-dynamic-lighting/companion-web test -- --run src/pages/MapPage.test.tsx src/mapEngine.test.ts
36 tests passed

npm --prefix D:/Temp/dualdex-gen2-dynamic-lighting/companion-web run build
built successfully
```

Post-stage comparison against the specification closes blocker Task #149. There are no shared-presentation blockers or deferrals.

## GB/GBC corpus

The complete private-input matrix verified all 334 manifest hashes and parsed all 102 selected Gen I/II rows twice from fresh bytes. All 102 were deterministic with zero parser errors. Comparison by manifest index and ROM hash against pre-stage commit `d6b3722` found zero differences in selected identity, generation/family, Local capability, map count, static/indexed/timed asset counts, raster signature, or error state. The current parser retains 13,685 maps and 13,685 raster assets exactly; all 57 available Local rows now contain scenes, with 69 scenes total.

Three source-backed Shin release controls each retained 226 maps and the same deterministic scene signature. A focused compiled-ROM check verified the Pallet Town (`0x00`) to Route 1 (`0x0c`) displacement `(0, -36)` documented by public source revision `a7a9b1361e55aaa5afed6b5d14b5e7bd44002179`. The strict run reported three verified hashes, three deterministic controls, zero parser errors, three preserved baselines, and zero strict-control failures.

The 45 `LOCAL_MAP NOT_FOUND` rows are unchanged from baseline, produce bounded diagnostics, and retain Atlas plus unrelated capabilities. Task #153 records the exact compatibility gap, prioritizes the 40 rows with potential local public-source oracles, and tracks the five rows awaiting equivalent source evidence. This is a valid deferral because no previously accepted Local raster is missing and Checkpoint A normalizes accepted Local catalogs; corpus-wide Gen I/II Local support is explicitly not claimed. Safe fallback is Atlas. The target is the source-backed GB/GBC Local-map compatibility expansion, accepted only when generic compiled-structure resolution preserves baseline capabilities and passes focused plus corpus controls.

Full public-safe evidence is retained in `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a.md`. No Gen I/II production parser or Local-map code changed between complete matrix commit `8cf81da` and audit base `a8579cc`; post-sync focused strict, API, Android, and all-six official controls cover the integrated changes without another redundant full corpus parse. Post-stage comparison closes blocker Task #150 with no Checkpoint A corpus blocker.

## Final Checkpoint A specification audit

| Specification area | Evidence | Result |
|---|---|---|
| Checkpoint A delivery scope | Gen I/II scenes, live area/X/Y, native-or-dot marker, Gen II lighting, cache/API/Android/web coverage | PASS |
| Shared renderer authority | `MapPage` remains the only Local presentation path and contains no generation/family/platform branch | PASS |
| Structural decoders | Relative 11-byte Gen I and 12-byte Gen II compiled ABIs; bounded targets/strips/WRAM/width/alignment; no production identities | PASS |
| Shared scene solver | Reciprocal canonicalization, ambiguity rejection, overlap isolation, deterministic partitioning, non-negative bounded scenes, unchanged Gen III controls | PASS |
| Player and overworld assets | Existing bounded runtime publication, exact static/timed API scenes, native dimensions, sole-appearance selection, positive dot fallback | PASS |
| Gen II lighting | Indexed assets retain four palettes and clock metadata; server and connected-scene tests retain lazy per-map lighting URLs | PASS |
| Discovery, camera, and raster budget | Hidden URLs absent, black placeholders present, Atlas excluded beneath Local, individual-map fallback, continuous pan/zoom/recenter/follow, 32 MiB budget | PASS |
| Persistence and ledger | Parser schema 35 one-time rebuild; static/indexed/timed/scenes/trainer round trips; stale keys removed while valid revealed areas and preferences survive | PASS |
| Failure isolation | Malformed connection/trainer/clock evidence fails independently; accepted rasters remain; unavailable Local uses Atlas | PASS |
| Official/source/corpus validation | Six official controls, three source-backed controls, complete deterministic matrix, zero parser errors or accepted-raster regressions | PASS |
| Concurrent-work reconciliation | Every implementation commit was fetched against master; the corpus gate rebased onto incoming unified-state work and reran overlapping API/Android controls | PASS |

Final local gates:

```text
Gradle test + app unit gate: BUILD SUCCESSFUL in 22m 52s; 71 tasks
Final API + ledger regressions: BUILD SUCCESSFUL in 1m 2s
Companion web: 26 files / 189 tests passed; production build succeeded
Final MapPage regression: 24 tests passed
Release policy: 18 tests passed
Official compiled controls: 13 tests, 0 skipped, 0 failures; BUILD SUCCESSFUL in 5m 13s
```

Checkpoint B POIs and collection evidence are outside Checkpoint A and are tracked as Task #154 rather than silently treated as complete. Task #153 is the one valid Checkpoint A compatibility deferral recorded below. All local implementation and lab-validation blockers are closed; interactive signed-APK validation remains pending explicit device ownership.

## Signed validation artifact

The already-published `v1.1.0-rc.54` tag contains implementation commit `8cf81da`. Changes from that tag through corpus commit `a8579cc` are limited to reports, plans, and the evidence-only parser-cli matrix runner; no production source differs. Protected workflow run `32731056044` completed successfully at tagged commit `37881694`; the released APK was independently downloaded and verified:

```text
package=com.darkaxt.dualdex
versionName=1.1.0-rc.54
versionCode=1010054
apkSha256=6b1d78d3e062f7c514d2f3f4c4fa0983a68160c19055259c28e3cceabd627264
signers=1
signatureScheme=v3
certificateSha256=C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA
```

The APK hash matches both the release asset digest and `SHA256SUMS.txt`; package/version/hash/certificate match protected provenance. The artifact is ready for live Checkpoint A validation. It was not installed or driven because this thread has not been granted explicit device/emulator ownership; no live-device result is claimed.

## Deferral ledger

| ID | Missing behavior / affected family | Safe fallback | Target | Acceptance |
|---|---|---|---|---|
| Task #153 | Local-map authority for 45 pre-existing unavailable rows; source-first Gen I Celebrations/Beyond/Red++/Static Yellow/matching Kaizo and Gen II Anniversary Crystal/Crystal Legacy/Timeless/Gold-Silver 97/Mystic/Orange/Peridot, then five rows awaiting matching source | `LOCAL_MAP NOT_FOUND`; Atlas remains available; parser selection and unrelated capabilities continue | Source-backed GB/GBC Local-map compatibility expansion before corpus-wide Local support is claimed | Each source-matched family resolves generically from bounded compiled structure, preserves baseline capabilities/rasters, emits valid scenes or standalone maps, and passes focused and affected-corpus controls; remaining rows close only with equivalent structural evidence |

## Classification rules

A stage is marked `PASS` only from an exact command, bounded real-ROM result, or retained artifact. A Checkpoint A requirement cannot be deferred to unblock release. Any permitted deferral must include a stable task ID, exact missing behavior, affected structural family, safe fail-closed behavior, target stage, and concrete acceptance condition.
