# October 3, 2026 ROM-library refresh and compatibility priorities

## Decision and scope

**Active parent goal: IN_PROGRESS — 100% compatibility with all ROMs, one ROM at a time.** The user clarified this completion boundary on October 7. The [parent specification](../../superpowers/specs/2026-10-07-all-rom-compatibility-completion-design.md) and [active ledger](../../superpowers/plans/2026-10-07-all-rom-compatibility-completion-plan.md) govern continuation. Pokescape's accepted World Map, packed moves, native Dex descriptions, [complete native move fields](2026-10-07-pokescape-move-text-acceptance.md), type names, [persisted species categories](2026-10-07-pokescape-species-category-acceptance.md) and [native ability text/species joins](2026-10-07-pokescape-ability-text-acceptance.md) are leaf checkpoints; the ROM is now95.83%, so numeric ability mechanics/typed modifiers and remaining item/Local/encounter/POI text obligations stay active before selecting another implementation target. Priorities below remain the future candidate queue, not authorization to stop or skip current-ROM deficits.

The GB/GBC/GBA Pokémon hack library has changed enough to warrant a new corpus snapshot. The current snapshot contains **342 distinct in-scope ROM identities**. The old corpus and its release evidence remain preserved. The inventory refresh itself did not change the production parser; the [Gen I expansion](2026-10-03-gen1-core-acceptance.md) adds ten selected catalogs and the [Gen II expansion](2026-10-03-gen2-core-acceptance.md) adds four. Neither step publishes a new APK.

- [Current 342-ROM compatibility matrix](2026-10-03-library-compatibility.md)
- [October 4 Gen II relationship acceptance: seven gains, 19 checked catalogs](2026-10-04-gen2-relationships-acceptance.md)
- [October 5 Gen III widened-core acceptance: two recovered ROWE cores, 13-input cohort](2026-10-05-gen3-wide-core-acceptance.md)
- [October 6 Pokescape World Map acceptance: one independently checked static map](2026-10-06-pokescape-world-map-acceptance.md)
- [October 7 Pokescape packed-move acceptance: 828 native details/categories](2026-10-07-pokescape-moves-acceptance.md)
- [October 7 Pokescape native Dex acceptance: 1,235 descriptions and dimensions](2026-10-07-pokescape-dex-acceptance.md)
- [October 7 Pokescape native move-text acceptance: 823 prose fields and five exact native placeholders](2026-10-07-pokescape-move-text-acceptance.md)
- [Machine-readable rows, aliases, provenance, and delta receipt](2026-10-03-library-compatibility.json)
- [Current library identity](../../../release/current-library-corpus.json)
- [Frozen 333-input release matrix](../../rom-compatibility-matrix.md)

The scope is the user's existing Game Boy, Game Boy Color, and Game Boy Advance Pokémon hack folders. Original archives were read-only throughout. No ROM or save was downloaded, modified, redistributed, or added to repository assets.

## Inventory and corpus update

| Measure | Count |
|---|---:|
| Archives reviewed | 269 |
| ROM payload entries | 383 |
| Distinct source-byte identities before scope exclusion | 343 |
| Pinball identity excluded under existing mainline-parser scope | 1 |
| Additional duplicate payload entries collapsed | 40 |
| Current in-scope inputs / distinct parser identities | 342 / 342 |
| Current identities unchanged from the release corpus | 296 |
| New or changed in-scope identities | 46 |
| Old input identities absent from the current library | 35 |

The arithmetic is **383 − 1 excluded payload − 40 duplicate entries = 342**. The historical benchmark had 333 input names but only 331 distinct identities; collapsing those aliases and preserving the 296 still-present identities explains why this is not simply a 333 + 46 denominator.

Payload counts by source console folder are 69 GB, 85 GBC, and 229 GBA. These are folder labels, not detected generations: a `.gbc` hack may still use a Generation I engine. The Mew Distribution save-only archive contributes no ROM input.

A separate retained local snapshot and current-corpus pointer were created. Every staged ROM's source size and SHA-256 were checked against the inventory. Both source identity and parser identity are retained privately. Adventure Red's oversized archive payload is normalized by the existing loader to the first addressable 32 MiB; applying that same rule reproduces the frozen release corpus digest exactly. There was no baseline identity drift and no loader change.

Alias metadata matters:

- The ROWE bundle includes both 2.1.1 and **2.1.9.1 Experimental**; the outer archive label does not identify every contained build.
- The September 14 Polished Crystal and Faithful payloads are byte-identical and count once, with both names retained as aliases.
- Bundled older versions remain in scope when they are still present and byte-distinct. A newer filename alone is not authority to remove another input.
- TCG-labelled entries remain unmatched under the inherited scanner policy. Supporting a card-game engine is a separate product scope, not a mainline-family routing fix. Pinball stays excluded.

## Compatibility evidence

The original inventory refresh parsed only the **46 new or changed identities**. Their schema-16 report and execution receipt bind source commit `a306bffd64dfb50d81076a91657bb76c1f8e9617`, generator digest, report digest, and input count. Their exact normalized identity multiset was checked against the staged delta. That run returned **34 selected, 0 ambiguous, 12 unmatched, 0 parser errors**, with **0 catalog errors and 0 persistence errors**.

At that checkpoint, the other **296 identities** inherited the published September 14 rows after byte-identity verification, with no parser/catalog/coverage code changes. Identical old alias rows agreed before deduplication. Inherited confidence retains published integer rounding and coverage retains two decimal places.

The subsequent [Gen I acceptance](2026-10-03-gen1-core-acceptance.md) supersedes **30 rows**, adding six PureRGB and four Yellow catalogs through generic compiled-consumer changes. Three bounded runs total 48 executions over 30 distinct identities; rejected initial PureRGB joins and sprite regressions are superseded by corrected checkpoints. These rows validate in-memory materialization/reference closure, not new persistence, SaveRAM, or live Android acceptance.

The [Gen II acceptance](2026-10-03-gen2-core-acceptance.md) supersedes **18 rows** and adds Polished, Inheritance, Ambrosia and Sour Crystal. Four bounded executions total 58 parser executions over 18 identities, with diagnostic/intermediate rows superseded explicitly. All 15 accepted selected catalogs close decoded references and pass complete SQLite write/reopen equality and matching logical digests. The effective matrix is **263 historical + 31 retained delta + 30 Gen I + 18 Gen II identities**. Not every row was scanned against the latest source; official standalone Gen II controls were unavailable and not run.

The [October 5 Gen III widened-core acceptance](2026-10-05-gen3-wide-core-acceptance.md) supersedes 13 cohort rows after a fresh serial packaged run. ROWE 2.1.1 now selects, and Experimental gains 1,025 canonical names/stats/sprites and a reference-safe expanded ordinary level-up catalog. All ten selected catalogs close complete references and exact SQLite reopening. The 11 controls retain their outcomes/counters/capability statuses; Voyager's two builds and Elite Redux remain unmatched. The matrix now combines 19 Gen II, 13 Gen III widened-core and one [Pokescape map/move/Dex](2026-10-07-pokescape-dex-acceptance.md) focused rows with 309 unchanged full342-baseline rows, without a full-corpus rerun.

| Current snapshot outcome | Count |
|---|---:|
| Inputs | 342 |
| Selected family and materialized catalog | 301 |
| Ambiguous family | 2 |
| No family match | 39 |
| Parser errors | 0 |
| Resolved ROM-language manifests | 286 |
| Unknown ROM-language manifests | 15 |
| Proven multilingual manifests | 0 |

The original inventory-only routing rate was 286/342, versus 276/333 in the historical release benchmark; that denominator/input change was not a parser gain. The subsequent **286 → 296 → 300 → 301 on the same 342 identities** is a verified ten-catalog Gen I gain, four Gen II catalogs and one newly routed ROWE catalog. Experimental's core recovery improves an already-selected row rather than adding another routing gain. Routing still does not imply complete optional data or maps.

N/A capabilities remain outside coverage denominators. Capability confidence and record-based coverage are different measures; neither family recognition nor a high confidence cell proves complete runtime behavior.

### What the changed builds reveal

| Changed group | Current evidence | Interpretation |
|---|---|---|
| Intense Indigo / IndigoLite, 16 variants | All select Red/Blue; 99.34–99.50% coverage | Broadly working; small stats/sprite gaps are lower priority than wholly unmatched families. |
| PureRGB, 6 variants | All now select Red/Blue; 75.00%; 151 canonical names/stats, 165 detailed moves | Core/index authority accepted; sprites, Dex text, maps, and non-Dex form stats remain separate follow-ups. |
| Yellow Kaizo, 2 variants | Both now select Yellow; 87.50%; 151 names/stats/sprites, 166 detailed moves | Independent restart-copy ABI accepted; Dex text and Local Map remain missing. |
| Yellow Legacy / Legacy+, 2 variants | Both now select Yellow; 100.00%; 151 names/stats/sprites/descriptions, 165 detailed moves | Independently proven helper-bank ABI; no new live-state or persistence claim. |
| Polished Crystal, Ambrosia, Crystal Inheritance, Sour Crystal | All four select with complete named canonical stat joins and independently resolved move details | Modern catalogs remain essential-only; Ambrosia categories and Sour optional gaps are explicit. Faithful alias is not a fifth identity. |
| Static Yellow, 2 variants | Both select; 87.50%; Dex text and Local Map not found | A focused optional-capability follow-up, not a family-routing problem. |
| Battle Theater 2.6 | Selects; 89.78%; both maps available | Preserve as an expanded-engine regression control, not a first map target. |
| Pokescape 1.0.4 | Selects;95.83%;1,235 Dex descriptions/dimensions/categories,18 referenced type names,828 move details/texts,298 ability names/descriptions and1,235 native ability joins/2,697 edges; both maps preserved | [Independent ability acceptance](2026-10-07-pokescape-ability-text-acceptance.md) checks94,291 references, whole SQLite and exact category-baseline reversal; numeric mechanics/typed modifiers and required item/Local/encounter/POI text remain mandatory. |
| Heart and Soul 2.0.6 / Soulgold 1.1.4 | 80.92% / 79.40%; World Map missing, Local Map partial | Useful source-backed map follow-ups with functioning base catalogs. |
| FireRed Reignited / Leafgreen Regrown | Both select; 95.33% | Lower urgency; numeric ability proof is not a prerequisite for otherwise usable catalogs. |
| Saiph 2, 4 modes | All select; 95.71% | Keep all four distinct binaries as regression controls. |
| Crystal Advance Redux | Selects; 82.58%; move text, machines, and World Map missing | Secondary structural follow-up; no exact public source match established by this audit. |
| Phoenix Red | Selects; 95.22% | Lower urgency. |
| Emerald Ex / Exceeded | Select; 54.17% / 54.16% | Names or acquisition/text datasets remain absent; routing alone is insufficient. |
| ROWE 2.1.9.1 Experimental / 2.1.1 | Both select; 70.82% / 70.83%; 1,025 canonical names/stats/sprites each | Core and ordinary level-up recovery accepted; nonordinary categories, forms, tutor/machine gaps, Dex/evolutions/maps remain separately ledgered. |
| Emerald Rogue 2.2.1 EX / Vanilla | Select; 62.47% / 83.08% | Evidence retained, but dedicated Rogue-only work remains outside the active priority queue. |

## Recommended compatibility-expansion order

These are investigation priorities, not verified shared root causes or promised gains. Local source checkouts are structural oracles and may not match the current compiled build. Match the build before implementing an ABI; never add ROM names, hashes, fixed addresses, or project profiles to production resolution.

### 1. Restore missing Gen I core routing — accepted host/static

**Completed:** PureRGB's six distinct builds, then two Yellow Legacy builds and two Yellow Kaizo builds independently. All ten now uniquely select with coherent canonical base catalogs. [Acceptance](2026-10-03-gen1-core-acceptance.md) records 69 focused tests, three bounded source-bound runs, 20 related controls, and the exact limitations; no full-corpus or official standalone-ROM rerun occurred.

PureRGB required verified bank-local name/base/move consumers plus the actual compiled species-index and non-Dex exclusion chain. Yellow Legacy required helper-called bank authority; Yellow Kaizo required restart-copy geometry. Kaizo's extra TWISTER is independently proven compiled content, not a parser-count adjustment. Validators and family thresholds were not relaxed, and no project/name/hash profile was added.

Sources: [PureRGB](https://github.com/Vortyne/pureRGB), [Yellow Legacy](https://github.com/cRz-Shadows/Pokemon_Yellow_Legacy), and native compiled-layout fixtures for controls.

**Accepted boundary:** correct navigable species/name/type/stat joins and decoded reference closure, with preserved related Gen I controls. Optional graphics/text/maps and PureRGB alternate-form stats remain ledgered below; this does not certify runtime or SQLite reopen behavior. The subsequent Gen II stage is now accepted below; the next recommended investigation is Gen III, not another Gen I or full-corpus rerun.

### 2. Modern Gen II base-data and index contracts — accepted host/static

**Completed:** Polished Crystal + Crystal Inheritance, then Ambrosia and Sour Crystal independently. [Acceptance](2026-10-03-gen2-core-acceptance.md) records their complete canonical joins, independently resolved move details, 116 focused tests and SQLite/reference closure. Black & White 3 Genesis, Orange and Peridot remain unmatched under `LIB26-G2-SIBLINGS`; no shared identity/core contract was inferred from project names.

The inspected Polished Crystal source has abilities, EV yields, a variable-width TM/HM/tutor bitset, and an extended species/form representation. These are not the vanilla Crystal base-data and reference contracts. Determine which compiled consumers establish stride, count, index width, and banked pointers; do not force every derivative into one inferred layout. Static catalog support must not silently claim compatible SaveRAM or live-WRAM structures.

Sources: [Polished Crystal](https://github.com/rangi42/polishedcrystal), [Crystal Inheritance](https://github.com/dwg-and-dogs/PLC_Polished), [Ambrosia](https://github.com/AndrewC101/PokemonAmbrosia), [SourCrystal](https://github.com/SoupPotato/sourcrystal).

**Accepted boundary:** independently validated canonical species/name/stat/type and move joins, decoded reference closure and 15 complete SQLite reopen checks. Eleven selected related controls retain their data; three unmatched siblings are explicit. Official Gold/Silver/Crystal inputs were unavailable and not run. Maps, optional fields and live state remain separately ledgered/gated; the next investigation priority is Gen III, not an automatic new parser or Android run.

### 3. Gen III core ABI correctness — ROWE accepted host/static

**Completed at the bounded core boundary:** ROWE 2.1.9.1 Experimental and retained 2.1.1 independently resolve 1,025 canonical names/stats/types/sprites and complete ordinary level-up joins. [Acceptance](2026-10-05-gen3-wide-core-acceptance.md) records 313 affected parser/catalog tests, 40 CLI tests, a fresh 13-input cohort, all ten selected catalogs' exact SQLite reopening and unchanged related controls. Physical stat/name extents remain 2,301/2,302; move counts are independently proven ordinary acquisition minima, not full game bounds.

Bidirectionally coupled compiled name/map/default-stat consumers prove the widened 64-byte core and canonical native indices. Actual field/acquisition consumers independently prove aligned byte-target 20-byte and 56-byte move prefixes, distinct from the existing hybrid ABI. Nonordinary split rows remain unclassified and MOVE_DETAILS is PARTIAL; no project title/hash/fixed-root override, source count or relaxed threshold was added.

Voyager's two builds and Elite Redux remain unmatched under `LIB26-G3-SIBLINGS`, not inferred members of one ABI. Emerald Ex and Exceeded are preserved deficient controls, with no exact public-source match established by this audit. The next core investigation must be separately selected; this acceptance does not authorize an automatic new sibling or full-corpus run.

Sources: [ROWE](https://github.com/BelialClover/RoweSource), [newer ROWE structural oracle](https://github.com/BelialClover/RoweRepo/tree/e596e740cffbfb1d18e9bbd1ee2bd6a757f478c8), [Voyager](https://github.com/ghoulslash/pokevoyager), [Elite Redux](https://github.com/Elite-Redux/eliteredux). Neither ROWE source snapshot is exact-build authority.

**Accepted boundary:** coherent canonical core, ordinary level-up minimum/reference closure, canonical sprite rendering and exact SQLite persistence, preserving the 11 related controls. Official standalone Gen III controls were not retained/admitted and were not run. Experimental's old 528 tutor links are now explicitly NOT_FOUND rather than carried across an unvalidated expanded join; older machines, categories/full move domain/extensions, forms/runtime variants, evolution/Dex text, encounters/maps and numeric mechanics have named closure conditions below. No APK, live-memory or device acceptance is implied.

### 4. Finish reusable map consumers on working catalogs

**Pokescape World Map accepted:** [one target's exact compiled/static acceptance](2026-10-06-pokescape-world-map-acceptance.md) verifies the raster, 64 locations, native joins, complete references and SQLite equality while retaining all 520 Local Maps. It does not close missing optional text or imply a shared ABI.

Remaining source-backed candidates: **Heart and Soul, Soulgold, Tourmaline**. World Map remains missing on these three; Local Map is partial on the first two and absent on Tourmaline. Do not treat Pokescape's maps or Battle Theater's two maps as missing. Select and independently admit one target per round.

After those consumers are understood, check Static Yellow's two missing Local Maps and both Christmas Kaizo map gaps for shared Gen I patterns. Group candidates by compiled loading/rendering contracts, not project titles.

**Acceptance:** exact map IDs, geometry, raster assets, and cross-map references validate independently; malformed maps fail closed without destroying working catalog modules. Source-backed static tests and related-corpus regression are sufficient for this host stage; live Android testing remains a separate, explicitly authorized checkpoint.

### Lower-priority work and measurement cautions

Across the 301 selected composite rows, recomputing PARTIAL/AMBIGUOUS/NOT_FOUND cells gives: species 142, Dex text 166, Local Map 141, numeric ability mechanics 105, and World Map 70. These counts are **not missing-record totals** and exclude N/A. A nearly complete 151-species catalog and a severely deficient expanded catalog can both contribute one partial cell. Newly routed incomplete catalogs can increase these gap counts even though compatibility improved.

Therefore:

- Resolve broad base-catalog/routing gaps before polishing the already-99% Indigo cohort.
- Improve record completeness and maps before expensive per-engine numeric ability mechanics.
- Keep TCG/Pinball and dynamic Rogue run-state work outside this mainline expansion batch.
- Do not revive the previously dropped Rogue-only ability/move/acquisition tasks.
- Treat source-index “none found” entries as dated observations, not proof that a source never exists.
- Reuse this compact evidence for report corrections; scan changed ABI cohorts after implementation, not the entire library after every document edit.

## Follow-up ledger

`LIB26-G1-CORE` and `LIB26-G2-CORE` are accepted at their bounded host/static boundaries. The subsequent [October 4 relationship checkpoint](2026-10-04-gen2-relationships-acceptance.md) accepts seven Gen II evolution/level-up gains and validates all 19 selected Gen II catalogs, without a new full342 run. The current matrix is composite: 19 Gen II, 13 Gen III widened-core and one Pokescape map/move/Dex focused rows supersede the full342 baseline, while the other 309 row objects and their provenance remain unchanged. Gen II acceptance closes canonical/static joins and reference-safe SQLite persistence, not every optional feature. Remaining entries have named targets and explicit closure conditions; none is silently deferred or claimed complete.

| ID | Status | Named target | Closure condition |
|---|---|---|---|
| LIB26-G1-CORE | Accepted host/static | Six PureRGB, two Yellow Legacy, two Yellow Kaizo | Compiled-consumer-backed routing, correct canonical base joins/reference closure, and related Gen I controls; [evidence](2026-10-03-gen1-core-acceptance.md). |
| LIB26-G2-CORE | Accepted host/static | Polished Crystal / Inheritance; Ambrosia / Sour Crystal independently | [Four canonical core catalogs, reference closure, 15 SQLite write/reopen checks and bounded controls verified](2026-10-03-gen2-core-acceptance.md). |
| LIB26-G2-RELATIONSHIPS | Accepted host/static | Sour, both Crystal Clear builds, Crystal Legacy, Timeless, Kalos and Digimon; 19 selected Gen II controls | [Seven complete evolution/level-up gains, no regressions, reference closure and 19 exact SQLite reopen checks](2026-10-04-gen2-relationships-acceptance.md). |
| LIB26-G2-RELATIONSHIPS-REMAINING | Open | Ambrosia, Anniversary, Crystal Kaizo, Mystic, Gold 97, Silver 97, Polished and Inheritance | Independently prove compiled pointer/index/record contracts and canonical references; preserve the related cohort and verify SQLite reopening. Both datasets remain NOT_FOUND with zero records. |
| LIB26-G2-CLASSIC-OPTIONAL | Field-level closure only | Ambrosia / Sour | Sour evolution/level-up fields are accepted; Ambrosia relationships and separately missing packed categories/custom type presentation, type labels/charts, Sour sprite decoding and acquisition/Dex/maps remain open as applicable. |
| LIB26-G2-LINEAGE | Not implemented | Gold 97 / Silver 97 | Independently prove lineage/tutor applicability; preserve all 253 canonical joins and 104 type-chart records without a title/profile override. |
| LIB26-G2-SIBLINGS | Not implemented | Black & White 3 Genesis / current Orange / Peridot 2.3.0 | Separate complete identity/core/move/graphics contracts and exact-build reference-safe catalogs. |
| LIB26-G2-OFFICIAL-CONTROLS | Not run | Official Gold / Silver / Crystal | Retained, separately authorized official inputs and bounded host/static regression; no download or Android authorization implied. |
| LIB26-G2-OPTIONAL | Not implemented | Polished Crystal / Inheritance | Independently prove National Dex conversion, noncanonical forms, abilities, acquisition/text/graphics/maps, and save/live ABIs before exposing them; no inherited retail geometry or blanket modern ability N/A. |
| LIB26-G3-CORE | Accepted host/static | ROWE 2.1.9.1 Experimental / 2.1.1; 11 related controls | [1,025 canonical names/stats/sprites each, coherent ordinary level-up minimum, complete references and ten exact SQLite reopens](2026-10-05-gen3-wide-core-acceptance.md). |
| LIB26-G3-SIBLINGS | Open; unchanged unmatched controls | Voyager Battle Frontier Demo 1.1 / Voyager 0.3.6 / Elite Redux 2.65.3b | Separately prove exact compiled core/index/move consumers and coherent canonical catalogs; preserve the related ABI cohort and verify complete references/SQLite reopening. |
| LIB26-G3-ROWE-MOVE-DOMAIN | Open | Both ROWE builds; three Experimental and one older unclassified split rows | Independently prove the full move bound, nonordinary static category semantics and any extension fields before promoting them; ordinary minima 852/833 remain distinct from full game counts. |
| LIB26-G3-ROWE-FORMS | Open | Both ROWE builds; excluded aliases/forms and runtime stat variant | Complete alternate native-index/form/default-versus-runtime contracts, reference-safe materialization and appropriate static/runtime authority; no liveness inferred from names or stats. |
| LIB26-G3-ROWE-RELATIONSHIPS | Open | Both ROWE builds; zero evolution/Dex-description rows | Prove compiled evolution/Dex-text consumers and native/Dex joins against the canonical domain, with malformed/truncated cases, complete references and SQLite equality. |
| LIB26-G3-ROWE-TUTORS | Open; old Experimental links not retained | Experimental tutor join; older missing machine dataset | Exact list/compatibility/index/count consumers and all referenced move-domain authority, canonical joins and ambiguity/termination guards. Experimental remains NOT_FOUND (528 old links → 0); older has 3,039 reference-closed tutor links but zero machine links. |
| LIB26-G3-ROWE-OPTIONAL | Open | Both ROWE builds; encounter/maps, numeric ability mechanics and save/live structures | Independently prove each missing consumer/ABI; retain canonical core and fail-closed status. Static host references/persistence do not authorize Android or runtime-state acceptance. |
| LIB26-G3-OFFICIAL-CONTROLS | Not run | Official Ruby/Sapphire/Emerald/FireRed/LeafGreen | Retained, separately authorized exact inputs and bounded host/static regression; no download, device or release authority. |
| LIB26-G3-POKESCAPE-WORLD | Accepted host/static | Pokescape 1.0.4 World Map | [Exact 26,880-pixel raster, 64 native locations/116 encounter-base bindings, complete references and SQLite equality; working core/520 Local Maps unchanged](2026-10-06-pokescape-world-map-acceptance.md). |
| LIB26-G3-POKESCAPE-SECTION-DOMAIN | Explicit represented-domain limit | Pokescape's one encountered section outside the compiled static table | Independently prove a representing compiled consumer before exposing a name/location; absent representation is not authority to invent geometry or expand the table. |
| LIB26-G3-POKESCAPE-TEXT | Open | Pokescape's 214 unresolved required Local Map names, one encounter label and 1,097 POI text obligations | Independently prove static/contextual text consumers and native map/POI joins; preserve the raster/catalog and explicit applicability contract, with reference closure/SQLite equality. POI text covers 1,052/2,149, distinct from guide records with content. |
| LIB26-G3-POKESCAPE-MOVES | Accepted host/static | Pokescape's 828 named move details/categories | [Compiled packed-bitfield ABI, 5,796 independently checked scalar fields, 84,452 references and whole-catalog SQLite/baseline preservation](2026-10-07-pokescape-moves-acceptance.md). Packed flags remain opaque; no unsupported mechanics claim. |
| LIB26-G3-POKESCAPE-DEX | Accepted host/static | Pokescape's 1,235 native species/form description and dimension joins | [906 physical rows/905 positive rows; all published texts and 2,470 dimensions independently checked, 84,452 references and whole SQLite/packed-baseline equality](2026-10-07-pokescape-dex-acceptance.md). Raw category-prefix decoding is verified, not category persistence. |
| LIB26-G3-POKESCAPE-CATEGORIES | Accepted host/static; compact proof and cleanup closed | Pokescape's 1,235 persisted native species/form categories | [All categories/compiled aliases/native names independently checked across 906 physical cells/905 positive rows, 90,998 structural/localized references, whole SQLite and exact type-name baseline equality; 16 complete other sections unchanged](2026-10-07-pokescape-species-category-acceptance.md). SPECIES_CATEGORIES 1,235/1,235; score unchanged 87.50%. |
| LIB26-G3-POKESCAPE-MOVE-TEXT | Accepted host/static | Pokescape's 828 named move texts: 823 prose/five exact native placeholders | [All native texts/name joins independently verified, five consumer envelopes rechecked, 84,452 references and whole SQLite/exact Dex-baseline equality; 17 complete other sections unchanged](2026-10-07-pokescape-move-text-acceptance.md). No prose fabricated. |
| LIB26-G3-POKESCAPE-TYPE-NAME | Accepted host/static referenced-field boundary | Pokescape's 18 referenced native type names/semantic roles, including ID 18 | [All labels/roles, 828 native move-type joins, 2,470 preserved species-label joins, eight retained windows, 84,452 references and whole SQLite/exact move-text baseline independently verified; 15 complete other sections unchanged](2026-10-07-pokescape-type-name-acceptance.md). Requested prefix is not whole-game count authority; score remains 87.50%. |
| LIB26-G3-POKESCAPE-ABILITY-TEXT | Accepted host/static; publication/compact proof/cleanup closed | Pokescape's298 native ability names/descriptions and1,235 possible species joins/2,697 edges | [All299 physical cells/pointers/prose, complete local consumers,94,291 references, whole SQLite/independent digest and exact category-baseline reversal](2026-10-07-pokescape-ability-text-acceptance.md);299 physical rows/293 referenced minimum are not a global enum/mechanics count. |
| LIB26-G3-POKESCAPE-OPTIONAL | Open; next current-ROM work | Pokescape's numeric ability mechanics/typed modifiers and item names | Native text/joins accepted, not mechanics authority. [Active native mechanics contract](../../superpowers/plans/2026-10-07-pokescape-native-ability-mechanics-plan.md) starts from source-only role/packed-move diagnosis; native proof precedes implementation. Preserve all working datasets and verify complete references/SQLite equality. Item and required map text remain mandatory. |
| LIB26-MAPS | Recommended; separate one-target rounds | Heart and Soul, Soulgold, Tourmaline | Independently prove map consumers, valid assets/geometry/native references and bounded regressions; no shared ABI inferred from names or Pokescape acceptance. |
| LIB26-G1-OPTIONAL | Recommended | Six PureRGB builds; both Yellow Kaizo builds; Static Yellow and Christmas Kaizo variants | PureRGB sprites/Dex/World/Local datasets and Yellow/Christmas missing Dex/Local datasets resolved through independent compiled authority and failure isolation. |
| LIB26-G1-FORMS | Recommended | Six PureRGB builds, 13 separately handled non-Dex form IDs | Proven alternate base/index/type semantics and reference-safe materialization; no positional canonical-stat fallback. |

## Retention and privacy

The original library, old corpus, and new retained snapshot remain intact. The private snapshot retains full source/parser identities, compact delta capability/counter evidence, and the execution receipt so another parser run is not required to revise this report. Disposable inventory, duplicate delta inputs, raw reports, and catalog caches are reclaimed only through the reviewed transactional-cleanup ticket.

Published files contain public project filenames, alias archive basenames, aggregate counts, capability evidence, commit IDs, and execution digests. They contain no ROM bytes, decoded bulk tables or text, sprites, saves, trainer details, credentials, raw-memory data, or private filesystem paths. The frozen release canonical identity and localization-closure evidence are unchanged.
