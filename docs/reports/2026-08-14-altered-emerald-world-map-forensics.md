# Altered Emerald Gen III World-Map Forensics

This note records why Altered Emerald v4.2c previously scored 0%, the raw-ROM evidence behind the generic correction, and the real-ROM controls used to prevent a parser-only or gibberish-map result.

## Result

Altered Emerald now scores 100% with one parser-generated region. Two fresh parses produced the same raster, semantic geometry, map-header joins, and encounter/base-area bindings.

Parser PNG:

`D:\Temp\dualdex-altered-emerald-release-render\gen3-region-0.png`

The 224×120 image was visually inspected. It is a coherent region map rather than the horizontal strips produced by the first POC.

Raster SHA-256:

`120fc88466f34514b5555f38101074c2218e268cb9bbec4e3a693c153154f539`

The 28×15 semantic grid contains 78 encounter-bound locations.

## Source oracle

Repository: <https://github.com/tuckerwhite/pokeemerald-expansion>

Source commit used as an oracle:

`39636f43ae27c305edfbc5e61f82abe775ae1f0b`

The source described likely loader and table ABIs. Asset identity, control flow, sizes, and joins were accepted only after the raw ROM independently proved them.

## Raw loader evidence

ROM SHA-256:

`8fe93d8245c96ea5aa49d61df2c74ee99a439b15cde7c0afa4f0b5a87aac34f0`

The loader owner begins at `0x1C4D70`. Its mode guard is:

- `0x1C4D86`: compare the mode register with zero;
- `0x1C4D88`: branch to the affine arm at `0x1C4DDC` when nonzero.

The fallthrough arm loads the normal assets:

- graphics root `0x61D1A0`, 14848 decoded bytes;
- tilemap root `0x61DEF4`, 2048 decoded bytes;
- forward join branch at `0x1C4DCE`.

The taken arm loads the affine assets:

- graphics root `0x61E208`, 14912 decoded bytes;
- tilemap root `0x61EF64`, 4096 decoded bytes;
- copy call at `0x1C4E2A`;
- join at `0x1C4E2E`.

Both affine roots belong to the same taken branch arm. Pairing the affine tilemap with the normal graphics root crosses the mode guard and is rejected.

## Palette contract

The palette block is:

- `0x1C4E58`: source `0x61D140`;
- `0x1C4E5A`: destination `0x020377F4` in EWRAM;
- `0x1C4E5C`: `CpuSet` control `0x04000018`;
- `0x1C4E5E`: copy call.

The control word proves 24 32-bit units, or 96 bytes. Reading only the historical fixed 32 colors omitted part of the compiled load. The generic resolver now decodes `CpuSet` unit width/count and accepts only bounded writable EWRAM, IWRAM, or palette-RAM destinations.

Regression controls exposed the inverse instruction order as well: some loaders set the destination before loading the source. Destination inference therefore scans the complete straight-line call setup block rather than only instructions after the source literal.

GBA palette entries are BGR555. Bit 15 is unused by the hardware, so proven palette-copy bytes are normalized with `0x7FFF` instead of rejecting the complete load. This preserved Classic's exact raster while retaining the compiled byte-count contract.

## Affine row layout

The first resolved raster consisted of horizontal strips. Raw tilemap comparison proved a stronger layout invariant:

For every logical row 1 through 31, the trailing 32 bytes of physical row `2y - 1` exactly equal the leading 32 bytes of physical row `2y`.

All 31 comparisons passed. The 4096-byte map therefore stores 32 logical 64-byte rows at a 128-byte physical stride. The compositor now detects this complete invariant and reads the 28×15 crop from the logical rows. No ROM identity or offset participates in the decision.

Modern Emerald contains the same raw affine tilemap and satisfies the same 31-row invariant. Its old frozen raster hash changed from:

`0163d9b5e747d788db925776c25a087a1cc4bbfa34fd3e021580aa8756717fb0`

to the corrected hash:

`120fc88466f34514b5555f38101074c2218e268cb9bbec4e3a693c153154f539`

Both parser PNGs were inspected. The new output reconstructs the logical rows and removes the old crop/seam error; this is a verified correction rather than a regression.

## Semantic joins

The compiled `gMapGroups[group][map]` consumer identifies root `0xE43964`.

One encounter record requests group 0, map 61, whose raw map-header pointer is filler `0xF7F7F7F7`. The resolver now preserves every structurally valid encounter map-header join and excludes records that cannot name a valid map header. Compiled-consumer authority remains fail-closed: it does not fall back to a structural scan when compiled roots exist but fail validation.

The region-entry table resolves uniquely at `0x5A147C`. Its records use the source-backed eight-byte ABI:

- x, y, width, height;
- four-byte name pointer.

Sections 134 and 189 are unrelated to the encounter-bound set and lead to unterminated zero data. Requiring every intermediate section through the maximum ID rejected the valid table. The generic correction validates the required encounter-bound shells and decodes only those records, while retaining reference-count authority and encounter binding.

## Branch ownership regressions corrected

Modern Emerald uses a second compiler shape:

- the fallthrough arm ends through an indirect return;
- the taken arm later branches backward into a shared fallthrough suffix.

The branch resolver now recognizes both forward joins and early-return/backward joins. This restored the exact Modern Emerald loader while rejecting its opposite-arm graphics stream.

A loader function may also contain several numerically composable UI maps. Affine candidates now require a unique 4096-byte map in their single owning function. This rejects unrelated multi-map UI loaders without identifying a ROM.

Official Emerald contains both its direct palette-loader world map and a `CpuSet` software-buffer presentation that composes successfully. When both remain, the direct immediate-argument palette load is the stronger authority; `CpuSet` remains eligible when it is the only proven contract. Official Emerald therefore retains its exact established raster while Altered Emerald still resolves through its only valid `CpuSet` cluster.

## Real-ROM controls

Every control completed two deterministic fresh parses.

Exact established rasters were preserved for:

- official Emerald: 100%;
- official FireRed: 100%, four regions;
- official LeafGreen: 100%, four regions;
- Classic: 100%;
- Dark Violet: 100%, four regions;
- Clover: 100%, four regions;
- Celia's Stupid Romhack: 100%, two regions.

Modern Emerald remained 100% with the visually and structurally corrected affine raster described above. Altered Emerald moved from 0% to 100%.

No ROM name, hash, fixed production offset, or per-ROM profile was added to parser logic.

## Reusable workflow

See `.claude/skills/dualdex-gen3-map-forensics/SKILL.md`.
