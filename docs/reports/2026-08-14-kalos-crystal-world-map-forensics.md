# Kalos Crystal world-map forensics

Date: 2026-08-14

## Result

Kalos Crystal v1.0 moved from 0% world-map compatibility (`NO_FAMILY_MATCH`) to a complete deterministic two-region map resolution.

The correction does not add a ROM profile, title, hash, fixed production offset, section list, or ROM-specific fallback. It recognizes a contracted Gen II core-data ABI only when paired complete compiled consumers select contiguous name and base-data tables whose shared boundary proves a smaller active species domain than the inherited retail profile.

## Inputs

- ROM SHA-256: `7cd8957e47a04bf0542de5d6a65affb369704e85ce11e03022be491be7dc1050`
- Header: GBC, `PM_CRYSTAL`, revision 0
- Public source oracle: <https://github.com/AzureKeys/KalosCrystal>
- Source branch: `9bit`
- Source commit inspected: `95be0cdf39d0f85d0d96c7fa9313eb866b7dfa6b`

No source offset was used as parser authority.

## Corrected diagnosis

The original Crystal probe already passed its hard gate but scored only 62, below the selection threshold of 75. Its inherited retail layout found a relocated name candidate but only 2/251 plausible base-stat rows, so cross-table integrity failed.

The current public `9bit` source was useful for identifying the compiled-consumer pattern, but it does not describe this distributed v1.0 ROM's table or Town Map extents:

- current source: 291 raw species IDs, 34-byte base-data records, and Johto/Orange/Kanto Town Map planes;
- raw v1.0 ROM: a 229-row active base-data table, 32-byte records, and the already-supported Johto/Kanto Town Map chain.

The ROM bytes therefore rejected the initial extended-ABI and three-plane hypotheses.

## Raw compiled authority

### Fixed-name consumer

The complete bank-0 consumer at raw `0x343B` proves:

- a bank-switch to bank `0x14`;
- an 8-bit species index decremented to zero-based form;
- the exact `index * 10` address formation;
- name root CPU address `0x70C6`;
- a ten-byte copy followed by a `0x50` terminator;
- bank restoration and return.

Raw name-table root: `0x530C6`.

The first 229 consumer-selected rows validate as fixed Gen II names.

- table bytes: 2,290
- table SHA-256: `98cbaa99630e9155879eb2b6c656b0ab0003ba65c4fb1dc08726a808410ab8d4`
- consumer SHA-256: `feb292aa3baf4f3ae846188a7523a49637cb595b618fb0676802f433cb3f0afb`

### Base-data consumer

The complete bank-0 consumer at raw `0x3856` proves:

- the same bank `0x14`;
- an 8-bit species index decremented to zero-based form;
- a 32-byte multiplier and a second independent 32-byte copy length;
- base-data root CPU address `0x5426`;
- the direct and special-record branches converging before bank restoration and return.

Raw base-data root: `0x51426`.

The two consumer roots share a bank and satisfy:

```text
(0x530C6 - 0x51426) / 32 = 229
```

All 229 base-data records carry the exact sequential IDs `1..229`. Existing Gen II semantic validation accepts 214/229 records (93.4%); the remaining rows use custom type IDs outside the retail validator's preferred range, not broken record boundaries.

- table bytes: 7,328
- table SHA-256: `258bae6d69f2b5a0f8808533c6e5dcd7b82bcab672cdbd755443597eb9869176`
- consumer SHA-256: `4661dacf39d72279692d024d301d743b80b96321d38ecab936997d7b0ba7ec9c`

### Fail-closed scope

The resolver requires one unique validated pair after deduplication. It rejects incomplete consumers, different banks, non-integral boundaries, non-sequential IDs, invalid name/base content, and domains outside `151..255`.

Only a domain smaller than the inherited retail profile is published. A larger 8-bit boundary may include reserved IDs and is not accepted without an independent active-row predicate.

## Family and catalog result

The focused diagnostic changed from `NO_FAMILY_MATCH` to:

```text
Decision: SELECTED / CRYSTAL
Score: 87
Species names: 229 at 0x530C6, stride 10
Base data: 229 at 0x51426, stride 32
```

The focused parser materialized 229 named species, 214 validated base-stat rows, 335 encounter areas, and complete cross-references without crashing.

## World-map validation

No Town Map parser correction was necessary after family selection. The raw ROM's compiled Town Map authority uses the existing two-plane Gen II path, not the three-plane shape in the current source branch.

Encounter projection:

- encounter areas: 335
- unique group/map IDs: 114
- method counts: water 62; morning/day/night grass 91 each
- every slot has species `1..229`, levels `1..100`, and positive weight
- encounter fingerprint: `287025214d7e1eb4c5aa43a744924b133dded32cd7cc0db3eeb6d8397aede804`

Every one of the 114 encounter group/map IDs joins through compiled map headers, landmarks, and region classification.

| Region | Raster | Grid | Locations | Bindings | ARGB SHA-256 | PNG SHA-256 |
| --- | --- | --- | ---: | ---: | --- | --- |
| Johto | 160×144 | 20×18 | 45 | 78 | `adb9cefb64aece67c7cff271b70183af5dafa7c3e95beffd31436a7cab79a5e9` | `23739bddf01b2c98a03ca1c4af28ade7d751623ec8063311dd2b8b366c81c516` |
| Kanto | 160×144 | 20×18 | 34 | 36 | `c53b3c2e032545fa2452bbadd4a29aea8619cc852b9ed45d17d6d8475cebe5b7` | `c06748683d60a89e4d2984bbcb565dc854ddd7942295d5039b80bcabe223258d` |

Canonical location fingerprint:

`355728883137963f6696793e9b5834a0155be312c8abd4d57a572c78981445d2`

Both PNGs were exported to `D:\Temp\dualdex-kalos-crystal-poc-render` and visually inspected. They are coherent Johto and Kanto maps, not gibberish.

## Bounded regression evidence

The retained 120-ROM GB/GBC baseline was reused; no broad map matrix was rerun.

A raw structural survey of all 120 retained paths found:

- 120/120 inputs present;
- 19 complete fixed-name consumer shapes;
- 10 name/base consumer pairs with integral validated ID boundaries;
- Kalos Crystal is the only pair whose derived count is smaller than the inherited 251-species Crystal profile (`229 < 251`).

Therefore the published contraction rule changes routing only for Kalos Crystal in the retained subset. Existing equal or larger domains remain on their previous parser path.

Focused exact controls passed for:

- Kalos Crystal: 335 encounters, 114 complete joins, exact encounter/location/raster fingerprints;
- Anniversary Crystal: its previously published compact-encounter and guarded-map fingerprints remain unchanged.
