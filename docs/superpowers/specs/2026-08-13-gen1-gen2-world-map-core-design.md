# Gen I and Gen II World Map Core Design

## Goal

Add source-oracle-backed, fail-closed Gen I and Gen II world-map parsing to the
RC24-derived normalized map pipeline. Red, Blue, and Yellow use the Gen I Town
Map format; Gold, Silver, and Crystal use the Gen II Pokégear maps. Every
successful resolver emits the existing `WorldMapCatalog`, so catalog storage,
PNG serving, and the approved presentation remain generation-independent.

This slice is core-only. It does not change app navigation, device behavior,
release state, or the paused Gen III hack compatibility work.

## Source and evidence authority

The format oracles are local source trees and source-built official controls:

| Family | Source tree commit | Required official controls |
| --- | --- | --- |
| Red/Blue | `pret/pokered` `2ab2421410b764e4dfebeddf8d9249d2cba947c4` | Red and Blue |
| Yellow | `pret/pokeyellow` `e6ba56989b0f2694f393e6924820be11dcc1fbb8` | Yellow |
| Gold/Silver | `pret/pokegold` `a0dad0957ac8a9ffa67e950ee3ab6715a212ded5` | Gold and Silver |
| Crystal | `pret/pokecrystal` `8e8f7e20052a596371a77022f0392c285e51bbf1` | Crystal |

Source names, commits, ROM hashes, symbol names, and source-derived offsets are
allowed only in tests and evidence reports. Production code must classify the
format from coherent loader instructions and data roles. It must never select a
path through a ROM title, filename, digest, symbol, or absolute offset table.

Real controls come before synthetic rejection cases. Each official control
freezes exact decoded asset hashes, normalized ARGB raster hashes, region keys,
pixel/grid dimensions, location geometry, and base-area bindings. The test
oracles are generated from the matching source build, not from parser output.

## Architecture

### Shared normalized result

Generation-specific parsers terminate at a small typed result contract:

```kotlin
sealed interface WorldMapResolution {
    data class Resolved(val catalog: WorldMapCatalog, val reasons: List<String>) : WorldMapResolution
    data class Unavailable(val stage: String, val reason: String) : WorldMapResolution
    data class Ambiguous(val stage: String, val reason: String) : WorldMapResolution
    data class BudgetExceeded(val stage: String, val reason: String) : WorldMapResolution
}
```

`Gen1WorldMapResolver`, `Gen2WorldMapResolver`, and the existing Gen III
resolver remain separate source-family implementations. `ParserOrchestrator`
chooses only by the already selected parser generation and supplies the active
encounter base IDs. `CatalogParser` maps the common result to `WORLD_MAP`
capability evidence and materializes the normalized catalog. Presentation,
SQLite, and API code never see a Gen I/II ABI.

Typed stages distinguish at least asset-loader, map-plane, palette,
landmark/entry-table, map-header join, encounter binding, ambiguity, and
budget failure. A failed dependency emits no region and no asset.

### Gen I resolver

The Gen I resolver proves one complete Town Map chain within coherent Game Boy
bank and loader references:

1. A loader far-copies exactly 16 raw 2bpp tiles (256 bytes) to the Town Map
   tile range.
2. The same loader points to a zero-terminated nibble RLE stream. Each byte's
   high nibble is the source tile index and its low nibble is a nonzero run
   length. Decoding must produce exactly 360 cells (20 by 18), with no overflow
   or trailing partial run.
3. The loader's tile-base addition must agree with the copied tile range; tile
   indices are normalized before composition.
4. A structurally referenced entry lookup proves the external three-byte and
   grouped internal four-byte entry layouts used by that engine variant. Every
   required encounter map ID must resolve to one packed anchor coordinate and
   one bank-valid, terminated ROM text name.
5. All anchors must remain inside the 20 by 18 grid. Entries prove one-cell
   anchors only; the resolver does not invent route polygons.

Red/Blue and Yellow loader/table instruction shapes are validators within this
one resolver, not product identities. Exactly one complete chain resolves.
Zero chains are `Unavailable`; multiple complete chains are `Ambiguous`.

The normalized output is one Kanto region with a 160 by 144 raster, a 20 by 18
grid, stable structure-derived location keys, and explicit encounter map IDs as
`baseAreaIds`.

### Gen II resolver

The Gen II resolver proves one complete Pokégear authority chain:

1. The map graphics loader identifies one compressed 2bpp stream whose declared
   output is exactly 48 tiles (768 bytes).
2. Johto and Kanto map loaders identify two independent 361-byte planes. The
   first 360 bytes are the exact 20 by 18 screen tilemap and byte 361 is the
   `$ff` loader terminator. All screen tile IDs must address the 48 decoded
   tiles.
3. The palette assignment routine proves a 48-byte nibble palette map covering
   tile IDs `$00..$5f`. A co-referenced six-palette BGR555 set supplies the four
   colors for each palette bank. Palette indices and BGR555 high bits are
   validated before composition.
4. Crystal's alternate player palette is a presentation variant, not a second
   geographic region. The deterministic catalog raster follows the structurally
   proven default/fall-through palette chain used when the gender flag is zero;
   alternate branches must still be structurally valid and cannot replace the
   geographic authority.
5. A four-byte landmark table supplies signed pixel X/Y plus a bank-local name
   pointer. Ordinary landmarks must map to an in-grid anchor cell. Source-valid
   special/off-map records, including the sentinel special landmark, are
   accepted as table members but never become geometry or selectable locations.
6. The map-group pointer table and fixed-width map headers are proven through
   the `GetAnyMapPointer`/`GetWorldMapLocation` instruction chain. Each required
   encounter `(group,map)` base ID must join to exactly one ordinary landmark.
   Multiple encounter maps may bind the same landmark; their base IDs are
   grouped into that semantic location.
7. Landmark ranges determine Johto versus Kanto exactly as the ROM's region
   routine does, including source-defined special cases. Both region identities
   must remain distinct in the normalized catalog.

The normalized output contains `gen2-johto` and `gen2-kanto` when both complete
joins exist. Each raster is 160 by 144 with a 20 by 18 grid. A source-valid
special record may be retained as diagnostic evidence, but it never creates an
off-canvas `WorldMapCell`.

## Data flow

```text
ROM bytes + decoded header
  -> existing parser family/generation selection
  -> encounter materialization with explicit baseAreaId
  -> Gen1 or Gen2 structural resolver
       -> loader/data role classification
       -> exact codec and compositor
       -> entry/landmark and map-header join
       -> encounter-bound normalized locations
  -> WorldMapResolution
  -> CatalogParser WORLD_MAP capability + WorldMapCatalog
  -> CatalogStore world_maps section and raster assets
  -> companion API metadata + /api/maps/world/{regionKey}.png
```

Neither catalog storage nor the API reopens ROM bytes. Raster materialization is
complete during `CatalogParser.parse`.

## Failure and compatibility behavior

- Every dependency is bounds checked before a read or decode.
- Candidate authority is complete-chain uniqueness, never best-effort scoring.
- Missing, truncated, malformed, or multiply authoritative roles fail closed
  with a typed earliest stage and an empty normalized map catalog.
- A valid Gen I/II family with unresolved map structure reports a typed resolver
  failure. It no longer reports `NOT_APPLICABLE`.
- Area encounters, catalog parsing, and the existing no-map UI remain available
  when map resolution fails.
- `NOT_APPLICABLE` remains reserved for generations or engines with no resolver
  stage, not for a supported GBC family whose map could not be proven.
- Existing Gen III 1/1/1/4/4 controls and presentation behavior must remain
  unchanged.

## Verification

Implementation follows real-result-first TDD:

1. Build or locate clean source-built Red, Blue, Yellow, Gold, Silver, and
   Crystal controls and freeze independent source-asset/raster/geometry hashes.
2. Add exact codec/compositor tests and watch them fail before adding production
   codecs.
3. Add official resolver tests for all six controls and watch them fail at the
   generation-specific resolver boundary.
4. Implement Gen I, then Gen II, until the exact controls pass.
5. Only after each real path is green, add narrow truncation, malformed special,
   and duplicate-authority regressions derived from observed invariants.
6. Verify `CatalogParser -> CatalogStore -> runtime/API` for all six controls,
   including exact PNG bytes and region/location metadata.
7. Rerun the four GBC rows in exact first50 twice. Each must be `RESOLVED` or a
   typed Gen II resolver failure, deterministic, and safe-fallback when not
   resolved; none may remain `NOT_APPLICABLE`.
8. Run focused parser/catalog/server suites and the five established Gen III
   real controls before committing the implementation.

Private ROM bytes and private paths remain outside the repository. Generated
reports contain only sanitized source identity, source commit, ROM digest,
normalized hashes, dimensions, semantic identities, and typed outcomes.
