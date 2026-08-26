# Gen I–III full-corpus status

Measured on 2026-08-26. Full catalogs were parsed at `13a21658d2339af710901b5d5b7be010d836e40f`; current evidence contract `1e8515ada31166997a5d04fe515779cc845e93da` adds exact Local-map retained/expected counts without changing catalog materialization. Exact comparison parser: `466d5ffcd6acfa5926a7ae3753605ab561465161`.

This report compares both parser revisions against the same exact local corpus, deduplicated by SHA-256 before aggregation. Public source names and ROM hashes are evidence only; no ROM bytes, decoded bulk text, sprites, saves, private roots, or signing material are retained.

## Corpus and integrity

- Input rows: 333
- Unique SHA-256 identities: 331
- Generation distribution: Gen I 95; Gen II 27; Gen III 209
- Duplicate input groups: 2
- RC21 parser/read error rows: 0 across 0 unique ROMs
- Current parser/read error rows: 2 across 1 unique ROMs
- Current selected catalogs persisted and reopened: 256
- Current persistence errors: 0
- Decoded cross-reference errors: RC21 533 across 1 ROMs; current 533 across 1 ROMs

## Generation coverage

Coverage is weighted across every applicable ROM/table cell. `NOT_APPLICABLE` is excluded rather than treated as success or failure. Generation-wide inapplicability is normalized before per-ROM evidence so a failed family route cannot make abilities or capture balls appear applicable to Gen I/II.

| Generation | RC21 on same corpus | Current shared-table coverage | Current all-table coverage | Applicable current tables |
|---|---:|---:|---:|---:|
| Gen I | 58.81% | 87.10% | 87.10% | 16 |
| Gen II | 48.36% | 48.36% | 48.36% | 19 |
| Gen III | 71.77% | 72.44% | 72.35% | 24 |
| **All generations** | **67.13%** | **73.91%** | **73.80%** | **24** |

## Routing and catalog health

| Outcome | RC21 | Current | Delta |
|---|---:|---:|---:|
| AMBIGUOUS | 2 | 2 | +0 |
| ERROR | 0 | 1 | +1 |
| NO_FAMILY_MATCH | 74 | 72 | -2 |
| SELECTED | 255 | 256 | +1 |

## Per-table coverage and exact deltas

Deltas compare the same ROM identities and the table types shared by both parser revisions. Natures are new after RC21 and therefore have no historical delta.

| Table | Gen I | Δ | Gen II | Δ | Gen III | Δ | Overall | Δ |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Species catalog | 85.05% | +0.00 pp | 61.28% | +0.00 pp | 69.69% | -0.48 pp | 73.41% | -0.30 pp |
| Species names | 80.57% | +2.11 pp | 69.62% | +0.00 pp | 86.66% | -0.48 pp | 83.52% | +0.30 pp |
| Species types | 87.56% | +0.00 pp | 61.79% | +0.00 pp | 75.36% | -0.48 pp | 77.76% | -0.30 pp |
| Type chart | 95.79% | +85.26 pp | 25.93% | +0.00 pp | 84.21% | -0.48 pp | 82.78% | +24.17 pp |
| Base stats | 87.56% | +0.00 pp | 61.79% | +0.00 pp | 75.36% | -0.48 pp | 77.76% | -0.30 pp |
| Sprites | 88.31% | +44.13 pp | 73.75% | +0.00 pp | 78.86% | +1.91 pp | 81.16% | +13.88 pp |
| Pokédex descriptions | 90.53% | +48.80 pp | 39.93% | +0.00 pp | 69.81% | +1.91 pp | 73.32% | +15.22 pp |
| Evolutions | 96.62% | +84.19 pp | 18.16% | +0.00 pp | 87.83% | -0.48 pp | 84.67% | +23.86 pp |
| Move catalog | 90.24% | +40.00 pp | 79.64% | +0.00 pp | 86.60% | -0.48 pp | 87.08% | +11.18 pp |
| Move details | 91.44% | +3.02 pp | 44.24% | +0.00 pp | 72.73% | -0.48 pp | 75.77% | +0.56 pp |
| Move descriptions | N/A | N/A | 62.96% | +0.00 pp | 73.17% | -0.48 pp | 72.00% | -0.43 pp |
| Learnsets | 94.62% | +94.62 pp | 18.16% | +0.00 pp | 67.16% | -0.47 pp | 71.04% | +26.85 pp |
| Egg moves | N/A | N/A | 33.33% | +0.00 pp | 68.42% | +1.91 pp | 64.41% | +1.70 pp |
| Machine moves | 87.37% | +45.26 pp | 44.44% | +0.00 pp | 65.07% | +1.91 pp | 69.79% | +14.20 pp |
| Tutor moves | N/A | N/A | 37.50% | +0.00 pp | 69.27% | -0.52 pp | 65.74% | -0.46 pp |
| Abilities | N/A | N/A | N/A | N/A | 74.61% | +0.95 pp | 74.61% | +0.95 pp |
| Ability descriptions | N/A | N/A | N/A | N/A | 67.28% | +0.95 pp | 67.28% | +0.95 pp |
| Ability mechanics | N/A | N/A | N/A | N/A | 65.56% | +0.96 pp | 65.56% | +0.96 pp |
| Area encounters | 87.37% | +1.05 pp | 62.96% | +0.00 pp | 68.42% | -0.48 pp | 73.41% | +0.00 pp |
| Type presentation | 88.42% | +2.10 pp | 62.96% | +0.00 pp | 73.68% | -0.48 pp | 77.04% | +0.30 pp |
| Ball catalog | N/A | N/A | N/A | N/A | 72.25% | +0.48 pp | 72.25% | +0.48 pp |
| World map | 87.37% | +1.05 pp | 40.74% | +0.00 pp | 54.55% | +0.00 pp | 62.84% | +0.30 pp |
| Local maps | 54.74% | +1.06 pp | 18.52% | +0.00 pp | 59.35% | +10.55 pp | 54.70% | +6.97 pp |
| Natures | N/A | N/A | N/A | N/A | 70.33% | N/A | 70.33% | N/A |

## Exact per-ROM comparison

- Improved compatibility on shared table types: 125
- Unchanged compatibility: 187
- Regressed compatibility on shared table types: 19
- Routing status changes: 3
- Selected-family changes: 3
- ROMs with fewer decoded cross-reference errors: 0
- ROMs with more decoded cross-reference errors: 0

The tables below show the largest 25 changes in each direction. The machine-readable report contains every ROM delta.

### Largest improvements

| ROM | Gen | RC21 | Current | Delta | Routing |
|---|---:|---:|---:|---:|---|
| Nova (v1.0.2).gb | 1 | 30.01% | 98.55% | +68.54 pp | NO_FAMILY_MATCH → SELECTED |
| Celebrations Blue (CrysAudio Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Blue (CrysAudio Snowy Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Blue (CrysAudio Snowy).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Blue (CrysAudio).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Blue (Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Blue (Snowy Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Blue (Snowy).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Blue.gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green (CrysAudio Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green (CrysAudio Snowy Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green (CrysAudio Snowy).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green (CrysAudio).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green (Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green (Snowy Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green (Snowy).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Green.gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red (CrysAudio Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red (CrysAudio Snowy Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red (CrysAudio Snowy).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red (CrysAudio).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red (Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red (Snowy Gen 2 UI).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red (Snowy).gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |
| Celebrations Red.gbc | 1 | 47.97% | 91.72% | +43.75 pp | SELECTED → SELECTED |

### Regressions

| ROM | Gen | RC21 | Current | Delta | Routing |
|---|---:|---:|---:|---:|---|
| Adventure Red Chapter (Beta 15 + Expansion Fix C).gba | 3 | 95.61% | 0.00% | -95.61 pp | SELECTED → ERROR |
| Lime (Demo) (v1.4).gba | 3 | 56.31% | 55.77% | -0.54 pp | SELECTED → SELECTED |
| Peach (Demo) (v1.4).gba | 3 | 56.31% | 55.77% | -0.54 pp | SELECTED → SELECTED |
| Emerald Ex (1.0.2).gba | 3 | 46.44% | 46.19% | -0.25 pp | SELECTED → SELECTED |
| Vega [English Translation] (20200823).gba | 3 | 95.04% | 94.98% | -0.06 pp | SELECTED → SELECTED |
| Vega.gba | 3 | 95.04% | 94.98% | -0.06 pp | SELECTED → SELECTED |
| Saiph 2 [Vigilante Mode] (v1.4.0).gba | 3 | 99.97% | 99.93% | -0.04 pp | SELECTED → SELECTED |
| Altered Emerald (v4.2c).gba | 3 | 99.93% | 99.90% | -0.03 pp | SELECTED → SELECTED |
| Inclement Emerald (v1.1.3).gba | 3 | 86.95% | 86.93% | -0.02 pp | SELECTED → SELECTED |
| Inclement Emerald [Custom UI Update 1.5] (v1.1.3).gba | 3 | 86.95% | 86.93% | -0.02 pp | SELECTED → SELECTED |
| Saiph 2 (v1.4.0).gba | 3 | 99.97% | 99.95% | -0.02 pp | SELECTED → SELECTED |
| Saiph 2 [Lag Fix Removal] (v1.4.0).gba | 3 | 99.97% | 99.95% | -0.02 pp | SELECTED → SELECTED |
| Saiph 2 [Time Based Removal] (v1.4.0).gba | 3 | 99.97% | 99.95% | -0.02 pp | SELECTED → SELECTED |
| Aesthetic Red (DS Font & Sprites) (Faithful Version) (v1.2).gba | 3 | 100.00% | 99.99% | -0.01 pp | SELECTED → SELECTED |
| Aesthetic Red (DS Font & Sprites) (v1.2).gba | 3 | 100.00% | 99.99% | -0.01 pp | SELECTED → SELECTED |
| Aesthetic Red (GBC Font & Sprites) (Faithful Version) (v1.2).gba | 3 | 100.00% | 99.99% | -0.01 pp | SELECTED → SELECTED |
| Aesthetic Red (GBC Font & Sprites) (v1.2).gba | 3 | 100.00% | 99.99% | -0.01 pp | SELECTED → SELECTED |
| Fire Red Backwards Edition.gba | 3 | 95.65% | 95.64% | -0.01 pp | SELECTED → SELECTED |
| Fuligin.gba | 3 | 100.00% | 99.99% | -0.01 pp | SELECTED → SELECTED |

## Tracked findings

- `G3-INPUT-001` — the one material parser regression is Adventure Red's 33,555,563-byte image. The current bounded loader rejects its 1,131 bytes beyond the GBA's 32 MiB addressable ROM window; both duplicate input rows fail closed. A generic trailer policy requires separate proof and must not weaken archive/decompression limits.
- `G3-LOCAL-PARTIAL-001` — the other 18 decreases are bounded Local-map fractions of 0.01–0.54 percentage points. Their base catalogs still select and persist; stricter raster/POI validation skips only malformed maps or POIs. Source-backed families remain the priority for closing these gaps.
- `G3-SAFFRON-001` — Saffron Demo v2.0 remains the only selected identity with decoded cross-reference errors: 533, unchanged from the exact RC21 rerun. The base catalog remains usable while unresolved relationships require independent quarantine or structural resolution.

## Interpretation boundary

These percentages describe static parser datasets only. Live game-state fields, SaveRAM decoding, THUMB/ARM mechanics, UI behavior, and physical-device validation retain their independent reports and denominators.
