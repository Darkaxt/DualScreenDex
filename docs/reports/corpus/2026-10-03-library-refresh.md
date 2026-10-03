# October 3, 2026 ROM-library refresh and compatibility priorities

## Decision and scope

The GB/GBC/GBA Pokémon hack library has changed enough to warrant a new corpus snapshot. The current snapshot contains **342 distinct in-scope ROM identities**. The old corpus and its release evidence remain preserved; this refresh neither changes the production parser nor publishes a new APK.

- [Current 342-ROM compatibility matrix](2026-10-03-library-compatibility.md)
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

Only the **46 new or changed identities** were parsed. Their schema-16 report and execution receipt bind source commit `a306bffd64dfb50d81076a91657bb76c1f8e9617`, generator digest, report digest, and input count. Their exact normalized identity multiset was checked against the staged delta. The run returned **34 selected, 0 ambiguous, 12 unmatched, 0 parser errors**, with **0 catalog errors and 0 persistence errors**.

The other **296 identities** inherit the published September 14 rows after byte-identity verification. Parser, catalog, and coverage-calculation code remained unchanged. Identical old alias rows were checked for agreement before deduplication. Inherited capability confidence retains the old published integer rounding; inherited coverage retains two decimal places. This is deliberately a **composite snapshot**, not a fresh full-corpus run.

| Current snapshot outcome | Count |
|---|---:|
| Inputs | 342 |
| Selected family and materialized catalog | 286 |
| Ambiguous family | 2 |
| No family match | 54 |
| Parser errors | 0 |
| Resolved ROM-language manifests | 271 |
| Unknown ROM-language manifests | 15 |
| Proven multilingual manifests | 0 |

The routing rate is 286/342, not a claim that all 286 have complete Pokédex data or maps. The change from 276/333 in the historical release benchmark reflects changed inputs and deduplication, **not a parser compatibility gain**.

N/A capabilities remain outside coverage denominators. Capability confidence and record-based coverage are different measures; neither family recognition nor a high confidence cell proves complete runtime behavior.

### What the changed builds reveal

| Changed group | Current evidence | Interpretation |
|---|---|---|
| Intense Indigo / IndigoLite, 16 variants | All select Red/Blue; 99.34–99.50% coverage | Broadly working; small stats/sprite gaps are lower priority than wholly unmatched families. |
| PureRGB, 6 variants | All unmatched | Highest-count coherent newly unsupported source-backed project. |
| Yellow Kaizo, 2 variants | Both unmatched | Include in the bounded Yellow routing investigation, without assuming the same cause as PureRGB. |
| Polished Crystal, Ambrosia, Crystal Inheritance, Sour Crystal | Four distinct identities, all unmatched | Investigate modern Gen II compiled-core changes; Faithful alias is not a fifth test. |
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

### 1. Restore missing Gen I core routing

**First target: PureRGB's six distinct builds**, followed by the two already-unmatched Yellow Legacy builds and the two new Yellow Kaizo builds. This gives a bounded ten-input investigation set, not a claim that one change fixes all ten.

The local PureRGB source explicitly changes Pokédex indexing to include MissingNo at index zero. That is a concrete reason to inspect identity/index consumers and cross-table invariants rather than merely relaxing a header or name gate. It is not yet proof of the compiled ROM's failing gate. Yellow Legacy has an available source oracle; Yellow Kaizo requires its own compiled evidence and patch provenance.

Sources: [PureRGB](https://github.com/Vortyne/pureRGB), [Yellow Legacy](https://github.com/cRz-Shadows/Pokemon_Yellow_Legacy), and official Yellow consumers for controls.

**Acceptance:** uniquely route each supported ABI through compiled consumers; materialize consistent names, species IDs, types, stats, and references; preserve official Red/Blue/Yellow and the working Indigo variants; keep genuinely incompatible candidates unmatched.

### 2. Modern Gen II base-data and index contracts

Start with **Polished Crystal + Crystal Inheritance**, then Ambrosia and Sour Crystal. All four refreshed identities are currently unmatched. Their locally available sources make this preferable to blind binary-only fishing. Black & White 3 Genesis, Orange, and Peridot are three additional unchanged source-backed unmatched inputs to check for genuinely shared contracts.

The inspected Polished Crystal source has abilities, EV yields, a variable-width TM/HM/tutor bitset, and an extended species/form representation. These are not the vanilla Crystal base-data and reference contracts. Determine which compiled consumers establish stride, count, index width, and banked pointers; do not force every derivative into one inferred layout. Static catalog support must not silently claim compatible SaveRAM or live-WRAM structures.

Sources: [Polished Crystal](https://github.com/rangi42/polishedcrystal), [Crystal Inheritance](https://github.com/dwg-and-dogs/PLC_Polished), [Ambrosia](https://github.com/AndrewC101/PokemonAmbrosia), [SourCrystal](https://github.com/SoupPotato/sourcrystal).

**Acceptance:** restore independently validated base catalogs for matching ABI cohorts, close species/move references, and regression-check official Gold/Silver/Crystal plus already-supported Crystal-family hacks. Maps and live state remain separately gated.

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

Across the 286 selected rows, the counts of PARTIAL/AMBIGUOUS/NOT_FOUND cells are: species 178, Dex text 155, Local Map 128, numeric ability mechanics 102, and World Map 61. These counts are **not missing-record totals** and exclude N/A. A nearly complete 151-species catalog and a severely deficient expanded catalog can both contribute one partial cell.

Therefore:

- Resolve broad base-catalog/routing gaps before polishing the already-99% Indigo cohort.
- Improve record completeness and maps before expensive per-engine numeric ability mechanics.
- Keep TCG/Pinball and dynamic Rogue run-state work outside this mainline expansion batch.
- Do not revive the previously dropped Rogue-only ability/move/acquisition tasks.
- Treat source-index “none found” entries as dated observations, not proof that a source never exists.
- Reuse this compact evidence for report corrections; scan changed ABI cohorts after implementation, not the entire library after every document edit.

## Follow-up ledger

All entries are **recommended / not implemented**. Each closes only with the named target and acceptance condition above.

| ID | Named target | Closure condition |
|---|---|---|
| LIB26-G1-CORE | PureRGB first; Yellow Legacy and Yellow Kaizo independently | Compiled-consumer-backed routing and coherent base catalog, plus Gen I controls. |
| LIB26-G2-CORE | Polished Crystal / Inheritance first; Ambrosia / Sour Crystal separately | Verified modern Gen II record/index contracts and reference-safe persistence. |
| LIB26-G3-CORE | ROWE first; Voyager and Elite Redux separately | Usable expanded species/stat/type/acquisition catalogs without wrong-stride fallback. |
| LIB26-MAPS | Heart and Soul, Soulgold, Pokescape, Tourmaline | Map-consumer proof, valid assets/references, and related-cohort regression. |
| LIB26-G1-OPTIONAL | Static Yellow and Christmas Kaizo variants | Missing Dex/Local datasets resolved where compiled evidence supports them, with independent failure isolation. |

## Retention and privacy

The original library, old corpus, and new retained snapshot remain intact. The private snapshot retains full source/parser identities, compact delta capability/counter evidence, and the execution receipt so another parser run is not required to revise this report. Disposable inventory, duplicate delta inputs, raw reports, and catalog caches are reclaimed only through the reviewed transactional-cleanup ticket.

Published files contain public project filenames, alias archive basenames, aggregate counts, capability evidence, commit IDs, and execution digests. They contain no ROM bytes, decoded bulk tables or text, sprites, saves, trainer details, credentials, raw-memory data, or private filesystem paths. The frozen release canonical identity and localization-closure evidence are unchanged.
