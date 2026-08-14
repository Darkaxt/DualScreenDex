---
name: dualdex-gen3-map-forensics
description: Reconstruct failed Gen III world-map pipelines from real ROM bytes, source oracles, rendered output, and regression controls.
---

# DualDex Gen III Map Forensics

Use this workflow when a Gen III ROM scores 0% for world maps. The objective is a complete `WorldMapCatalog`, not merely decoding a raster.

## Non-negotiable rules

- Raw ROM bytes are authoritative. Public source is only an oracle for likely structures.
- Work in an isolated dirty POC first.
- Extend the existing resolver; do not add ROM names, hashes, fixed production offsets, or per-ROM profiles.
- Compatibility is binary per ROM: 100% only when raster, semantic geometry, map-header join, and encounter bindings all resolve; otherwise 0%.
- Validate with real ROMs. Use synthetic tests only for small decoder mechanics.
- Export and inspect PNGs. Passing control flow with gibberish pixels is 0%.
- Run two fresh parses and require identical regions, raster hashes, geometry, and base-area bindings.

## Procedure

1. Run the focused real-ROM matrix row and record the earliest failure stage.
2. Select a near-working ROM with public source. Check `references/pokemon_romhack_sources.md`; treat every link as a lead and verify its contents before using it as an oracle.
3. Read the existing resolver and compositor contracts before disassembling anything.
4. Use source to identify likely asset roles and loader functions. Do not require source revision equality.
5. In raw ROM bytes, locate compiled literal references and prove that all candidate assets share the loader owner.
6. Decode the Thumb control flow around each reference:
   - compare-immediate selector values;
   - taken branch destinations;
   - guarded fallthrough blocks;
   - forward joins and early-return/backward joins;
   - literal-loaded asset pointers;
   - immediate palette byte counts or `CpuSet` control words and writable destinations;
   - decompression destinations and their strides.
7. Reconstruct runtime ownership. Associate graphics and palettes independently by branch before pairing them. Reject pairs whose proven branch arms or slots conflict.
8. Derive the raster layout from raw structure. For FRLG text maps, the proven stride is 1200 bytes and the fifth destination is the shared background plane. For affine maps, test the complete row-layout invariant before changing the 64×64 crop stride. For regular 8bpp backgrounds, validate the full u16 entry ABI, required tile coverage, and source-backed semantic crop.
9. Compose each region only with its eligible branch-owned bundle. A successful composition is necessary but not sufficient.
10. Resolve semantic planes through compiled consumers. Treat a compiled `gMapGroups[group][map]` consumer as authority, retain structurally valid encounter map-header joins, and do not structurally fall back when compiled roots exist but fail validation. Match complete Thumb instruction shapes rather than one compiler's register allocation. If several semantic groups remain, use encounter-bound section coverage as authority.
11. Complete map-header and encounter/base-area joins. Reject any published region with no bindings.
12. Export parser-produced PNGs and inspect them visually.
13. Run two fresh parses and compare the entire deterministic projection.
14. Run exact raster controls for FireRed, LeafGreen, Dark Violet, Clover, Dreamstone, Battle Theater, and Blazed Glazed, plus affine controls such as Classic. Investigate every regression from its real ROM/source.
15. Only then port the generic rule to the current production parser, commit, and push.

## Thumb branch ownership pattern

A branch-owned asset may appear either at an exact branch destination or in the fallthrough guarded by `CMP selector, #slot` followed by `BNE` past the asset load. Scan the function up to the literal-load site and prefer:

1. the nearest exact conditional-branch destination equal to the site;
2. otherwise the nearest `BNE` whose destination skips past the site.

Do not combine target sites before inferring ownership. Infer graphics and palette slots separately, then pair only compatible slots.

Affine loaders may instead compile as two larger control-flow arms. Prove ownership from the conditional branch plus its join:

- a fallthrough arm may end with a forward branch over the taken arm;
- a fallthrough arm may return indirectly while the taken arm branches backward into a shared suffix.

Classify only literal sites inside the proven arm ranges. Shared suffix loads remain unowned and eligible to both arms.

## Palette-copy contracts

Do not assume 32 colors. Derive the byte count from immediate call arguments or a valid GBA `CpuSet` control word. For `CpuSet`, prove that register 1 names a bounded writable EWRAM, IWRAM, or palette-RAM range; its literal may appear before or after the source load in the same straight-line call setup block. Normalize proven GBA colors to BGR555 (`value & 0x7FFF`) because hardware ignores bit 15.

If both a direct immediate-argument palette loader and a software-buffer `CpuSet` presentation compose, prefer the direct load as authority. Retain `CpuSet` candidates when no direct candidate survives.

## Affine row-layout pattern

A 4096-byte decode does not by itself prove conventional 64-byte physical rows. Some real loaders store 32 logical 64-byte rows at 128-byte physical stride. Require the full duplicated-half invariant across logical rows 1 through 31 before using that layout; then export and inspect the changed raster. A few matching or zero-filled rows are not sufficient evidence.

## Tiled 8bpp regular-background pattern

A 1280-byte decode can be a 32×20 array of little-endian regular-background entries rather than an affine map or 4bpp plane. Decode each u16 as:

- tile index: `entry & 0x03FF`;
- horizontal flip: `entry & 0x0400`;
- vertical flip: `entry & 0x0800`.

Require 64-byte-aligned 8bpp graphics, prove every referenced tile and palette index is covered, and derive the semantic viewport from the raw loader/source coordinate contract. Dreamstone uses a source-backed 28×15 crop at tile `(1, 2)` inside the 32×20 physical map.

Do not require a valid map root to have exactly one global function owner. Normal and fly loaders may share a tilemap while using different graphics formats. Evaluate map uniqueness inside the specific function shared by the candidate map and graphics. Cross-format pairing must still fail structural coverage; Dreamstone's fly graphics expose only 120 tiles when interpreted as 8bpp, while the raw map references tile 120 and above.

## SMOL tilemap mode 8

pokeemerald-expansion can encode affine tilemaps with SMOL mode 8 rather than GBA LZ77 or graphics SMOL modes 1 through 6. The first header word retains the decoded byte length and u16-symbol count; the second word is the instruction-vector byte count. The payload is:

1. a little-endian u16 symbol vector;
2. four-byte alignment;
3. the ordinary SMOL copy/literal instruction vector;
4. final four-byte stream alignment.

Expand the instruction vector with the u16 symbols, then cumulatively delta-decode every output u16 modulo 65536. Bound and validate the encoded length before reading either vector. Prove the implementation against raw streams and exact source-generated `.bin` products before adding a small synthetic mechanic test.

## Table-driven multi-region loaders

A loader may reference one indexed data table instead of loading map, graphics, and palette roots directly. Treat a table as authoritative only when a compiled-reference root exposes at least two contiguous records and every record satisfies the full ABI. Battle Theater demonstrated a 28-byte record with:

- dex map, graphics, and palette pointers;
- region map, graphics, and palette pointers;
- a complete palette-byte count;
- zero alignment padding.

Require all six pointers to remain in ROM, decode and validate both compressed halves, prove map tile coverage and palette-bank coverage, and compose every region-side asset. Do not require table-contained assets to have their own Thumb literal sites. Stop at the first invalid record and reject duplicate region bundles.

The corresponding semantic authority can be a compiled consumer referencing one contiguous array per table slot. Battle Theater uses five single-layer 28×15 u8 planes with a dominant `MAPSEC_NONE` sentinel. Require the exact region count, contiguous plane storage, one shared Thumb function owner, non-overlapping section ownership, and encounter-bound coverage. Compiler/linker output stores these planes in reverse address order relative to the table slots; accept that association only as part of the complete count/contiguity/function contract. Compose and visually inspect every table asset, but publish only regions that retain encounter bindings.

## Expansion encounter base IDs

The expansion encounter materializer encodes time-specific areas as `baseAreaId * 100 + time * 10 + method`, while ordinary Gen III areas use `baseAreaId * 10 + method`. Normalize with the stride selected by the already-resolved expansion ABI before invoking map-header joins. Dividing expansion IDs by ten invents false group IDs and makes a valid `gMapGroups[group][map]` consumer appear broken.

## Compiled map-group consumers

Compilers can express `gMapGroups[group][map]` with direct register-indexed loads instead of explicit address additions. One valid u16-argument form loads the root into `r3`, zero-extends and scales `r0` and `r1` by four, then performs `ldr r3, [r0, r3]` and `ldr r0, [r1, r3]` before `bx lr`.

Match the complete function shape, calculate the PC-relative literal from the actual literal-load site, and validate every encounter-requested group/map slot. Do not accept isolated indexed loads or weaken map-header validation.

## Explicit region-entry states

After a compiled map-group root establishes the required section IDs, a referenced region-table record can be:

- a complete bounded geometry shell with a terminated valid name;
- a named off-map record whose `x` and `y` are both `0xFF`, preserving section identity without publishable geometry; or
- exactly eight zero bytes, meaning explicitly absent and unbindable.

A nonzero malformed record still invalidates the candidate table. Reject a half-sentinel where only one coordinate is `0xFF`, and retain the ordinary width, height, pointer, termination, and text-quality checks for a complete sentinel record. Keep at least three ordinary alphanumeric entries as authority anchors before selecting by compiled reference count. Once the table is authoritative, retain valid nonblank punctuation-only names such as `???`; punctuation is not evidence against a source-defined name. Off-map sentinels may name encounter areas such as caves, but they must not produce out-of-bounds world-map geometry.

## False-positive defense

A visually composable asset can be unrelated to the world map. For affine loaders, require a unique 4096-byte map in the single owning function before accepting a numeric composition. For text loaders, require the complete loader contract, including the full destination sequence. Classic demonstrated the text case: its actual world map is Emerald-style affine 8bpp, while a Fly Map frame looked like a one-region text map. Requiring the proven five 1200-byte destinations rejected the false candidate without ROM-specific logic.

## Evidence to retain

For each successful ROM, record:

- ROM SHA-256 and exact corpus row;
- loader function owner;
- asset roots and decoded sizes;
- raw branch and destination evidence;
- parser-produced PNG paths;
- region raster hashes;
- semantic-location and binding counts;
- deterministic two-pass result;
- real-ROM controls and any regression correction.
