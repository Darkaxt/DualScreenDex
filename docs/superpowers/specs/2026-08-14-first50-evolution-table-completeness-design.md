# First-50 evolution-table completeness design

## Objective

Raise evolution-table parsing for the exact first-50 ROM cohort from 44 complete tables to 50 complete tables, while preserving every already-complete result. Also resolve the separate Modern Emerald 3.5 evolution table as a source-backed control.

This work concerns evolution relationships only. It does not change family routing, compatibility scoring, maps, battle mechanics, UI behavior, or any other dataset.

## Measured baseline

The current exact first-50 report contains evolution edges for all 50 ROMs, but only 44 have every selected species row structurally valid. Six tables are only partially decoded:

| Row | ROM | Valid rows | Total rows | Completion |
| ---: | --- | ---: | ---: | ---: |
| 34 | Crippling Medical Debt Edition 1.1 | 1,306 | 1,528 | 85.47% |
| 36 | Crystal Advance Redux 2026-07-08 | 513 | 760 | 67.50% |
| 43 | Dark Violet | 244 | 412 | 59.22% |
| 44 | Dark Violet Fan-Patch | 327 | 412 | 79.37% |
| 46 | DarkFire 2.1.3 | 392 | 494 | 79.35% |
| 50 | Dreamstone Mysteries | 1,422 | 1,525 | 93.25% |

Modern Emerald 3.5 is outside the first-50 cohort. Its Release 3.5 source defines a `NUM_SPECIES x 8` array whose entries contain three `u16` fields: method, parameter, and target species. The shipped compiler ABI pads each entry to eight bytes; the exact Bulbasaur/Ivysaur source sequence appears at the parser-selected ROM root with a 64-byte species stride. RC25 currently publishes no evolution table because generic discovery exhausts its 4,096-shape prefilter before validating that real layout.

The maintained ROM-source index identifies public repositories for Dreamstone Mysteries (`dsmyst/dreamstone-mysteries`) and Modern Emerald (`resetes12/pokeemerald`). Dreamstone's source family stores a pointer to a terminated `struct Evolution` list in each unified species record and defines `EVOLUTIONS_END` as `0xFFFF`; Modern's Release 3.5 source defines the fixed eight-slot table described above. The index explicitly records Crippling Medical Debt, Crystal Advance Redux, Dark Violet, its Fan-Patch parent, and DarkFire as closed-source/binary. Those five are therefore verified from exact real-ROM structure and deterministic semantic edge hashes, not represented as source-backed builds.

## Rejected approaches

### Per-ROM layouts

Selecting a layout by ROM name, SHA-256, known symbol address, or fixed table offset would make the seven controls pass but would not improve the parser. Production code must not contain those identities.

### Content-only ranking

Scanning for plausible triples and choosing the table with the highest valid-row ratio is vulnerable to zero-filled data and aliases with the same row stride. Content validation remains necessary but cannot establish table ownership by itself.

### Larger search budgets

Increasing the 4,096-shape or candidate limits would spend more time enumerating unowned layouts and could merely move the failure threshold. It does not establish why a candidate is the evolution table.

## Selected approach: typed reference-root and embedded-pointer layouts

The fixed-table resolver starts only from roots in the session-owned compiled-reference index, derives candidate row ABIs from the selected species cardinality, and validates the complete table with the typed evolution codec. Candidate-budget accounting begins only after a referenced root has passed the evolution-table sample, so unrelated reference shapes cannot starve a real table. Complete candidates prefer the narrowest ABI and strongest direct compiled-root authority; contradictory equal evidence remains unresolved.

Headerless unified-species engines use a different source-backed structure: each already-proven species record contains an evolution-list pointer. The resolver tests aligned pointer fields inside that independently established record ABI, requires one unique field and one unique terminated record ABI, and validates every active target against the same proven species domain. Null pointers and inactive records are empty; malformed, unterminated, out-of-range, or contradictory candidates fail closed.

Both paths decode through the existing `EvolutionCodec`. Empty rows are valid, while malformed active records are never silently discarded. The original content-anchor resolver remains a fallback for already-working ROMs, and the exact first-50 gate requires every previously complete semantic edge hash to remain unchanged.

## Data flow

`RomAnalysisSession` supplies the ROM and compiled-reference index. Fixed tables publish the selected typed `TableLayout`; unified-record engines publish a typed `EvolutionTableLayout` backed by the proven species root and stride. `DependentDatasetsStrategy` materializes both through the existing catalog relationship path; no schema change is required.

Production selection uses structural evidence only. ROM names, hashes, source symbols, and exact offsets are permitted only in tests and diagnostics.

## Failure behavior

- Incomplete instruction decoding, unresolved call ownership, unsupported arithmetic, exhausted analysis budgets, or contradictory layouts produce no new selection.
- A malformed table is rejected rather than partially promoted by the new path.
- Existing complete tables remain unchanged if the new proof is absent.
- No missing evolution table is converted to not-applicable.

## Verification

### RED gate

Real-ROM tests will first reproduce the six incomplete first-50 results and Modern Emerald's unresolved result. Tests bind fixture identity by SHA only to guarantee the intended test input; production logic receives no identity selector.

### Focused GREEN gate

For each of the six first-50 ROMs:

- valid evolution rows equal selected species rows;
- malformed/skipped evolution slots equal zero;
- the materialized catalog contains the exact deterministic edge set on two fresh sessions;
- SQLite write/reopen preserves that edge set.

For Modern Emerald:

- the derived layout is source-defined three-`u16` records compiled to eight bytes, eight slots per species;
- every source-defined active evolution edge matches the source oracle and no extra edge is published;
- the catalog and reopened SQLite database retain the exact edge set.

### Cohort gate

Run the exact first-50 cohort twice from fresh analysis sessions. Required outcome:

- 50 of 50 evolution tables have complete row coverage;
- zero malformed or skipped active records;
- 50 of 50 evolution-edge hashes are deterministic;
- 50 of 50 catalogs persist and reopen with identical edges;
- the original 44 complete edge hashes do not change.

Run the full parser-core evolution, family-probe, catalog-materialization, and persistence suites after the real-ROM gate. A full 332-ROM scan is outside this correction and is not required before committing.

## Deliverable

One focused parser commit containing the consumer-derived layout proof and its real-ROM regressions, followed by one evidence commit containing the sanitized 50-row evolution result. No release or device work is part of this design.
