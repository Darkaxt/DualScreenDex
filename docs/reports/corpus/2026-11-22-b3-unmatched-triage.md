# B3 unmatched and ambiguous rows: triage ledger and placeholder-slot fix (2026-11-22)

Roadmap stage B3 ([2026-11-21 roadmap](../../superpowers/plans/2026-11-21-compatibility-roadmap.md)), item `DEF-ROWS-41`. The [R1 baseline](2026-11-21-r1-corpus-refresh.md) left 41 rows unselected: 39 `NO_FAMILY_MATCH` and 2 `AMBIGUOUS`. Every row now has an ID, a cluster and a target. The largest cluster that can select with a generic rule was implemented.

Row IDs are `ROW-<first 12 hex of the SHA-256>`, the same prefix the corpus library uses for its directories.

## Clusters

| Cluster | Rows | Cause | Target |
| --- | --- | --- | --- |
| C1 Published-header pointer block | 7 | Emerald-derived hacks whose published data header is ambiguous between the 0x1B4 and 0x1BC layouts, or sits at 0x1B8 (one save field removed), or has zeroed ability/ball slots. The header is only the entry point: each also has a per-hack species ABI (innates, widened abilities, relocated names). | GBA focus stage (`LIB26-GBA-HEADER-1B8`): a 0x1B8 candidate plus leading-run scoring, then per-ABI species support. |
| C2 Expanded-species placeholder slots | 7 | FireRed/Emerald hacks with expanded species tables (DPE/CFRU style). Unused slots are empty records, which the base-stat validator counted as invalid; one move record per table has PP above 64. | **B3 (this stage)** for the rows whose table extent is proven; the rest named below. |
| C3 FireRed vs. Emerald tie | 2 | Both probes score 80 on identical vanilla FireRed tables; the type-chart tie-break does not separate them. | GBA focus stage (`LIB26-GBA-FRLG-EM-TIE`): an engine discriminator from compiled code, not tables. |
| C4 Modern pokeemerald-expansion | 9 | `SpeciesInfo` layouts other than the supported 152/160-byte ones, or stale published pointers into vanilla tables. | GBA focus stage (`LIB26-GBA-EXPANSION-ABI`), per expansion version. |
| C5 FireRed-header outliers | 1 | The Nuzlomizer: base stats 14/411, no move names. Randomizer-relocated tables. | GBA focus stage, after C1–C4. |
| C6 Non-mainline / garbled header | 6 | Header does not identify a mainline engine (GB-style header in a .gba, `UNKNOWN`, `TARC2PKM`). | Unsupported engine unless a source-backed engine is found. |
| C7 Pokémon TCG | 3 | Trading Card Game engine, not a mainline engine. | Unsupported engine. |
| C8 Gen I/II custom | 6 | Custom Gen I/II engines (Prism, Orange, Peridot, Brown, Fools Gold, BW3 Genesis). | Gen I/II follow-up (`LIB26-G12-CUSTOM`), after the GBA focus. |

## Row ledger

| ID | ROM | Cluster | Target / reason |
| --- | --- | --- | --- |
| ROW-35cbc369eb8f | Light and Shadow (V2.0.4) | C1 | GBA focus: 0x1BC header with zeroed ability slots |
| ROW-3698392d2760 | Scorched Silver (v1.3) | C1 | **Selected in B3a** (Emerald, 0x1B8 header at 20f4b0af) |
| ROW-55b887450b93 | Elite Redux (2.65.3b) | C1 | GBA focus: 0x1BC header, missing ball slots; innates species ABI (source: `Game Boy Advance/Elite Redux`) |
| ROW-560c746d5b9b | Team Rocket Edition (v2.1) | C1 | **Selected in B3a** (Emerald, 0x1B8 header at 20f4b0af) |
| ROW-57d9ee54b152 | Parallel Emerald (v1.2) | C1 | **Selected in B3a** (Emerald, 0x1B8 header at 20f4b0af) |
| ROW-8f58e67c994e | Scale x Fang (v1.0.2) | C1 | GBA focus: header at 0x1B8 |
| ROW-9c66085c61cf | WaveBlue (v1.9.4) | C1 | GBA focus: 0x1BC header with zeroed ability slots |
| ROW-87766f522368 | Moon Galaxy | C2 | **Selected in B3** (FireRed). MOVE_DETAILS stays not found: move 506 is active with PP above the typed codec's ceiling of 64 (`LIB26-GBA-MOVE-PP`) |
| ROW-f5bf7340a5ea | Sun Sky | C2 | **Selected in B3** (FireRed) |
| ROW-e907c970a70b | Elysium (Part B) (v2.5.0) | C2 | GBA focus: base-stat table ends in a 40-row zero tail, so its extent is unproven (`LIB26-GBA-ZERO-TAIL`) |
| ROW-186a36cd389a | Gaia (v3.2) | C2 | GBA focus (`LIB26-GBA-TABLE-EXTENT`): base-stat region runs into learnset data from row 915, and its move table has no proven boundary; both extents need their own bound |
| ROW-15df05788ca9 | Recharged Emerald (v2.2.8) | C2 | GBA focus: base stats interleave with learnset data from row 414; sprites unresolved |
| ROW-442c38ee4ac5 | Recharged Yellow (v1.9.7) | C2 | GBA focus: same layout as Recharged Emerald |
| ROW-64712b2e16c5 | Noon (Completed) | C2 | GBA focus: header matches neither family; base stats run into learnsets from row 203 |
| ROW-467fe116ff6d | Ultra Violet (v1.22) | C3 | **Selected in B3a** (FireRed, GetMoney discriminator at c4697b98) |
| ROW-c632fed60254 | Lugias Ocean | C3 | **Selected in B3a** (FireRed, GetMoney discriminator at c4697b98) |
| ROW-0c31f9415c7b | Too Many Types (v1.6) | C4 | GBA focus: expansion SpeciesInfo stride |
| ROW-7bb7df60a2ed | Too Many Types 2 (v1.5.2) | C4 | GBA focus: expansion SpeciesInfo stride |
| ROW-87686967d562 | Unova Emerald (2.0.3) | C4 | GBA focus: stale published names pointer |
| ROW-c68f5d2932f6 | Voyager (v0.3.6) | C4 | GBA focus: expansion SpeciesInfo stride |
| ROW-1108eccf0fa9 | Voyager (Battle Frontier Demo) (v1.1) | C4 | GBA focus: expansion SpeciesInfo stride |
| ROW-5a3bde580eb6 | Pisces (v1.5.4) | C4 | GBA focus: expansion SpeciesInfo stride |
| ROW-d756b2783ded | Lazarus (v2.0) | C4 | GBA focus: expansion SpeciesInfo stride |
| ROW-b6b7cb019394 | Recordkeepers (v1.2.1) | C4 | GBA focus: non-standard header (code at 0x100) |
| ROW-ded48b60accf | Sovereign of the Skies (v2.1.2) | C4 | GBA focus: 36-byte stats validate, names 940/1208 (source: `Game Boy Advance/Sovereign of the Skies`) |
| ROW-0166385dc909 | The Nuzlomizer (3.0.1) | C5 | GBA focus, after C1–C4 |
| ROW-07dcceb75eee | Harvestcraft (v2.0) | C6 | Unsupported engine: header `UNKNOWN`, not a mainline engine |
| ROW-1e1180b8dd30 | Order & Chaos Remastered | C6 | Unsupported engine: garbled header |
| ROW-293ad62d949c | Sors (v1.3) | C6 | Unsupported engine: garbled header |
| ROW-6348ca6218cd | Septo Conquest (v3.1) | C6 | Unsupported engine: garbled header |
| ROW-e7360e78bc43 | Let´s Go Pikachu (v6.0) | C6 | Unsupported engine: garbled header |
| ROW-be2864da219b | Palimpsest (v1.2.0) | C6 | Unsupported engine: `TARC2PKM` header |
| ROW-4d860eae2555 | TCG - Neo (v1.43) | C7 | Unsupported engine: Pokémon TCG |
| ROW-d4cf20a84964 | TCG - Neo (Legacy) (v1.43) | C7 | Unsupported engine: Pokémon TCG |
| ROW-c171ee7d5aa5 | TCG - Generations (1.7.2b) | C7 | Unsupported engine: Pokémon TCG |
| ROW-037f5ba91395 | Orange (Suloku Patch 2026.0.2 PSS) | C8 | Gen I/II follow-up |
| ROW-22dc749a396a | Peridot Version (v2.3.0) | C8 | Gen I/II follow-up |
| ROW-770c9b7dfbaf | Fools Gold (v1.3.2) | C8 | Gen I/II follow-up |
| ROW-b43cb68c9a62 | Black & White 3 - Genesis (24.12.23) | C8 | Gen I/II follow-up |
| ROW-dea0729edbc5 | Prism (v0.95.0254) | C8 | Gen I/II follow-up |
| ROW-33ca3bd175c3 | Brown (v6.1.2) | C8 | Gen I/II follow-up |

C6 rows are "unsupported engine" only until a source-backed engine shows otherwise; none was found in the local source index.

## Rules added (C2)

- **Empty placeholder species slots** (`TableValidators.baseStats`, Gen III): an empty record is all zero, except that the standard 28-byte record may keep its two egg-group bytes. Such slots are reserved instead of invalid when all of these hold:
  - at least 80% of rows are plausible species and at most 1% are malformed;
  - the final record is a populated species, which proves the table extent (a zero tail stays unproven, as the partial-stats doctrine requires);
  - no other record size between 20 and 64 also fits at least 70% of rows (stride ambiguity is refused).
  - The empty slots stay uncovered; they never become species data. The published partial-stats resolver keeps the strict rule and its precedence.
- **One PP outlier** (`TableValidators.moveData`, Gen III): a single interior move record whose only violation is PP above the retail ceiling of 64 is accepted with a stated reason, but only when the record after the table is non-zero and not a plausible move. That proves the table ends there, so a truncated view of a longer table cannot validate. Two outliers, a final-row outlier or any other violation still fail.
- Parser schema 144.

Commits: 5f179dae (rules and tests), 4f523473 (schema 144), ff0a24cd (move-table boundary proof).

## Run

Two qualification runs, each on the parser CLI jar built from the named commit (the manifest's `DualDex-Source-Commit` matches). Each run is compared with the R1 baseline report.

| Run | Commit | Inputs | Result |
| --- | --- | --- | --- |
| 1 | 4f523473 | 82: the 28 GBA unmatched rows, 47 selected GBA rows missing base stats or move details, and the controls | Exit 0. 78 identical to R1. Moon Galaxy 37.06→81.87% and Sun Sky 37.07→86.09% (both now SELECTED). Gaia 41.34→70.20%. **Elysium B regressed** 41.64→37.47%: MOVE_CATALOG went available→ambiguous. |
| 2 | ff0a24cd | 8: Moon Galaxy, Sun Sky, Gaia, Elysium A and B, plus controls Radical Red, Unbound and Emerald Kaizo | Exit 0. 6 identical to R1, including Elysium B (regression fixed) and Gaia. Moon Galaxy and Sun Sky still select with the same scores. |

Elysium B's regression was a truncated 355-row move view that validated through the PP-outlier rule and then conflicted with the real 455-name catalog. ff0a24cd requires a proven table end. Run 2 covers only the rows whose result changed in run 1, plus controls: the rule only got stricter, so the other 74 rows, which were already identical to R1, cannot change.

Gaia's run-1 selection rested on the same unproven move-table boundary, so it returns to its R1 state. It is not a regression against the baseline, and it stays in the GBA focus.

Capability changes for the two new selections: SPECIES_CATALOG, SPECIES_TYPES and BASE_STATS move from R1's family-independent "available" view to a selected "partial". The R1 view covered only a numeric prefix of the table. The selected partial maps 1405 slots through the species-to-Dex table and leaves the reserved empty slots uncovered. Every other change is a gain (evolutions, egg/machine/tutor moves, abilities, encounters, maps, natures and type presentation).

Originals: all 82 inputs hash identically before and after (SHA-256, size and mtime).

Host tests at ff0a24cd: parser-core 2674 run, 240 skipped, 0 failed. At 4f523473: catalog-store 130, 0 failed.

Acceptance: every row has an ID and a target. The largest cluster that a generic rule can select (C2) now selects and parses for its two proven-extent rows. The remaining C2 rows and C1, C3, C4 and C5 are the GBA focus stage, under the IDs above. C6 and C7 are unsupported engines. C8 is the Gen I/II follow-up.
