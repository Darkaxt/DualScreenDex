# Exact first50 world-map compatibility stage matrix

> Historical baseline: this survey records the 12/50 state at `a50dcc4`.
> The current release-gate result is the mechanically verified 26/50 report at
> `0682607`; see `2026-08-13-map-first50-release-gate.md` and its raw JSON.

## Scope and method

This is a read-only compatibility survey at `a50dcc4`. It does not change the
world-map resolver or any production selection rule.

- Corpus: entries 1 through 50 from the expanded-corpus manifest, in manifest
  order.
- Identity: every ROM was read again and its SHA-256 was compared with the
  manifest before analysis.
- Classification: generation comes from the decoded ROM header; parser family
  comes from the existing production family selector. No filename was used to
  classify a ROM.
- Execution: two fresh `CatalogParser.parse` calls, each over freshly read ROM
  bytes, for every row.
- Map evidence: capability status and reasons, the earliest structural failure
  stage, ambiguity/budget/error flags, region identity and dimensions, ARGB
  raster hashes, semantic geometry, and every location-to-base-area binding.
- Safety: every non-resolved row must return no world-map region or asset.
- Regression guard: the frozen first33 routing baseline is compared separately
  from reference evidence. Reference preservation is exact for every
  capability except that `AREA_ENCOUNTERS` may become more authoritative or
  grow without changing any previously concrete offset or record shape.

The exact first50 contains 46 Gen III GBA headers and four Gen II GBC headers.
It contains no Gen I ROM, so this matrix makes no Gen I compatibility claim.

## Mechanical results

All counts below are computed from the 50 raw rows, not from checkpoint prose.

| Earliest stage | Rows | Meaning |
| --- | ---: | --- |
| `LOADER_ASSET_CLUSTER` | 16 | No tile, tilemap, and BGR555 palette cluster passed a proven loader contract. |
| `SEMANTIC_REGION_JOIN` | 14 | Loader-format evidence progressed, but region/map-header semantics did not join uniquely. |
| `RESOLVED` | 12 | Normalized world-map output was available. |
| `NOT_APPLICABLE` | 4 | The selected Gen II engine has no normalized map path in this stage. This is neutral, not success. |
| `FAMILY_SELECTION_NO_FAMILY_MATCH` | 3 | No parser family was selected, before map applicability could be evaluated. |
| `SEMANTIC_REGION_PLANES` | 1 | Four semantic text-map planes did not resolve uniquely. |

- 50/50 ROM hashes matched the manifest.
- 50/50 rows produced identical semantic results across the two fresh runs.
- 12/50 resolved; those rows emitted 39 total normalized regions.
- 38/38 non-resolved rows used the safe no-map fallback.
- 0 ambiguity flags, 0 budget failures, and 0 thrown errors were recorded.
- First33 family routing was preserved for 33/33 rows.
- First33 non-regressing reference preservation passed for 33/33 rows.
- First33 reference signatures were byte-for-field exact for 4/33 rows. All 29
  non-exact rows changed only `AREA_ENCOUNTERS`: a previously absent offset was
  materialized and, on eight rows, availability or record count improved. No
  non-area capability changed and no previously concrete evidence regressed.

The largest fixable cluster is therefore the 16-row
`LOADER_ASSET_CLUSTER`. It is split between 12 `FIRERED_LEAFGREEN` selections
and four `EMERALD` selections. No resolver fix is included in this checkpoint.

## Family and outcome distribution

| Parser-selected family | Rows | Outcome breakdown |
| --- | ---: | --- |
| `FIRERED_LEAFGREEN` | 30 | 8 resolved, 12 loader-asset-cluster, 9 semantic-region-join, 1 semantic-region-planes |
| `EMERALD` | 11 | 4 resolved, 4 loader-asset-cluster, 3 semantic-region-join |
| `RUBY_SAPPHIRE` | 2 | 2 semantic-region-join |
| `GOLD_SILVER` | 3 | 3 not applicable |
| `CRYSTAL` | 1 | 1 not applicable |
| no selected family | 3 | 3 family-selection failures |

Resolved manifest indices are 1, 4, 5, 6, 7, 8, 9, 13, 24, 26, 28, and 29.
Index 29 is the frozen Classic control and reproduces the expected one-region
ARGB raster hash `dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f`.
The raw evidence also carries the previously proven official Emerald, Modern
Emerald, Classic, FireRed, and LeafGreen 1/1/1/4/4 control expectations. Those
control identities are evidence-only and never enter production selection.

## Evidence and reproduction

- `2026-08-13-map-first50-raw.json` is the complete row-level matrix, including
  normalized regions, raster hashes, geometry, and location bindings.
- `2026-08-13-map-first33-reference-preservation.json` is a compact audit of
  routing and reference-signature preservation, including every field delta.
- `MapFirst50Matrix.kt` is the environment-driven evidence harness. It does not
  contain corpus paths, ROM names, ROM offsets, or production selectors.

Run from the repository root with JDK 17 and the three environment variables
pointing to the private manifest, output file, and frozen baseline:

```powershell
$env:DUALDEX_FIRST50_MANIFEST = '<manifest.json>'
$env:DUALDEX_MAP_MATRIX_OUTPUT = '<output.json>'
$env:DUALDEX_FIRST33_BASELINE = 'reports/dualdex-parser-compatibility.json'
$env:DUALDEX_MAP_MATRIX_COMMIT = 'a50dcc4'
.\gradlew.bat :parser-cli:mapFirst50Matrix --no-daemon --console=plain
```

Private ROM bytes and absolute corpus paths are not stored in these reports.
