# Celia Gen III World-Map Forensics

This note records why Celia's Stupid Romhack 1.1.4 previously scored 0%, the raw-ROM evidence behind the generic correction, and the controls used to prevent a raster-only or false-positive fix.

## Result

Celia now scores 100% with two parser-generated regions. Two fresh parses produced identical raster hashes, semantic geometry, and base-area bindings.

Parser PNGs were exported to:

- `D:\Temp\dualdex-master-celia-render\gen3-region-0.png`
- `D:\Temp\dualdex-master-celia-render\gen3-region-1.png`

Both were visually inspected and contain coherent maps rather than gibberish.

## Source oracle

Repository: <https://github.com/celias-stupid-team/celias-stupid-repository>

`src/region_map.c` describes two runtime-selected graphics/palette bundles and five fixed-stride map layouts. Source identity was not assumed; the ROM bytes below independently prove the loader shape.

## Raw ROM evidence

ROM SHA-256:

`81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148`

Compiled loader owner: `0x0DB400`.

Graphics streams:

- `0xC3792C`: 9728 decoded bytes, 304 4bpp tiles.
- `0xC39E64`: 7168 decoded bytes, 224 4bpp tiles.

Palette roots and raw loader byte counts:

- `0xC3758C`: 32 bytes; unrelated top-bar palette.
- `0xC3762C`: 96 bytes; region slot 0 palette.
- `0xC3AA4C`: 160 bytes; region slot 1 palette.

Text-map roots and destinations:

- `0xC38A44` -> destination offset 38 -> slot 0.
- `0xC38D80` -> destination offset 1238 -> slot 1.
- `0xC39020` -> destination offset 2438 -> slot 2.
- `0xC39104` -> destination offset 3638 -> slot 3.
- `0xC39DB4` -> destination offset 4838 -> shared background slot 4.

The 1200-byte destination stride proves the slot sequence.

Selector control flow proves branch-local assets:

- selector 0 loads palette `0xC3762C` and graphics `0xC3792C`;
- selector 1 loads palette `0xC3AA4C` and graphics `0xC39E64`;
- selector 1 uses guarded fallthrough blocks, so ownership cannot be inferred only from exact branch destinations.

## Original resolver defects

The prior resolver assumed:

1. one graphics sheet for every text-map region;
2. one fixed 80-color palette;
3. exactly four published regions;
4. one globally unique semantic-layout group before encounter joining.

Celia violated the first two assumptions. It loads distinct graphics/palette bundles by runtime region branch, and its slot 0 palette contains 48 colors.

## Generic correction

`Gen3WorldMapResolver` now:

- derives palette size from the compiled load byte count in complete 16-color banks;
- reconstructs map slots from decompression destinations;
- infers exact-branch and guarded-fallthrough selector ownership;
- infers graphics and palette ownership separately and rejects conflicting pairs;
- composes each map only with an eligible branch-owned bundle;
- publishes contiguous branch-owned regions rather than forcing four;
- filters multiple semantic-layout groups through encounter-bound section coverage while preserving a sole candidate unchanged;
- retains fail-closed behavior through map-header and encounter binding.

No ROM identity, hash, fixed asset offset, or per-ROM profile enters production logic.

## Parser output

Region 0:

- raster SHA-256: `aca5157539fa81a69e11b459c26afc8589a823c35bfde835f24710ac0dc14433`
- semantic locations: 47

Region 1:

- raster SHA-256: `cf5eb6d1458a3bef8c954cb70308d9dc9139470e252bb9c4a211f022a49544db`
- semantic locations: 1

Both runs produced the same output.

## Regression found and corrected

Classic temporarily changed from 100% to 0% because the broader text-map discovery accepted a decorative Fly Map frame alongside Classic's real Emerald-style affine world map.

Classic source: <https://github.com/DaniRainbow/pokeclassic>

Its `src/region_map.c` proves:

- the world map uses an 8bpp affine map;
- the apparent text-map asset is `region_map/frame.bin.lz`, a decorative Fly Map frame.

The generic correction requires a one-region text candidate to retain the full proven five-destination text-loader sequence. This rejects Classic's frame without identifying Classic. Classic returned to 100%, and its affine raster hash remained:

`dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f`

## Real-ROM controls

Exact raster controls passed for:

- FireRed: 100%
- LeafGreen: 100%
- Dark Violet: 100%
- Clover: 100%
- Classic: 100%

The focused rows 25-29 remained deterministic, with Celia moving from 0% to 100% and no completed ROM falling to 0%.

## Reusable workflow

See `.claude/skills/dualdex-gen3-map-forensics/SKILL.md`.
