# B5 GBA: custom-ordered expansion type charts (2026-11-22)

Roadmap stage B5+ ([2026-11-21 roadmap](../../superpowers/plans/2026-11-21-compatibility-roadmap.md)), item `LIB26-GBA-HEADER-1B8`. Qualified on affected inputs plus controls only; no release.

## Root cause

Light and Shadow (V2.0.4) is a pokeemerald-expansion 1.15.2 build. Its Game Freak header has the modern expansion shape: name, ability and ball slots are left NULL, and gSpeciesInfo sits at 0x1BC. That is why the published-header probe saw three equally complete blocks (0x1b4, 0x1b8, 0x1bc). The B3 triage label ("zeroed ability slots") described the symptom.

The real entry point is the `RHHEXP` expansion header, and it is present and well-formed:
- 1734 species with 260-byte SpeciesInfo records;
- 848 moves with 68-byte records;
- 311 abilities.

The species, learnset, move and ability shape inference all pass. The resolver fails at the type chart. The hack reorders its type domain, so the vanilla Normal→Rock ½, Normal→Ghost 0 and Fighting→Normal ×2 cells no longer hold, even though the 21×21 u32 Q4.12 matrix is intact.

Fix (5d355ffa): when no anchored chart exists, `PokeemeraldExpansionResolver` falls back to the existing compiled-reference locator (`locateReferencedGen3Q412TypeCharts`). It accepts the fallback chart only when all of these hold:
- the matrix is u32;
- both its root and its one-past-end are compiled pointer targets;
- it covers every move type;
- it is the only such matrix.

A missing end reference or two candidate matrices still fail closed (three new fixture tests).

## Qualification

One run of 11 ROMs at 5d355ffa, with the jar manifest matching the commit.

The 11 ROMs:
- Affected: the four unmatched ROMs with an `RHHEXP` header in the 31-ROM census: Light and Shadow, Too Many Types 2, Voyager (Battle Frontier Demo) and Lazarus.
- Expansion controls: Soulgold, Battle Theater, Emerald Rogue EX and Valiant.
- Other controls: Radical Red, Unbound and Emerald Kaizo.

Results:
- Exit 0, 0 read/parse, persistence or cross-reference errors, originals unchanged.
- Compared with the B4 full run:
  - **Light and Shadow: unmatched 7.95% → SELECTED Emerald 89.88%** (22/23 features; 1732 species, 848 moves, 311 abilities, 338 local maps).
  - The other 10 rows are identical: decision, percentage, every capability status/confidence and catalog counts.

## Remaining expansion rows

| ROM | Finding | Ledger |
| --- | --- | --- |
| Too Many Types 2 | Move type IDs reach 31, the ceiling of the 5-bit packed type field, so the move ABI is widened for its type domain. | LIB26-GBA-EXPANSION-ABI |
| Voyager Demo | Custom 108-byte SpeciesInfo: category name is a pointer (+36), the inline name is at +40, dex numbers are not an identity map, and the evolution pointer sits 16 bytes after the learnset pointer. Voyager publishes no source, so there is no source-backed layout. | LIB26-GBA-EXPANSION-ABI |
| WaveBlue | FireRed-based expansion: inline-name SpeciesInfo with 268-byte records at 0x1BC, but no `RHHEXP` header, so species, move and ability counts are unpublished. It needs an extent-proof route, not guessed counts. | LIB26-GBA-HEADER-1B8 |

## B5b: disabled species families (813fec8c)

Lazarus (v2.0) is pokeemerald-expansion 1.9.4 with 212-byte SpeciesInfo records: category at +31, name at +44, dex number at +60. That matches the source-backed 1.10 shape the resolver already infers.

The shape probe failed because expansion gates each family's initializers behind `#if P_FAMILY_*` (`include/config/species_enabled.h`). Lazarus disables 16 of species 1–24, so those rows are zero-filled, and the fixed ID 1–24 sample fell below the 80% stats threshold.

The probe now samples the first 24 rows that are not entirely zero. It still fails closed when the extent cannot fill the sample. Any ROM whose first 24 rows are populated is sampled exactly as before. Two fixture tests cover this: zero-filled disabled rows resolve, and too few populated rows are rejected.

Qualification: one run of 14 ROMs at 813fec8c, jar manifest matching the commit.
- Affected: Lazarus.
- Unmatched expansion rows: Voyager Demo, Too Many Types, Too Many Types 2.
- Controls: Light and Shadow, Soulgold, Battle Theater, Emerald Rogue EX, Valiant, Pisces, Sovereign of the Skies, Radical Red, Unbound, Emerald Kaizo.

Results:
- Exit 0, 0 errors, originals unchanged.
- **Lazarus: unmatched 8.20% → SELECTED Emerald 73.61%** (19/23 features, 667 species, 849 moves, 318 abilities). EVOLUTIONS is partial (0.369) and EGG_MOVES partial (0.139); both are left as findings for the expansion-learnsets follow-up.
- Light and Shadow shows its B5a gain against the B4 baseline. The other 12 rows are identical to B4.
