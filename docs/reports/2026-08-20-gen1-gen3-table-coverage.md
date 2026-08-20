# Gen I–III ROM table coverage — v1.1.0-rc.18

This report measures the released parser against the consolidated Game Boy, Game Boy Color, and Game Boy Advance ROM corpus. It contains aggregate percentages only; it contains no ROM bytes, decoded text, sprites, saves, or private filesystem paths.

Measured release: `v1.1.0-rc.18` (`49c07f9f456f3076c4e0dd9237cf59bf073a7c6e`).
Unique ROMs: 331 (Gen I 95; Gen II 27; Gen III 209).

Corpus integrity: 333 input rows became 331 unique ROMs after exact-SHA deduplication. One Gen III ROM, Radical Red v4.1, failed before catalog publication because its ability domain exceeded the selected typed field width; its applicable tables contribute 0% and the failure remains unresolved. Every selected catalog persisted and reopened successfully.

Each ROM has equal weight within a table type. A usable partial table contributes its validated record fraction; unresolved or erroneous tables contribute 0%. `NOT_APPLICABLE` cells are excluded from that table's denominator.

| Table type | Gen I | Gen II | Gen III | Overall |
|---|---:|---:|---:|---:|
| Species catalog | 85.05% | 61.28% | 69.68% | 73.41% |
| Species names | 78.46% | 69.62% | 86.66% | 82.92% |
| Species types | 87.56% | 61.79% | 75.35% | 77.75% |
| Type chart | 10.53% | 25.93% | 84.21% | 58.31% |
| Base stats | 87.56% | 61.79% | 75.35% | 77.75% |
| Sprites | 44.18% | 73.75% | 76.47% | 66.98% |
| Pokédex descriptions | 41.73% | 39.93% | 67.42% | 57.80% |
| Evolutions | 12.43% | 18.16% | 87.83% | 60.51% |
| Move catalog | 50.24% | 79.64% | 87.08% | 75.90% |
| Move details | 88.42% | 44.24% | 72.73% | 74.91% |
| Move descriptions | N/A | 62.96% | 73.17% | 72.00% |
| Learnsets | 0.00% | 18.16% | 67.62% | 44.18% |
| Egg moves | N/A | 33.33% | 66.03% | 62.29% |
| Machine moves | 42.11% | 44.44% | 63.16% | 55.59% |
| Tutor moves | N/A | 39.13% | 71.51% | 67.94% |
| Abilities | N/A | N/A | 73.18% | 73.18% |
| Ability descriptions | N/A | N/A | 65.89% | 65.89% |
| Ability mechanics | N/A | N/A | 64.60% | 64.60% |
| Area encounters | 86.32% | 62.96% | 68.42% | 73.11% |
| Type presentation | 86.32% | 62.96% | 73.68% | 76.44% |
| Ball catalog | N/A | N/A | 71.29% | 71.29% |
| World map | 86.32% | 40.74% | 54.07% | 62.24% |
| Local maps | N/A | N/A | 48.33% | 48.33% |

## Memory measurements

These are desktop corpus peaks with four concurrent parser workers, not Android single-ROM requirements.

| Corpus | Peak working set | Peak private memory |
|---|---:|---:|
| Gen II | 1343.78 MiB | 1758.52 MiB |
| Gen III | 8076.18 MiB | 8556.44 MiB |
| Unbound, one worker | 2067.02 MiB | 2227.00 MiB |

The Gen III peak includes concurrent large-ROM allocation churn. Android prevention must therefore use a single-ROM, stage-aware memory budget, release temporary buffers between stages, and preserve the base catalog when an optional high-memory stage cannot safely start.
