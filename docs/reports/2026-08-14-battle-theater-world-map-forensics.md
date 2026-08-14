# Battle Theater v2.3.0 world-map forensics

Date: 2026-08-14

## Outcome

- Corpus row: 017/050
- Compatibility before: **0%** (`LOADER_ASSET_CLUSTER`)
- Compatibility after: **100%** (`RESOLVED`)
- ROM SHA-256: `99c84950e2be2f887a84bdc32c741c92385bb4a54843d871a8876e9b47e1d59d`
- Selected family: Emerald
- Published regions: 1 encounter-bound region
- Deterministic: yes, across two fresh complete parses

The parser validates all five table-owned map bundles. Only Hoenn is published because the other four compiled legacy regions retain no encounter binding in this ROM. Publishing an unbound region would violate the catalog contract.

## Source oracle

The exact public source tree was available at local commit:

`75ee0b09e7c5077a4a35c9887ee16962a9cb6002`

Source identified two likely missing patterns:

1. region tilemaps use pokeemerald-expansion SMOL mode 8;
2. the loader indexes `gRegionMapInfos[regionMapType]`, so asset pointers live in a data table rather than as direct literals in one function.

No source address entered production logic. Every accepted stream, table, semantic plane, and join was re-proven from raw ROM bytes.

## Raw asset-table authority

The compiled-reference-owned table begins at raw ROM offset `0xCB70A4`. It contains five contiguous 28-byte records. Each record has six in-ROM pointers, a 96-byte palette count, and zero tail padding.

| Slot | Region map | Region graphics | Region palette | Decoded map | Decoded graphics |
|---:|---:|---:|---:|---:|---:|
| 0 | `0xCBD458` | `0xCBD668` | `0xCBE7FC` | 4096 | 14912 |
| 1 | `0xCB8CD0` | `0xCB8EB0` | `0xCB9814` | 4096 | 12288 |
| 2 | `0xCB830C` | `0xCB83C8` | `0xCB87A4` | 4096 | 4096 |
| 3 | `0xCB7CA0` | `0xCB7D28` | `0xCB7F84` | 4096 | 2048 |
| 4 | `0xCB7130` | `0xCB7208` | `0xCB7694` | 4096 | 4096 |

The resolver also validates the dex-side map and graphics pair in every record. Those maps decode to 2048 bytes; their graphics decode to 14848, 11264, 3072, 2048, and 4096 bytes respectively. Requiring both halves prevents a random six-pointer record from becoming table authority.

## SMOL mode 8

All five region tilemaps are SMOL mode 8. The raw streams decode byte-for-byte to the source-generated map products:

- Hoenn: `0xCBD458`
- Kanto: `0xCB8CD0`
- Sevii 1–3: `0xCB830C`
- Sevii 4–5: `0xCB7CA0`
- Sevii 6–7: `0xCB7130`

Mode 8 stores a u16 symbol vector, aligned instruction bytes, and cumulative u16 deltas. The decoder now validates the declared vectors and aligned encoded extent before expansion.

## Semantic authority

The raw ROM contains five contiguous 28×15 u8 section planes:

| Table slot | Plane offset | Non-empty cells | Distinct sections |
|---:|---:|---:|---:|
| 0 | `0xCBD1B4` | 158 | 54 |
| 1 | `0xCBD010` | 90 | 38 |
| 2 | `0xCBCE6C` | 19 | 8 |
| 3 | `0xCBCCC8` | 14 | 7 |
| 4 | `0xCBCB24` | 31 | 11 |

All five are direct literals owned by the same Thumb function at `0x1E75B2`. They are contiguous in reverse slot order, use section ID 209 as the dominant terminal sentinel, and have non-overlapping active section sets.

The encounter map-header join resolves one compiled `gMapGroups[group][map]` root at `0xEF4DC0`, binding all 116 required encounter maps with no malformed non-null headers. Expansion encounter IDs had to be normalized by 100 rather than 10; the latter had fabricated groups 240–244 from time-specific area IDs.

The published Hoenn region contains:

- 43 encounter-bound semantic locations;
- 48 base-area bindings;
- 142 semantic cells;
- 28×15 geometry;
- 224×120 raster;
- ARGB SHA-256 `1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab`.

## Render validation

Parser/compositor output was exported to:

`D:\Temp\dualdex-battle-theater-all-renders`

All five PNGs were visually inspected and are coherent maps rather than gibberish.

| Slot | PNG SHA-256 |
|---:|---|
| 0 | `c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6` |
| 1 | `66ff671b9c80c39ab3c026944b39f9057cdf57e9f5144f82815812368f0b145d` |
| 2 | `721d9471fc45ff6f293c727afa95b256f546fa1612ab9fcb609d6728d0dd5522` |
| 3 | `cdf91ee5bcffa00246b6a8e0f3dfa5883e978bd4ac25606e28b4852c9f60723e` |
| 4 | `073217c3c8f59efe635d01f06101413c492ce9eeeda45b9547df9d28d7dd72a0` |

The final parser-published render is also at:

`D:\Temp\dualdex-battle-theater-parser-render\gen3-region-0.png`

## Generic corrections

- Decode and size-probe SMOL mode 8.
- Decode table-owned compressed streams as GBA LZ77 or structurally valid bounded SMOL.
- Discover multi-region 8bpp asset tables from compiled-reference roots.
- Validate both halves of each 28-byte record before accepting table authority.
- Account for source palettes loaded into a nonzero 16-color bank.
- Resolve contiguous compiled 28×15 u8 semantic planes.
- Publish only table slots that retain encounter bindings.
- Normalize expansion encounter area IDs with their 100-value stride before map-header lookup.

No ROM name, hash, fixed asset offset, or per-ROM profile was added to production parsing.

## Performance check

A non-table first-50 control was parsed twice on clean base commit `7ffe1a5` and twice with this change applied to that base. Complete parse times were `41,832/39,207 ms` on the base and `41,923/36,458 ms` with this change. Under `DUALDEX_MAP_TRACE=1`, table nomination took `3 ms` in both changed-branch runs. The apparent long-running matrix behavior therefore reproduced without table-driven map discovery and was not introduced by this change.

## Focused validation

The following real controls pass:

- all five raw SMOL mode-8 streams equal their source-generated 4096-byte products;
- all five table-owned region bundles produce exact PNG hashes;
- the complete Battle Theater resolver produces one encounter-bound region with exact pixels and binding counts;
- two fresh matrix parses are identical and finish at `RESOLVED`;
- all ten Gen III real-ROM resolver controls pass with zero skips, failures, or errors.

## First-50 regression gate

The complete 50-ROM matrix was rerun after rebasing onto `fork/master` commit `180e4e6`. All 50 map projections matched the pre-rebase gate exactly, with two fresh parses per ROM:

- 50/50 raw ROM hashes matched the corpus manifest;
- 50/50 outputs were deterministic;
- 50/50 parses completed without a loading/parsing exception;
- 37/50 resolved at 100%; all other rows published no map;
- 33/33 frozen first-33 family routes were preserved;
- all 26 ROMs resolved in the frozen 26/50 gate remain resolved with identical region dimensions and exact raster hashes;
- Battle Theater moved from `LOADER_ASSET_CLUSTER` to `RESOLVED` with its frozen raster and binding counts.

Classic's bindings differ from the older frozen 26/50 artifact because of corrections already present on base commit `7ffe1a5`; a dedicated row-29 rerun proved this change matches that clean base exactly. There is no Battle Theater regression.

Merged raw evidence is stored outside the repository at:

`D:\Temp\dualdex-battle-theater-first50-final.json`
