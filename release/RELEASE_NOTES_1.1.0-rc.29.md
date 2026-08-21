# DualDex 1.1.0-rc.29

RC29 refines RC27's seamless Local-map scenes and retains RC28's ROM-derived Nature catalogs.

## Seamless map corrections

- Connected scenes initially focus the current Local placement at the same detail level as the former individual Local-map view.
- Scene zoom preserves the former four-times Local detail range instead of stopping at the whole-scene ceiling.
- Recenter returns to the current Local placement while movement between connected maps preserves the continuous viewport.
- Organic and Hidden knowledge modes cover unrevealed Local placements; the current area remains visible even before its discovery ledger update is persisted.
- The regional Atlas is rendered as a darkened compatibility underlay beneath inaccessible scene gaps instead of leaving them pure black.
- Discovered mode keeps all connected Local placements and the Atlas underlay visible.

## Delivery

- RC29 retains RC28's ROM-derived Gen III Nature details and all prior parser, map-lighting, and compatibility improvements.
- RC29 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
