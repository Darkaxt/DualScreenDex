# DualDex 1.1.0-rc.32

RC32 fixes the Organic fog-of-war regression reported while moving from Littleroot Town into Route 101.

## Persistent Local-map discovery

- A visited Local/Atlas location now remains revealed even when it has no wild-encounter table of its own.
- Live location tracking and ledger integrity checks share the same catalog-validated discovery domain: encounter areas, Local maps, and World Map locations.
- The API continues publishing the complete persisted reveal set, so connected Local-map rasters remain visible after crossing into the next area.
- Unknown area IDs remain rejected rather than being persisted or revealed.

## Delivery

- RC32 is an in-place prerelease update of `com.darkaxt.dualdex` and retains all RC31 Theme, Nature, and Global Local Map behavior.
- Locations discarded by an older build cannot be reconstructed retroactively; visiting such a location once under RC32 records it persistently.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
