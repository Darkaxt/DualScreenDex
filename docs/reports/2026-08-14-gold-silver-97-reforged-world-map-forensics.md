# Gold and Silver 97 Reforged world-map forensics

Date: 2026-08-14

## Result

Gold 97 Reforged v6.1f and Silver 97 Reforged v6.1f moved from 0% world-map compatibility (`NO_FAMILY_MATCH`) to complete deterministic two-region map resolution.

The correction does not add a ROM profile, title, hash, fixed production offset, section list, threshold exception, or ROM-specific fallback. It adds two guarded structural interpretations:

1. a complete compiled Gen II move-type consumer may select a relocated seven-byte move table;
2. a compiled CGB layout may copy seven or eight palettes when its first six palettes are the complete Town Map palette domain.

## Inputs

- Gold ROM SHA-256: `5e0c4688abd5ce2cb00d76902301791d5dfd196a99ff1e764268dffb196c50c3`
- Silver ROM SHA-256: `6d491ec85788e967aface80b61f91936bd84deb9239fef1d010f93962fe58828`
- Headers: GBC, `PM_GOLD` / `PM_SILVER`, revision 1
- Public source oracle: <https://github.com/SoupPotato/gold97>
- Source commit inspected: `976507f9e6e605050384e9ec12e9651988ae7c46`

No source offset was used as parser authority. Gold and Silver contain the same raw authorities described below.

## Corrected diagnosis

The initial hypothesis was that the pair failed because their compiled name/base consumers expose a 253-row same-bank boundary. Raw focused diagnostics disproved that as the immediate blocker:

- 251 species names already validate at `0x53384`;
- 251 32-byte base rows already validate at `0x513B0`;
- the Crystal probe scored 67 with valid names, base data, move names, sprites, and three anchors;
- its inherited move-data root `0x41AFB` was three bytes before the actual record boundary.

The Gold/Silver probe happened to inherit `0x41AFE`, but its weaker identity and cross-table evidence reached only 57. The complete compiled move consumer independently selects `0x41AFE`; giving that structural evidence to the non-exact Crystal probe raises it to 80 with four anchors and a 23-point selection margin.

## Raw compiled move authority

The complete consumer at raw `0x50924` proves:

- `push hl; ld a,b; dec a` converts the one-based move ID;
- `ld bc,7` supplies both the record multiplier and copy length;
- `ld hl,$5AFE` and bank `0x10` select raw root `0x41AFE`;
- the row is copied to `$D073`;
- the byte at destination plus three is loaded as the move type;
- that value indexes a two-byte type-name pointer table;
- the complete path restores `hl` and jumps to the string renderer.

The selected 251×7-byte table passes the existing Gen II move-data validator.

- consumer bytes: 42
- consumer SHA-256: `b42bf17752a55f596e5ef478dc8112cc5fafa8327b800cd68e20ab75d6960243`
- move-table bytes: 1,757
- move-table SHA-256: `3670935f03450a8bc77164dc71254ff0d8b758a9e7a4c6d9522b7fb5d00b6130`

The resolver requires one unique surviving layout. It rejects incomplete instruction chains, a non-seven-byte multiplier, a copied-field mismatch, an invalid bank/address pair, invalid move content, and ambiguous roots. Exact official profiles remain authoritative and bypass this heuristic.

## Extended CGB palette copy

The Town Map plane, palette-map, and graphics consumers were already structurally complete. The asset chain failed only because the CGB layout copies seven palettes (`56` bytes), while the parser previously required an exact six-palette (`48`-byte) copy.

The raw CGB routine beginning at `0x8EAD` proves:

- a runtime gender branch selects one of two palette roots;
- the fallthrough root is `0xB6FF`;
- the destination is the CGB background-palette buffer;
- `ld bc,$0038` copies seven complete palettes;
- the routine is the third entry of the compiled CGB layout jump table;
- Town Map callers select the same entry through `ld b,2`.

The Town Map palette map addresses only palette IDs `0..5`. The parser now accepts an aligned six-to-eight-palette CGB copy but parses and validates only its required first six-palette prefix. It still requires six valid BGR555 palettes, a shared background color, at least five distinct palettes, the compiled jump-table authority, and the Town Map caller. Trailing UI palettes cannot affect composition.

- palette routine bytes: 35
- palette routine SHA-256: `78c5191c0d18b169c03b09e86622ac0c92cd284395b284a01cd209a714f7bf3b`
- six-palette prefix root: `0xB6FF`
- six-palette prefix SHA-256: `3f4e5a315395b9d665ab77740c8575ad550bfeac45db7a4779f725c1fdf51d89`

## Town Map raw authorities

The common Town Map chain proves:

- direct Johto/Kanto plane selection and a shared terminator-copy loop;
- 20×18 planes whose 360 tile IDs stay within the 48-tile domain;
- a 24-byte packed palette map;
- a same-bank loader that requests exactly 48 decompressed 2bpp tiles;
- the compiled CGB layout and caller relationship described above.

| Authority | Raw root | Bytes | SHA-256 |
| --- | ---: | ---: | --- |
| map/palette-map consumer span | `0x91CE7` | 77 | `bca9ae84e7bbecfdc30f6c4c32a20e25fa5240c72b5c0cec526c2fce7b39d42d` |
| first plane plus terminator | `0x91DE8` | 361 | `08df83a0036d0b9c67c4b26c4eadd1e772b140d34ffb0e5ded35240beceb0291` |
| second plane plus terminator | `0x91F51` | 361 | `4652be184cf214b59d81b5bff2e8a6c18392cefebf3cb76bee0eaea8bd557207` |
| packed palette map | `0x91D34` | 24 | `245f90e9bff2cda21a8e28c9d111d1492f7e229a248f309c0fc299b431f49a78` |
| graphics loader | `0x91DDB` | 13 | `1384073d4764cd779da525e817a2b84b73c2e55e9c89b6e2baace77ded955806` |

The graphics loader selects compressed root `0xF89A0`, which decodes to exactly 768 bytes (48×16).

## Family and catalog result

Both focused diagnostics changed from `NO_FAMILY_MATCH` to:

```text
Decision: SELECTED / CRYSTAL
Score: 80
Margin: 23
Species names: 251 at 0x53384, stride 10
Base data: 251 at 0x513B0, stride 32
Move data: 251 at 0x41AFE, stride 7
```

Each focused parser run materialized 249 named/stat/sprite species, 251 moves with move data, 347 encounter areas, and validated cross-references without crashing.

## World-map validation

Encounter projection for each variant:

- encounter areas: 347
- unique group/map IDs: 116
- method counts: water 71; morning/day/night grass 92 each
- every slot has species `1..251`, levels `1..100`, and positive weight
- Gold encounter fingerprint: `ca44fdb56e0e6d9efa35a51016cb244b49249b7b35d692eeed32360d4385ca78`
- Silver encounter fingerprint: `ae7f69524f2fde1202875a4ff3037dc4380d364e93c43efec3546eabaa9fbffd`

All 116 encounter group/map IDs join through one compiled map-header, landmark, and region-classifier chain.

| Region | Raster | Grid | Locations | Bindings | ARGB SHA-256 | PNG SHA-256 |
| --- | --- | --- | ---: | ---: | --- | --- |
| first / Nihon | 160×144 | 20×18 | 44 | 75 | `918bdd844e7a55c84e7e1c88275ba0dcf517e51623f3d1d31908e7fece2cbdfe` | `b526ae430bf61564734659ab5b7a80368e0dbc2c6deb2633a8f1c0bae6f38758` |
| second / Southwest Islands | 160×144 | 20×18 | 26 | 41 | `9ca35356ada5a30589dfa1ce8459b9043b86057a31d14f751cc7157c9687e8ea` | `b4b5a30c62d59dde74e74915eaca03e32f373afa220b0ab2aaf2718ff58a4e65` |

Canonical location fingerprint for both variants:

`9ef198994a7b16dc395876e7f0fb98ac45ba5a23210777b99a36a2d070029af7`

Four PNGs were exported under `D:\Temp\dualdex-gold97-poc-render` and visually inspected. Gold and Silver render the same coherent Nihon and Southwest Islands maps; none is gibberish.

Each retained control performs both direct map resolution and full `CatalogParser` materialization. The independent paths reproduce the same location and raster fingerprints, and the integrated catalog reports `WORLD_MAP = AVAILABLE`.

## Bounded regression evidence

The retained 120-row GB/GBC baseline was reused; no broad map matrix was rerun. The rows represent 119 unique ROM hashes, all of which were present in the raw survey.

Nine retained ROMs contain a complete compiled move consumer whose selected 251-row root passes existing validation:

- Crystal Kaizo, Crystal Legacy, and Crystal Legacy Timeless retain their existing Crystal routing;
- Gold and Silver 97 Reforged are the only newly selected pair;
- Kalos Crystal retains Crystal routing and its exact encounter/location/raster control remains unchanged;
- both Orange Suloku variants and Peridot remain `NO_FAMILY_MATCH`;
- exact official profiles remain excluded from the heuristic.

A related 13-ROM expanded-corpus subset additionally covered Bronze, Bronze 2, and Dark Energy consumer shapes. Their existing Gold/Silver or Crystal routing remained selected; there were no ambiguous selections or parser errors.

The palette survey found 17 validated compiled CGB layout authorities in the retained baseline: 11 exact six-palette copies, five seven-palette copies, and one eight-palette copy. Only Gold and Silver combine the newly accepted seven-palette form with this complete map chain. Existing six-palette behavior is unchanged, while every accepted extended copy still passes the same six-palette semantic prefix and compiled caller/jump-table proofs.

Focused exact controls passed for Gold 97, Silver 97, and the previously published Kalos Crystal output.
