# R1 full-corpus refresh: parser revision 141 (2026-11-21)

Roadmap stage R1 ([2026-11-21 roadmap](../../superpowers/plans/2026-11-21-compatibility-roadmap.md)): one full run of the canonical 342-identity library after B0 and B1. It is the new baseline for B2/B3.

## Run

- Source: `160861feda040e8ad998fb23482ce285d9eba16d` (master after B0+B1), parser revision 141, SQLite schema 2.
- Parser CLI built through the Gradle gate (`:parser-cli:installDist`); generator digest `38d2c5c7…0ff1`.
- One batch, one worker, `-Xmx3g`. Originals were read in place, never copied.
- Originals: all 342 were hashed and stat-checked before and after. All were unchanged.
- Duration: 2 h 52 m. The 139 run took 4 h 05 m on the same corpus and settings.
- Public evidence: [summary](2026-11-21-r1-full342-evidence.md) ([json](2026-11-21-r1-full342-evidence.json)) and [execution receipt](2026-11-21-r1-full342-execution.json). Raw report digest `09159048…ded95`.

## Outcome

| | Parser 139 (+140 overlay) | R1 (141) |
| --- | --- | --- |
| Selected / ambiguous / unmatched | 301 / 2 / 39 | 301 / 2 / 39 |
| Complete / partial / unresolved | 0 / 332 / 10 | 0 / 332 / 10 |
| Read/parse, catalog, persistence, cross-reference errors | 0 | 0 |
| Catalogs persisted and reopened | 301 | 301 |
| Mean compatibility | 79.875% | 79.911% |

Baseline: the 2026-10-18 parser-139 full run, with the five parser-140 Unbound follow-up rows overlaid. Rows are compared by SHA-256 identity. The comparison covers the decision, compatibility %, every capability's status and confidence, and the materialized catalog-count row.

- **Regressions: none.** No ROM lost compatibility, a capability, a decision or a catalog count.
- **Improved: 8 ROMs.** All of them are LOCAL_MAP going from partial to available:

| ROM | 139 | 141 |
| --- | --- | --- |
| Mega Power (v5.71) | 97.06% (158 maps) | 99.51% (384 maps) |
| Dreamstone Mysteries | 93.28% | 95.81% |
| Nameless (v5.45) | 77.18% | 79.17% |
| Cloud White 2 (v279) | 88.96% | 90.64% |
| Cloud White 3 (v277) | 90.01% | 91.69% |
| FireRed Extended (v3.5.6) | 78.13% | 78.95% |
| Fire Red Extended (3.5.6) | 78.10% | 78.95% |
| Resolute (v2.97) | 87.14% (141 maps) | 87.36% (149 maps) |

- **Cause:** at 139 these ROMs hit the 100 M raw-pixel raster budget and skipped maps. At 141 every map is kept as a lossless static tiled raster under the shared storage budgets. This is the representation that the parser-140 follow-up qualified on only five ROMs; R1 confirms it across the whole corpus.
- **Wording-only changes: 3 ROMs** (FireEmerald, Soulgold, Valiant). The LOCAL_MAP evidence line now adds "retained N lossless static tiled rasters"; the counts are identical.

## Follow-ups

- `release/compatibility-evidence.json` still names the 139 run plus the bounded 140 qualification. B4 switches it to this run, or to a later one if B2/B3 change shared parser paths. B4 then binds the release source commit and the cache decision 140→141.
- Not covered by this run: hosted packaged Android acceptance and signing. Those remain B4 release gates.
