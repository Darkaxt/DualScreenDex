# Gen III semantic-join structural evidence

This report follows the 14 frozen `SEMANTIC_REGION_JOIN` rows plus the padded
text-map rows that reached the same stage after `ace32de`. Titles, hashes, and
physical addresses below are evidence only. Production selection uses decoded
consumer instructions, ROM-bounded pointers, required encounter keys, map
headers, section IDs, semantic planes, and normalized assets.

## Source and compiled authority

`pret/pokefirered` implements `Overworld_GetMapHeaderByGroupAndId` as
`gMapGroups[mapGroup][mapNum]`. Its compiled official FireRed control
zero-extends two 16-bit arguments, indexes the root and selected group by four,
and performs the two pointer loads. A second real compiler shape receives
already-narrow arguments and omits only the two zero-extension pairs. Both
terminate at the same data contract.

The resolver now requires exactly one decoded consumer/root, every required
encounter `(group,map)` key to resolve one ROM-bounded header, and a section ID
from every selected header. It does not enumerate unrelated groups or infer
array extent from adjacent packing. Partial, duplicate, and decoy consumers
fail closed.

The map header validator also follows the engine's nullable data contract.
Both pret FRLG and Emerald-family object-event consumers explicitly handle a
null `events` pointer. Zero is therefore accepted, while any nonzero pointer
must still be ROM-bounded. The Clover held-out contains the real null-events
control at required encounter key `0x2818`.

## Mechanical focused result

The pre-edit pass measured the selected root against every required encounter
key. The post-edit pass parsed a complete normalized catalog for each row.

| Index | Row | Required header bindings | Focused outcome |
| ---: | --- | ---: | --- |
| 2 | Advanced Adventure | 126/128 | fail closed |
| 3 | Adventure Red Chapter | 18/19 | fail closed |
| 10 | Altair | 122/122 | fully resolved, 1 region |
| 14 | Arcoiris | 97/97 | fully resolved, 1 region |
| 18 | Bill's Secret Garden DX | 127/127 | fully resolved, 4 regions |
| 20 | Blazing Emerald | 140/142 | fail closed |
| 30 | Cloud White | 15/15 | typed encounter-binding failure; one semantic plane has no encounter binding |
| 33 | Clover | 157/157 | fully resolved, 4 regions |
| 35 | Crown | 0/0 | safe no-map fallback |
| 39 | Dark Rising - Order Destroyed | 126/126 | fully resolved, 4 regions |
| 40 | Dark Rising 2 | 126/126 | fully resolved, 4 regions |
| 41 | Dark Rising Origins - Worlds Collide | 127/127 | fully resolved, 4 regions |
| 42 | Dark Rising | 132/132 | fully resolved, 4 regions |
| 43 | Dark Violet | 124/124 | fully resolved, 4 regions |
| 44 | Dark Violet fan patch | 124/124 | fully resolved, 4 regions |
| 45 | Dark Worship | 160/161 | fail closed |
| 47 | Delta Emerald | 116/116 | fully resolved, 1 region |
| 48 | Dragonstone | 60/100 | fail closed |
| 49 | Dreams | 33/33 | typed encounter-binding failure; one semantic plane has no encounter binding |

That is eleven focused full resolutions. It is not the public exact50 count;
only a fresh two-run exact50 report can move the release-gate numerator.

## Held-out exact controls

Dark Violet is the first full held-out vertical control:

- raster ARGB hashes, slots 0..3:
  `117e4d9c854ec0b80ab942dcd7f65d8e52d8826589e93fa88532a8ce60422118`,
  `da5db5e336b772d95b541a793b3d44a6dc6ce628e43f6077b65c430b024e4aa1`,
  `17a547a2ecec1d3f93abfd74f569f250a92f16e303f93c12c1311566538db0bf`,
  `fd9e4540d935e9756f5fe9c7c519a9c7cbc3920778e61a1df0c9737c511d6b3d`;
- location counts: `44, 10, 6, 3`;
- normalized geometry/base-area binding hash:
  `e8c90983d9128c110668a55f7656a37062f7b5d63ff749e8e9d98c8b5d8c620d`;
- API PNG hashes:
  `d5f07e96179d64e411ac4dec65c6b5d45fd190391b153a67c0a12927ab0a63bb`,
  `5a5da685c0211d1639f9de29c0749239db8ed22aa819b249e23bb940fa43c32c`,
  `e46976338b3b08670f1c2e846100a58bfc7f337ba92a0f989024aa357b0f8778`,
  `d7a86d7147422ba4dc09e72e14a1ba8c5bc3f2feafcb6e78cf4aac7875c7a68a`.

Clover independently freezes 4 exact rasters, location counts `45, 10, 4, 8`,
binding hash `9cd112641a396c32e571557d6fd42d92b87d52dd7d71ffd9ff605e609212dc10`,
and exact API PNG bytes. Official FireRed and LeafGreen retain their source
oracle rasters, four region identities, and semantic bindings.
