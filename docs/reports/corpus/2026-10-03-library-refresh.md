# October 3, 2026 ROM-library refresh and compatibility priorities

## Decision and scope

The GB/GBC/GBA Pokémon hack library has changed enough to warrant a new corpus snapshot. The current snapshot contains **342 distinct in-scope ROM identities**. The old corpus and its release evidence remain preserved. The inventory refresh itself did not change the production parser; the [Gen I expansion](2026-10-03-gen1-core-acceptance.md) adds ten selected catalogs and the [Gen II expansion](2026-10-03-gen2-core-acceptance.md) adds four. Neither step publishes a new APK.

- [Current 342-ROM compatibility matrix](2026-10-03-library-compatibility.md)
- [October 4 Gen II relationship acceptance: seven gains, 19 checked catalogs](2026-10-04-gen2-relationships-acceptance.md)
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

| Current snapshot outcome | Count |
|---|---:|
| Inputs | 342 |
| Selected family and materialized catalog | 300 |
| Ambiguous family | 2 |
| No family match | 40 |
| Parser errors | 0 |
| Resolved ROM-language manifests | 285 |
| Unknown ROM-language manifests | 15 |
| Proven multilingual manifests | 0 |

The original inventory-only routing rate was 286/342, versus 276/333 in the historical release benchmark; that denominator/input change was not a parser gain. The subsequent **286 → 296 → 300 on the same 342 identities** is a verified ten-catalog Gen I gain plus four Gen II catalogs. Routing still does not imply complete optional data or maps.

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
| Heart and Soul 2.0.6 / Soulgold 1.1.4 | 80.92% / 79.40%; World Map missing, Local Map partial | Useful source-backed map follow-ups with functioning base catalogs. |
| FireRed Reignited / Leafgreen Regrown | Both select; 95.33% | Lower urgency; numeric ability proof is not a prerequisite for otherwise usable catalogs. |
| Saiph 2, 4 modes | All select; 95.71% | Keep all four distinct binaries as regression controls. |
| Crystal Advance Redux | Selects; 82.58%; move text, machines, and World Map missing | Secondary structural follow-up; no exact public source match established by this audit. |
| Phoenix Red | Selects; 95.22% | Lower urgency. |
| Emerald Ex / Exceeded | Select; 54.17% / 54.16% | Names or acquisition/text datasets remain absent; routing alone is insufficient. |
| ROWE 2.1.9.1 Experimental | Selects; 41.29%; base stats, learnsets, encounters, and Local Map absent | Urgent Gen III core-layout target before cosmetic or numeric-mechanics work. |
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

### 3. Gen III core ABI correctness, led by ROWE

Prioritize **ROWE 2.1.9.1 Experimental and the retained unmatched 2.1.1**, then source-backed Voyager's two builds and Elite Redux. These five inputs are a high-value oracle set, not necessarily one engine ABI.

ROWE's fresh materialized catalog has only 204 species and **zero species with base stats**, despite a selected Emerald family. Its local source declares expanded species and a `BaseStats` structure with widened experience/ability fields. Derive alignment and stride from compiled accesses, not stale offset comments or familiar struct sizes. This is a more meaningful improvement than raising an aggregate percentage with optional text or numeric ability proofs.

Emerald Ex and Exceeded are secondary sibling checks only if compiled evidence proves that a generic resolver change applies. No exact public-source match for those current binaries was established here.

Sources: [ROWE](https://github.com/BelialClover/RoweSource), [Voyager](https://github.com/ghoulslash/pokevoyager), [Elite Redux](https://github.com/Elite-Redux/eliteredux).

**Acceptance:** supported core layouts must produce coherent names, stats, types, learnsets, and cross-references, with no wrong-stride fallback. Preserve Battle Theater, Heart and Soul, Soulgold, Modern Emerald, and official Gen III controls across their distinct ABIs. Selection alone is not closure.

### 4. Finish reusable map consumers on working catalogs

First source-backed set: **Heart and Soul, Soulgold, Pokescape, Tourmaline**. World Map is missing on all four; Local Map is partial on the first two, available on Pokescape, and absent on Tourmaline. Do not reuse an old plan that treats Pokescape's Local Map or Battle Theater's two maps as still missing.

After those consumers are understood, check Static Yellow's two missing Local Maps and both Christmas Kaizo map gaps for shared Gen I patterns. Group candidates by compiled loading/rendering contracts, not project titles.

**Acceptance:** exact map IDs, geometry, raster assets, and cross-map references validate independently; malformed maps fail closed without destroying working catalog modules. Source-backed static tests and related-corpus regression are sufficient for this host stage; live Android testing remains a separate, explicitly authorized checkpoint.

### Lower-priority work and measurement cautions

Across the 300 selected rows, the counts of PARTIAL/AMBIGUOUS/NOT_FOUND cells are: species 179, Dex text 166, Local Map 140, numeric ability mechanics 104, and World Map 70. These counts are **not missing-record totals** and exclude N/A. A nearly complete 151-species catalog and a severely deficient expanded catalog can both contribute one partial cell. Newly routed incomplete catalogs can increase these gap counts even though compatibility improved.

Therefore:

- Resolve broad base-catalog/routing gaps before polishing the already-99% Indigo cohort.
- Improve record completeness and maps before expensive per-engine numeric ability mechanics.
- Keep TCG/Pinball and dynamic Rogue run-state work outside this mainline expansion batch.
- Do not revive the previously dropped Rogue-only ability/move/acquisition tasks.
- Treat source-index “none found” entries as dated observations, not proof that a source never exists.
- Reuse this compact evidence for report corrections; scan changed ABI cohorts after implementation, not the entire library after every document edit.

## Follow-up ledger

`LIB26-G1-CORE` and `LIB26-G2-CORE` are accepted at their bounded host/static boundaries. The subsequent [October 4 relationship checkpoint](2026-10-04-gen2-relationships-acceptance.md) accepts seven Gen II evolution/level-up gains and validates all 19 selected Gen II catalogs, without a new full342 run. The current matrix is composite: these 19 rows supersede the full342 baseline, while the other 323 row objects and their provenance remain unchanged. Gen II acceptance closes canonical/static joins and reference-safe SQLite persistence, not every optional feature. Remaining entries have named targets and explicit closure conditions; none is silently deferred or claimed complete.

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
| LIB26-G3-CORE | Recommended | ROWE first; Voyager and Elite Redux separately | Usable expanded species/stat/type/acquisition catalogs without wrong-stride fallback. |
| LIB26-MAPS | Recommended | Heart and Soul, Soulgold, Pokescape, Tourmaline | Map-consumer proof, valid assets/references, and related-cohort regression. |
| LIB26-G1-OPTIONAL | Recommended | Six PureRGB builds; both Yellow Kaizo builds; Static Yellow and Christmas Kaizo variants | PureRGB sprites/Dex/World/Local datasets and Yellow/Christmas missing Dex/Local datasets resolved through independent compiled authority and failure isolation. |
| LIB26-G1-FORMS | Recommended | Six PureRGB builds, 13 separately handled non-Dex form IDs | Proven alternate base/index/type semantics and reference-safe materialization; no positional canonical-stat fallback. |

## Retention and privacy

The original library, old corpus, and new retained snapshot remain intact. The private snapshot retains full source/parser identities, compact delta capability/counter evidence, and the execution receipt so another parser run is not required to revise this report. Disposable inventory, duplicate delta inputs, raw reports, and catalog caches are reclaimed only through the reviewed transactional-cleanup ticket.

Published files contain public project filenames, alias archive basenames, aggregate counts, capability evidence, commit IDs, and execution digests. They contain no ROM bytes, decoded bulk tables or text, sprites, saves, trainer details, credentials, raw-memory data, or private filesystem paths. The frozen release canonical identity and localization-closure evidence are unchanged.
