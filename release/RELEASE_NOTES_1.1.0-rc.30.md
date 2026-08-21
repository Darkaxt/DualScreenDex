# DualDex 1.1.0-rc.30

RC30 corrects RC29's seamless Local-map renderer and restores a legible, ROM-derived player marker.

## Local map rendering

- Local rasters are laid out at their final dimensions before nearest-neighbour rendering, eliminating the blurred compositor at high zoom.
- The opening camera retains the original individual-Local-map detail level.
- Recenter targets the live player cell without resetting manual zoom.
- Maximum Local zoom is derived from the player marker and source tile sizes. Gen III 16-pixel tiles stop at 64 pixels, matching the intrinsic 64×64 trainer avatar.
- The ROM-derived trainer portrait replaces the abstract player dot when available; the dot remains only as a safe fallback.

## Discovery and map context

- Organic mode does not create or request raster images for undiscovered Local placements.
- Undiscovered placements remain opaque black, and the low-resolution regional Atlas is no longer composited beneath Local scenes.
- Visible Local routes and towns receive compact names; unrevealed names remain hidden in Organic mode.
- Discovered mode continues to expose all resolved Local placements.

## Trainer asset compatibility

- Player portraits and badge artwork are now independent parser roles. A valid portrait pair remains available when a ROM's badge sheet cannot be resolved.
- Modern Emerald is the real-ROM control for the partial trainer-asset path and the complete Local-map browser contract.

## Delivery

- RC30 retains RC29's connected Local scenes, RC28's ROM-derived Gen III Nature catalogs, and all prior parser, loading, save-integrity, theme, archive, and compatibility improvements.
- RC30 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
