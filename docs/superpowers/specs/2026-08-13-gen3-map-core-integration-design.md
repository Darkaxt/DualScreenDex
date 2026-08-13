# Gen III map-core integration design

## Scope

Restore only the normalized parser/compositor/catalog path needed to materialize ROM-derived world-map rasters from the RC24 baseline. Restore catalog persistence and the existing loopback asset endpoint only far enough to serve a persisted normalized PNG. Do not restore the World Map page, Pokédex navigation, companion routing, emulator integration, device work, release work, or public repository changes.

## Chosen approach

Port the prior normalized catalog types and persistence shape deliberately, then replace the removed Gen III compositor's implicit layout guessing with two explicit structural formats proven by the stage matrix. This is preferred over reviving the historical resolver unchanged because the historical `decoded.size % 2 == 0` branch was the root cause of the striped-circle raster. It is also preferred over family-name dispatch because recompilation and Classic ABI changes must terminate before presentation.

The alternative of implementing only an Emerald special case would leave FRLG bytes liable to accidental interpretation. The alternative of a generic dimension-guessing compositor would recreate the ambiguity that caused the removed release's false positive.

## Components and data flow

1. `GbaWorldMapCompositor` accepts decoded tile bytes, decoded tilemap bytes, palette colors, and a structural format candidate. It validates the entire selected crop before rendering and returns either a normalized `RgbaSprite` plus fixed grid dimensions or a typed rejection.
2. `Gen3WorldMapResolver` discovers co-referenced compressed streams and raw palettes exactly as before, but asks the compositor to classify only the two proven contracts. It ranks structurally valid candidates using compiled-reference evidence and fails closed on equivalent winners. It does not use source names, ROM hashes, symbols, or offsets.
3. `CatalogParser` projects a uniquely resolved overview into `WorldMapCatalog`. Emerald-family geometry joins keep encounter-bound semantic locations, while the raster canvas comes from the format (`28x15`), not the maximum location extent. FRLG compositor support is independent of FRLG region-layout discovery; if the layout/identity stage is not uniquely resolved, the catalog reports unavailable and exposes no substitute image.
4. `catalog-store` persists normalized region metadata and PNG bytes transactionally. Its reader returns the same dimensions, grid, locations, and asset key.
5. The existing Android loopback server serves only a catalog-owned asset key through its asset route. No application navigation or page is restored.

## Failure behavior

- A 4,096-byte map is affine-only. It is never retried as a 16-bit text map.
- A 1,200-byte FRLG text map must validate tile index, palette bank, crop bounds, and referenced palette colors for every rendered pixel.
- Missing source stages, invalid entries, reference-index overflow, zero candidates, and tied authoritative candidates return unavailable/ambiguous/budget outcomes without stock art.
- Multiple FRLG region maps are not collapsed to an arbitrary selected map. Runtime selection depends on the current map section; without that state the catalog must preserve separate identity or fail closed.

## Verification contract

Tests begin with the actual ROM controls and the source-oracle hashes in the stage matrix. The Emerald regression must assert exact normalized ARGB/PNG hashes and representative pixels, so the striped-circle image cannot pass by matching dimensions or merely containing nonzero pixels. Classic must assert the source/binary-equivalent full `224x120` affine result. FRLG must assert Kanto and three Sevii exact pixels/hashes through the 4bpp text compositor, and a catalog-level control may remain fail closed until region geometry resolves uniquely.

After focused red/green tests, verify catalog-store round-trip and loopback byte serving, then run the relevant parser-core, catalog-store, and app unit suites once. Capture normalized PNGs and a machine-readable result manifest under ignored build output; do not commit ROM bytes, source-owned binaries, private paths, or stock art.

## Approved next boundary

If these real controls pass, the next gate may integrate the normalized catalog contract into application state and browser presentation. That later gate must separately enforce the no-toolbar navigation and semantic-icon acceptance criteria. This gate does not touch that UI.
