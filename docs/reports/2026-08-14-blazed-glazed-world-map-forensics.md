# Blazed Glazed v1.3 world-map forensics

Date: 2026-08-14

## Outcome

- Corpus row: 019/050
- Compatibility before: **0%** (`SEMANTIC_REGION_JOIN` / `map-header-join`)
- Compatibility after: **100%** (`RESOLVED`)
- ROM SHA-256: `0b55d44bfd32a350202c0878754cfcacbbaee128de3b59297ee669b69269199f`
- Selected family: Emerald
- Published regions: 1
- Deterministic: yes, across two fresh complete parses

The existing parser already proved and composed the world-map raster. It also bound all 187 encounter-requested map headers through one compiled map-group consumer. Resolution failed because ten source-valid region entries use a named off-map coordinate sentinel, which the region-table authority check had treated as malformed.

## Source oracle

The available community source port was inspected at:

`TrainerX493/pokeglazed` commit `dbe7c3b2ee4f66918b623a5bf5b897e7dd652c6f`

It established the relevant structural contract: `gRegionMapEntries[]` consists of eight-byte `RegionMapLocation` records containing four u8 geometry fields and one name pointer. The source was not assumed to match this ROM's build. Every table root, sentinel, string, consumer, and binding below was re-proven from raw ROM bytes.

## Existing raster authority

The existing compiled-loader heuristic selected one affine 8bpp asset cluster:

- graphics: `0x61E208`, decoded to 14,912 bytes;
- map: `0x61EF64`, decoded to 4,096 bytes;
- palette: `0x61D140`, 96-byte compiled load;
- format: 64×64 affine tilemap cropped to 28×15 cells.

The parser-generated 224×120 image was exported to:

`D:\Temp\dualdex-blazed-glazed-poc-render\gen3-region-0.png`

It was visually inspected and is a coherent map rather than gibberish.

- ARGB SHA-256: `120fc88466f34514b5555f38101074c2218e268cb9bbec4e3a693c153154f539`
- PNG SHA-256: `17ab8878a1ecc51a545f30d065e8bb5fc943a1cf4dfcb19a22d196ddfba30d82`

## Raw semantic authority

The compiled `gMapGroups[group][map]` consumer resolves root `0x486578`. It binds all 187 required encounter maps without an unbindable header across groups 0, 11, 24, 25, 26, 28, 29, and 33. Those headers produce 122 distinct required map-section IDs.

The source-shaped region-entry table begins at `0x5A147C` and has five raw pointer references. It is the sole table satisfying the complete required section set once named off-map records are recognized.

Ten required records use this exact shape:

- `x = 0xFF`;
- `y = 0xFF`;
- `width = 1`;
- `height = 1`;
- valid in-ROM pointer to a terminated map name.

The affected section IDs and decoded raw names are:

| Section | Name |
|---:|---|
| 101 | Green Swamp |
| 129 | Sprout Tower |
| 131 | Dark Cave |
| 133 | Church of Alpha |
| 134 | Dragon's Den |
| 138 | Mt. Mortar |
| 141 | Slowpoke Well |
| 157 | Cliff Edge Cave |
| 159 | Underground |
| 160 | Ice Path |

These are coherent named caves or interiors, not malformed geometry. The paired `0xFF,0xFF` coordinates mean the section has an identity but no publishable position on the world map. The other 112 required records have ordinary bounded geometry. A half-sentinel remains invalid.

## Generic correction

Region-table validation now accepts three explicit record states:

1. bounded on-map geometry with a terminated valid name;
2. paired `0xFF,0xFF` off-map coordinates with otherwise complete dimensions and name authority;
3. exactly eight zero bytes for an explicitly absent record.

Named off-map entries remain available for encounter identity and complete-table validation, but only ordinary on-map names count toward the three authority anchors. The existing normalized geometry boundary excludes sentinel coordinates from published world-map locations. Nonzero malformed records still invalidate a candidate.

No ROM name, hash, fixed table offset, section ID list, or per-ROM profile was added to production parsing.

## Focused result

The final published region contains:

- 110 encounter-bound semantic locations;
- 171 base-area bindings;
- 28×15 normalized geometry;
- 224×120 exact raster;
- no off-map sentinel geometry.

## Validation

The focused unit controls and all eleven Gen III real-ROM resolver controls pass with zero skips, failures, or errors.

The complete first-50 matrix passed after two fresh parses per ROM:

- 50/50 ROM hashes matched the manifest;
- 50/50 outputs were deterministic;
- 50/50 parses completed without an exception;
- 38/50 resolved at 100%, up from 37/50;
- all 12 unresolved rows published no map;
- 33/33 frozen first-33 family routes were preserved;
- all 49 non-Blazed-Glazed map projections exactly match the preceding Battle Theater gate;
- Blazed Glazed is the only changed row and moved from 0% to 100%.

Merged raw evidence is stored outside the repository at:

`D:\Temp\dualdex-blazed-glazed-first50-final.json`
