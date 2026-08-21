# ROM-derived Nature catalog design

## Objective

Replace the two canonical Gen III Nature assumptions currently embedded in the companion core and web UI with a normalized catalog decoded from the loaded ROM. A ROM may relocate, rename, reorder, or alter Nature stat and flavor data without requiring an APK update.

## Source authority

Modern Emerald, Pokémon Unbound, and Pokémon Odyssey are the primary real-ROM acceptance targets. Official Emerald, official FireRed, and Pokémon Classic are regression controls. Across those binaries, the parser proves the same three semantic roles in compiled ROM code:

- an indexed pointer table of Nature display names;
- a signed Nature-by-stat modifier table consumed by the stat calculation routine;
- a separate signed Nature-by-flavor compatibility table consumed by Pokéblock calculations.

The two signed tables are independent. The parser must never derive flavor preferences from stat modifiers. Modern Emerald may select a hidden Nature ID instead of `personality % natureCount`, but the selected ID still indexes the same normalized catalog.

## Normalized contract

`NatureRecord` contains:

- the ROM Nature ID;
- a ROM-decoded display name;
- five ordered stat modifiers for Attack, Defense, Speed, Special Attack, and Special Defense;
- the positive, neutral, and negative multipliers proved by the stat consumer;
- five ordered flavor affinities for Spicy, Dry, Sweet, Bitter, and Sour when the flavor table is independently resolved.

Names and stat modifiers are required for a usable Nature catalog. Flavor affinities are optional per record and make the capability partial when their independent table is absent.

## Resolver authority

The resolver operates only for selected Gen III GBA layouts and reuses the shared `RomAnalysisSession` reference index. Production selection must not use ROM names, SHA-256 values, source symbols, fixed ROM offsets, or the canonical 25-name list.

Candidate roots are nominated from compiled literal references. A name root must decode as an indexed GBA-pointer table whose strings form a complete bounded domain. A stat root must expose the same domain with five signed byte fields and must be referenced by a decoded consumer that indexes Nature and stat independently. The consumer must distinguish positive, negative, and neutral table values and prove the corresponding percentage multipliers. The flavor root, when present, must expose the same domain with five signed byte fields and be referenced by a distinct indexed flavor consumer.

Exactly one coherent name/stat contract is required. Multiple survivors are ambiguous and no survivors are not found. A malformed row, unsupported signed value, incomplete compiled-reference evidence, or truncated candidate fails closed. Flavor ambiguity or absence does not invalidate independently proven names/stat effects; it yields partial capability evidence.

## Platform semantics

- Gen III GBA: `AVAILABLE`, `PARTIAL`, `AMBIGUOUS`, or `NOT_FOUND` according to evidence.
- Gen I and Gen II: `NOT_APPLICABLE`, because those engines have no Nature mechanic.
- Unknown/nonselected ROM: no Nature catalog and no inferred canonical fallback.

Normal UI surfaces omit missing Nature details. Diagnostic evidence remains available only through the existing Debug Settings capability report.

## Persistence and presentation

The parsed Nature catalog is a required SQLite catalog section and increments the parser schema version. The party API exposes a typed Nature view resolved by `natureId`. The web Nature Detail page consumes that view directly. Both existing hardcoded Nature lists are deleted after the vertical catalog path is green.

## Verification

Primary real-ROM controls cover Modern Emerald, Pokémon Unbound, and Pokémon Odyssey. Each must resolve all 25 ROM-native Nature records through full parser materialization, including names, stat directions, 110/90 percentage multipliers, and independent flavor affinities. Official Emerald, official FireRed, and Pokémon Classic protect the existing engine families from regression. Catalog write/reopen, API projection, Party navigation, and Nature Detail rendering preserve the same typed records. Ambiguous, truncated, and unsupported candidates fail closed.
