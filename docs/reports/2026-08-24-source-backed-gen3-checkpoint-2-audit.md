# Source-backed Gen III Checkpoint 2 Audit

**Result:** PUBLISHED — the optional headerless unified `SpeciesInfo` presentation ABI passes focused real-ROM, failure-isolation, parser, materialization, persistence, eight-ROM matrix, protected release, and independent public-artifact gates.

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

## Published candidate

- Annotated tag [`v1.1.0-rc.58`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.58) peels to exact source commit `7a6083cb29c7bdd8fbdd1d72e7ff0007d697a4c6`.
- Protected workflow [`32773171422`](https://github.com/Darkaxt/DualScreenDex/actions/runs/32773171422) completed both `verify-and-build` and `sign-and-publish` successfully against that commit.
- Public APK `DualDex-v1.1.0-rc.58.apk` is 17,744,358 bytes with SHA-256 `f0c82fcaf5c3f58bd7799293c23ff17c74a7d57a4bb2a19d3b4419b5201855b3`.
- All 27 `SHA256SUMS.txt` payload entries verify; they cover every release asset other than the checksum manifest itself. Authenticated and anonymous APK downloads are byte-identical.
- The APK declares `com.darkaxt.dualdex`, version `1.1.0-rc.58`, and version code `1010058`.
- Independent `apksigner` verification accepts APK Signature Scheme v3 with one signer. Its certificate SHA-256 is `c5a02cecb47cda41b618817ea684cbb6ccfdcc17a3e7d8243448175c8e3b2fba`, matching both the published PEM and pinned release identity.
- `provenance.json` binds the repository, exact commit, tag, workflow run, release kind, package/version identity, APK hash, certificate hash, and protected `release-signing` authority.
- No APK installation, launch, ADB, emulator, or gameplay action was used as a publication gate.
