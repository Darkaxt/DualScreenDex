# Gen I–III ROM table coverage — v1.1.0-rc.21

This report updates the frozen Gen I–III corpus baseline with an exact rerun of the one parser row changed for rc21. It contains aggregate percentages only; it contains no ROM bytes, decoded text, sprites, saves, or private filesystem paths.

Baseline corpus: `v1.1.0-rc.18` (`49c07f9f456f3076c4e0dd9237cf59bf073a7c6e`). Radical Red v4.1 was rerun on the narrow parser correction `466d5ffcd6acfa5926a7ae3753605ab561465161`; every other immutable corpus row is unchanged.
Unique ROMs: 331 (Gen I 95; Gen II 27; Gen III 209).

Corpus integrity: 333 input rows became 331 unique ROMs after exact-SHA deduplication. Parser errors are now 0. Radical Red v4.1 is selected as FireRed/LeafGreen at 82.21% (19/23 applicable table types); its 15-section, 6,922,240-byte SQLite catalog persisted and reopened successfully. Every selected catalog persisted and reopened successfully.

Each ROM has equal weight within a table type. A usable partial table contributes its validated record fraction; unresolved or erroneous tables contribute 0%. `NOT_APPLICABLE` cells are excluded from that table's denominator.

| Table type | Gen I | Gen II | Gen III | Overall |
|---|---:|---:|---:|---:|
| Species catalog | 85.05% | 61.28% | 70.16% | 73.71% |
| Species names | 78.46% | 69.62% | 87.14% | 83.22% |
| Species types | 87.56% | 61.79% | 75.83% | 78.05% |
| Type chart | 10.53% | 25.93% | 84.69% | 58.61% |
| Base stats | 87.56% | 61.79% | 75.83% | 78.05% |
| Sprites | 44.18% | 73.75% | 76.95% | 67.28% |
| Pokédex descriptions | 41.73% | 39.93% | 67.90% | 58.10% |
| Evolutions | 12.43% | 18.16% | 88.31% | 60.81% |
| Move catalog | 50.24% | 79.64% | 87.08% | 75.90% |
| Move details | 88.42% | 44.24% | 73.21% | 75.21% |
| Move descriptions | N/A | 62.96% | 73.65% | 72.43% |
| Learnsets | 0.00% | 18.16% | 67.62% | 44.18% |
| Egg moves | N/A | 33.33% | 66.51% | 62.71% |
| Machine moves | 42.11% | 44.44% | 63.16% | 55.59% |
| Tutor moves | N/A | 39.13% | 72.04% | 68.42% |
| Abilities | N/A | N/A | 73.66% | 73.66% |
| Ability descriptions | N/A | N/A | 66.33% | 66.33% |
| Ability mechanics | N/A | N/A | 64.60% | 64.60% |
| Area encounters | 86.32% | 62.96% | 68.90% | 73.41% |
| Type presentation | 86.32% | 62.96% | 74.16% | 76.74% |
| Ball catalog | N/A | N/A | 71.77% | 71.77% |
| World map | 86.32% | 40.74% | 54.55% | 62.54% |
| Local maps | N/A | N/A | 48.80% | 48.80% |

## Memory measurements

These are desktop corpus peaks with four concurrent parser workers, not Android single-ROM requirements.

| Corpus | Peak working set | Peak private memory |
|---|---:|---:|
| Gen II | 1343.78 MiB | 1758.52 MiB |
| Gen III | 8076.18 MiB | 8556.44 MiB |
| Unbound, one worker | 2067.02 MiB | 2227.00 MiB |

The Gen III peak includes concurrent large-ROM allocation churn. Android prevention must therefore use a single-ROM, stage-aware memory budget, release temporary buffers between stages, and preserve the base catalog when an optional high-memory stage cannot safely start.
