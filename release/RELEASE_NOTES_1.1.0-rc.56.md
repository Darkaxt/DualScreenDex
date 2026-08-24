# DualDex 1.1.0-rc.56

RC56 completes Gen I and Gen II Local-map POI parity on top of RC55's unified state and runtime-performance work.

## Gen I and Gen II Local-map POIs

- Accepted Local maps now expose bounded warp and entrance POIs from compiled event structures.
- Exact sign forms provide labels where their text or script is structurally decodable.
- Visible item balls and hidden items are emitted only from exact generation-specific records.
- Gen II Pokémon Center and Mart signs use explicit standard-script roles; unsupported service semantics remain unclassified rather than guessed.
- Malformed POI evidence disables only the affected map's POIs and cannot remove its raster, scene, lighting, or Atlas fallback.

## Collection evidence

- Checksum-valid Gen I saves publish the 14-byte hidden-item flag array used by structurally matched hidden-item coordinates.
- Checksum-valid Gold/Silver and Crystal save copies publish their 256-byte event-flag arrays, including validated backup-copy selection.
- The existing save-scoped knowledge ledger maps those flags to collected and identified item POIs.
- Gen I visible item balls retain unknown collection state because their compiled object records do not prove an item-to-save-flag mapping.

## Compatibility and validation

- Official Red, Blue, Yellow, Gold, Silver, and Crystal retain their exact accepted map counts, scenes, rasters, and Gen II lighting controls while passing exact entrance, sign, item, destination, service, and collection-flag assertions.
- Synthetic malformed-map controls prove per-map failure isolation and coordinate bounds.
- Three source-backed Gen I controls parsed deterministically twice with zero parser errors or strict-control failures.
- Parser schema 36 intentionally rebuilds cached catalogs once so Gen I/II POIs are materialized.
- Catalog persistence, knowledge/API projection, Android unit tests, shared Map-page tests, and the production web build passed.

## Delivery

- RC56 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010056`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
