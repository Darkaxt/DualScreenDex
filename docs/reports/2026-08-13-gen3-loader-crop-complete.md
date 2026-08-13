# Gen III loader cluster: crop-complete text planes

This focused stage comparison follows the frozen 16-row `LOADER_ASSET_CLUSTER`
from `2026-08-13-map-first50-gen12-raw.json`. ROM identity, hashes, and physical
offsets below are test evidence only. Production classification uses compiled
loader roles, decompressed dimensions, destination slots, palette validity, and
composition invariants.

## Source contract

`pret/pokefirered` `LoadRegionMapGfx` proves one shared 4bpp sheet and 80-color
BGR555 palette plus five ordered text planes: Kanto, Sevii 1-3, Sevii 4-5,
Sevii 6-7, and a separate background. Each normal source plane is 30x20 u16
cells, while presentation reads only crop `(4,4)` with dimensions `22x15`.
The last required crop cell ends at byte 1132. Bytes 1132 through 1199 are not
read by normalized presentation.

## Real binary stage comparison

| Control | Sheet role | Palette role | Region destinations | Plane sizes | Earliest result |
| --- | --- | --- | --- | --- | --- |
| Official FireRed | one 10240-byte 4bpp root | one valid 160-byte load in the same complete loader CFG | slots 0..3 plus background | 1200/1200/1200/1200 + 1200 | exact 4-region control |
| Dark Cry | one 11520-byte 4bpp root | one valid 160-byte load in the same complete loader CFG | slots 0..3 plus background, at the same 1200-byte destination stride | 1184/1200/1200/1200 + 1200 | first region omitted only eight unused trailing cells; exact crop is complete |
| AshGray 4.6 | one 8192-byte sheet chain | one valid 160-byte load in the complete loader CFG | five destination roles remain | only two distinct 1184-byte region roots, one reused for slots 1..3, plus background | reject: four semantic region identities are not one-to-one |
| Altered Emerald | candidate affine loaders exist | no valid BGR555 palette in the source-owned affine cluster | not applicable | candidate 4096-byte planes do not establish the proven affine world-map cluster | reject at asset-loader |

Dark Cry ROM SHA-256 is
`e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed`.
Its focused binary controls are:

- sheet: offset `0x7680c1`, decompressed 11520 bytes, SHA-256
  `ee83cb51854bb3f67a88e43cd254ef34c4bf9239e439929432bbd8cd381a9547`;
- crop-complete first plane: offset `0x769161`, decompressed 1184 bytes,
  SHA-256 `6d330c519ae07ce7e8e09fd6dc30de980e2445c956496d87269a5db477a9b1cc`;
- palette: offset `0x3ef2dc`, 160 bytes, SHA-256
  `e3ef03b01b555aa548511076cabe462f6a2d95cb668a5a4c2b9e4271ed18b060`.

The loader binds the short root to destination slot 0, three independent
1200-byte roots to slots 1..3, and the background to the fifth destination.
The four normalized ARGB raster hashes are:

1. `bb44c69d073c93911dd47d6121b936e40174cb84ca5128a5d0912ea6981b36d7`
2. `1933d3f93fc82dcfc0f7f5c5db82a9f98d264108ac3fb9f6da4aa46aa41c1d0d`
3. `182c44baf94103874a3aa76867b6640d74bc6b0709cdd551bcd30ba358f2e4e6`
4. `9aa8f8db6faf3d0317a5a3dececeac4fed923816f7b1ff105293111c129de19c`

These four raster hashes are loader/compositor evidence only. The semantic
resolver cannot bind any encounter location to Dark Cry's fourth region, so it
fails closed at `encounter-binding`. CatalogParser records `WORLD_MAP` as
`NOT_FOUND`; SQLite/runtime reload retains zero regions and assets; the
loopback asset route returns 404. This is the deterministic `LOCATION_BINDING`
row recorded by the frozen exact50 release gate, not a four-region available
catalog.

## Fail-closed boundary

The compositor accepts an even-length text plane only when it retains the
30-cell row stride and every byte through the final cell touched by the crop.
An exact 1132-byte boundary produces the same Dark Cry raster; 1130 bytes,
which removes the last required cell, is rejected. Trailing unused cells may be
absent. The resolver still requires one complete loader CFG, one sheet/palette
authority, exactly four distinct semantic region destinations, and the separate
background destination. No ROM identity or physical placement enters
production selection.
