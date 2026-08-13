# Gen III world-map stage matrix

This report freezes the real-control evidence used by the isolated map-core gate. Source names, revisions, ROM hashes, and observed offsets belong only in evidence and tests. Production selection must use decoded structure, compiled-reference authority, and fail-closed classification.

## Real controls

| Family control | ROM SHA-256 | Source oracle |
| --- | --- | --- |
| Official Emerald | `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` | `pret/pokeemerald` at `9a83a2bbe8e0` |
| Modern Emerald 3.5 | `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895` | `resetes12/pokeemerald` at `01a4212` |
| Classic 1.5.0b | `01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c` | `DaniRainbow/pokeclassic` at `c85ebde792bd` |
| Official FireRed/LeafGreen Rev 1 | `729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059` / `2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825` | `pret/pokefirered` at `c75f352304d5` |

## Format and stage matrix

| Stage | Official Emerald | Modern Emerald | Classic | FireRed/LeafGreen |
| --- | --- | --- | --- | --- |
| Source asset root | `graphics/pokenav/region_map` | `graphics/pokenav/region_map` | `graphics/pokenav` | `graphics/region_map` |
| Tile sheet | 8bpp, `128x120`, 233 compiled tiles | 8bpp, `128x120`, 240 compiled tiles | 8bpp, `128x120`, 233 compiled tiles | 4bpp, `128x160`, 320 compiled tiles |
| Decoded tile hash | `7fab32a15049c96dd3d8eb9c0ef9ff969a0254c6d96c8538b4b104e8af13dd39` | `5828ca11400d78d81f09aad639ee481155eda63f923dc6ca1175ea6193367148` | `ce2b7db0298fe504ec250092748c940649deb61ad342655480576aef34622de8` | `f9e8ddc403b2efcd9eaf87a8a1f16d9248f92d2372e42fb7aa88b09aed5fb3b4` |
| Tilemap | one-byte affine, 4096 entries (`64x64`) | one-byte affine, 4096 entries (`64x64`) | one-byte affine, 4096 entries (`64x64`) | four 16-bit text maps, each 600 entries (`30x20`) |
| Tilemap hash(es) | `dcf3d464dad11083ece52687184c89ab069c108340ecf5540eb0f14c6d8c8096` | `1627ca00f20c0a593ed30d4657cd165bdf92f31c30b4304464aed3e2688de873` | `8675dbba552d2ca9f2179bf15597fa1ed1612a2a39faf54dda173b887d4836a1` | Kanto `c9f38c5d52099958c18efe737a45ba04ce8101b0eb349e9d2243bf8324d82b49`; Sevii 123 `72c0b2615eaf061f5490779a3caf0470dbb88b08dd010c4887b8ed6d61eac124`; Sevii 45 `cd68b8a70e21ac1f53344160234d1eabb7221040c62102c3a648faa550eb40db`; Sevii 67 `2d0f7a665f88f15d28c213f7e490c232c3eeea8ffc12156aafce32499caa2400` |
| Palette | 32 BGR555 colors loaded at index 112 | same | same | 80 BGR555 colors in five 16-color banks |
| Palette hash | `795a5502910a4a8d226589bfd0d8c421111e30db3d152acaf66186e6659b4563` | same | same | `116382eeea3b668f188e80eb49f7440b1daeb0732cd81da2401da887e1e0e227` |
| Crop and raster | loader cells `(1,2)`, `28x15` -> `224x120` | same | same full canvas -> `224x120` | loader cells `(4,4)`, `22x15` -> `176x120` |
| Region geometry | `28x15` layout and entries | `28x15` layout and entries | 420-byte `28x15` section plane; entry extent is only `19x15` and must not shrink the canvas | four independent `22x15` map/dungeon section planes |
| Normalized ARGB SHA-256 | `1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab` | `0163d9b5e747d788db925776c25a087a1cc4bbfa34fd3e021580aa8756717fb0` | `dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f` | Kanto `250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654`; Sevii 123 `8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135`; Sevii 45 `eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a`; Sevii 67 `b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b` |
| `PngEncoder` SHA-256 | `c9d5f2a5c77c0df16c14c73a15577f0c6f4a05794c191ebe72ed5a24724aadc6` | `80c4a69b9372276818768123dcd7cad09bcced88720704c8f424bc4501931ffe` | `0c171c9fe8175629aa47de4e2854a334a2025f21b9196ba2f4c57a8cdcbc67ec` | Kanto `c691c958253ff35595b36bf69f85d8d8940929c13deb7d0851ece717ab9d67aa`; Sevii 123 `5bf5a1caf04a9bdbbbb80ea4dba5f9cdbf7d1eb046e7d29a85f6cacd392fbb70`; Sevii 45 `d6f9b9aac3127691700f46e4df681ce6c1aee8a4f32c0f274b1df043dc47c160`; Sevii 67 `2e1d951bf0cdf4181a43fc2e451428b067a7f9ba4307dfbc7a6eea237bf01765` |

The PNG hashes are the actual Java `PngEncoder` byte contract re-computed from the real binary controls. They replace preliminary Python DEFLATE hashes; both encoders produced the exact ARGB hashes above, but DEFLATE byte streams are not required to be identical. The POC's sanitized, visually equivalent Modern PNG hash `e5a9ea67be860e1110678118267e50b30b4002001d42f12eea8575436283f493` preserves source PNG channel values rather than BGR555 round-trip values and is therefore a presentation oracle, not the exact parser pixel oracle.

## Proven loader semantics

Emerald-family region maps decompress an 8bpp sheet and a 4096-byte affine tilemap separately. Each tilemap byte is a direct tile index. The cursor constants add `(1,2)` around a fixed `28x15` semantic canvas. A 4096-byte stream must never be reinterpreted as 2048 text entries. That old interpretation admitted the striped-circle false result.

FRLG decompresses one shared 4bpp sheet and four named `30x20` text maps. Each little-endian map entry uses bits `0..9` for tile index, bit `10` for horizontal flip, bit `11` for vertical flip, and bits `12..15` for palette bank. `BufferRegionMapBg` copies all 30 columns into a 32-column background buffer. Cursor centers are `8*x + 36`, `8*y + 36`, proving that semantic cell `(0,0)` starts at backing tile `(4,4)`. The source initializes selected region to Kanto for Kanto map sections and to one of three Sevii groups by explicit section membership. A parser without runtime current-map state must emit separately proven regions or fail closed; it must not guess a selected region.

## Binary/source equivalence controls

Evidence-only source offsets showed exact decoded equality, not visual similarity:

- official Emerald decoded sheet/map matched the source-owned binaries;
- Modern decoded sheet/map matched its source-owned binaries;
- Classic's correct streams decoded to 14,912-byte sheet hash `ce2b7db...` and 4,096-byte map hash `8675dbba...`, matching the source exactly;
- FireRed's 10,240-byte sheet and all four 1,200-byte maps matched the source exactly.

The previously reported Classic `c5ebe868...` raster came from unrelated 2,048-byte streams interpreted through the removed text compositor. It is a negative control only. The correct full Classic raster is the `224x120` ARGB hash `dc326776...` above.

## Production boundary

Production may classify only these structural contracts:

1. `AFFINE_8BPP_64X64`: 8bpp tiles, a 4,096-byte one-byte affine map, a valid BGR555 palette window, and fixed crop `(1,2,28,15)`.
2. `TEXT_4BPP_30X20`: 4bpp tiles, a 1,200-byte little-endian text map whose tile/bank/flip fields validate, a valid BGR555 banked palette, and fixed crop `(4,4,22,15)`.

The compositor returns normalized raster dimensions and pixels or a typed rejection. Candidate discovery and source-family ABI end before catalog persistence. Names, ROM hashes, source symbols, and absolute offsets remain confined to tests and this report.
