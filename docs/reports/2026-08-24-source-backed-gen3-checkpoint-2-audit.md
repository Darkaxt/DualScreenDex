# Source-backed Gen III Checkpoint 2 Audit

**Result:** VERIFIED FOR PROTECTED PUBLICATION — the optional headerless unified `SpeciesInfo` presentation ABI passes focused real-ROM, failure-isolation, parser, materialization, persistence, and eight-ROM matrix gates.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Compiled authority | The existing complete name accessor and six-stat leaf consumer uniquely select the 260-byte root, stride, count, and active-row predicate before presentation fields are considered | PASS |
| Generic presentation ABI | Source-backed field order and compiled rows establish category at `nameOffset - 13`, a four-byte-aligned description tail, height/weight immediately before it, front graphics at `description + 12`, and palette at `description + 20` | PASS |
| No ROM identity | Production code contains no project names, filenames, hashes, fixed table roots, allowlists, or per-ROM profiles | PASS |
| Description validation | Every active Dreamstone and Crippling row has a valid inline category and bounded terminated GBA-text description pointer; publication requires at least 80% active-row coverage | PASS |
| Sprite validation | Every active row has bounded 2,048-byte-frame graphics and a structurally valid BGR555 palette; compressed, short compressed, and raw palettes are handled without interpreting compressed bytes as colors | PASS |
| Independent failure isolation | Corrupting 400 Dreamstone description pointers drops description coverage below 80% and removes only the description table/metadata; names, stats, sprites, routing, and startup remain accepted | PASS |
| Typed retail isolation | Headerless 260-byte rows bypass the retail 32/36-byte description codec and retail pointer/size sprite validator; official and other non-headerless paths are unchanged | PASS |
| Materialization | Dreamstone and Crippling materialize Bulbasaur's ROM-native description, dimensions, and 64×64 palette-colored sprite; direct unified rows join by internal species ID | PASS |
| Cache invalidation | Parser schema 38 intentionally rebuilds cached catalogs once; all catalog-store round-trip tests pass | PASS |
| Maps and UI | No Local/World/Atlas, scene, fog, POI, persistence contract, API, UI, navigation, or runtime-memory behavior changed | PASS |

## Validation evidence

- Focused live controls passed:
  - Dreamstone: 1,522/1,522 active descriptions and sprites; sparse internal IDs preserved.
  - Crippling: 1,525/1,525 active descriptions and sprites; the alternate compiled accessor layout resolves the same pointer-aligned presentation tail.
  - Mutated Dreamstone: descriptions fail closed while sprites remain available.
- `SpriteMaterializerTest` passed, preserving raw palettes whose first byte resembles a compression header.
- All `CatalogStoreTest` methods passed with parser schema 38.
- The parser CLI and its tests passed.
- Fresh one-job eight-ROM matrix:
  - 8 evaluated;
  - 6 selected and persisted/reopened through SQLite;
  - 2 retained no-family-match;
  - 0 parser errors;
  - 0 persistence errors;
  - 0 decoded cross-reference errors.
- Dreamstone improved from 15/24 at 62.48% to 17/24 at 70.81%, with 1,522 sprites and 1,522 Pokédex descriptions.
- Battle Theater, Celia, Elite Redux, GS Chronicles, Pokescape, Tourmaline, and Voyager retained their prior routing, feature counts, and compatibility scores.
- No APK installation, launch, ADB, emulator, or gameplay action occurred.

## Remaining deferrals

`G3-DREAM-001` now covers only egg/machine moves, abilities, balls, and the bounded Local-raster overage. The other Stage 2 family deferrals remain listed in `2026-08-24-source-backed-gen3-family-audit.md`; each unsupported module continues to fail closed independently.

Protected publication is the next gate. Release artifact hashes, signer identity, tag provenance, and workflow evidence will be appended only after the protected workflow completes.
