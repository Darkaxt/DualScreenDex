# Local Map Rendering Contract Design

## Objective

Restore the RC29 connected Local map as the single authoritative gameplay map surface while preserving the original Local-map opening detail. The renderer must remain sharp at maximum zoom, keep manual zoom when recentering, reveal only discovered geography in Organic mode, and identify visible routes and towns without falling back to Atlas presentation.

## Rendering model

The Local scene continues to use parser-produced local-map rasters and scene placement geometry. The camera keeps its existing logical `scale`, `panX`, and `panY`, but the final raster dimensions are applied as layout width and height rather than a composited CSS `scale()` transform. This lets the browser apply nearest-neighbour raster scaling to the source images instead of bilinearly scaling one large precomposited layer.

The initial scene camera still fits the current Local placement exactly as the pre-scene Local view did. Moving between connected placements preserves the viewport. Recenter changes only pan: it centers the live player cell and retains the current scale. If no valid player cell exists, it centers the current placement at the retained scale.

Player and POI markers remain screen-readable because the map plane is resized instead of transform-scaled. Their positions remain tied to scene coordinates. Trainer Card portraits never serve as map markers. Gen III catalogs instead resolve the distinct normal walking frame for both player genders, and live trainer gender selects the correct overworld asset. Native dimensions are preserved per ROM: standard Gen III controls use 16×32 while Unbound uses 32×32. The sprite follows the Local raster scale while never rendering below its native size; the compact dot remains only when the map-specific asset is unavailable. The intrinsic ROM-derived sprite width also supplies the zoom bound for scenes whose starting Local placement is below native raster scale. A starting Local view that must scale above native size to fill the approved placement framing remains the minimum and the sprite scales with it.

Trainer portraits, overworld sprites, and badge artwork are independent parser roles. A structurally decoded role remains available when another does not resolve; one missing role must not suppress the others.

## Discovery and fog

In Organic mode, only placements whose base area is current or persisted as revealed receive `<img>` elements. Undiscovered placements receive an opaque black placeholder and their image URLs are never requested. The low-resolution Atlas raster is not rendered beneath a Local scene.

In Discovered mode, all Local placements are rendered. Empty scene gaps remain black. Atlas rendering is used only when no Local map/scene exists.

This makes fog a data-access boundary as well as a visual boundary: hidden geography is neither visible nor decoded by the browser.

## Local POIs

Every rendered Local placement with a non-empty display name receives a compact route/town label at the placement center. Organic mode exposes labels only for current/revealed placements; Discovered mode exposes all labels. The current placement is visually distinguished. Labels are informational and do not create a second navigation mode.

## Performance and failure behavior

The DOM contains raster elements only for maps eligible under the current knowledge mode. No scene-wide Atlas underlay is loaded. Existing dynamic lighting query parameters remain unchanged. Missing names omit labels, and invalid player coordinates omit the player marker without changing the camera.

## Verification contract

- Unit tests prove player-centered recentering preserves scale and that the Local zoom bound derives from the overworld sprite and source-tile sizes.
- Page tests prove hidden Local images are absent, fog placeholders are opaque, Atlas is absent from Local scenes, POIs obey knowledge mode, and current-map transitions preserve zoom.
- A production build must pass.
- Real-ROM checks against Modern Emerald, official FireRed/LeafGreen, Unbound, and Odyssey must prove gender-selected overworld extraction with each ROM's native dimensions. The page check must prove initial Local detail, sharp nearest-neighbour rendering, proportional map scaling with a native-size floor, player-centered recentering with unchanged scale, hidden-image non-loading, opaque fog, and visible eligible POI names.
