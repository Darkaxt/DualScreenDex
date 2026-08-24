# Source-backed Gen III Checkpoint 3 Audit

**Result:** VALIDATED — the optional headerless unified ability ABI passes focused real-ROM, failure-isolation, cache, persistence, and eight-ROM matrix gates.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Compiled authority | The validated 260-byte species ABI supplies three u16 ability IDs at byte 24; a complete Thumb leaf independently proves a 28-byte ability stride and signed rating byte at 24 before the table is accepted | PASS |
| Generic resolution | Production resolution starts from compiled-reference-index targets, requires typed 17-byte names across the species-referenced ID domain, and accepts exactly one eligible root | PASS |
| No ROM identity | Production code contains no project names, filenames, hashes, fixed table roots, allowlists, or per-ROM profiles | PASS |
| Materialization | Dreamstone and Crippling both materialize Bulbasaur ability IDs `[65, 34]`, 310 named abilities, descriptions, signed AI ratings, and seven-bit flags | PASS |
| Independent failure isolation | Corrupting 80 Dreamstone description pointers withholds only description metadata; ability names/relationships and mechanics remain available, and family routing/startup remain accepted | PASS |
| Ambiguity rejection | Zero or multiple eligible compiled roots return no headerless ability module; the enclosing species family remains selectable | PASS |
| Cache invalidation | Parser schema 39 rebuilds prior caches once; all `catalog-store` tests pass | PASS |
| Existing behavior | Local/World/Atlas, scene, fog, POI, persistence contracts, API, UI, navigation, and runtime-memory behavior are unchanged | PASS |

## Validation evidence

- Four focused real-ROM tests passed:
  - Dreamstone: 311-row table shape, 310 materialized abilities/descriptions/mechanics, species joins, known rating and flag values.
  - Crippling: independent compiled root with the same generic ABI and materialized results.
  - Mutated Dreamstone: description failure disables only `ABILITY_DESCRIPTIONS`.
- All `catalog-store` tests passed with parser schema 39.
- Fresh one-job eight-ROM matrix:
  - 8 evaluated;
  - 6 selected and persisted/reopened through SQLite;
  - 2 retained no-family-match;
  - 0 parser errors;
  - 0 persistence errors;
  - 0 decoded cross-reference errors.
- Dreamstone improved from 17/24 at 70.81% to 20/24 at 83.31%, with 310 abilities, 310 ability descriptions, and 310 ability-mechanics records.
- Battle Theater, Celia, Elite Redux, GS Chronicles, Pokescape, Tourmaline, and Voyager retained their routing, feature counts, compatibility scores, and materialized counts.
- No APK installation, launch, ADB, emulator, or gameplay action occurred.

## Remaining deferrals

`G3-DREAM-001` now covers only egg/machine moves, balls, and the bounded Local-raster overage. Other Stage 2 family deferrals remain unchanged; every unsupported optional module continues to fail closed independently.
