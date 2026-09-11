# Stage 4 official capability applicability audit

Date: 2026-09-11

Status: **independent pre-capture applicability audit complete; zero final controls accepted**

## Scope

`official-capability-applicability-audit.json` reconciles every one of the 15 localized capabilities for the exact 44-control / 43-cell official roster. It is bound to source commit `51e70d00347e4b6633bb11f6e2e3d77b55d49138` and contains exactly **660 required final dispositions**:

- **506 `ACCEPTED` requirements**: the final control must provide positive, independently sampled ROM-native content. `PARTIAL` is permitted only when every missing record has an independently proved exclusion.
- **154 `EXCLUDED` requirements**: the capability is structurally absent from that generation/family and must finish `NOT_APPLICABLE` with an independent `NOT_APPLICABLE` proof. Unavailable output is not an exclusion.
- **0 accepted controls**: this artifact fixes the independent semantic policy before capture. It is not actual parser, cache, API, or linguistic acceptance.

The builder reads only the existing metadata inventory, copies only SHA-256/family/language/release identity fields, and rejects incomplete rosters, duplicate identities, wrong Korean releases, malformed source pins, or totals other than 44 controls, 43 cells, 660 dispositions, 506 required acceptances, and 154 required exclusions. Private paths, headers, filenames, payloads, ROM bytes, caches, and captured field values are neither read nor emitted.

## Ratified capability policy

`A` means the final matrix must earn `ACCEPTED`; `X` means it must earn source-proven `EXCLUDED`/`NOT_APPLICABLE`.

| Capability | Gen I | Gen II | Ruby/Sapphire + Emerald | FireRed/LeafGreen |
|---|---:|---:|---:|---:|
| `SPECIES_NAMES` | A | A | A | A |
| `SPECIES_DESCRIPTIONS` | A | A | A | A |
| `MOVE_NAMES` | A | A | A | A |
| `MOVE_DESCRIPTIONS` | X | A | A | A |
| `ABILITY_NAMES` | X | X | A | A |
| `ABILITY_DESCRIPTIONS` | X | X | A | A |
| `TYPE_NAMES` | A | A | A | A |
| `NATURE_NAMES` | X | X | A | A |
| `ITEM_NAMES` | A | A | A | A |
| `AREA_NAMES` | X | X | A | X |
| `LOCAL_MAP_NAMES` | A | A | A | A |
| `WORLD_REGION_NAMES` | X | X | A | X |
| `WORLD_LOCATION_NAMES` | A | A | A | A |
| `ENCOUNTER_AREA_NAMES` | A | A | A | A |
| `POI_TEXT` | A | A | A | A |

This yields the following independently fixed control totals:

| Control group | Controls | Required accepted per control | Required excluded per control | Dispositions |
|---|---:|---:|---:|---:|
| Gen I | 12 | 9 | 6 | 180 |
| Gen II | 14 | 10 | 5 | 210 |
| Ruby/Sapphire + Emerald | 12 | 15 | 0 | 180 |
| FireRed/LeafGreen | 6 | 13 | 2 | 90 |
| **Total** | **44** |  |  | **660** |

## Semantic authority

The audit follows the production denominator contract in `CatalogLocalizedTextExtractor.expectedRecords` rather than historical status labels:

- species, move, type, item, static Local-map, static World-location, encounter-base-name, and retained POI domains are ROM-native obligations wherever their numeric records exist;
- Gen I has no move-description domain;
- abilities begin in Gen III and natures begin in Gen III;
- the distinct runtime `AREA_NAMES` domain exists for Ruby/Sapphire and Emerald, not Gen I, Gen II, or FireRed/LeafGreen;
- Ruby/Sapphire and Emerald affine region maps retain static region-title obligations;
- Gen I, Gen II, and FireRed/LeafGreen world rasters are graphics-only and have no static `WORLD_REGION_NAMES` obligation;
- encounter method/time suffixes and other application phrasing remain Stage 6 interface copy, not `ENCOUNTER_AREA_NAMES`;
- every retained POI remains in `POI_TEXT.expectedRecords`.

The disposition audit incorporates the published structural checkpoints for official description slots, native move prose, referenced item names, contextual Local maps, static/graphics-only regions, contextual GBA World locations, encounter labels, Japanese/Korean declared signs, and final POI applicability. Public source repositories remain structural oracles only; exact compiled controls remain execution authority.

## POI denominator and exclusions

`POI_TEXT` is required for all 44 controls and is expected to remain `PARTIAL` where the retained POI set contains records without a static headline. The final oracle must preserve each such record and use its exact proof kind:

- `CONTEXTUAL_TEXT` for runtime-selected or runtime-substituted first headlines, context-dependent Gen I/II destinations, and Gen III dynamic map/section destinations;
- `NO_TEXT` only with positive compiled proof that the event is textless;
- `UNRESOLVED` for unsupported or unknown scripts that fail closed;
- `RESERVED_SLOT` or `NOT_APPLICABLE` only when those narrower meanings are independently true.

These three semantic kinds are valid only for missing `POI_TEXT` records. They cannot waive missing item, map, encounter, species, move, ability, type, nature, area, region, or location text. The validator and deterministic assembler enforce that boundary.

## Verification

TDD evidence for `build_official_capability_audit.py`:

- RED: module import failed before implementation.
- GREEN: **6/6 tests passed**, zero failures/errors/skips.
- The real metadata-only generation reported **44 controls, 43 cells, 660 dispositions, 506 required accepted, 154 required excluded, zero final accepted controls**. The canonical 224,271-byte artifact SHA-256 is `dc53a7c842980a3180f355b2cce80dfeb0a6a7ff41c486b246a70200f99db4c2`.
- The matrix evidence contract separately passed **120/120 tests**, including POI-only acceptance of `CONTEXTUAL_TEXT`, `NO_TEXT`, and `UNRESOLVED` and rejection of those kinds for unrelated capabilities.

## Remaining boundary

Task 386's independent all-capability applicability decision is complete. Task 373 must now capture the exact current outputs once, supply independent field samples and per-record proofs, assemble G0–G6 evidence, and require all 506 positive and 154 excluded dispositions to validate. Actual observations may prove projection/persistence equality but cannot become their own linguistic oracle.

Tasks 364/372/390/417/425/428/430 close only when their final current-control evidence is incorporated into that accepted matrix. Tasks 374–375 still require the final eligible source-bound corpus, Stage 4 verification, ledger reconciliation, commit, and publication. Stage 5 remains blocked until legitimate Stage 4 closure. No corpus, original ROM, cache payload, device/emulator, ADB, APK, signing, or release work was performed for this audit.
