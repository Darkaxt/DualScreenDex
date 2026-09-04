# Localization Stage 3 Closure

**Decision:** `COMPLETE`

**Stage branch:** `feat/full-translation-system`

**Synchronized baseline:** `2fef59df95f6deb0ae861aad42cff52eff739a86` (`fork/master`)

**Implementation checkpoint:** `030667d1a1b1584fa1b41232998d7a86d913b57f`

**Official-matrix evidence checkpoint:** `b5c8e8c1d0bb9c30981008095c28eb4ec14e1cd1`

**Specification:** `LNG-INV-001`, `LNG-INV-005`–`LNG-INV-007`, Sections 5–8, 10, 14.1, 14.2, 14.5, and Stage 3 in Section 15 of `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`

## Delivered official Western parsing

### Bounded language and codec authority

- The registry contains generation-specific English, French, German, Italian, and Spanish codecs: `gb-gen1-*`, `gb-gen2-*`, and `gba-gen3-*`, all at codec version 1.
- Gen I and II codecs terminate at `0x50`; Gen III codecs terminate at `0xFF` and preserve `0x00` as whitespace. Golden vectors cover accented glyphs, controls, terminators, invalid bytes, and bounded reads.
- Regional header and game-code evidence only nominate official language candidates. A manifest becomes `RESOLVED` only after compiled structural tables validate with the nominated bounded codec.
- Probe codecs may locate candidate structure but cannot become semantic text authority. The selected manifest projection supplies the exact codec used by downstream names, descriptions, abilities, type labels, records, and relationships.
- English-only `SEED` and Gen II move anchors are explicitly English-scoped in typed and legacy discovery. Gen II species and move roots can instead be recovered from adjacent compiled far-pointer consumers.
- Gen II Town Map names delegate standard glyphs to the selected codec, localize the Technical Machine substitution as `TM`, `CT`, or `MT`, and reject English-only `TRAINER`, `ROCKET`, contractions, and the expanded English dialect under non-English authority.
- Latin word-count, lowercase, and uppercase name-shape checks are limited to the five ratified Western languages. Unratified scripts fail those checks closed until Stage 4 introduces script-aware validation.
- Published Gen III headers, headerless unified moves, and pokeemerald-expansion tables accept and use an explicit supplied codec. Deterministic rejecting-codec tests prove the argument is authoritative rather than an ignored replacement for hidden English decoding. Expansion-specific discovery remains explicitly English-only until `LNG-D001` is ratified.
- Source-backed named ability behavior is available only under a resolved English manifest. Compiled numeric mechanics remain independent of localized names.

### Decoded type semantics and fail-closed optional text

- Official Western type-name tables resolve from compiled ROM structure for the Gen I 27-entry bank-local pointer ABI, Gen II 28-entry bank-local pointer ABI, and Gen III 18-row fixed-width ABI.
- `TypeRecord.semanticRole` is joined only from complete decoded labels validated for the selected language. All 35 official Western controls have complete names and semantic roles, including the Spanish Gen II mystery label.
- Complete decoded standard-label domains can prove semantics independently of assumed numeric order. Reordered or custom type authority remains absent unless its compiled labels independently prove every meaning.
- Missing or malformed optional localized tables remain absent in that projection. No projection borrows English, another Western language, or stripped shared text.
- Independently validated positive ability IDs remain in the shared graph when ability names are unavailable. Ordinary 28-byte base-stat slots and headerless unified-species ability metadata have separate bounded validators.
- Compiled numeric ability mechanics use that validated numeric domain; source-backed named behavior remains gated on decoded English names. Coverage compares exact positive ID sets rather than unrelated cardinalities.

## Specification and acceptance audit

| Requirement | Evidence | Result |
|---|---|---|
| `LNG-INV-001` — ROM content is authoritative | Candidate headers are corroborated by compiled table geometry and codec validation; no filename, path, identity profile, or hash selects a production language | Pass |
| `LNG-INV-005` — shared mechanics stay singular | Numeric ability entities and mechanics remain shared; overlays contain labels only; type roles derive from decoded compiled evidence | Pass |
| `LNG-INV-006` — fail closed by capability | Missing localized keys do not fall back across languages; malformed labels preserve independently validated numeric data but publish no invented text | Pass |
| `LNG-INV-007` — official first, extensible afterward | All 35 applicable Western cells are ratified through generic descriptors, codecs, evidence, and persistence; fan translations remain `LNG-D001` rather than production identities | Pass |
| `LNG-DISC-004` — bounded work | Generation-specific terminators, maximum-byte limits, malformed inputs, pointer-table bounds, reference-scan limits, and cancellation paths have unit coverage | Pass |
| Sections 5–8 — family and language coverage | English, French, German, Italian, and Spanish pass Red/Blue, Yellow, Gold/Silver, Crystal, Ruby/Sapphire, Emerald, and FireRed/LeafGreen | Pass, 35/35 |
| Section 10 — overlay materialization | Every official control produces exactly one matching overlay with all 15 localized capability states and no shared localized fallback | Pass, 35/35 |
| Section 14.2 — parser and catalog acceptance | Every official control selects the expected family and codec, materializes, persists, closes, and reopens through SQL schema 2/parser schema 49 | Pass, 35/35 |
| Section 14.5 — corpus and privacy | The final current-corpus report preserves the exact eligible-input multiset, contains no parser/catalog/persistence/reference errors, and leaves raw reports, paths, names, and SQLite files private | Pass |
| Stage 3 acceptance — no required deferral | All required Western cells pass; every corpus delta is classified below; no Western cell or blocking parser gap remains | Pass |

## Verification evidence

### Focused and full gates

| Scope | Command | Result |
|---|---|---|
| Final authority regressions | `gradlew :parser-core:test` for locale-scoped anchors and plausibility, Gen II landmark dialects/substitutions, exact published-header/headerless-move/expansion codecs, and English-scoped ability behavior | All requested tests passed; the Gen II expanded-dialect regression was observed red before the fail-closed fix and green afterward |
| Full Stage 3 JVM gate | `gradlew :parser-core:test :catalog-store:test :parser-cli:test --rerun-tasks --no-parallel --stacktrace --console=plain` | `BUILD SUCCESSFUL` in 17m 18s; 15/15 Gradle tasks executed |
| `parser-core` result files | Same full gate | 174 suites; 1,418 test cases; 1,221 passed; 197 skipped; 0 failures; 0 errors |
| `catalog-store` result files | Same full gate | 3 suites; 76 test cases; 70 passed; 6 skipped; 0 failures; 0 errors |
| `parser-cli` result files | Same full gate | 7 suites; 50 test cases; 49 passed; 1 skipped; 0 failures; 0 errors |
| Packaged official Western matrix | Source-bound packaged `parser-cli` over 35 exact private controls with an empty cache and four bounded workers | 35 selected; 0 ambiguous; 0 no-family-match; 0 errors; 35 exact persistence/reopen successes |
| Authoritative current corpus | Clean detached `030667d1` worktree; `Invoke-DualDexCorpusValidation.ps1 -Reset` over the current source inventory with four bounded workers | 334 eligible inputs; 279 selected; 2 ambiguous; 53 no-family-match; 0 parser/catalog/persistence/reference errors |

The full JVM gate covers **1,544 test cases: 1,340 passed and 204 skipped**, with zero failures or errors. No Android device, emulator, ADB, production signing, APK, tag, or release publication was used for Stage 3.

## Official Western matrix evidence

`docs/reports/localization/official-western-matrix.json` is the sanitized public authority. Raw ROMs, filenames, decoded text beyond bounded samples, reports, and SQLite catalogs remain private and uncommitted.

### Provenance

- Parser source commit: `030667d1a1b1584fa1b41232998d7a86d913b57f`.
- Parser CLI report schema: 14.
- Generator classpath SHA-256: `f4db0ff2ded9b09dfacd03c674033a09b0a146256ef847a5beb2036ab3e03e53`.
- Raw-report SHA-256: `da6204163ea415a8c11621c83b331d6025d6038349318b1617b19b6defcfecc3`.
- Exact external controls: 35 distinct ROM SHA-256 identities.

### Outcomes

- 35/35 expected family-language cells selected the expected family.
- 35/35 manifests resolved exactly one expected language and generation-specific codec.
- 35/35 catalogs materialized and reopened from the exact SHA-keyed cache.
- 35/35 type tables had complete decoded names and compiled semantic roles.
- 35/35 caches contained exactly 17 shared sections plus the one canonical language overlay.
- Across 525 per-language capability states: 260 `AVAILABLE`, 93 `PARTIAL`, 77 `NOT_FOUND`, and 95 `NOT_APPLICABLE`.
- Cross-language fallback evidence: zero.
- Catalog errors: zero. Persistence errors: zero.

## Authoritative current-corpus evidence

The single replacement Stage 3 closure run executed from a clean detached worktree after parser/catalog code was final, verified, committed, and pushed at `030667d1a1b1584fa1b41232998d7a86d913b57f`. All earlier Stage 3 corpus runs are superseded and contribute no closure evidence.

### Provenance and parity

- Current source inventory: 262 archives; 335 supported payloads; 333 unique payload SHA-256 identities.
- Scanner-eligible inputs: 334; 332 unique ROM identities.
- Privacy-safe input multiset SHA-256: `95c81178e540818952973735e7064cf4afd2c989860e9b075c7ec913a51b311b`, exactly equal to Stages 1 and 2.
- Parser CLI report schema: 14.
- Generator classpath SHA-256: `f4db0ff2ded9b09dfacd03c674033a09b0a146256ef847a5beb2036ab3e03e53`.
- Raw-report SHA-256: `3d77b9818bf5a29f41058cf6604f6be20fc42fa281f746b057639863dd3b0a11`.

| Measure | Stage 2 | Final Stage 3 | Classification |
|---|---:|---:|---|
| Eligible inputs | 334 | 334 | Exact parity |
| Unique eligible identities | 332 | 332 | Exact parity |
| Selected | 279 | 279 | Exact parity |
| Ambiguous | 2 | 2 | Exact parity |
| No family match | 53 | 53 | Exact parity |
| Materialized catalogs | 279 | 279 | Exact parity |
| Persisted and exactly reopened catalogs | 279 | 279 | Exact parity |
| Source/parser/catalog/persistence errors | 0 | 0 | Exact parity |
| Resolved language manifests | 262 | 264 | Two structurally proven Gen II English input rows gained authority |
| Explicit `UNKNOWN` manifests | 17 | 15 | Same two-row authority gain; no identity override |
| Decoded cross-reference errors | 533 | 0 | Generic numeric ability-domain closure |

The 264 resolved rows use `gb-gen1-en@1` 79 times, `gb-gen2-en@1` 17 times, and `gba-gen3-en@1` 168 times. The 15 unresolved selected rows remain explicitly `UNKNOWN`; they publish no overlay and retain only independently validated shared data.

Two pairs of selected inputs share exact ROM SHA-256 identities. The 279 successful persistence/reopen results therefore converge on 277 cache files:

- 277/277 use SQL schema 2 and parser schema 49, are marked complete, and bind metadata identity exactly to the cache filename;
- 262 resolved unique catalogs contain exactly 17 shared sections plus one canonical English overlay;
- 15 unknown unique catalogs contain exactly the 17 shared sections and no overlay;
- zero schema, identity, manifest, overlay, coverage, or section-inventory errors were found;
- maximum observed overlay size was 147,248 encoded bytes and 708,178 inflated bytes, below every enforced limit.

### Capability-delta classification

Thirteen shared capability distributions remain exactly equal to Stage 2: `AREA_ENCOUNTERS`, `BALL_CATALOG`, `BASE_STATS`, `EGG_MOVES`, `EVOLUTIONS`, `LOCAL_MAP`, `MACHINE_MOVES`, `NATURES`, `SPECIES_TYPES`, `TUTOR_MOVES`, `TYPE_CHART`, `TYPE_PRESENTATION`, and `WORLD_MAP`.

All seven changed distributions are explained structural gains or fail-closed accounting corrections:

| Capability | Stage 2 → Final Stage 3 | Classification |
|---|---|---|
| `ABILITIES` | one `NOT_FOUND` becomes `AVAILABLE` | Independently validated positive ability IDs now form a shared entity domain without requiring localized names |
| `ABILITY_MECHANICS` | one `NOT_FOUND` and one `AVAILABLE` become `PARTIAL` | One numeric mechanic domain is recovered; exact ID-set coverage exposes one formerly overstated complete result |
| `LEARNSETS` | eight `AVAILABLE` become `PARTIAL` | Row-level completeness reports malformed or unavailable rows instead of treating the valid subset as complete; valid relationships remain available |
| `MOVE_CATALOG` | one `PARTIAL` becomes `AVAILABLE` | A complete typed retail move domain is recovered from bounded compiled references |
| `MOVE_DETAILS` | one `AVAILABLE` becomes `AMBIGUOUS` | Conflicting complete record formats at one root fail closed instead of selecting by incidental ordering |
| `SPECIES_CATALOG` | 26 `PARTIAL` become `AVAILABLE` | Generation-specific codec authority and stronger compiled table roots complete previously partial semantic domains |
| `SPRITES` | seven `AVAILABLE` become `PARTIAL` | Coverage is measured against the strengthened semantic species domain; decoded sprites remain available while missing records are explicit |

The pre-final diagnostic exposed 5,498 dangling ability references across nine unique catalogs after unknown-language text was correctly withheld. The final generic numeric-domain fix closes all nine without inventing labels. The replacement corpus has zero cross-reference errors across all 279 selected catalogs, including the Saffron reference control, satisfying the durable Task #182 acceptance condition without an identity, hash, fixed root, allowlist, or per-ROM profile.

## Mandatory blocker and deferral audit

There is no open `STOP-SAFETY` or `STOP-CORE` blocker. Every required Western family-language cell passes exact parse, materialization, persistence, close/reopen, codec, type-semantics, and contamination checks.

| ID | Spec clause | Stage found | Classification | Observed | Expected | Evidence | Temporary disposition | Owner | Target | Acceptance | Status | Closure commit |
|---|---|---:|---|---|---|---|---|---|---|---|---|---|
| `LNG-D001` | Section 17, expanded fan-translation corpus | Design; Stage 1 | `POST-SYSTEM` | The official Western matrix is ratified, but 15 selected current-corpus layouts still have `UNKNOWN` text authority. | Source-backed and structurally characterized fan translations use the generic language manifest, codecs, and evidence without production identities. | Final Stage 3 corpus audit; `official-western-matrix.json` | Unknown fan/hack text publishes no overlay; independently validated shared data remains available. | Post-official localization corpus stage | First post-official localization corpus stage | Selected fan translations publish validated manifests through the generic architecture and pass sanitized corpus evidence. | Deferred | — |
| `LNG-D002` | Sections 12.1 and 17, Japanese/Korean interface packs | Design | `POST-SYSTEM` | Japanese and Korean ROM parsing is required in Stage 4, but matching interface dictionaries are not in the Stage 6 Western UI set. | Complete typed dictionaries, native resources, font/line-break review, accessibility checks, and compact-layout validation. | Specification `LNG-D002` | The production interface remains English-only until Stage 6; ROM-native content remains catalog data. After Stage 6, unsupported interface locales use the specified `AUTO`/English fallback. | Interface-locale expansion stage | Post-Stage 6 interface expansion | Both locales pass dictionary completeness, native resource, accessibility, and compact-layout gates. | Deferred | — |
| `LNG-D003` | Sections 13.4 and 17, RTL interface layout | Design | `POST-SYSTEM` | The first interface ratification set is left-to-right only. | The first supported RTL locale has direction-aware layout and bidirectional text validation. | Specification `LNG-D003` | No RTL locale is advertised or bundled. | RTL interface expansion stage | First supported RTL interface locale | Logical CSS properties, direction-aware navigation/icons, bidirectional tests, and compact-layout acceptance pass. | Deferred | — |
| `LNG-D004` | Sections 12.5 and 17, non-production documents | Design | `POST-SYSTEM` | Engineering and release material remains English. | A separately commissioned document/store localization stage defines locale set, ownership, review, and publication. | Specification `LNG-D004` | Non-production material remains English and is not represented as translated. | Documentation/release localization stage | Separately commissioned milestone | Named document/store locale set, owner, review process, and publication path are implemented. | Deferred | — |
| `LNG-D005` | `LNG-INV-005`, `LNG-INV-006`, Sections 10.1, 14.2, Stages 3–4 | Stage 2 | `CONVERGENCE` | All 35 Western controls have decoded type semantics; Japanese/Korean controls remain Stage 4. Complete standard-label domains prove roles independently of numeric order, while unproven reordered/custom domains remain absent. | Every official control derives `TypeRecord.semanticRole` only from decoded, structurally validated compiled labels; custom/reordered types remain unavailable unless independently proven. | `CompiledTypeNameResolver.kt`; `official-western-matrix.json`; materialization/cache/forecast tests | Proven standard domains publish roles; unproven custom, reordered, unknown, or ambiguous authority is omitted and damage forecasting reports unresolved interaction. | Stage 4 | Stage 4 Japanese/Korean official parsing | Every remaining Japanese/Korean control populates semantics from decoded evidence and passes materialization/cache/forecast tests; custom/reordered types continue to fail closed unless proven. | Open | — |

No required Western control is deferred. Required Japanese and Korean parser cells are Stage 4 scope and cannot be deferred there.

## Final decision

`COMPLETE` — Localization Stage 3 has no open blocker. Stage 4 may implement official Japanese and Korean ROM parsing and complete `LNG-D005`. No APK, candidate, tag, production signing, or release is authorized by this closure.
