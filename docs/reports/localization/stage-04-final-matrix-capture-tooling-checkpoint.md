# Stage 4 final matrix capture tooling checkpoint

Date: 2026-09-10

Status: **tooling complete; no official control accepted by this checkpoint**

## Scope

This checkpoint closes the remaining execution seam between one current `parser-cli` control run and the existing final matrix validator:

- `OfficialMatrixApiBatchCapture` applies the already-tested cache-only production bootstrap capture to an explicit one-to-44-control partition under one exact five-field run binding.
- `OfficialMatrixApiCaptureConfig` requires a SHA-256-pinned configuration and explicit cache, working, and output paths. The opt-in JUnit entrypoint is skipped unless its configuration property is present.
- `build_official_matrix_observations.py` joins the current report, execution receipt, codec-golden execution, and cache-only API document without reading a ROM or SQLite payload.
- The exporter emits all seven G1 checks for each control: raw header, codec vectors, structural authority, reopen parity, projection isolation, type semantics, and API bootstrap.
- Structural authority is the canonical digest of the selected resolved layout's consumer-facing authority fields plus the count of non-null corroborating profile tables. The independent G4 oracle must still ratify that value; the exporter does not create expected values.
- A failed projection/type measurement, stale binding, mismatched logical digest, missing codec execution, duplicate identity, or malformed input blocks export rather than producing partial positive evidence.

The output remains private evidence. It contains actual localized API values and must not be committed or copied into public assets.

## Verification

TDD RED was observed before each implementation:

- unresolved `OfficialMatrixApiBatchCapture` and lambda types;
- unresolved `OfficialMatrixApiCaptureConfig` and `MatrixApiCaptureConfig`;
- missing `build_official_matrix_observations` module.

Fresh GREEN evidence:

| Gate | Result |
|---|---|
| `OfficialMatrixApiBatchCaptureTest` | 2 tests, 0 failures/errors/skips |
| `OfficialMatrixApiCaptureConfigTest` | 2 tests, 0 failures/errors/skips |
| `test_build_official_matrix_observations.py` | 3 tests, 0 failures/errors/skips |
| Existing `test_official_matrix.py` | 65 tests passed |
| Existing `test_build_official_matrix_plan.py` | 50 tests passed |
| Combined Python evidence tooling | 118 tests passed |
| `git diff --check` | clean |

The existing unrelated `SpriteCodec.kt` unused-expression warning and existing unrelated app-test Java interop warnings remain unchanged.

## Remaining Stage 4 boundary

This checkpoint does not run official controls, read original ROMs, inspect caches, issue `FIELD_ACCEPTANCE`, assemble a final plan, run the final matrix, or run the corpus. Tasks 417/419/425/428/430 remain open until the exact current 44-control capture, whole-original Ruby/Sapphire title uniqueness, current-domain/API parity, independently reviewed capability proofs, and final five-field bindings are present. The final control run should occur once, after this tooling is published, and supply the two bounded 35-Western and nine-native partitions if needed.
