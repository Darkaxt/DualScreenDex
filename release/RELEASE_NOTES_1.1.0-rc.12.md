# DualDex 1.1.0-rc.12

RC12 restores the Atlas and local-map catalogs that RC11 parsed correctly but did not preserve in the incremental SQLite cache.

## Atlas and local maps

- The final extended catalog phase now persists one coherent snapshot of every production section, including world maps, local maps, and trainer assets.
- The completion phase remains a lightweight commit marker and does not redundantly serialize the same catalog again.
- Modern Emerald 3.5 is verified through the production incremental phase sequence: one world map and all 557 local maps survive SQLite reopen with exact local-map assets and Littleroot Town bindings.

## Cache migration

- Parser-cache schema 17 invalidates RC11's incomplete schema-16 catalog and reparses the active ROM once.
- Save snapshots, discovery knowledge, and per-ROM settings are stored separately and are not cleared by this catalog rebuild.
- The base parser, map resolver, clock, navigation, and compatibility denominators are unchanged.

## Verification boundary

- The regression uses the exact Modern Emerald 3.5 ROM rather than a synthetic parser substitute.
- Focused catalog-store, production runtime, loopback API, and exact map-serving tests cover persistence and presentation boundaries.
- The signed APK is built and signed only by the protected GitHub release workflow. It is not installed or launched on a device by this release task; device acceptance remains with the user.
