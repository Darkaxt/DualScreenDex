# Source-backed Gen III Checkpoint 5 Audit

**Result:** PUBLISHED — compiled-authorized expanded split capture balls pass focused real-ROM, independent-control, failure-isolation, cache, persistence, and eight-ROM matrix gates. The protected RC61 candidate and its public artifacts were independently verified.

## Requirement audit

| Requirement | Evidence | Result |
|---|---|---|
| Compiled authority | Complete Thumb consumers prove separate graphics/palette roots and eight-byte indexing; complete bounded item getters prove 80-byte records, item extent, secondary ID, pocket, and type fields | PASS |
| Generic resolution | Production uses compiled-reference targets, relative fields, consecutive tag closure, and a unique per-ROM relationship; no identity, filename, hash, fixed root, allowlist, or profile participates | PASS |
| Item mapping | Dreamstone and Crippling each produce one unique contiguous ball-index inversion despite different item bounds and pocket values | PASS |
| Failure isolation | Each asset row decodes independently; the 30-byte Strange Ball palette withholds only that sprite while 27 siblings remain available | PASS |
| Existing paths | Standard 12-ball and integrated 44-byte materializers retain their prior behavior and controls | PASS |
| Cache invalidation | Parser schema 41 rebuilds prior catalogs once; `catalog-store` tests pass | PASS |
| Unrelated behavior | Local/World/Atlas, scene, fog, POI, persistence, API, UI, navigation, abilities, and acquisitions are unchanged | PASS |

## Validation evidence

- Focused real-ROM controls:
  - Dreamstone: 28 mapped item relationships and 27 decoded 16×16 sprites; item 828's malformed Strange Ball palette fails alone.
  - Crippling: independent roots, item bound, and pocket value; the same 28/27 result.
- Existing standard split ball tests pass.
- Fresh one-job eight-ROM matrix:
  - 8 evaluated;
  - 6 selected and persisted/reopened through SQLite;
  - 2 retained no-family-match;
  - 0 parser errors;
  - 0 persistence errors;
  - 0 decoded cross-reference errors.
- Dreamstone improves from 22/24 at 91.64% to 23/24 at 95.81%.
- The other seven controls retain routing, feature counts, compatibility scores, and prior materialized counts.
- No APK installation, launch, ADB, emulator, or gameplay action occurred.

## Published candidate

- Release: [`v1.1.0-rc.61`](https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.61), published by protected workflow run `32789778391` from annotated source commit `77e76255b975765b2814d96299d1a7834c5bb665`.
- APK: `DualDex-v1.1.0-rc.61.apk`, 17,781,222 bytes, SHA-256 `4ae7e328b3b4488a48923ba6370634e3c3964e3a5a092e36c9f366246af2c693`.
- Package metadata: `com.darkaxt.dualdex`, version `1.1.0-rc.61`, version code `1010061`.
- Signing: APK Signature Scheme v3, one signer, certificate SHA-256 `c5a02cecb47cda41b618817ea684cbb6ccfdcc17a3e7d8243448175c8e3b2fba`.
- Independent public-artifact verification passed all 27 published checksum entries. Authenticated and anonymous APK downloads were byte-identical, and `provenance.json` matched the tag, commit, workflow, package, APK checksum, certificate, and protected signing authority.

## Remaining deferral

`G3-DREAM-001` now covers only the bounded Local-raster overage: 100,409,600 pixels against the 100,000,000 global limit. Other Stage 2 family deferrals remain unchanged; every unsupported optional module continues to fail closed independently.
