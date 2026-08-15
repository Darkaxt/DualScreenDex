# Source-backed GBA compatibility survey

Date: 2026-08-15

This survey covers the 36 exact GBA ROM variants whose projects are present under
`D:\Temp\PokemonHacks\sources\Game Boy Advance`. Each ROM was re-read by the current
parser and every selected catalog was written to and reopened from schema-10 SQLite.
The pass completed with 26 selected ROMs, 10 safe `NO_FAMILY_MATCH` results, and zero
parser, catalog, or persistence errors.

## Numeric definitions

- **Strict functional coverage** is `AVAILABLE applicable capabilities / applicable
  capabilities * 100`. A ROM without a selected family is 0%, because isolated table
  evidence cannot produce a usable catalog. `PARTIAL`, `NOT_FOUND`, and `AMBIGUOUS`
  receive no full-credit points.
- **Record-weighted coverage** is the parser's existing 0-100 compatibility formula.
  It gives proportional credit to partial record coverage and can therefore be nonzero
  for an unselected ROM. It is diagnostic only; it does not override routing.
- The current capability model has 22 GBA features. Battle Theater has 21 applicable
  features because its source-selected capability set explicitly excludes one feature.

The earlier exploratory table that read preliminary family-probe capabilities was
discarded. The numbers below come from each final materialized catalog capability set.

## Results

| ROM | Routing | Family | Fully available | Strict functional | Record-weighted |
|---|---|---:|---:|---:|---:|
| Altered Emerald (v4.2c) | selected | Emerald | 18/22 | 81.82% | 99.93% |
| AshGray - Newerest Edition (v1.0) | selected | FRLG | 20/22 | 90.91% | 95.42% |
| Battle Theater (v2.3.0) | selected | Emerald | 21/21 | 100.00% | 100.00% |
| Celia's Stupid Romhack (v1.1.4) | selected | FRLG | 17/22 | 77.27% | 95.36% |
| Classic (v1.5.0b) | selected | Emerald | 21/22 | 95.45% | 95.45% |
| Clover (v1.3.3) | selected | Emerald | 19/22 | 86.36% | 95.42% |
| Delta Emerald (v1.1.5) | selected | Emerald | 19/22 | 86.36% | 90.87% |
| Dreamstone Mysteries | selected | Emerald | 14/22 | 63.64% | 63.64% |
| Elite Redux (v2.65.3b) | no family match | - | 1/22 isolated | 0.00% | 9.09% |
| Emerald Imperium (v1.3.1) | no family match | - | 2/22 isolated | 0.00% | 13.49% |
| Emerald Legacy (2026-06-04) | selected | Emerald | 21/22 | 95.45% | 99.96% |
| Emerald Rogue EX (v2.1.2) | no family match | - | 3/22 isolated | 0.00% | 17.85% |
| Emerald Rogue Vanilla (v2.1.2) | selected | Emerald | 18/22 | 81.82% | 81.82% |
| FireRed & LeafGreen+ (v1.5.1) | selected | FRLG | 19/22 | 86.36% | 86.36% |
| FireRed Reignited Legacy | selected | FRLG | 21/22 | 95.45% | 95.45% |
| FireRed Team Rocket Edition (v1.02) | selected | FRLG | 21/22 | 95.45% | 99.69% |
| GS Chronicles (v2.7.6) | selected | FRLG | 18/22 | 81.82% | 86.27% |
| Heart & Soul (v1.2.1) | selected | Emerald | 19/22 | 86.36% | 90.85% |
| Pokémon Hearth (v0.1.27) | no family match | - | 1/22 isolated | 0.00% | 8.90% |
| Inclement Emerald (v1.1.3) | selected | Emerald | 18/22 | 81.82% | 81.82% |
| Inclement Emerald Custom UI (v1.1.3) | selected | Emerald | 18/22 | 81.82% | 81.82% |
| Modern Emerald (v3.5) | selected | Emerald | 21/22 | 95.45% | 99.95% |
| Pokescape (v1.0.4) | selected | Emerald | 16/22 | 72.73% | 72.73% |
| Project Nova (v2.7.0) | selected | FRLG | 19/22 | 86.36% | 90.90% |
| R.o.w.e (v2.1.9) | selected | Emerald | 8/22 | 36.36% | 44.83% |
| ROWE (v2.1.1) | no family match | - | 2/22 isolated | 0.00% | 17.55% |
| Soulgold (v1.0.2) | no family match | - | 5/22 isolated | 0.00% | 22.73% |
| Sovereign of the Skies (v2.1.2) | no family match | - | 5/22 isolated | 0.00% | 27.06% |
| Sword and Shield Ultimate Plus Casual + Performance (v1.2.1.2) | selected | FRLG | 20/22 | 90.91% | 95.44% |
| Sword and Shield Ultimate Plus Casual (v1.2.1.2) | selected | FRLG | 20/22 | 90.91% | 95.44% |
| Sword and Shield Ultimate Plus Performance (v1.2.1.2) | selected | FRLG | 20/22 | 90.91% | 95.44% |
| Sword and Shield Ultimate Plus (v1.2.1.2) | selected | FRLG | 20/22 | 90.91% | 95.44% |
| The Unown King (v1.1.0) | no family match | - | 1/22 isolated | 0.00% | 16.99% |
| Tourmaline (v1.1.1) | selected | Emerald | 12/22 | 54.55% | 59.08% |
| Voyager Battle Frontier Demo (v1.1) | no family match | - | 1/22 isolated | 0.00% | 13.04% |
| Voyager (v0.3.6) | no family match | - | 3/22 isolated | 0.00% | 22.50% |

Mean strict functional coverage across the 26 selected ROMs is **83.74%**.

## Ability-description improvement

The Game Freak public header already exposed authoritative description-table roots, but
the materializer still enumerated unrelated compiled pointer spans. ROMs with more than
512 otherwise valid pointer-span candidates were rejected after their published table
had already validated. Compiled-candidate discovery now runs only when the published
root is absent.

This changes exactly three rows in the 36-ROM comparison:

| ROM | Before | After | Description coverage |
|---|---:|---:|---:|
| Modern Emerald (v3.5) | 90.91% | 95.45% | 81/81 |
| Heart & Soul (v1.2.1) | 81.82% | 86.36% | 81/81 |
| Emerald Rogue Vanilla (v2.1.2) | 77.27% | 81.82% | 77/77 |

All other rows retain their prior strict percentage. The mean selected-ROM strict
coverage rises from **83.22%** to **83.74%**.

## Remaining shared gaps among selected ROMs

The largest unresolved groups are ability mechanics (14 ROMs), Pokédex descriptions
(11), world maps (10), partial move catalogs (9), machine moves (6), move details (6),
and sprites (6). After this change, ability descriptions remain incomplete in five ROMs
(three absent and two genuinely partial). THUMB mechanics remain the largest group but
are intentionally not conflated with standard table support.
