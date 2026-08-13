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

Modern Emerald 3.5 is outside the first-50 cohort. Its source defines a `NUM_SPECIES x 8` array of six-byte records containing three `u16` fields: method, parameter, and target species. RC25 currently publishes no evolution table because generic discovery exhausts its 4,096-shape prefilter before validating that real layout.

## Rejected approaches

### Per-ROM layouts

Selecting a layout by ROM name, SHA-256, known symbol address, or fixed table offset would make the seven controls pass but would not improve the parser. Production code must not contain those identities.

### Content-only ranking

Scanning for plausible triples and choosing the table with the highest valid-row ratio is vulnerable to zero-filled data and aliases with the same row stride. Content validation remains necessary but cannot establish table ownership by itself.

### Larger search budgets

Increasing the 4,096-shape or candidate limits would spend more time enumerating unowned layouts and could merely move the failure threshold. It does not establish why a candidate is the evolution table.

## Selected approach: compiled-consumer-derived layout

The parser will derive a typed evolution layout from decoded code that consumes the table.

1. Start only from roots present in the session-owned compiled-reference index.
2. Decode ARM/Thumb control flow for the owning consumer reached from real call targets; do not assign ownership from the nearest `PUSH` or an arbitrary address window.
3. Track the species identifier through address formation and prove a row address of `root + speciesId * rowStride`.
4. Track slot iteration within that row and derive `recordSize` and `slotsPerSpecies` from address progression or loop bounds.
5. Require typed halfword reads for method at `+0`, parameter at `+2`, and target species at `+4` from the same record lineage. An optional fourth halfword at `+6` admits the existing eight-byte format.
6. Decode the proposed table with the existing `EvolutionCodec`. Empty rows are valid; malformed active records are not silently discarded.
7. Select only one complete, structurally owned layout. Multiple contradictory layouts remain unresolved.

The existing content-anchor resolver remains a fallback for already-working ROMs. A compiled-consumer result may replace a partial inherited/content result, but it may not replace an existing complete result unless both decode to the same evolution-edge set.

## Data flow

`RomAnalysisSession` supplies the ROM, compiled-reference index, and decoded instruction support. The new consumer-layout resolver returns an `EvolutionTableLayout` plus structural provenance. `EvolutionResolver` performs the byte decoding and candidate selection. `DependentDatasetsStrategy` then materializes the selected rows through the existing catalog relationship path; no schema change is required.

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

- the derived layout is six-byte records, eight slots per species;
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
