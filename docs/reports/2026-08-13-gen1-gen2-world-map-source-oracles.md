# Gen I and Gen II world-map source oracles

This report freezes the independent evidence used by the normalized Gen I/II map
resolvers. ROM bytes, local source paths, symbol files, and absolute offsets are
not repository artifacts. Source identities and exact-control digests below are
test/evidence inputs only; production selection uses compiled loader and data
roles.

## Reproducible sources and controls

| Control | Source revision | RGBDS | Source-build SHA-256 |
| --- | --- | --- | --- |
| Red | `pret/pokered` `2ab2421410b764e4dfebeddf8d9249d2cba947c4` | 1.0.3 | `5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b` |
| Blue | `pret/pokered` `2ab2421410b764e4dfebeddf8d9249d2cba947c4` | 1.0.3 | `2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d` |
| Yellow | `pret/pokeyellow` `e6ba56989b0f2694f393e6924820be11dcc1fbb8` | 1.0.3 | `8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf` |
| Gold | `pret/pokegold` `a0dad0957ac8a9ffa67e950ee3ab6715a212ded5` | 1.0.3 | `fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e` |
| Silver | `pret/pokegold` `a0dad0957ac8a9ffa67e950ee3ab6715a212ded5` | 1.0.3 | `72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c` |
| Crystal | `pret/pokecrystal` `8e8f7e20052a596371a77022f0392c285e51bbf1` | 1.0.3 | `d6702e353dcbe2d2c69183046c878ef13a0dae4006e8cdff521cca83dd1582fe` |

Each source build matched its repository checksum manifest. Yellow's frozen tree
declares RGBDS 1.0.2, but the available 1.0.3 toolchain reproduced the manifest
digest exactly.

## Gen I oracle

The three controls share the same 16-tile graphics and 20 by 18 Town Map:

| Evidence | SHA-256 |
| --- | --- |
| 256-byte 2bpp tile set | `8c3be45fbd98cd2ec054fe4ae9cc57fd9720b6b168aec4b4791ecdb253dfaeef` |
| 171-byte zero-terminated nibble-RLE stream | `d034ce912040a8ab68be68833e21b433bac6dfb35d7da8d0e672cce3d58d62d1` |
| decoded 360-cell tilemap | `0a27d622a2963940f2c41c3d780b056a98651982af5729a7e17b488ab1eacc0e` |
| normalized 160 by 144 opaque ARGB raster | `d55384218790ed7744af655bef486bcba8b1a932aa81e3d5701871f8ac60eca4` |
| deterministic PNG | `aa70952cb3c34789bc63639861d304b05b1c034dfb57e58720520de72d2ed098` |

The selected parser encounters require 57 map IDs in every control. The exact
sorted `mapId:x,y` binding fingerprint is
`165454e8cc5450e8a1edd0c6dcbfebb47e3254f1eb198d396efcdd5ef97d4433`
for Red/Blue and
`3c2f8177ae8d2073822e04d85bc76fb7f45e056e48c6ba5bf7297e22ef54dfbf`
for Yellow. The difference is the source-defined map-ID table, not the raster.

## Gen II oracle

Gold, Silver, and Crystal share the exact presentation assets:

| Evidence | SHA-256 |
| --- | --- |
| 768-byte decoded 48-tile 2bpp set | `8612dc1f4a9f2159594781010cac599df9f101e1fe92d6bd9ba3bbce7610c8a0` |
| 361-byte Johto plane including terminator | `d32fa5c06e8cf8a38caa290f677b5d292f411bf2a4661d4826d24ba83e69d7b3` |
| 361-byte Kanto plane including terminator | `9d7f64b1f1bb8768f07a23c8f49795faa1620b9f67e5aaa09319d4af6eab5517` |
| 24-byte packed tile-to-palette map | `245f90e9bff2cda21a8e28c9d111d1492f7e229a248f309c0fc299b431f49a78` |
| 48-byte default six-palette BGR555 set | `3f4e5a315395b9d665ab77740c8575ad550bfeac45db7a4779f725c1fdf51d89` |
| Johto normalized opaque ARGB raster | `adb9cefb64aece67c7cff271b70183af5dafa7c3e95beffd31436a7cab79a5e9` |
| Kanto normalized opaque ARGB raster | `c53b3c2e032545fa2452bbadd4a29aea8619cc852b9ed45d17d6d8475cebe5b7` |
| Johto deterministic PNG | `23739bddf01b2c98a03ca1c4af28ade7d751623ec8063311dd2b8b366c81c516` |
| Kanto deterministic PNG | `c06748683d60a89e4d2984bbcb565dc854ddd7942295d5039b80bcabe223258d` |

Every control joins 114 selected encounter base IDs. The canonical sorted
`baseId:landmarkId:x,y:region` fingerprint is
`455b70cb56ebf3494334e179736a247723c83d10c41a477a5ee7239869942749`
for Gold/Silver and
`355728883137963f6696793e9b5834a0155be312c8abd4d57a572c78981445d2`
for Crystal. Crystal inserts a source-defined landmark, shifting later landmark
IDs without changing the map rasters.

The landmark table contains a valid dynamic/off-map special record. None of the
114 encounter-bound control maps resolves to it. The normalized model excludes
that record from geometry. A narrow source-derived mutation that makes a
required header use the special record is required to fail closed at the
landmark join instead of inventing coordinates.

## Production boundary

The Gen I resolver validates the compiled far-copy, tile-base and RLE loop,
entry lookup, bank-local names, and full required map-ID coverage. The Gen II
resolver validates the compiled LZ3 graphics copy, two terminated plane wrapper
roles, packed palette-map consumer, CGB layout jump-table/default palette copy,
fixed-width group-map header consumer, landmark consumer, and region classifier.
Exactly one complete authority chain may publish a catalog.

The output contract is only normalized raster, dimensions, semantic region and
location geometry, and encounter base IDs. No source revision, ROM digest,
title, filename, symbol, or source-derived absolute offset participates in
production selection.
