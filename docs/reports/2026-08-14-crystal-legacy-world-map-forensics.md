# Crystal Legacy world-map forensics

Date: 2026-08-14

## Outcome

- Corpus row: 249
- Compatibility before: **0%** (`asset-loader`)
- Compatibility after the isolated correction: **100%**
- ROM SHA-256: `18153207488a9e2b4837d677ec9f1240dc2674a29dd6a0319553b73cafccceaa`
- Selected family: Crystal
- Published regions: 2
- Deterministic: yes, across two complete parses

The parser already selected the correct base family and later semantic pipeline. Resolution stopped at the asset chain because the Gen II palette-map consumer required one exact shared-screen tile cutoff.

## Source oracle

The public `cRz-Shadows/Pokemon_Crystal_Legacy` source was inspected at commit `77e6b3859349698ad0a7a8a93cd27f78860d3e4a`. This revision is newer than the corpus ROM and was used only to identify likely roles.

Its Town Map source preserves the relevant Gen II contracts:

- `LoadTownMapGFX` decompresses 48 2bpp tiles;
- `FillJohtoMap` and `FillKantoMap` select terminated 20×18 tile planes;
- `TownMapPals` uses a packed nibble palette table;
- the palette consumer also covers adjacent Pokégear tiles and compares tile IDs against `0x68`, rather than the canonical `0x60` cutoff.

All accepted addresses, data, and joins were then established independently from the raw ROM.

## Raw asset authority

The ROM contains one complete source-shaped asset owner in bank `0x24`:

- map authority: `0x91FD2`;
- Johto plane: `0x9250D`, 360 cells plus `0xFF`, maximum tile 47;
- Kanto plane: `0x923A4`, 360 cells plus `0xFF`, maximum tile 47;
- palette consumer: `0x91FF1`, compiled as `cp 0x68; jr nc`;
- packed palette-map root: `0x9201E`, whose first 24 bytes cover all 48 Town Map tiles with palette IDs 0–5;
- graphics loader: `0x920C5`, loading exactly 48 tiles into VRAM from compressed root `0xF8AB0`;
- six-palette load: `0x8ED2`, with exact raw palette SHA-256 `3f4e5a315395b9d665ab77740c8575ad550bfeac45db7a4779f725c1fdf51d89`.

The `0x68` comparison is a shared runtime fallback boundary, not the number of Town Map tiles. The two raw planes only reference tiles 0–47, so the authoritative map palette data is the valid 24-byte prefix. Requiring the canonical `0x60` immediate rejected this otherwise complete chain.

## Generic correction

Palette-map discovery now accepts a packed consumer boundary when:

- the consumer compares a tile limit and branches on `jr nc` for out-of-range tiles;
- the limit describes complete nibble pairs;
- the covered packed entries are sufficient for all 48 Town Map tiles;
- the first 24 bytes contain only palette IDs covered by the six proven palettes;
- the surrounding two-plane, graphics-loader, palette-loader, same-bank, and unique-chain requirements all remain satisfied.

The resolver still consumes only the palette prefix required by the 48 proven Town Map tiles. It does not add a ROM name, digest, fixed offset, threshold allowlist, or per-ROM profile.

## Rendered output

Both parser-generated PNGs were inspected and are coherent maps rather than gibberish:

| Region | Raster | Locations | Bindings | ARGB SHA-256 | PNG SHA-256 |
| --- | --- | ---: | ---: | --- | --- |
| Johto | 160×144, 20×18 | 45 | 78 | `9d348e028f32fe38f23c3ae561ee2f512fd41fa360d9313d412b5337c178411a` | `8cd6695ceed1b62540cb3e0fdd462667868cdc73406f06cda99413e0d00c6fd7` |
| Kanto | 160×144, 20×18 | 34 | 36 | `ae6bd49974c5d87260f8567b0810bdd0d9c0aabfe1a453a3d7459a91dc1faaa6` | `733420d9fa1c8ad3a7ed70279ef98297f43cc11fcae3e737b18c5446f61e3a75` |

The complete encounter/landmark/geometry fingerprint is `355728883137963f6696793e9b5834a0155be312c8abd4d57a572c78981445d2`.

The retained render directory is:

`D:\Temp\dualdex-crystal-legacy-poc-render`

## GB/GBC regression evidence

The completed regression evidence was reused rather than rerun:

- the baseline covered all 120 GB/GBC corpus paths;
- the final two-pass outputs covered 79 paths, including all 65 Game Boy Color rows;
- all 79 final outputs were deterministic and fail-closed when unavailable;
- all 31 previously available GBC map projections remained exactly unchanged;
- only three GBC rows changed, each from 0% to a complete two-region result: Crystal Legacy, Crystal Legacy Timeless, and Crystal Kaizo;
- Crystal Legacy Timeless produced the exact same complete map projection as the visually inspected Crystal Legacy output;
- no existing 100% row changed and no unresolved row published a partial map.

The retained evidence is stored outside the repository at:

- `D:\Temp\dualdex-gb-gbc-map-baseline.tsv`
- `D:\Temp\dualdex-gb-gbc-map-crystal-legacy-1.tsv`
- `D:\Temp\dualdex-gb-gbc-map-crystal-legacy-2.tsv`
- `D:\Temp\dualdex-gb-gbc-map-crystal-legacy-3.tsv`
