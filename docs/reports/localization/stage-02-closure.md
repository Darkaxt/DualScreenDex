# Localization Stage 2 Closure

**Decision:** `COMPLETE`

**Stage branch:** `feat/full-translation-system`

**Synchronized baseline:** `2fef59df95f6deb0ae861aad42cff52eff739a86` (`fork/master`)

**Implementation checkpoint:** `3fced69b453ac7205fc3f8ce8e30cea506f20d5a`

**Specification:** `LNG-INV-003`–`LNG-INV-006`, Sections 10, 11.1, 14.3, 14.5, and Stage 2 in Section 15 of `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`

## Delivered multi-projection foundation

### One shared graph plus exact language overlays

- `ParsedCatalog` owns one immutable `CatalogLocalization`: one parser-selected language manifest and one `CatalogLanguageOverlay` for every resolved projection.
- Stable entity IDs, stats, move details, proven mechanics, type chart, media, encounters, maps, runtime layouts, themes, and structural capability evidence remain in the shared graph. Localized names, descriptions, labels, and POI text are removed from that graph and keyed by stable IDs in overlays.
- A resolved manifest must have exactly one overlay for each resolved language; missing, extra, duplicate, noncanonical, ambiguous, or unknown overlays are rejected.
- Every overlay publishes all 15 localized capability states independently. One projection cannot make another projection appear complete.
- `CatalogTextProjection` resolves only the requested exact overlay. It never borrows another language or falls back to stripped shared text. Shared localized text remains readable only for explicit legacy `UNKNOWN` catalogs that have no overlays.
- Language-neutral `SpeciesRecord.navigable`, `CatalogRuntimeMetadata.areaBaseIds`, and `TypeSemanticRole` prevent navigation, area identity, and mechanics from depending on localized display text.

### Atomic bounded persistence

- Catalog SQL schema version 2 and parser schema revision 48 invalidate the previous catalog representation under the existing catalog-only migration policy.
- The manifest remains a required shared section. Resolved overlays are stored as canonical dynamic sections named `language_overlay:<language-tag>` and are derived from the manifest, not from caller-supplied section inventories.
- Writes are transactional, remove stale dynamic sections, preflight omitted sections, persist section digests and chunks, and publish completion only after the exact manifest-derived inventory is present.
- Reads decode the manifest first and reject missing, extra, or noncanonical overlays before decoding the shared payload.
- Enforcement covers section chunk count, per-overlay encoded and inflated bytes, aggregate overlay encoded and inflated bytes, and whole-catalog encoded and inflated bytes. The production ceilings are 8 MiB encoded and 32 MiB inflated per overlay, 32 MiB encoded and 128 MiB inflated across overlays, and 64 MiB encoded and 256 MiB inflated for the catalog.
- Exact ROM SHA-256 remains the sole cache identity. Active ROM language and interface language are not cache keys.

### Default bootstrap without reparsing

- Companion bootstrap publishes the manifest summary, ROM default language, active language, `ROM_DEFAULT` authority, active overlay version, per-language capability states, and text from `catalog.defaultLocalizedText()`.
- Production Android and standalone companion runtimes share the centralized bootstrap builder.
- A persisted artificial English/French catalog reopens equal to its written source and publishes either exact overlay with zero parser invocations.
- Stage 2 intentionally adds neither a manual ROM-language override nor RAM polling. `ROM_DEFAULT` remains authoritative until Stage 5 introduces validated read-only runtime selection.

### Semantic authority correction

- Generation and numeric type ID remain presentation-only fallbacks; they no longer publish a mechanical `TypeSemanticRole`.
- Damage forecasts use only source-validated semantic roles and otherwise report unresolved type/weather interaction instead of inventing mechanics.
- Decoding official compiled type semantics is tracked as `LNG-D005`: Western controls are owned by Stage 3 and Japanese/Korean controls by Stage 4. Reordered or custom types remain unavailable unless their semantics are independently proven.

## Specification and acceptance audit

| Requirement | Evidence | Result |
|---|---|---|
| `LNG-INV-003` — parse once, select many | Artificial persisted English/French fixture; exact close/reopen equality; parser-invocation spy observes zero calls while publishing both persisted projections | Pass |
| `LNG-INV-004` — separate authorities | Cache identity remains exact ROM SHA-256; bootstrap authority is explicitly `ROM_DEFAULT`; no interface setting, manual content override, or live RAM selector was added | Pass |
| `LNG-INV-005` — shared data stays singular | Overlay model/extractor tests prove shared stats, mechanics, media, encounters, maps, and runtime metadata are not copied into overlays | Pass |
| `LNG-INV-006` — fail closed by capability | Missing localized keys do not borrow French or stripped shared English text; ambiguous/unknown manifests cannot carry overlays; legacy shared fallback is limited to `UNKNOWN` catalogs with no overlays | Pass |
| Section 10.2 — per-language capability state | Every overlay must publish all 15 localized capability domains; capability-state maps are immutable and projection-specific | Pass |
| Section 10.3 — migration and bounded persistence | SQL schema 2/parser schema 48, catalog-only migration tests, manifest-first inventory validation, transaction tests, digest tests, and all encoded/inflated boundary tests pass | Pass |
| Section 11.1 — bootstrap | Manifest/default/active language, `ROM_DEFAULT`, overlay version, localized capabilities, and exact default overlay are emitted by the shared bootstrap builder | Pass |
| Section 14.3 — cache and API | Two-language equality, stale-schema rejection, exact SHA identity, parser-free persisted publication, and default selection after reopen pass | Pass |
| Stage 2 acceptance — stale and oversized data fail closed | Missing/extra/noncanonical sections, oversized chunks/sections/overlays/aggregates/catalogs, and stale revisions are rejected without partial publication | Pass |

## Verification evidence

### Focused and full gates

| Scope | Command | Result |
|---|---|---|
| Migrated environment-backed text/map controls | `gradlew.bat :parser-core:test` with the seven `DescriptionLiveRomTest`, catalog/world-map, Gen I/II/III Local-map, and POI real-control filters, `--rerun-tasks --no-parallel` | `BUILD SUCCESSFUL`; all seven control classes passed |
| Full JVM/Android unit gate | `gradlew.bat :parser-core:test :catalog-store:test :companion-core:test :parser-cli:test :companion-server:test :app:testDebugUnitTest --rerun-tasks --no-parallel --stacktrace --console=plain` | `BUILD SUCCESSFUL` in 28m 29s; 57/57 Gradle tasks executed |
| `parser-core` result files | Same full gate | 169 test files; 1,375 tests; 197 skipped; 0 failures; 0 errors |
| `catalog-store` result files | Same full gate | 3 test files; 76 tests; 6 skipped; 0 failures; 0 errors |
| `companion-core` result files | Same full gate | 18 test files; 124 tests; 1 skipped; 0 failures; 0 errors |
| `parser-cli` result files | Same full gate | 7 test files; 49 tests; 1 skipped; 0 failures; 0 errors |
| `companion-server` result files | Same full gate | 5 test files; 29 tests; 0 skipped; 0 failures; 0 errors |
| Android app result files | Same full gate | 90 test files; 515 tests; 28 skipped; 0 failures; 0 errors |
| Companion web | `npm --prefix companion-web test && npm --prefix companion-web run build` | 35 files and 329 tests passed; TypeScript/Vite production build passed |
| Diff integrity | `git diff --check` | Passed with no output before the implementation checkpoint |

The full JVM/Android gate covers 2,168 tests with 233 environment-gated skips and no failures or errors. No Android device, emulator, ADB, production signing, or release publication was used for Stage 2.

## Authoritative current-corpus evidence

The single final Stage 2 corpus gate ran after the code was final and pushed at `3fced69b453ac7205fc3f8ce8e30cea506f20d5a` through `tools/corpus/Invoke-DualDexCorpusValidation.ps1`. No earlier or subsequent Stage 2 corpus run contributes evidence.

### Provenance

- Current source inventory: 262 archives; 335 supported payloads; 333 unique payload SHA-256 identities.
- Scanner-eligible inputs: 334; 332 unique ROM identities.
- Parser CLI report schema: 13.
- Generator SHA-256: `e622d77c087a27accc06787f738c8df1d2f4cd8c746a2e7a78222a40e16a37d4`.
- Raw-report SHA-256: `28ac378bc05691a1cacf7e566aa2b7e7445082a4c76aa36a41d25604f21d9398`.
- Privacy-safe eligible-input multiset digest: `95c81178e540818952973735e7064cf4afd2c989860e9b075c7ec913a51b311b`, exactly equal to Stage 1.
- Raw reports, individual identities, source paths, SQLite catalogs, and decoded ROM text remain private and uncommitted.

### Selection, language, and persistence outcomes

| Measure | Stage 1 | Stage 2 | Classification |
|---|---:|---:|---|
| Eligible inputs | 334 | 334 | Exact parity |
| Unique eligible identities | 332 | 332 | Exact parity |
| Selected | 279 | 279 | Exact parity |
| Ambiguous | 2 | 2 | Exact parity |
| No family match | 53 | 53 | Exact parity |
| Parser/source errors | 0 | 0 | Exact parity |
| Materialized catalogs | 279 | 279 | Exact parity |
| Persisted and exactly reopened catalogs | 279 | 279 | Exact parity |
| Catalog/persistence errors | 0 | 0 | Exact parity |
| Resolved English layouts | 262 | 262 | Exact parity |
| Explicit `UNKNOWN` layouts | 17 | 17 | Exact parity |
| `gba-english@1` projections | 168 | 168 | Exact parity |
| `gb-english@1` projections | 94 | 94 | Exact parity |

The 262 resolved result rows each persisted the 17 shared sections plus `language_overlay:en`; the 17 `UNKNOWN` rows persisted only the 17 shared sections. Two pairs of selected inputs share exact ROM SHA-256 identities, so the 279 persistence/reopen successes correctly converge on 277 cache files rather than creating language-specific or source-specific cache identities.

A direct post-run audit of all 277 unique SQLite files found:

- 277/277 at SQL schema 2 and parser schema 48;
- 277/277 marked complete with metadata SHA-256 exactly equal to the cache filename;
- 260 resolved manifests with exactly one canonical English overlay and 17 unknown manifests with no overlay;
- all 277 with exactly the required 17 shared sections and no extra/noncanonical dynamic section;
- maximum observed overlay size of 146,687 encoded bytes and 699,604 inflated bytes, below every enforced limit;
- zero schema, identity, manifest, overlay-coverage, or section-inventory errors.

### Shared-capability comparison

Sixteen language-neutral capability distributions are exactly equal to Stage 1: `AREA_ENCOUNTERS`, `BALL_CATALOG`, `BASE_STATS`, `EGG_MOVES`, `EVOLUTIONS`, `LEARNSETS`, `LOCAL_MAP`, `MACHINE_MOVES`, `MOVE_DETAILS`, `SPECIES_CATALOG`, `SPECIES_TYPES`, `SPRITES`, `TUTOR_MOVES`, `TYPE_CHART`, `TYPE_PRESENTATION`, and `WORLD_MAP`.

The remaining expected differences are authority reclassifications caused by the shared/localized split, not parser losses:

- `SPECIES_NAMES`, `POKEDEX_DESCRIPTIONS`, `MOVE_DESCRIPTIONS`, and `ABILITY_DESCRIPTIONS` moved from the shared capability map into per-language overlay state.
- `MOVE_CATALOG` and `ABILITIES` now measure shared ID/mechanical domains without requiring localized names, increasing structural availability.
- `NATURES` now measures ROM-native stat effects and flavor affinities without localized names.
- `ABILITY_MECHANICS` now counts only retained non-localized mechanics. Exact behavior prose copied from localized descriptions no longer masquerades as shared mechanics.

Because the existing report's explicitly legacy `dataCompatibility` classifier still iterates the flat `RomCapability` enum, removing the four localized-only entries from the shared map reclassifies the 20 formerly `COMPLETE` rows as `PARTIAL`; selected totals become 279 partial while all 14 unresolved rows remain non-selected `NO_FAMILY_MATCH` inputs. This legacy aggregate is not evidence of a shared capability regression; the exact shared-domain comparison and persisted per-language capability states are the Stage 2 authorities.

### Existing unrelated reference gap

The raw report contains 533 decoded cross-reference errors, all in Saffron Demo v2.0 and all missing ability references. This is exactly the pre-existing `G3-SAFFRON-001` result documented in `docs/reports/2026-08-26-gen1-gen3-full-corpus-status.md` and owned by Task #182. It did not change selection, catalog materialization, overlay persistence, or exact reopen behavior and is not a Stage 2 localization regression.

## Blocker and deferral audit

### Blockers

None. The shared/overlay model, per-language capability state, exact projection coverage, no-cross-language resolver, schema migration, atomic and bounded persistence, exact reopen equality, default bootstrap publication, parser-free cache publication, exact SHA identity, test gates, and single current-corpus gate are complete.

### Tracked deferrals

The durable ledger remains authoritative:

- `LNG-D001` — expanded fan-translation corpus after official language ratification;
- `LNG-D002` — Japanese/Korean interface packs after Stage 6;
- `LNG-D003` — RTL interface layout with the first supported RTL locale;
- `LNG-D004` — non-production document and store localization as a separate milestone;
- `LNG-D005` — source-validated decoded type semantics, with Western controls owned by Stage 3 and Japanese/Korean controls owned by Stage 4.

Required official Western language-family cells remain mandatory in Stage 3. Required Japanese and Korean parsing cells remain mandatory in Stage 4 and cannot be deferred.

## Final decision

`COMPLETE` — Localization Stage 2 has no open blocker. Stage 3 may ratify official English, French, German, Italian, and Spanish parsing against the persisted overlay architecture. No APK, candidate, tag, or release is authorized by this closure.
