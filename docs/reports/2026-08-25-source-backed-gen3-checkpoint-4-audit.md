# Source-backed Gen III Checkpoint 4 Audit

**Result:** PUBLISHED — compiled-authorized headerless teachable and egg lists pass focused real-ROM, independent-control, failure-isolation, cache, persistence, eight-ROM matrix, protected release, and independent public-artifact gates.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Compiled authority | Complete Thumb getters prove the selected species root/bound, `speciesId * 260`, active-row test, row-zero null fallback, pointer field, and return before a list is accepted | PASS |
| Generic resolution | Production resolution uses the compiled-reference index plus relative fields inside the already validated species ABI; it contains no ROM identity, filename, hash, absolute root, allowlist, or profile | PASS |
| Independent fields | Teachable byte 152 and egg byte 156 each require their own complete getter and bounded u16/`0xFFFF` list closure | PASS |
| Materialization | Existing generation-neutral `MoveAcquisition` contracts publish teachable lists as `MACHINE` and egg lists as `EGG`; duplicates per species are removed | PASS |
| Tutor preservation | Dreamstone retains 4,247 links from the independently accepted generic tutor resolver; embedded teachable data does not suppress it | PASS |
| Referenced move closure | Compiled relationships may introduce IDs beyond currently named move rows; the existing closure creates explicit unnamed ROM-referenced records rather than dropping valid links | PASS |
| Failure isolation | Corrupting one Dreamstone teachable pointer withholds only machines; corrupting one egg pointer withholds only eggs. Tutor, abilities, routing, startup, and the sibling field remain available | PASS |
| Cache invalidation | Parser schema 40 rebuilds prior caches once; all `catalog-store` tests pass | PASS |
| Existing behavior | Local/World/Atlas, scene, fog, POI, persistence, API, UI, navigation, and runtime-memory behavior are unchanged | PASS |

## Validation evidence

- Focused real-ROM controls passed:
  - Dreamstone: 1,489 species with 47,108 distinct teachable links across 134 move IDs, plus 487 species with 4,321 egg links across 433 move IDs.
  - Crippling: independent root/getter forms; 1,489 species with 88,104 teachable links across 362 move IDs, plus 503 species with 4,671 egg links across 458 move IDs.
  - Malformed Dreamstone: each invalid pointer disables only its own acquisition module.
- Fresh one-job eight-ROM matrix:
  - 8 evaluated;
  - 6 selected and persisted/reopened through SQLite;
  - 2 retained no-family-match;
  - 0 parser errors;
  - 0 persistence errors;
  - 0 decoded cross-reference errors.
- Dreamstone improved from 20/24 at 83.31% to 22/24 at 91.64%.
- Battle Theater, Celia, Elite Redux, GS Chronicles, Pokescape, Tourmaline, and Voyager retained their routing, feature counts, compatibility scores, and prior materialized counts.
- No APK installation, launch, ADB, emulator, or gameplay action occurred.

## Remaining deferrals

`G3-DREAM-001` now covers only balls and the bounded Local-raster overage. Other Stage 2 family deferrals remain unchanged; every unsupported optional module continues to fail closed independently.

## Published candidate

- Annotated tag [`v1.1.0-rc.60`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.60) peels to exact source commit `b1bae3fcfacd5e875ada7b7e93fd3e9c5c221ace`.
- Protected workflow [`32784097897`](https://github.com/Darkaxt/DualScreenDex/actions/runs/32784097897) completed both `verify-and-build` and `sign-and-publish` successfully against that commit.
- Public APK `DualDex-v1.1.0-rc.60.apk` is 17,768,934 bytes with SHA-256 `18d9eddd367f5457c48e0c7e247b4d8eaa1f20b5c792f13c0841d262e98940db`.
- All 27 checksum entries verify. Authenticated and anonymous APK downloads are byte-identical.
- The APK declares `com.darkaxt.dualdex`, version `1.1.0-rc.60`, and version code `1010060`.
- Independent `apksigner` verification accepts APK Signature Scheme v3 with one signer. Its certificate SHA-256 is `c5a02cecb47cda41b618817ea684cbb6ccfdcc17a3e7d8243448175c8e3b2fba`, matching the published PEM and pinned release identity.
- `provenance.json` exactly binds the repository, tagged commit, workflow run, release kind, package/version identity, APK hash, certificate hash, and protected `release-signing` authority.
- No APK installation, launch, ADB, emulator, or gameplay action was used as a publication gate.
