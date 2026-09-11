# Localization Stage 4 Closure

**Decision:** `COMPLETE`

**Stage branch:** `feat/full-translation-system`

**Synchronized baseline:** `2fef59df95f6deb0ae861aad42cff52eff739a86` (`fork/master`)

**Final parser evidence source:** `5e2e10c256f8aaccccc7f7f0ff5c4508526b70d8`

**Final corpus source:** `1f13bf3f4a224f585c0aafd552b8b89cbab883b0`

**Parser / SQL / codec schemas:** `81 / 2 / 1`

**Specification:** `LNG-INV-001`, `LNG-INV-005`–`LNG-INV-007`, Sections 5–8, 10, 14.1, 14.2, 14.5, and Stage 4 in Section 15 of `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`

## Delivered official Japanese and Korean parsing

Stage 4 completes the official Gen I–III content-language matrix. Japanese Red/Green, Blue, Yellow, Gold/Silver, Crystal, Ruby/Sapphire, Emerald, and FireRed/LeafGreen are covered alongside separate Korean Gold and Silver controls. The 35 Stage 3 Western cells remain part of the final combined matrix.

Language selection remains structural and fail-closed:

- Regional headers nominate candidates but never establish language authority.
- Exact generation/language codecs decode bounded compiled-ROM tables and scripts.
- No filename, path, ROM identity, hash, fixed address, or per-ROM profile selects a production language.
- Public source repositories are structural oracles; each exact compiled ROM remains execution authority.
- Missing, malformed, ambiguous, or unsupported localized content is withheld by capability while independently validated numeric, geometry, media, and relationship data survives.
- Runtime substitutions in a first displayed headline are contextual. A proven first static headline remains authoritative when substitutions occur only later.
- Direct text requires positive bounded compiled message authority. Textless and contextual classifications also require positive compiled proof.
- Every retained POI remains in the `POI_TEXT` denominator. Later paragraphs or nearby labels cannot replace a required first headline.

## Final official matrix

`docs/reports/localization/official-language-matrix.json` is the sanitized public authority. Raw ROMs, filenames, private paths, decoded control values, raw reports, proof fixtures, and SQLite catalogs remain outside public assets.

| Measure | Required | Validated |
|---|---:|---:|
| Family-language cells | 43 | 43 |
| Exact controls | 44 | 44 |
| Mandatory check records | 308 | 308 |
| Capability dispositions | 660 | 660 |
| `ACCEPTED` dispositions | 506 | 506 |
| `EXCLUDED` dispositions | 154 | 154 |
| Blockers | 0 | 0 |

### Evidence provenance

- Parser evidence source commit: `5e2e10c256f8aaccccc7f7f0ff5c4508526b70d8`.
- Matrix-validator commit: `d4d8c4ec3ebc797bda439de3f8ec2e143bf33b90`.
- Matrix plan SHA-256: `1a7fbf4001c72b275a3744d6a32fc8c76d5138a8ed597e4493fec461fae73aa0`.
- Matrix validation-result SHA-256: `a4e3b9ce9408e7e8cb9b5d7afa1cbe3f5b2e64ac9f89a9ee27e36f8f3b476491`.
- Independent source-evidence SHA-256: `fe70614ab53a03a865505003fd02976cc43281c695ff4eddf1133791c11dea92`.
- Public matrix-file SHA-256: `13be1587fa94538a5a7e4353c2d04b1e3703f629c9fe010781cc7f50e63426c5`.

The parser evidence source precedes two non-semantic corrections included in the final corpus source: fixture-only POI authority declarations and the logical-digest capacity correction described below. Neither changes parser output or the independently accepted matrix observations.

### Evidence-layer separation

- **G0** records parser reports, receipts, and persisted caches.
- **G1** normalizes source-bound observations.
- **G2** executes the independently declared codec golden vectors.
- **G3** observes the actual production API from reopened caches through `ProductionCompanionRuntime`. It remains `acceptance=false`, has scope `CACHE_ONLY_OBSERVATION`, invokes the parser zero times, and proves cache SHA/logical-digest parity. G3 does not establish linguistic truth.
- **G4** contains independently reviewed semantic and linguistic oracles and proof documents. It is the authority for accepted fields, exclusions, contextual text, textless scripts, unresolved scripts, and reserved slots.
- **G6** assembles the complete pinned plan deterministically without manufacturing expectations.

Persisted and report JSON are compared semantically by omitting null-valued object members while preserving list entries and every non-null value. This permits serializer-equivalent omitted versus explicit nullable members without weakening language authority.

## Verification evidence

### Host gates

The final Stage 4 JVM gate completed with `BUILD SUCCESSFUL in 19m 57s`. Its retained XML contains:

| Module | Cases | Passed | Skipped | Failures / errors |
|---|---:|---:|---:|---:|
| `parser-core` | 2,054 | 1,853 | 201 | 0 |
| `catalog-store` | 115 | 109 | 6 | 0 |
| `companion-core` | 125 | 124 | 1 | 0 |
| **Total** | **2,294** | **2,086** | **208** | **0** |

The official-matrix validator suite passed 68/68 cases before the final real plan validated all 44 controls with zero blockers. The complete matrix includes parser selection, exact codec and manifest authority, materialization, actual SQLite close/reopen equality, logical digest parity, cache-only API parity, semantic field samples, capability dispositions, and contamination/fail-closed checks.

No Android device, emulator, ADB, APK, production signing, tag, release, or live battle execution was used for Stage 4.

## Final current-corpus evidence

The only successful Stage 4 closure corpus executed after executable parser/catalog code stabilized and was committed at `1f13bf3f4a224f585c0aafd552b8b89cbab883b0`. It used the current authoritative inventory; deprecated and superseded archives were not closure inputs.

### Provenance

- Current archives: 262.
- Supported payload rows: 335.
- Unique payload SHA-256 identities: 333.
- Scanner-eligible inputs: 334.
- Parser CLI report schema: 16.
- Final report SHA-256: `518ac6fb79d0b6700f58e40b251061002abdda51ce06985daa8f1c573ba99821`.
- Execution receipt SHA-256: `10ad17dc0a9875bf00a7c47decf669c0fdd6fc9fb2f60b57a3f478c70313b02f`.
- Archive-manifest SHA-256: `65079880d456e53f7ea1e43c43acd2391e5becd0ae14f75642c8ff7ef5feb577`.
- ROM-manifest SHA-256: `5197a04c45361d9747a8ba27fd4d395d6871bbf2161b87c4b4021c4f19547cc2`.

### Outcomes

| Measure | Final Stage 3 | Final Stage 4 | Classification |
|---|---:|---:|---|
| Eligible inputs | 334 | 334 | Exact parity |
| Selected | 279 | 277 | Two rows sharing one identity now fail closed |
| Ambiguous | 2 | 2 | Exact parity |
| No family match | 53 | 55 | Same two-row fail-closed correction |
| Successful persistence observations | 279 | 277 | Every selected row |
| Unique cache files | 277 | 276 | One duplicated selected identity |
| Resolved selected manifests | 264 | 262 | Same two rows no longer select |
| `UNKNOWN` selected manifests | 15 | 15 | Exact parity |
| Parser/catalog/persistence/reference errors | 0 | 0 | Exact parity |

All 277 selected rows have successful persistence observations and matching before/after logical catalog digests. The 262 `RESOLVED` selections publish their proven overlay. The 15 `UNKNOWN` selections publish no overlay and retain independently validated numeric data.

### Stage 3 delta classification

Exactly two rows, sharing one ROM identity, changed from selected Crystal to no-family-match. Strict variable-name validation encounters an unterminated variable-length move-name record, so the next record boundary cannot be proven. Move data remains structurally compatible, but move names do not; the complete family score correctly remains below the minimum. Restoring unsafe boundary inference or lowering the selection threshold would violate fail-closed authority.

Among rows selected in both corpora, exactly two Gold/Silver rows changed `WORLD_MAP` from `NOT_FOUND` to `AVAILABLE`. The promotion is supported by a validated compiled 48-tile request/decode, a two-plane 2bpp palette-map chain, a six-palette asset chain, and 47 encounter maps joined through compiled header, landmark, and region evidence. No other stable-selected capability-status delta occurred.

## Logical-digest diagnostic and correction

The first final-corpus attempt exited its wrapper successfully but contained 22 row-level failures: 20 `local_maps` sections and two `species` sections exceeded a stale 32 MiB logical-evidence section ceiling. Root-cause tracing established that `CatalogLogicalDigest.sha256(catalog)` runs before `CatalogCache.write(...)`; SQLite had not been entered. The failures were therefore not SQLite corruption and did not demonstrate duplicated localization data.

Prior valid persisted catalogs already contained affected sections above 32 MiB while remaining below persistence limits. The correction aligns the logical-digest section maximum with the persisted 128 MiB section ceiling and permits the persisted 256 MiB catalog ceiling plus a bounded 1 MiB canonical-envelope allowance. Canonical bytes for previously accepted catalogs are unchanged, so logical-digest version 1 remains valid. A regression above the former ceiling was red before the correction and green afterward; the full `catalog-store` suite and final JVM gate passed.

The failed attempt is retained only as diagnostic evidence. The single replacement closure corpus is semantically identical to it across all 334 input keys, parser results, catalog summaries, and compatibility summaries; the only change is removal of all 22 pre-write digest failures and completion of the remaining persistence observations. No further Stage 4 corpus run is required.

## Blocker and deferral audit

`LNG-B001`, `LNG-B002`, `LNG-B003`, and convergence item `LNG-D005` are closed. There is no open Stage 4 `STOP-SAFETY` or `STOP-CORE` blocker and no required official cell is deferred.

The following post-system work remains open with its existing owner, target, acceptance condition, and fail-closed disposition:

- `LNG-D001` — expanded fan-translation and language-unresolved corpus support.
- `LNG-D002` — Japanese and Korean interface packs.
- `LNG-D003` — first supported RTL interface locale.
- `LNG-D004` — non-production document and store localization.

## Privacy and publication boundary

The public matrix, closure, and ledger contain hashes, aggregate counts, typed dispositions, and implementation provenance only. They contain no ROM bytes, private source paths, raw memory, decoded private strings, credentials, signing material, proof fixtures, raw reports, or SQLite catalogs. Production signing remains confined to protected GitHub Actions environments and was not invoked.

## Final decision

`COMPLETE` — Localization Stage 4 has no open blocker. Stage 5 and Stage 6 have not begun. Per the requested pause boundary, no runtime-language-selection, interface-translation, Android/device, signing, release, or cleanup work follows this publication checkpoint.
