# Dreamstone Mysteries Gen III World-Map Forensics

Dreamstone Mysteries previously scored 0% because its world map uses a source-backed tiled 8bpp background format that the generic Gen III compositor did not recognize. This note records the raw-ROM evidence behind the generic correction.

## Result

Dreamstone Mysteries now scores 100% with one deterministic parser-generated region:

- raster: 224×120 pixels;
- semantic grid: 28×15 cells;
- encounter-bound semantic locations: 96;
- raster SHA-256: `0cc223adec5306d6cdce6bc584a66f882f283fe5064eff114784143fab8da5e8`.

Two fresh parses produced identical assets, geometry, map-header joins, and encounter bindings.

Parser PNG:

`D:\Temp\dualdex-dreamstone-parser-render\gen3-region-0.png`

The image was visually inspected and is a coherent Cormoria map rather than a numerically composable but incorrect tile sheet.

## Source oracle

Repository: <https://github.com/dsmyst/dreamstone-mysteries>

Source commit used as an oracle:

`f7997186345885bfa23a170e5f573851fc034b9b`

The source described the likely loader and region-map ABIs. Every accepted asset, size, instruction shape, and table join was independently verified in the raw ROM.

## Raw asset-loader evidence

ROM SHA-256:

`ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220`

The normal loader begins at `0x1DC086`. Its compiled references prove one shared cluster:

- tiled graphics at `0xE84DB8`, decoded to 15360 bytes or 240 8bpp tiles;
- tilemap at `0xE843E8`, decoded to 1280 bytes or 32×20 u16 entries;
- palette at `0xE826E8`, loaded directly as 64 bytes.

The same tilemap is also referenced by the fly loader at `0x1DC29E`. That function uses the 7680-byte 4bpp graphics stream at `0xE846E8`. Interpreting it as 8bpp exposes only 120 tiles, while the raw tilemap requests tile 120 and higher, so the compositor rejects that cross-format pairing. The normal 15360-byte stream covers every referenced tile and every used palette index.

A map may therefore be shared by more than one source-owned loader. The uniqueness rule now applies within the specific function shared by a map/graphics pair. It still rejects functions containing multiple eligible map roots, but no longer rejects a legitimate map merely because normal and fly loaders both consume it.

## Tiled 8bpp composition

The raw 1280-byte map uses the GBA regular-background entry ABI:

- bits 0–9: tile index;
- bit 10: horizontal flip;
- bit 11: vertical flip;
- 32×20 physical tilemap dimensions.

The source and raw loader coordinate system place the 28×15 semantic map at tile offset `(1, 2)`. The compositor applies the raw flip flags, crops exactly that viewport, and validates every tile and palette index used by it before producing a raster.

The parser output exactly matches the independently rendered raw cluster.

## Map-header authority

The compiled `gMapGroups[group][map]` consumer begins at `0x17D324`. This compiler emits a 16-byte indexed-load form:

- literal-load the table root into `r3`;
- zero-extend and scale each u16 argument by four;
- load `gMapGroups[group]` with `ldr r3, [r0, r3]`;
- load the map header with `ldr r0, [r1, r3]`;
- return through `bx lr`.

The literal proves root `0x1186E6C`. The generic resolver now recognizes this complete instruction shape. All 154 encounter-requested map-header slots were evaluated and bound.

## Region entries

The uniquely referenced region-entry table is at `0xE82D24`. Its records use the existing eight-byte ABI: x, y, width, height, and a name pointer.

One required section record is explicitly all zero. It belongs to stale Hoenn Victory Road maps retained in the encounter data and cannot name or locate a Cormoria region-map entry. The resolver now accepts a candidate table only when every required record is either:

- a structurally valid, terminated region entry; or
- an exact all-zero record.

Nonzero malformed records still reject the candidate. At least three referenced entries with ordinary alphanumeric names remain required as table anchors.

A second valid source entry is named `???`. Valid terminated punctuation-only names are now retained after table authority is established, while anchor selection still requires ordinary text. This preserves the intended location instead of silently dropping it.

## Validation

- raw assets matched the public source products byte-for-byte after decompression;
- the parser PNG was visually inspected;
- two fresh complete parses were deterministic;
- all 154 encounter map-header slots were classified;
- the resulting catalog contains 96 encounter-bound semantic locations;
- the focused map-location and compositor contracts are covered by tests;
- no ROM name, ROM hash, production offset, or per-ROM profile was added to parser logic.
