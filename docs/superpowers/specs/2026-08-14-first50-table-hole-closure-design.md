# First-50 table-hole closure design

## Goal

Increase exact first-50 table coverage with source-backed, real-ROM structural fixes. The first slice targets Dreamstone Mysteries level-up learnsets because all 1,522 active species are currently missing despite the data being present in the already-proven unified species table.

## Frozen baseline

- Exact first-50 identities and family routing remain unchanged.
- Evolutions are already 50/50 and are not part of this change.
- Level-up learnsets are 38 available, 4 partial, and 8 missing.
- Dreamstone is selected as Emerald with 1,522 active species and 0 resolved level-up learnsets.

## Source and binary authority

Dreamstone source commit `f7997186345885bfa23a170e5f573851fc034b9b` shows that `GetSpeciesLevelUpLearnset` reads the `levelUpLearnset` pointer embedded in `gSpeciesInfo[SanitizeSpeciesId(species)]`, falling back to the `SPECIES_NONE` row for a null pointer. Source is an oracle only; production selection must derive from ROM structure.

The parser already proves the shipped ROM's unified species root, 260-byte record stride, active-row predicate, exact species count, name accessor, and six-stat consumer. Embedded learnset discovery may operate only inside that selected record ABI. It must:

1. Decode each aligned pointer-field candidate across every active species row.
2. Accept only a supported, explicitly terminated Gen III learnset ABI.
3. Treat each positive `u16` move ID in a fully decoded relationship as existence evidence, while
   keeping names/details unavailable unless an independently selected move dataset covers that ID.
4. Select exactly one pointer field and ABI; zero or multiple survivors fail closed.
5. Preserve inactive species IDs as structural-empty rows and retain original active IDs.

No production ROM name, SHA, source symbol, absolute address, or fixed field offset is permitted. SHA and source line references are test/oracle evidence only.

## Output contract

On Dreamstone, the normal `CatalogParser` path must publish typed level-up entries for the full active species catalog, report LEARNSETS as AVAILABLE, close every referenced move ID without inventing names/details, preserve zero reference errors, survive SQLite write/reopen, and produce the same semantic result in two fresh parses. A malformed or ambiguous embedded field remains unavailable.

After the focused slice, rerun the exact first-50 matrix and report numeric before/after counts. Subsequent holes are separate source-backed slices, starting with Altered Emerald's two incomplete rows rather than broad heuristic changes.
