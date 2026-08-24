# Source-backed Gen III Checkpoint 1 Audit

Specification: `docs/superpowers/specs/2026-08-24-source-backed-gen3-compatibility-design.md`

**Result:** READY FOR PROTECTED PUBLICATION — the integrated `NatureInfo` checkpoint passes its focused implementation, regression, persistence, and eight-ROM matrix gates. Protected signing evidence is pending.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Compiled authority | Candidate roots come only from the shared GBA reference index; production code contains no ROM names, filenames, hashes, fixed roots, profiles, or allowlists | PASS |
| Integrated table ABI | Requires one aligned 25×20-byte table, valid distinct ROM-native names, name pointers at byte 0, stat IDs at bytes 4/5, and the exact canonical 5×5 matrix | PASS |
| Compiled consumer | A complete Thumb function referencing the root must prove 20-byte indexing, paired unsigned byte loads at offsets 4/5, field comparisons, multiplication/division, and 90/100/110 constants | PASS |
| Existing ABI retained | Emerald, FireRed, Modern, Classic, Unbound, and Odyssey retain their separate modifier/name/flavor table results | PASS |
| ABI ambiguity | Multiple integrated roots or a simultaneous integrated and separate proof return `NatureResolution.Ambiguous`; zero integrated roots preserve the previous result | PASS |
| Model/API isolation | Integrated rows reuse `NatureCatalog` and `NatureRecord`; flavor remains explicitly unavailable; no persistence, API, UI, map, runtime, or navigation contract changed | PASS |
| Failure isolation | Nature resolution remains an optional catalog capability; rejected evidence cannot suppress family routing, accepted datasets, Local/World maps, Local-first scenes, Atlas fallback, or app startup | PASS |
| Cache invalidation | Parser schema 37 performs one intentional rebuild; complete catalogs and normalized World/Local map sections round-trip with the new schema | PASS |
| Exact controls | Battle Theater 2.3.0 and Dreamstone resolve the independently observed integrated roots, 25 unique names, canonical modifier rows, 110/90 scaling, and null flavor evidence | PASS |
| Source/discovery discipline | Battle Theater's exact full-source tag anchors layout interpretation; the HackDex server-action snapshot supplies only CFRU/pokeemerald-expansion discovery metadata and never production authority | PASS |

## Focused automated evidence

- Nature resolver gate: four test methods passed, covering integrated Battle Theater and Dreamstone plus official Emerald/FireRed, Modern, Classic, Unbound, and Odyssey.
- Catalog persistence gate: the initial 25-test run exposed only two stale schema-36 assertions; the other 23 tests passed. Both assertions were updated to schema 37 and their exact round-trip tests passed on rerun.
- Eight-ROM parser matrix, one job:
  - 8 evaluated;
  - 6 selected and persisted/reopened through SQLite;
  - 2 retained their pre-existing fail-closed no-family-match result;
  - 0 parser errors;
  - 0 persistence errors;
  - 0 decoded cross-reference errors.
- Battle Theater improved from 22/23 at 95.63% to 23/23 at 99.97%. The remaining 0.03% is explicitly unavailable flavor affinity, not an unresolved expected capability.
- Dreamstone improved from 14/24 at 58.31% to 15/24 at 62.48%.
- Celia, Elite Redux, GS Chronicles, Pokescape, Tourmaline, and Voyager retained their previous routing, resolved-feature counts, and compatibility scores.
- No ADB, emulator, APK installation, launch, or gameplay interaction was performed.

## Deferral ledger

| ID | Missing behavior | Safe fallback | Acceptance condition |
|---|---|---|---|
| `G3-NATURE-001` | Flavor affinity for the integrated 20-byte `NatureInfo` ABI is not proven by the selected fields or compiled stat consumer | Publish all 25 names and stat effects with `flavorModifiers = null`; Nature detail omits unsupported flavor claims | A generic compiled consumer or structurally referenced table proves the ROM-native five-flavor mapping across the exact source-backed control and an independent sibling |
| `G3-CELIA-001` | Remaining Celia partial/unavailable domains | Preserve every accepted capability and skip only unsupported records/maps | Source-backed generic ABIs resolve the affected domains without FireRed regressions |
| `G3-DREAM-001` | Dreamstone non-Nature gaps, including the bounded Local-raster overage | Preserve accepted catalog/World-map modules; keep unsupported modules unavailable | Generic consumers resolve missing data and Local maps use a bounded structural representation without merely raising the cap |
| `G3-ELITE-001` | Elite Redux exact-release lineage and family routing | No family match; no speculative catalog | Exact or independently proven compiled lineage passes generic anchors and real-ROM controls |
| `G3-GSC-001` | GS Chronicles missing catalog domains | Preserve its 20 accepted capabilities | Compiled authority resolves each domain; engine-source labels alone are insufficient |
| `G3-POKESCAPE-001` | Pokescape semantic region-entry join and remaining catalog gaps | Preserve Local maps and accepted datasets; World map stays unavailable | A unique generic compiled region-entry consumer passes affected and Emerald sibling controls |
| `G3-TOURMALINE-001` | Tourmaline encounter/map-group authority and remaining catalog gaps | Map modules and unsupported datasets remain unavailable | Generic compiled encounter/map consumers establish bounded semantic joins |
| `G3-VOYAGER-001` | Voyager matching complete source lineage and family routing | No family match; no speculative catalog | Complete matching source or independent compiled-engine proof passes generic anchors and held-out controls |

## Publication gate

Before tagging, fetch and reconcile `fork/master`, inspect overlapping paths, and rerun only overlap-relevant tests. RC57 must then pass the repository's protected release workflow, persistent signer check, checksums, package/version validation, APK Signature Scheme v3 verification, and provenance verification. Publication evidence will replace this pending section after independent artifact download.
