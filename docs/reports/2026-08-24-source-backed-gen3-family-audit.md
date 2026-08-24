# Source-backed Gen III Family Audit

**Result:** Eight representative locally available ROMs were characterized against their public-source lineage and the current parser. Stage 2 has now published the integrated 20-byte `NatureInfo` ABI and validated the optional headerless unified `SpeciesInfo` presentation ABI for embedded sprites and Pokédex descriptions.

HackDex CFRU, hg-engine, and pokeemerald-expansion tags were used only as discovery and clustering hints. Production eligibility remains based on generic compiled evidence; source names, project identities, filenames, hashes, and absolute ROM offsets are not parser inputs.

## HackDex discovery snapshot

The public `/discover` Next.js server action returned 293 records on 2026-08-24. This resolved the earlier rate-limited page request and provided the following current metadata for the characterized controls:

| Control | HackDex version | Base | Relevant engine tag |
|---|---|---|---|
| Battle Theater | 2.8.0 | Emerald | None published; the exact local control remains the separately verified 2.3.0 source tag |
| Celia's Stupid Romhack | 1.1.4 | FireRed | None published |
| Dreamstone Mysteries | Not listed | — | — |
| Elite Redux | 2.65.3b | Emerald | `pokeemerald-expansion` |
| GS Chronicles | 2.7.6 | FireRed | `CFRU` |
| Pokescape | 1.0.4 | Emerald | `pokeemerald-expansion` |
| Tourmaline | 1.1.1 | Emerald | `pokeemerald-expansion` |
| Voyager Frontier Demo | 1.2 | Emerald | `pokeemerald-expansion`; the local characterized binary is older 0.3.6 |

No characterized control was tagged `hg-engine`. These labels improve clustering and priority order but do not establish source/binary alignment or select a production parser.

## Current matrix

The focused scan used one parser job and completed with zero read/parse and cross-reference errors.

| Representative ROM | Routing | Current result | Public-source alignment | Decision |
|---|---|---:|---|---|
| Battle Theater 2.3.0 | Emerald selected | 23/23, 99.97% | Exact full-source `v2.3.0` tag at [pokemon-battle-theater](https://github.com/logdog2325/pokemon-battle-theater) | Integrated Natures resolved; flavor affinity remains tracked under `G3-NATURE-001` |
| Celia's Stupid Romhack 1.1.4 | FireRed/LeafGreen selected | 22/24, 91.51% | Public source available locally | Retain as a FireRed-family regression; defer remaining gaps under `G3-CELIA-001` |
| Dreamstone Mysteries | Emerald selected | 17/24, 70.81% | Current public source at [dreamstone-mysteries](https://github.com/dsmyst/dreamstone-mysteries); no exact selected-ROM release tag established | Independent compiled controls now prove integrated Natures plus headerless embedded sprites and descriptions; defer remaining gaps under `G3-DREAM-001` |
| Elite Redux 2.65.3b | No family match | 2/24, 8.33% | Public source is older than the selected ROM and has no exact release tag | Defer under `G3-ELITE-001` |
| GS Chronicles 2.7.6 | FireRed/LeafGreen selected | 20/24, 83.25% | Local repository is an engine source, not an exact complete hack release | Retain as a regression; defer under `G3-GSC-001` |
| Pokescape 1.0.4 | Emerald selected | 18/24, 74.97% | Public source at [pokescape_rom](https://github.com/Demonheadge/pokescape_rom), without a matching 1.0.4 tag | Defer distinct semantic region-entry join under `G3-POKESCAPE-001` |
| Tourmaline 1.1.1 | Emerald selected | 14/24, 58.31% | Public source at [tourmaline](https://github.com/surskitty/tourmaline); available tags predate 1.1.1 | Defer missing encounter/map-group authority under `G3-TOURMALINE-001` |
| Voyager 0.3.6 | No family match | 5/24, 20.63% | Exact version label exists at [pokevoyager](https://github.com/ghoulslash/pokevoyager), but that tagged tree lacks the complete build source; current master is later | Defer under `G3-VOYAGER-001` |

## Selected compiled ABI

The exact Battle Theater source defines 25 `NatureInfo` records with this relevant layout:

- pointer to the ROM-native name at byte 0;
- `statUp` at byte 4;
- `statDown` at byte 5;
- total record size 20 bytes.

Its source consumer applies neutral behavior when the two fields are equal, otherwise scales the selected raised or lowered stat by `110 / 100` or `90 / 100`. The compiled ROM independently contains one structurally valid 25-row table and a Thumb consumer that indexes records by 20 bytes, reads bytes 4 and 5, compares the fields, and retains the 110/90/100 arithmetic.

Dreamstone independently contains one table with the same structural matrix and a compact compiled consumer proving the same record stride, field offsets, comparisons, and arithmetic. It is a sibling compiled control, not a source-identity exception.

The current `Gen3NatureResolver` supports only a separate signed five-byte stat-modifier table plus a separate name-pointer table. It therefore fails closed on both integrated controls.

## Checkpoint decision

Checkpoint 1 will add a second generic Nature ABI without changing the existing one:

1. Discover candidate roots only through the existing compiled GBA reference index.
2. Require exactly 25 20-byte records, valid ROM text pointers, 25 distinct names, and the canonical five-by-five `statUp`/`statDown` matrix.
3. Require a compiled Thumb consumer proving 20-byte indexing, byte fields 4 and 5, neutral/up/down comparisons, and 110/90/100 scaling.
4. Convert the two stat IDs to the existing five-column `-1/0/+1` model; flavor modifiers remain unknown.
5. Fail closed on zero candidates and ambiguous on multiple eligible ABIs or roots.

Expected focused gain: Battle Theater reaches 23/23; Dreamstone gains authoritative Nature data. Any additional gain is incidental and must satisfy the same compiled ABI.

## Checkpoint 1 result

The implemented generic ABI produced the expected gains in a fresh one-job scan: Battle Theater reached 23/23 at 99.97%, and Dreamstone reached 15/24 at 62.48%. The other six characterized controls retained their routing, feature counts, and compatibility scores. All six selected catalogs persisted and reopened; parser, persistence, and decoded cross-reference errors remained zero. The residual Battle Theater fraction is tracked as `G3-NATURE-001` because the compiled stat consumer does not prove flavor affinity. The checkpoint is published as signed prerelease `v1.1.0-rc.57`.

## Checkpoint 2 result

The existing compiled name accessor and six-stat consumer uniquely establish a 260-byte unified species root and extent before any presentation field is considered. The source-backed struct order and compiled rows then validate an optional pointer-aligned presentation tail: inline category, height/weight, terminated description pointer, compressed front graphics, and normal palette. Dreamstone and an independent Crippling control expose complete presentation data for every active row; corrupting more than 20% of Dreamstone's description pointers disables only descriptions while names, stats, sprites, routing, and startup remain accepted.

The fresh eight-ROM matrix improves Dreamstone from 15/24 at 62.48% to 17/24 at 70.81%, materializing 1,522 ROM-native 64×64 sprites and 1,522 descriptions. The other seven characterized controls retain their routing, feature counts, and compatibility scores. All six selected catalogs persisted and reopened through schema 38 with zero parser, persistence, or decoded cross-reference errors.

## Deferral ledger

| ID | Missing behavior | Safe fallback | Acceptance condition |
|---|---|---|---|
| `G3-NATURE-001` | Flavor affinity for the integrated 20-byte `NatureInfo` ABI is not proven | Publish 25 names and stat effects with flavor modifiers unknown | A generic compiled consumer or structurally referenced table proves the ROM-native five-flavor mapping across the exact control and an independent sibling |
| `G3-CELIA-001` | Remaining partial Local rendering/description coverage and unavailable ability mechanics | Preserve all accepted records and skip only malformed maps/unsupported mechanics | A source-backed generic ABI resolves each missing domain and affected plus FireRed controls remain stable |
| `G3-DREAM-001` | Egg/machine moves, abilities, balls, and Local maps; Local raster total is 100,409,600 pixels against the 100,000,000 bound | Keep those modules unavailable; preserve accepted catalog, embedded presentation, World map, and unrelated modules | Generic compiled consumers resolve the remaining data, and Local maps fit a bounded structural representation without merely raising the global cap |
| `G3-ELITE-001` | Family routing and most datasets for 2.65.3b | No family match; no speculative catalog | Establish exact or independently proven compiled lineage and pass generic family anchors plus real-ROM controls |
| `G3-GSC-001` | Missing learnset/ruleset, egg-move, and type-chart domains in the selected binary | Preserve the 20 accepted capabilities | Resolve each domain from compiled authority; engine-source observations alone are insufficient |
| `G3-POKESCAPE-001` | World-map semantic region-entry join plus remaining descriptions/move-details/ability domains | Local maps and accepted modules remain available; World map stays unavailable | Resolve a unique generic compiled region-entry consumer and pass sibling Emerald controls without fixed roots |
| `G3-TOURMALINE-001` | Encounters, unique `gMapGroups` authority, World/Local maps, and remaining catalog domains | Map modules and unsupported datasets remain unavailable | Resolve compiled encounter/map consumers generically and establish bounded semantic joins on the selected binary |
| `G3-VOYAGER-001` | Family routing and most datasets | No family match; no speculative catalog | Obtain complete matching source or independently prove the compiled engine ABI, then pass generic anchors and held-out controls |

None of these deferrals blocks the integrated Nature checkpoint. Each failed optional resolver must continue to disable only its own module.
