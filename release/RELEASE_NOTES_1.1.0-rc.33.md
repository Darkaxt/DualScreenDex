# DualDex 1.1.0-rc.33

RC33 adds knowledge-safe points of interest to Gen III Local maps and makes the starting Local-map zoom useful immediately.

## Local-map points of interest

- The initial Local zoom now shows every POI icon and label permitted by the active information mode and category filters.
- Gen III map events contribute entrances, visible items, hidden-item silhouettes, and unclassified event points without guessing unsupported identities.
- Organic mode keeps undiscovered coordinates private, reveals nearby hidden-item silhouettes when the player passes on or beside their tile, and preserves discoveries per ROM and save.
- Discovered mode can show the complete resolved static POI set; Hidden mode exposes none.
- Places, services, available items, collected items, and unknown points can be filtered independently.
- Icon and label zoom thresholds are configurable per ROM/save and default to 0%, so the starting Local view shows all permitted information.

## Collection tracking

- Source-defined Emerald and FireRed SaveBlock1 event-flag windows are decoded from SaveRAM and live WRAM.
- A visible or hidden item is marked collected only when its own decoded collection flag is set.
- Missing or unsupported flag layouts leave collection state unknown instead of guessing.

## Delivery

- RC33 is an in-place prerelease update of `com.darkaxt.dualdex` and retains RC32 map discovery, RC31 theme/Nature convergence, and RC30 Local-map rendering behavior.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
