# Localization Stage 1 Closure

**Decision:** `COMPLETE`

**Stage branch:** `feat/full-translation-system`

**Synchronized baseline:** `2fef59df95f6deb0ae861aad42cff52eff739a86` (`fork/master`)

**Implementation checkpoint:** `25297d75af790c69ecd984dcc9a8fa8057520889`

**Specification:** Sections 2–7, 14.1, 14.5, and Stage 1 in Section 15 of `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`

## Delivered authority foundation

### Language, evidence, and layout model

- `LanguageTag` is normalized, extensible, and independent of the registered first-party language set.
- `LanguageEvidence`, `RomLanguageProjection`, `RomLanguageManifest`, and `LocalizedTableLayout` enforce bounded confidence, unique projections, default-projection membership, stable codec identity, and explicit `RESOLVED`, `AMBIGUOUS`, or `UNKNOWN` outcomes.
- Header parsing retains complete raw GB/GBC title and identifier evidence and the complete GBA title/game-code evidence while preserving the existing sanitized display fields.
- Probe codecs remain structural decoding tools only. `RomLanguageAuthority` grants English authority only after selected table geometry, exact codec applicability, and bounded language-specific content corroboration agree. Header markers seed candidates but cannot independently resolve language; canonical English controls are optional corroboration and are excluded from the language classifier.

### Codec and cancellation contract

- `PokemonTextCodec` now consumes bounded tokens and reports consumed bytes, termination, glyph/control/invalid-unit counts, and cancellation.
- The Generation I/II and Generation III English codecs preserve their previous glyph/control/terminator output through golden parity tests while supporting future variable-width codecs.
- Variable, pointer, and fixed-record text paths enforce caller-supplied byte ceilings. Long scans and name materializers propagate `ParserCancellationToken`; whole-ROM scans check cancellation at least every 4,096 bytes.

### Single parser-selected authority

- Each selected family layout carries one immutable `RomLanguageManifest`; each resolved projection carries exact `codecId` and `codecVersion`.
- `ParsedCatalog` receives that parser-selected manifest and exact codec authority. Final record, relationship, description, ability, nature, map, and location materializers no longer reconstruct a codec from generation or directly choose `gbEnglish`/`gbaEnglish`.
- Catalog schema revision 47 persists `language_manifest` as a required section. Revision-46 catalogs invalidate, and write/close/reopen equality includes the manifest.

### Fail-closed behavior

- Unknown or ambiguous language removes only localized text. Numeric species and move domains, stats, types, mechanics, media, encounters, maps, runtime layouts, capability evidence, and save-facing identities remain available when independently validated.
- App, companion-core, and companion-web consumers accept missing localized names and use numeric labels such as `Pokémon #25`, `Ability #9`, and `Move #85` rather than inventing English text.
- A structural world-map fallback is emitted only when the complete structural location set is unnamed; partial localized gaps do not manufacture mixed-language map labels.

## Verification evidence

| Scope | Command | Result |
|---|---|---|
| `parser-core` full suite | `JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test --rerun-tasks --stacktrace` | 167 test files, 1,358 tests, 197 skipped, 0 failures, 0 errors |
| `catalog-store` and `companion-core` full suites | `JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :catalog-store:test :companion-core:test --stacktrace` | 69 catalog-store tests with 6 skipped and 123 companion-core tests with 1 skipped; 0 failures, 0 errors |
| `companion-web` full suite and production build | `npm --prefix companion-web test -- --run && npm --prefix companion-web run build` | 35 files and 329 tests passed; TypeScript/Vite production build passed |
| Android app unit suite | `JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:testDebugUnitTest --stacktrace` | 90 test files, 512 tests, 28 skipped, 0 failures, 0 errors |
| Focused language authority | Owning Gradle test filters recorded in implementation checkpoint `25297d75` | Header preservation, model invariants, token bounds, cancellation, codec parity, custom English without retail literals, French contamination rejection, unknown/ambiguous numeric preservation, exact-codec propagation, and schema-46 invalidation all passed |

No Android device, emulator, ADB, production signing, or release publication was used for Stage 1.

## Authoritative current-corpus evidence

The final Stage 1 corpus gate ran once at implementation checkpoint `25297d75af790c69ecd984dcc9a8fa8057520889` through `tools/corpus/Invoke-DualDexCorpusValidation.ps1`. The current main corpus is authoritative; superseded and historical ROM revisions are not closure inputs.

### Provenance

- Current source inventory: 262 archives; 335 supported payloads; 333 unique payload SHA-256 identities.
- Scanner-eligible parser inputs: 334; 332 unique ROM identities. The remaining payload is outside the existing mainline-family scanner scope.
- Parser CLI report schema: 13.
- Generator SHA-256: `e8afd9048ef065a48454a4ad1e2bd19b4307467c8bcc242148e5ba78a387a049`.
- Raw-report SHA-256: `dbb82b66801a0c35b1e2e58f36ffb05a5eed7fe095ed3d7418a8abf9531b058a`.
- Privacy-safe eligible-input multiset digest: `95c81178e540818952973735e7064cf4afd2c989860e9b075c7ec913a51b311b`.
- Raw reports, individual identities, source paths, and decoded ROM text remain private and uncommitted.

The bounded source view preserved the current archive set while making it executable under the guarded wrapper: current solid archives were repacked without changing member payloads, and two byte-identical GBA inputs had only the 1,131-byte trailer beyond the 32 MiB cartridge address space removed. Original source archives were not modified.

### Outcomes

| Measure | Current Stage 1 result | Existing committed aggregate | Classification |
|---|---:|---:|---|
| Eligible inputs | 334 | 333 | Current corpus contains one additional scanner-eligible input |
| Unique eligible identities | 332 | 331 | Current corpus contains one additional unique eligible identity |
| Selected | 279 | 278 | `+1`; no aggregate selection loss |
| Ambiguous | 2 | 2 | Unchanged |
| No family match | 53 | 53 | Unchanged |
| Parser/source errors | 0 | 0 | Unchanged |
| Complete data compatibility | 20 | 20 | Unchanged |
| Partial data compatibility | 300 | 302 | Current-source and explicit language-authority classification change |
| Unresolved data compatibility | 14 | 11 | All 14 are non-selected `NO_FAMILY_MATCH` inputs; no selected catalog became unresolved |
| Materialized catalogs | 279 | 278 | Equals the selected count |
| Persisted and reopened catalogs | 279 | 278 | Every materialized catalog reopened equal to its parsed source |
| Catalog/persistence errors | 0 | 0 | Unchanged |

The comparison baseline is the committed privacy-safe Stage 7 aggregate in `docs/reports/qa-hardening/stage-07-corpus-evidence.json`. Per direction, no separate historical or pre-Stage-1 full corpus was rerun. The current main-corpus result above is the sole Stage 1 corpus authority.

### Language and persistence totals

- 262 selected layouts resolved one English projection.
- Exact codec identities were 168 `gba-english@1` projections and 94 `gb-english@1` projections.
- 17 selected layouts remained explicitly `UNKNOWN`; none was mislabeled English and none borrowed English text. Every one still materialized a partial numeric/structural catalog.
- All 279 selected catalogs persisted and reopened with all 17 required schema-47 sections, including `language_manifest`.
- No selected catalog was `UNRESOLVED`; selected data compatibility was 20 complete and 259 partial.

The 17 unresolved fan/hack language projections are within the already tracked post-official scope `LNG-D001`. Stage 1 does not claim universal fan-translation language recognition. Their fail-closed text disposition is required by `LNG-INV-006`; their numeric, structural, media, map, runtime, capability, and mechanical data remains independently available.

## Corpus execution audit

Two earlier source selections are excluded from closure evidence:

1. A mixed view containing deprecated/superseded archives completed 355 eligible inputs. It is not the current corpus and establishes no Stage 1 result.
2. A reconstructed historical view was stopped during archive integrity checking before parsing. It produced no parser evidence and was deleted.

The first attempt against the unbounded archive representation also stopped before parsing when the 32 MiB member ceiling triggered. The safety ceiling was not raised. None of these attempts contributed rows, counts, or receipts to the authoritative result.

## Blocker and deferral audit

### Blockers

None. The required model, header evidence, bounded codec contract, cancellation, parser-to-catalog authority handoff, fail-closed numeric preservation, schema invalidation, focused regressions, full suites, and current source-bound corpus gate are complete.

### Tracked deferrals

No new untracked gap was found. The durable ledger remains authoritative:

- `LNG-D001` — expanded fan-translation corpus after official language ratification;
- `LNG-D002` — Japanese/Korean interface packs after Stage 6;
- `LNG-D003` — RTL interface layout with the first supported RTL locale;
- `LNG-D004` — non-production document and store localization as a separate milestone.

Required official Western cells remain mandatory in Stage 3. Required Japanese and Korean parsing cells remain mandatory in Stage 4 and cannot be deferred.

## Final decision

`COMPLETE` — Localization Stage 1 has no open blocker. Stage 2 may build the shared catalog plus localized-overlay persistence model on this authority foundation. No APK, candidate, tag, or release is authorized by this closure.
