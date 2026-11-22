# B4 release corpus run: parser revision 144 (2026-11-22)

Roadmap stage B4 ([2026-11-21 roadmap](../../superpowers/plans/2026-11-21-compatibility-roadmap.md)): the single full-corpus release gate for v1.2.5. B2 changed shared Gen II paths and B3/B3a changed shared Gen III validators and family selection, so the roadmap rule ("re-run the full corpus only if B2/B3 changed shared parser paths") requires one full run. Nothing else was re-run: every stage before it was qualified on affected inputs plus controls only.

## Run

- Source: `1e8777ee5cbdac1a224134e4d9609a4ee135639a` (master after B0–B3a and the Dependabot #63 guava pin), parser revision 144, SQLite schema 2, report schema 16.
- Parser CLI built through the Gradle gate (`:parser-cli:installDist`); jar manifest `DualDex-Source-Commit` equals the source; runtime frozen into the work root; generator digest `a8a543ff…f935`.
- One batch, one worker, `-Xmx3g`, run once through the pipeline gate (6 GiB cap). Originals were read in place, never copied.
- Originals: all 342 hashed and stat-checked before and after. All unchanged.
- Duration: 1 h 57 m (R1: 2 h 52 m).
- Public evidence: [localization summary](2026-11-22-b4-full342-localization-evidence.json) (release `CORPUS_SUMMARY`), [aggregate summary](2026-11-22-b4-full342-evidence.md) ([json](2026-11-22-b4-full342-evidence.json)) and [execution receipt](2026-11-22-b4-full342-execution.json). Raw report digest `7639a1b5…b761` (3.6 GB, private, streamed; never loaded whole).

## Outcome

| | R1 (141) | B4 (144) |
| --- | --- | --- |
| Selected / ambiguous / unmatched | 301 / 2 / 39 | **310 / 0 / 32** |
| Complete / partial / unresolved | 0 / 332 / 10 | 0 / 332 / 10 |
| Read/parse, catalog, persistence, cross-reference errors | 0 | 0 |
| Catalogs persisted and reopened | 301 | 310 |
| ROM-language manifests resolved / unknown | 286 / 15 | 295 / 15 |
| Mean compatibility | 79.911% | **81.513%** |

Baseline: the R1 full run. Rows are compared by SHA-256 identity: decision, compatibility %, every capability's status and confidence, and the materialized catalog-count row.

- **Regressions: none.** No ROM lost compatibility, a capability, a decision or a catalog count.
- **Improved: 18 ROMs**, each the gain its stage qualification predicted:

| ROM | R1 | B4 | Stage |
| --- | --- | --- | --- |
| Ambrosia (v2.9.0) | 63.10% | 73.62% | B2 |
| Anniversary Crystal (v1.2.3) | 51.81% | 62.34% | B2 |
| Crystal Inheritance (v1.1) | 29.17% | 37.30% | B2 |
| Crystal Kaizo (01.08.26) | 73.68% | 84.21% | B2 |
| Gold 97 Reforged (v6.1f) | 72.22% | 83.33% | B2 |
| Silver 97 Reforged (v6.1f) | 72.22% | 83.33% | B2 |
| Mystic Crystal (v1.0) | 72.55% | 83.08% | B2 |
| Polished Crystal (14.09.26) | 29.17% | 37.50% | B2 |
| Moon Galaxy | unmatched 37.06% | FireRed 81.87% | B3 |
| Sun Sky | unmatched 37.07% | FireRed 86.09% | B3 |
| Scorched Silver (v1.3) | unmatched 8.31% | Emerald 79.17% | B3a C1 |
| Parallel Emerald (v1.2) | unmatched 8.31% | Emerald 75.00% | B3a C1 |
| Team Rocket Edition (v2.1) | unmatched 8.31% | Emerald 70.83% | B3a C1 |
| Scale x Fang (v1.0.2) | 4.15% | 12.30% | B3a C1 |
| Ultra Violet (v1.22) | ambiguous 45.83% | FireRed 99.50% | B3a C3 |
| Lugias Ocean | ambiguous 50.00% | FireRed 91.15% | B3a C3 |
| Pisces (v1.5.4) | unmatched 8.13% | Emerald 54.17% | B3a C4 |
| Sovereign of the Skies (v2.1.2) | unmatched 28.97% | Emerald 53.24% | B3a C4 |

- **Confidence-only changes on capabilities that stay not found (2 rows):** Black & White 3 - Genesis MOVE_DETAILS 0.327→0.331; Voyager (Battle Frontier Demo) BASE_STATS/SPECIES_CATALOG/SPECIES_TYPES 0.015→0.000 and MOVE_DETAILS 0.555→0.000. Outcome and percentage are unchanged. Voyager's drop is the stricter B3 table validation correctly refusing a near-miss; C4 confirmed that build has no recognizable species row at all.
- **Detail-only changes (4 rows, no status, confidence or percentage change):** Elite Redux, Light and Shadow and WaveBlue now list a third ambiguous published data-pointer block (`0x1b4, 0x1b8, 0x1bc` instead of `0x1b4, 0x1bc`), from the B3a 0x1B8 header probe; they still fail closed. Peridot's move domain is now bounded by the compiled Metronome random-move consumer (count 251→254, offset unchanged), from B2.

## Release evidence

- `release/compatibility-evidence.json` now binds this run: scope `NONPARSER_REUSE` (fresh full342 at the frozen source; later commits are evidence/documentation only), cache decision `BUMP_REQUIRED` 140→144 with the seeded prior-revision rejection test in `CatalogStoreTest.kt`. The bounded 1.2.4 Unbound qualification is superseded and removed.
- Language census: a bounded read of each persisted catalog's `language_manifest` section counts 295 resolved / 15 unknown; every resolved catalog carries its overlay. The same method reproduces the published R1-era 286/15 exactly, and the matrix generator independently re-derives 295/15 from the raw report.
- Packaged acceptance: [hosted run 45022647102](https://github.com/Darkaxt/DualScreenDex/actions/runs/45022647102) executed the existing API35 managed-device workflow at the exact source. All **11 cases passed** with zero failures/errors/skips; the single JUnit file's SHA-256 is `fb1a970c…ef06`. The four screenshots were inspected: bootstrap, the injected index/guide recovery states and cache reopen render without a crash dialog. This is debug packaged execution on a disposable hosted emulator, not physical execution of the signed APK.
- The [ROM compatibility matrix](../../rom-compatibility-matrix.md) is regenerated from this run (closes DEF-MATRIX-STALE). Its renderer now prints the actual input count instead of a hardcoded 333.

## Follow-ups

Unchanged from the [B3a ledger](2026-11-22-b3a-gba-focus.md): LIB26-GBA-HEADER-1B8 remainder, LIB26-GBA-EXPANSION-ABI remainder, LIB26-GBA-EXPANSION-LEARNSETS, LIB26-GBA-EXPANSION-DEX-DOMAIN, ZERO-TAIL, TABLE-EXTENT and MOVE-PP stay open for B5+.
