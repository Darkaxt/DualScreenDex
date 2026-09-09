# Stage 4 — Cache-only API observation bridge

Date: 2026-09-09

## Scope and preservation contract

This Task 425 / `LNG-B003` tooling checkpoint captures the actual production `BootstrapView` through `ProductionCompanionRuntime.restoreCatalog(sha256)`, without loading a ROM or invoking the parser. It adds only two app test-source files: `OfficialMatrixApiCapture.kt` and `OfficialMatrixApiCaptureTest.kt`. Parser/cache revision **64**, SQL schema **2**, codec versions **1**, CorpusReport **14** and execution receipt **1** are unchanged.

Production cache reads can migrate or delete rejected databases. The bridge therefore validates a pinned retained SQLite file, creates an exclusive private working directory, makes and verifies an exact-byte copy bounded to 256 MiB, and passes **only that copy** to schema probes and production restore. It rejects invalid identities, aliases, sidecars, stale schemas and unexpected catalog/family/language/codec identities. A final source digest/sidecar check protects the retained input; no stability claim is made for the disposable copy after production access.

The observation retains `acceptance=false` and scope `CACHE_ONLY_OBSERVATION`. Its `bootstrap` envelope contains:

- `response`: the unchanged production response serialized with the server's explicit-null Gson policy;
- `parserInvocations`: measured instrumentation, required to remain zero;
- `captureProvenance`: restore method, pinned source-cache digest and parser/SQL revisions.

There is **no** `parserInvocations` field added to the production response. Language must be resolved, match the expected projection and remain `ROM_DEFAULT`; loading phase must be `CACHE_REOPEN`. This is a cache-only observation fragment, **not** a report producer or a completed `apiBootstrap` matrix check. Final response hashing must use `official_matrix.canonical` on the exact envelope, with independent oracle/report binding supplied separately.

## Tests and review

Nine fabricated-only tests cover successful production restore, wrong cache digest, wrong embedded identity, stale parser schema, family/language/codec/version mismatches, missing input, sidecars, pre-existing working directories and invalid identifiers. The synthetic SQLite fixture contains one French localized species and valid shared-domain capability counts; it never constructs or reads a ROM image.

The first isolated launch compiled zero tests because Android test compilation did not expose `Files.writeString/readString`; only fixture file operations were changed to compatible byte APIs. The next run failed all nine during fixture construction because species-description coverage incorrectly expected zero rather than one. That fixture was corrected without weakening production validation. Neither outcome is counted as behavioral RED.

A genuine stub RED then executed all nine tests and failed all nine. Initial implementation GREEN passed 9/9. Independent review found a test gap: probing the retained source through the database factory could leave bytes unchanged and evade hash-only assertions. The success test now records every factory path and requires exclusively the private copy. Deliberately changing the schema probe from copy to source produced one expected failure among nine tests; restoring the correct implementation passed 9/9 again. The original bridge implementation did not contain that mutation.

After exact-byte integration into parent `abfc222c97778de859778359ad346ead6a816c31`, a fresh owned offline Windows Job ran the exact nine selectors with private output/cache directories, two workers and a 900-second watchdog. App test-source compilation was deliberately focused; web packaging and all APK/device/signing tasks were excluded. This is not a complete app test-suite or packaged-UI gate.

| Boundary | Result |
|---|---:|
| Actual JUnit methods | **9 passed, 0 failures/errors/skips** |
| Elapsed | **337.042 seconds** |
| Gradle | **exit 0, 38 tasks executed, no timeout** |
| Parent source pins independently rehashed | **805 unchanged** |
| Actual JUnit XML files independently rehashed | **1** |
| Original selectors / reads | **none / 0** |

The coordinator inspected the complete log and actual XML, independently reconstructed the exact method inventory and verified both integrated source hashes. Existing compiler warnings were not changed by this checkpoint.

| Retained evidence | SHA-256 |
|---|---|
| Parent gate result | `474b9c3637999f3ff8360da120de2c531f988c264f3ad28962128a697b34d56f` |
| Actual parent JUnit XML | `83801ab72a8ccf0eed52b47d6751227fec911fb13975241d08b6728c23929baa` |
| Coordinator verification | `4ac21e950e5aff80c2cb9c4c24a1ce8f711171710d6e068e023974b5b66c43f5` |
| Bridge source | `47f809d7dbad95497734c8599d0b25c3970918e3d667e9d0a91269cf9bcabf82` |
| Bridge tests | `da0d1856036475f65787d87d19e78a0fe08d6822d66fc2ae0fce267516b3ba9d` |

Private paths and retained database payloads are not published.

## Remaining mandatory work

Task 425 remains **OPEN** for packaged parser-cli G0 provenance and normalized same-image header, compiled structural, isolation/type and canonical API-envelope bindings. Task 426 owns logical catalog digest parity. The [codec exporter](stage-04-codec-evidence-checkpoint.md) is implemented but still requires final stable-source capture.

Native acceptance preparation remains unfrozen. One instrumented runtime-inventory diagnostic stopped on an exact journal-replacement permission error; it did not locate the earlier hashing failure or complete runtime pinning. A retained FireRed diagnostic correction remains separately reviewed and does not establish original-ROM uniqueness or title applicability. Neither issue authorizes new original execution.

All **43 language-family cells / 44 controls / 308 checks / 660 capability dispositions**, independent applicability proofs and the final eligible corpus remain mandatory. **Zero final stable-source controls are accepted. Stage 4 remains OPEN; Stages 5–6 remain blocked.** No original acquisition, corpus run, device/emulator/ADB action, APK, signing or release occurred for this checkpoint.
