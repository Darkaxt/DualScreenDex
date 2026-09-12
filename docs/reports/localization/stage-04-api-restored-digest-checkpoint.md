# Stage 4 — exact restored-catalog capture

Date: 2026-09-09. First Task 430 checkpoint; G3 integration and Stage 4 remain open.

`OfficialMatrixApiCapture` now records the exact `StoredCatalog.catalog` returned by the repository to production `restoreCatalog`. Interface delegation intercepts that single read without a runtime accessor or second cache read. The capture exports its versioned `CatalogLogicalDigest`, explicitly selects DISCOVERED knowledge mode, and records source/report/receipt/generator bindings inside `bootstrap.captureProvenance.binding`. The actual production `BootstrapView` remains unchanged inside `bootstrap.response`.

Malformed bindings fail before filesystem access. Existing bounded private-copy, identity, schema, sidecar and retained-source checks remain intact. The binding values are supplied evidence identities, not independently verified artifact bytes at this capture boundary; the shared validator/assembler binding check remains required.

## Focused verification

- Genuine RED: 11 tests, exactly two expected assertion failures (missing restored digest and recorded knowledge mode); nine existing preservation regressions passed.
- GREEN: **13/13**, no failures/errors/skips, 27.143 seconds. Main verified actual XML method selectors and all 812 unchanged Kotlin/build source pins.
- The digest regression changes only the generated private working copy immediately before production restore. Its digest must match the changed catalog actually returned, not the retained source or a separate read; exactly two instrumented database opens occur (schema probe and restore).
- Additional tests cover all five embedded binding fields and malformed-binding rejection before copying/opening a database.
- These are generated SQLite fixtures, not official-control or linguistic acceptance. No original-ROM read, corpus, device/emulator/ADB, APK, signing or release occurred.

The private retained evidence root remains outside public assets. Frozen pre-commit HEAD: `d22289c789ffdf68e6554c41dba32f36b14cb30a`; source pins separately bind the tested implementation.

| Artifact | SHA-256 |
| --- | --- |
| GREEN result | `c2718d2674885145387036bb94e2b95cd800cbaa4bcd9c5a1653060173125012` |
| GREEN JUnit XML | `a623f7ee1f9ce99403643dfe226b894095dd496e3bcaa83b40bce6a2507ee19c` |
| GREEN source pins | `db187eff7541e0cbdc47e089f3172bbfe791ddc06e95dec5d1a5ad36ece753bc` |

## Remaining Task 430 scope

The all-15-group measured projection/API observations, type-consistency measurements, normalized actual API fields, and shared Python validator/assembler binding remain mandatory and unimplemented in this checkpoint. The stopped agent's draft remains untouched; work proceeds directly and serially. No parser/cache/report/codec version changes here. Zero final stable-source controls are accepted; Stages 5–6 remain blocked on sequential closure.
