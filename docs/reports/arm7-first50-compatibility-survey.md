# ARM7TDMI exact-first-50 compatibility survey

- Foundation commit: `bac2f8f1f4d8883e00dacbabe3cc5a130cb83a2a`
- Corpus identity: first 50 unique SHA-256 rows in manifest order; each extracted ROM was freshly rehashed before both analyses.
- Sessions: two fresh parser/mechanics analyses per row.
- Scope: evidence only. No catalog/API/app wiring and no production ROM-name, SHA, symbol, or absolute-offset selection.
- Gate interpretation: this report does not define a shipping-success threshold. `ORACLE_MATCH` means the emitted tuple set exactly matched the available source control; incomplete bounded paths are still reported.

## Aggregate

| Outcome | Count |
|---|---:|
| Total manifest rows | 50 |
| Applicable GBA headers | 46 |
| Gen I/II GBC headers (`NOT_APPLICABLE`) | 4 |
| `ORACLE_MATCH` | 1 |
| `MECHANICS_INPUT_UNAVAILABLE` | 39 |
| `PARSER_LAYOUT_UNAVAILABLE` | 3 |
| `STATIC_TYPED_LAYOUT_UNAVAILABLE` | 3 |
| Nondeterministic rows | 0 |

The dominant failure is structural input plumbing, not a missing arithmetic signature: 39/46 applicable ROMs already select a typed static move table and ability domain, but the parser publishes neither a typed `BattleMechanicsAbi` nor a role/dataflow-proven routine root.

## Exact semantic oracle

Classic is the sole row with a complete, independently derived source control in this slice. Both runs emitted exactly, with no extra tuples:

- ability 37: attacker Attack ×2, physical move
- ability 74: attacker Attack ×2, physical move
- ability 55: attacker Attack ×3/2, physical move
- ability 62: attacker Attack ×3/2, physical move and attacker status low byte nonzero

Its typed runtime cluster is indexed battle records with stride `0x5c`, Attack `u16@0x02`, ability `u16@0x20`, status `u32@0x50`, and a source-supported Q4.12 `CalcAttackStat` path. Each run decoded 4096 instructions and reported 288 bounded paths as incomplete; therefore this is an exact oracle match, not a claim that every path/mechanic was modeled.

## Structural clusters

| Parser family | Static move ABI | Typed battle ABI | Routine cluster | Rows |
|---|---|---|---|---:|
| EMERALD | BATTLE_ENGINE_20 | INDEXED_ARRAY:stride=0x5c:attack=u16@2:ability=u16@0x20:status=u32@0x50 | CLASSIC_CALC_ATTACK_Q4_12 | 1 |
| EMERALD | NO_MOVE_ABI | NO_BATTLE_ABI | NO_ROUTINE | 2 |
| EMERALD | RETAIL_12 | NO_BATTLE_ABI | NO_ROUTINE | 8 |
| FIRERED_LEAFGREEN | NO_MOVE_ABI | NO_BATTLE_ABI | NO_ROUTINE | 1 |
| FIRERED_LEAFGREEN | RETAIL_12 | NO_BATTLE_ABI | NO_ROUTINE | 29 |
| NO_FAMILY | NO_MOVE_ABI | NO_BATTLE_ABI | NO_ROUTINE | 3 |
| RUBY_SAPPHIRE | RETAIL_12 | NO_BATTLE_ABI | NO_ROUTINE | 2 |

FRLG retail-layout/no-runtime-input is the largest family cluster (29), followed by Emerald retail-layout/no-runtime-input (8) and Ruby/Sapphire retail-layout/no-runtime-input (2). These are the next source-control-driven implementation targets.

## First-33 preservation

Status and selected family remained stable for all first 33 rows. Row 14, Arcoiris, differs only in catalog reference-error text: both fresh sessions now produce zero reference errors, while the stored baseline records missing-ability references. This is deterministic catalog-output drift and remains a separate failed baseline field; it is not used to grant mechanics eligibility.

## Row matrix

| # | ROM | SHA-256 | Header | Parser family | Static ABI / abilities | Session 1 | Session 2 | First-33 |
|---:|---|---|---|---|---|---|---|---|
| 1 | A Grand Day Out.gba | `2005275fc54a…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 2 | Advanced Adventure (2021).gba | `736af8f70169…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 3 | Adventure Red Chapter (Beta 15 + Expansion Fix C).gba | `75ca054238d4…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 139 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 4 | Aesthetic Red (DS Font & Sprites) (Faithful Version) (v1.2).gba | `80e96000eb82…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 5 | Aesthetic Red (DS Font & Sprites) (v1.2).gba | `7f01d5ffd8b2…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 6 | Aesthetic Red (GBC Font & Sprites) (Faithful Version) (v1.2).gba | `a88c1d13b029…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 7 | Aesthetic Red (GBC Font & Sprites) (v1.2).gba | `d3b3b5a8556d…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 8 | Aesthetic Red (Music & Graphics Only) (v1.2).gba | `0dfca1fd701b…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 9 | All In (v1.0).gba | `baf1bad15fd2…` | GBA | EMERALD | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 10 | Altair (2019-06-13).gba | `333e4fcbf2b8…` | GBA | EMERALD | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 11 | Altered Emerald (v4.2c).gba | `8fe93d8245c9…` | GBA | EMERALD | RETAIL_12 / 255 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 12 | Amethyst (v1.3.0).gba | `3f987c21b2d6…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 13 | Amnesia (Save Fix).gba | `08b51b82beef…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 14 | Arcoiris.gba | `fe428c3a4574…` | GBA | RUBY_SAPPHIRE | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | reference drift |
| 15 | AshGray - Newerest Edition (v1.0).gba | `a08055484c83…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 16 | AshGray (v4.6).gba | `a2d141a4f080…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 17 | Battle Theater (V2.3.0).gba | `99c84950e2be…` | GBA | EMERALD | — | STATIC_TYPED_LAYOUT_UNAVAILABLE | STATIC_TYPED_LAYOUT_UNAVAILABLE | match |
| 18 | Bill's Secret Garden DX (v2.0).gba | `2eb56e73fdba…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 19 | Blazed Glazed (v1.3).gba | `0b55d44bfd32…` | GBA | EMERALD | RETAIL_12 / 85 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 20 | Blazing Emerald (v1.6).gba | `2ff140431181…` | GBA | EMERALD | RETAIL_12 / 125 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 21 | Bronze (Girl Patch) (v1.23).gbc | `9c6aa82ae48b…` | GBC | GOLD_SILVER | — | NOT_APPLICABLE | NOT_APPLICABLE | match |
| 22 | Bronze (v1.23).gbc | `3cf45157784f…` | GBC | GOLD_SILVER | — | NOT_APPLICABLE | NOT_APPLICABLE | match |
| 23 | Bronze 2 (v1.05).gbc | `87758fbc06a9…` | GBC | CRYSTAL | — | NOT_APPLICABLE | NOT_APPLICABLE | match |
| 24 | CAWPS.gba | `88c2e3f60924…` | GBA | EMERALD | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 25 | Celia's Stupid Romhack (1.1.4).gba | `81ac9b9d4e7b…` | GBA | FIRERED_LEAFGREEN | — | STATIC_TYPED_LAYOUT_UNAVAILABLE | STATIC_TYPED_LAYOUT_UNAVAILABLE | match |
| 26 | Chaos Black (Fixed) (v3.1).gba | `f21a917a5d43…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 27 | Chaos Black Recreated (2026-01-25).gba | `3358c57e2258…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 28 | Chronicles of Soala (v9.0).gba | `7c6f3945bdac…` | GBA | EMERALD | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 29 | Classic (v1.5.0b).gba | `01c0177b2498…` | GBA | EMERALD | BATTLE_ENGINE_20 / 267 | ORACLE_MATCH | ORACLE_MATCH | match |
| 30 | Cloud White (v523d).gba | `f70922408ea7…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 138 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 31 | Cloud White 2 (v279).gba | `6d9075a559c2…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 155 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 32 | Cloud White 3 (v277).gba | `7ced98ef9232…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 155 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 33 | Clover (v1.3.3).gba | `42f99abd5489…` | GBA | EMERALD | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | match |
| 34 | Crippling Medical Debt Edition (v1.1).gba | `79882b5e276f…` | GBA | — | — | PARSER_LAYOUT_UNAVAILABLE | PARSER_LAYOUT_UNAVAILABLE | — |
| 35 | Crown (v1.9).gba | `28d7f55c96bd…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 36 | Crystal Advance Redux (7-8-26).gba | `fbbcbf32afd4…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 207 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 37 | Dark Cry - The Legend of Giratina (v2.6.7).gba | `e61d4f66e2d4…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 38 | Dark Energy (v5.01).gbc | `6dba21527ea5…` | GBC | GOLD_SILVER | — | NOT_APPLICABLE | NOT_APPLICABLE | — |
| 39 | Dark Rising - Order Destroyed.gba | `71b44f3b4be1…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 130 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 40 | Dark Rising 2.gba | `81b97561b73d…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 41 | Dark Rising Origins - Worlds Collide.gba | `c6440addb23d…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 138 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 42 | Dark Rising.gba | `712697aba9a0…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 43 | Dark Violet.gba | `6b7e6df19c97…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 126 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 44 | Dark Violet (Fan-Patch).gba | `d171d29b691c…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 126 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 45 | Dark Worship.gba | `930663704d1a…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 46 | DarkFire (v2.1.3).gba | `8c564fcd1e41…` | GBA | — | — | PARSER_LAYOUT_UNAVAILABLE | PARSER_LAYOUT_UNAVAILABLE | — |
| 47 | Delta Emerald (v1.1.5).gba | `7f4aa1aa68b1…` | GBA | EMERALD | — | STATIC_TYPED_LAYOUT_UNAVAILABLE | STATIC_TYPED_LAYOUT_UNAVAILABLE | — |
| 48 | Dragonstone (v1.63).gba | `2772296094b3…` | GBA | RUBY_SAPPHIRE | RETAIL_12 / 77 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 49 | Dreams (v1.5.3).gba | `ad73b864873f…` | GBA | FIRERED_LEAFGREEN | RETAIL_12 / 254 | MECHANICS_INPUT_UNAVAILABLE | MECHANICS_INPUT_UNAVAILABLE | — |
| 50 | Dreamstone Mysteries.gba | `ac31df9cc158…` | GBA | — | — | PARSER_LAYOUT_UNAVAILABLE | PARSER_LAYOUT_UNAVAILABLE | — |

## Stage meanings

- `NOT_APPLICABLE`: actual ROM header is Gen I/II GBC; not counted as a mechanics success.
- `PARSER_LAYOUT_UNAVAILABLE`: parser did not select one engine layout.
- `STATIC_TYPED_LAYOUT_UNAVAILABLE`: selected family did not yield the typed static move/ability prerequisites.
- `MECHANICS_INPUT_UNAVAILABLE`: static prerequisites exist, but runtime battle ABI and structural routine-root provenance are absent.
- `ORACLE_MATCH`: exact expected semantic tuple set, no extras; bounded incomplete paths remain separately visible.
