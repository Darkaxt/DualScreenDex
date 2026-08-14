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

After the focused slice, rerun one exact first-50 matrix and report numeric before/after counts. Subsequent holes are separate source-backed slices selected by expected compatibility gain and available source authority rather than broad heuristic changes.

## Second slice: headerless unified move records

After the first slice, the matrix moved Dreamstone and Crippling learnsets to `AVAILABLE`. The next slice targets Dreamstone's move catalog and move details instead of Altered Emerald's two incomplete learnset rows: Dreamstone is the weakest source-backed first-50 catalog and the same public source plus exact ROM are already proven inputs.

Dreamstone source defines one `gMovesInfo` array of 48-byte `MoveInfo` records. Ordinary move IDs are the dense domain `0..847`; each record embeds name and description pointers plus typed effect, type, category, power, accuracy, target, PP, priority, flags, and argument fields. The exact ROM independently contains a complete 848-row ordinary domain matching those semantics.

Production selection must start from compiled-reference targets and the independently decoded positive move-ID domain already present in complete learnsets. It may admit the source-defined 48-byte ABI only when exactly one referenced root provides complete pointer names and typed detail rows for every ID from zero through the maximum referenced move ID. Table-wide content validates a compiled-nominated root; it cannot nominate a raw ROM offset. Invalid pointers, malformed packed fields, incomplete dense coverage, exhausted evidence, and multiple complete roots fail closed. Production must not select by ROM name, SHA, source symbol, absolute address, or source revision.

The normal catalog path must publish `MOVE_CATALOG` and `MOVE_DETAILS` as `AVAILABLE`, preserve the ordinary move IDs and decoded names/details, keep unsupported special-only move records outside the ordinary domain, preserve zero reference errors, and survive SQLite write/reopen. Ability mechanics remain independently gated and must not be promoted merely because the move ABI becomes available.

## Later slice: compiled Pokédex-entry descriptions

After Celia's widened move details were resolved, its next non-THUMB gap was the Pokédex-description table. The comparative source defines 386 `PokedexEntry` rows with a 36-byte record and one description pointer at `+16`; the exact ROM independently contains that table with eight compiled references.

Production may use the published Gen III Pokédex count only when the fixed species-name, move-name, and sprite header roles are valid and the count lies inside the independently decoded species-name domain. Typed discovery then remains compiled-reference-nominated and must agree with the legacy structural validator. Partial records stay unavailable, and a partial description table cannot invalidate an independently compiled species-to-Dex map.

The output gate is truthful semantic coverage, not an all-or-nothing label: Celia must report 382 decoded descriptions across 384 navigable Pokédex species, preserve two unavailable active rows, retain zero reference errors, survive SQLite reopen, and leave the other 49 exact-first-50 results unchanged. THUMB ability mechanics are out of scope for this slice.
