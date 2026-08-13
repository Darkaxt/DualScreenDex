# Gen III loader-cluster structural evidence

This report follows the 14 `LOADER_ASSET_CLUSTER` rows that remained after the
crop-complete change in `48fcb7c`. The evidence runner inspected both affine
and text formats for every ROM regardless of parser-selected family. Titles,
hashes, physical offsets, and function addresses below remain test evidence;
production classification uses only loader/data roles and normalized format
invariants.

Official source oracle: `pret/pokefirered` `LoadRegionMapGfx` loads one shared
4bpp sheet, one 80-color BGR555 palette, four semantic region planes, and a
separate background. `BufferRegionMapBg` consumes exactly `30 * 20` u16 cells.
The compiled official FireRed control binds five distinct roots to destination
offsets 38, 1238, 2438, 3638, and 4838 in one complete loader CFG.

## Per-row loader stage

`M/S/P` is the number of candidate map/sheet/palette targets in the closest
complete loader owner. Destination slots are reported after compiled call-
argument decoding.

| Index | Selected family | Loader owner | M/S/P | Decompressed roles and destinations | Exact earliest rejection |
| ---: | --- | --- | ---: | --- | --- |
| 11 | Emerald | affine `0x2a8e8` | 1/1/1 | 4096-byte map, 6656-byte sheet, 64-byte palette call | crop references tile 166 but sheet has 104 tiles |
| 15 | FRLG | text `0xc0238` | 3/1/1 | 1184, 1184, 1200 roots; slots 0, 1/2/3, 4 | only two distinct semantic region roots; one root is reused for slots 1..3 |
| 16 | FRLG | text `0xc0238` | 3/1/1 | same role shape as index 15 | same invalid duplicate-plane authority |
| 17 | Emerald | none | 0/0/0 | 11204 compiled targets, but zero directly referenced decodable LZ roots | no direct decompressed sheet/map authority |
| 19 | Emerald | affine `0x2a8e8` | 1/1/1 | 4096-byte map, 6656-byte sheet, 64-byte palette call | crop references tile 166 but sheet has 104 tiles |
| 25 | FRLG | text `0xdb400` | 5/2/1 | five 1200-byte roots at all five slots; 9728- and 7168-byte sheets | only three planes compose; retained tile indices exceed both co-loaded sheets |
| 27 | FRLG | text `0xc0238` | 5/1/1 | 1216-byte slot 0; three 1200-byte regions and background; 3072-byte sheet | padded slot 0 is valid, but retained regions reference tiles beyond the 96-tile sheet |
| 31 | FRLG | text `0xc0238` | 5/1/1 | 1216-byte slot 0; remaining roots 1200 bytes; 7520-byte sheet | retained regions reference tile 308 but sheet has 235 tiles |
| 32 | FRLG | text `0xc0238` | 5/1/1 | 1216-byte slot 0; remaining roots 1200 bytes; 8480-byte sheet | retained regions reference tile 308 but sheet has 265 tiles |
| 33 | Emerald | text `0xc0238` | 5/1/1 | four 1216-byte semantic roots plus 1200-byte background at the official five-slot stride | aligned suffix padding was the loader-stage rejection; now reaches `map-header-join` |
| 36 | FRLG | text `0xc0238` | 3/3/1 | two 1216-byte roots occupy slots 2/3; background occupies slot 4 | region roots for slots 0/1 are not proven in the loader CFG |
| 43 | FRLG | text `0xc0238` | 5/1/1 | two 1248-byte roots, two 1200-byte roots, and background at all five slots | aligned suffix padding was the loader-stage rejection; now reaches `map-header-join` |
| 44 | FRLG | text `0xc0238` | 5/1/1 | same complete role shape and exact assets as index 43 | aligned suffix padding was the loader-stage rejection; now reaches `map-header-join` |
| 45 | FRLG | text `0xc0238` | 5/1/1 | one 1216-byte root, three 1200-byte roots, and background at all five slots | aligned suffix padding was the loader-stage rejection; now reaches `map-header-join` |

AshGray rows 15 and 16 are excluded from fix clustering: accepting their
duplicate region authority would violate the required one-to-one region model.
Dreams is already in `SEMANTIC_REGION_JOIN` after `48fcb7c` and is not mixed
into this loader-format change.

## Largest shared compiler/data shape

Eight rows expose aligned suffix-padded logical text planes (1216 or 1248
decompressed bytes) in a loader that still writes region destinations at the
source-proven 1200-byte stride. The official consumer reads the first 1200
bytes, so bytes after that logical plane are padding, never a prefix. Production
now accepts only 16-byte-aligned suffixes up to the largest proven 48-byte
variant. It still requires every byte touched by the display crop, rejects
unaligned 1218-byte and unsupported 1264-byte roots, and excludes map-shaped
streams from sheet candidacy.

Four rows have complete loader authority after this change: Clover, both Dark
Violet binaries, and Dark Worship. They advance safely to the independently
typed semantic header join; this is asset/compositor support, not a claim of a
complete world map.

Exact normalized ARGB controls are:

- Clover slots 0..3:
  `50a41e7a72bddfb8812a99ff01ac3e26170d5ab2ca5ed179b885c7ad21ed0ebb`,
  `4cf81884ab3be1fd315555385dd719cab2b5d46880f17982a5fc3ceb5ba838da`,
  `c79660d299bd1fb315c32cacfd21a41d10d76ef20fffacea67267609fb038bf2`,
  `11fef4f3fdbc027b99034f2389238b5d4c88938292dac94fded4c7ee45fcd08e`;
- Dark Violet slots 0..3:
  `117e4d9c854ec0b80ab942dcd7f65d8e52d8826589e93fa88532a8ce60422118`,
  `da5db5e336b772d95b541a793b3d44a6dc6ce628e43f6077b65c430b024e4aa1`,
  `17a547a2ecec1d3f93abfd74f569f250a92f16e303f93c12c1311566538db0bf`,
  `fd9e4540d935e9756f5fe9c7c519a9c7cbc3920778e61a1df0c9737c511d6b3d`;
- Dark Worship slots 0..3:
  `55f12ce30d00015e28c278bd9b8a5eafaa392a0ae19947b504e4c637e58b2457`,
  `118b80b0aed6e7bbd80318230aced72dcb8c469634a87f5d34f57d36a3b83673`,
  `b56fd1bb7f986ec6c638f82b5f07bbe9f5b6654b495d7e680f1bc5ca061786e4`,
  `74811ed87bb54a05512ddf99c383b1b2d9b90fb3b1110547ddfeb9ec4ae79d8d`.

Focused two-run CatalogParser artifacts are stored outside the repository at
`D:\Temp\dualdex-map-core-integration-evidence\gen3-padded-*.json`.
