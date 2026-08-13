# World-map first50 release gate

## Result

The normalized world-map core passes the requested compatibility gate at
commit `0682607`: **26 of 50** exact corpus rows produced complete `AVAILABLE`
catalogs. The required threshold was 25. This is a parser/catalog handoff gate,
not a release action.

The count is computed from the raw rows. No projected, raster-only,
`NOT_APPLICABLE`, or fallback result is counted.

## Frozen method

- Manifest SHA-256:
  `7146d2410231febfba550470d62ac179ef8c532d94bc15d2365211f862d03d5f`.
- Two fresh `CatalogParser.parse` calls over freshly read bytes for each of the
  50 manifest entries.
- Every ROM SHA-256 checked against the manifest.
- `AVAILABLE` requires a nonempty normalized catalog whose every region has
  positive intrinsic dimensions, an exact ARGB raster hash, semantic geometry,
  and at least one location binding.
- Every unresolved row must expose no region/asset and report safe no-map
  fallback.
- The frozen first33 routing and non-regressing reference contracts are checked
  independently of map availability.
- Exact real vertical controls additionally preserve normalized catalogs
  through SQLite reopen, runtime model conversion, and HTTP PNG asset serving.
  The newly held-out Dark Violet and Clover controls freeze exact region keys,
  raster hashes, location counts, geometry/base-area binding hashes, and served
  PNG hashes; official source controls remain preserved.

## Mechanical totals

| Check | Result |
| --- | ---: |
| Completed rows | 50/50 |
| Manifest SHA verified | 50/50 |
| Deterministic across both parses | 50/50 |
| Fully `AVAILABLE` | **26/50** |
| Complete normalized regions | 81 |
| `AVAILABLE` rows missing raster/geometry/location evidence | 0 |
| Safe unresolved fallback | 24/24 |
| First33 routing preserved | 33/33 |
| First33 non-regressing references preserved | 33/33 |

Available rows are indices **1, 2, 4, 5, 6, 7, 8, 9, 10, 13, 14, 18, 23,
24, 26, 28, 29, 33, 39, 40, 41, 42, 43, 44, 47, and 48**. By selected
family, that is 16 FireRed/LeafGreen, seven Emerald, two Ruby/Sapphire, and one
Crystal catalog.

The remaining typed stages are:

| Earliest stage | Rows | Indices |
| --- | ---: | --- |
| `LOADER_ASSET_CLUSTER` | 10 | 11, 15, 16, 17, 19, 25, 27, 31, 32, 36 |
| `LOCATION_BINDING` | 4 | 3, 30, 37, 49 |
| `LANDMARK_JOIN` | 3 | 21, 22, 38 |
| `SEMANTIC_REGION_JOIN` | 3 | 20, 35, 45 |
| `FAMILY_SELECTION_NO_FAMILY_MATCH` | 3 | 34, 46, 50 |
| `SEMANTIC_REGION_PLANES` | 1 | 12 |

## Evidence

- `2026-08-13-map-first50-release-gate-raw.json` contains all 50 rows, both
  fresh outputs, region identities, intrinsic dimensions, exact raster hashes,
  geometry, location bindings, typed failures, determinism, safe fallback, and
  first33 checks. Its SHA-256 is
  `b4ed3f4729ad370bea4f7a953911adb7564f43506259a01a2853834af77d030a`.
- `2026-08-13-gen3-semantic-join-clusters.md` records the source and compiled
  consumer authority behind the direct-binding change and its permanent exact
  real controls.

No ROM bytes or private absolute paths are stored in either report. Production
selection remains structural; corpus names, hashes, and physical addresses are
test/evidence data only.
