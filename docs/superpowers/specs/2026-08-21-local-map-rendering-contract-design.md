# Local Map Rendering Contract Design

## Objective

Restore the RC29 connected Local map as the single authoritative gameplay map surface while preserving the original Local-map opening detail. The renderer must remain sharp at maximum zoom, keep manual zoom when recentering, reveal only discovered geography in Organic mode, and identify visible routes and towns without falling back to Atlas presentation.

## Rendering model

The Local scene continues to use parser-produced local-map rasters and scene placement geometry. The camera keeps its existing logical `scale`, `panX`, and `panY`, but the final raster dimensions are applied as layout width and height rather than a composited CSS `scale()` transform. This lets the browser apply nearest-neighbour raster scaling to the source images instead of bilinearly scaling one large precomposited layer.

The initial scene camera still fits the current Local placement exactly as the pre-scene Local view did. Moving between connected placements preserves the viewport. Recenter changes only pan: it centers the live player cell and retains the current scale. If no valid player cell exists, it centers the current placement at the retained scale.

Player and POI markers remain fixed-size screen-readable elements because the map plane is resized instead of transform-scaled. Their positions remain tied to scene coordinates.

## Discovery and fog

In Organic mode, only placements whose base area is current or persisted as revealed receive `<img>` elements. Undiscovered placements receive an opaque black placeholder and their image URLs are never requested. The low-resolution Atlas raster is not rendered beneath a Local scene.

In Discovered mode, all Local placements are rendered. Empty scene gaps remain black. Atlas rendering is used only when no Local map/scene exists.

This makes fog a data-access boundary as well as a visual boundary: hidden geography is neither visible nor decoded by the browser.

## Local POIs

Every rendered Local placement with a non-empty display name receives a compact route/town label at the placement center. Organic mode exposes labels only for current/revealed placements; Discovered mode exposes all labels. The current placement is visually distinguished. Labels are informational and do not create a second navigation mode.

## Performance and failure behavior

The DOM contains raster elements only for maps eligible under the current knowledge mode. No scene-wide Atlas underlay is loaded. Existing dynamic lighting query parameters remain unchanged. Missing names omit labels, and invalid player coordinates omit the player marker without changing the camera.

## Verification contract

- Unit tests prove player-centered recentering preserves scale.
- Page tests prove hidden Local images are absent, fog placeholders are opaque, Atlas is absent from Local scenes, POIs obey knowledge mode, and current-map transitions preserve zoom.
- A production build must pass.
- A real-browser check against parser-produced Modern Emerald maps must prove initial Local detail, sharp nearest-neighbour rendering at maximum zoom, fixed-size player marker, player-centered recentering with unchanged scale, hidden-image non-loading, opaque fog, and visible eligible POI names.

