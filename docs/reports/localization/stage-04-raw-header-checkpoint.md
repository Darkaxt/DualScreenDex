# Stage 4 — Same-image raw-header evidence

Date: 2026-09-09

## Scope

Task 427 completes Task 425's raw-header observation subcomponent. The packaged parser CLI hashes the already-loaded `RomImage`, using the platform from that image's actual parser analysis. It does not reopen an input, reparse it, route by extension or hash a sanitized title.

The nullable `CorpusResult.rawHeader` contains only `rawHeaderSha256` and `byteCount`. GB/GBC uses exactly `[0x100, 0x150)` (80 bytes); GBA uses exactly `[0, 0xC0)` (192 bytes). Unknown platforms and incomplete windows produce no observation, not a partial or padded digest. No raw bytes enter the report.

CorpusReport and receipt-generator schema advance **14 → 15**; the receipt envelope remains **1**. Parser/cache **64**, SQL **2** and codec versions **1** are unchanged. Existing report execution provenance and packaged runtime-classpath/source-commit enforcement remain intact. Historical schema-14 evidence stays immutable; future plans, reports, receipts and oracles must be freshly matched rather than relabeled. The strict matrix validator is unchanged.

## Verification

The first isolated attempt failed configuration before tests because the private task allowlist omitted the transitive `save-core` dependency; it is not behavioral RED. After correcting that allowlist, RED ran 42 methods with 13 assertion failures. The strengthened final selection ran 46 methods with 15 expected assertion failures, zero errors/skips. Those unchanged 46 tests then passed in 45.214 seconds, with 812 source pins unchanged.

Independent read-only review found no verified correctness defect. It checked the seven-file patch, 85 artifacts, before/after source pins and actual XML; regenerated all 20 fabricated images in memory; and verified exact header ranges, platform/null handling, input identities, 13 packaged JARs, source manifest and report/receipt bindings. It did not run a fresh JVM gate or inspect original inputs.

After exact integration into parent `00902d7fc416532dcf1ce8643bc030ed2b5e83e4`, the coordinator ran one fresh offline Windows Job against an explicit HEAD/source approval snapshot:

| Boundary | Result |
|---|---:|
| Actual JUnit methods | **46 passed, 0 failures/errors/skips** |
| Classes | **4: header CLI 14, same-image helper 4, report 25, receipt 3** |
| Packaged CLI inputs | **20 fabricated** |
| Elapsed | **226.341 seconds** |
| Gradle | **exit 0, 12 tasks executed, no timeout** |
| Parent source pins and retained source copies independently rehashed | **814 each** |
| Actual XML / packaged runtime JARs independently verified | **4 / 13** |
| Original reads / final controls accepted | **0 / 0** |

The gate used private outputs/cache directories, two workers, one test fork and a 900-second owned-process bound. Parent HEAD and sources remained frozen. The coordinator inspected the complete log, reconstructed the exact XML method inventory, rehashed runtime JARs, extracted the embedded source commit and independently verified report-15/receipt-1/classpath/raw-report bindings. Header observations and unrelated result fields match the private synthetic run, excluding duration. Existing compiler warnings were unchanged.

Tests cover both header endpoints, irrelevant outside-window bytes, GB/CGB flags, misleading extensions, title-sanitization collisions, missing/truncated headers, source removal after image loading, unpackaged entrypoint rejection and wrong-source rejection before input discovery. The packaged synthetic cases are all `NO_FAMILY_MATCH`: they verify observation/provenance plumbing, not successful catalog materialization or semantic acceptance.

| Retained evidence | SHA-256 |
|---|---|
| Reviewed seven-file patch | `e12384529bc78873f096e16beeaf6b476c3689bde5c7921aba882f8283c75d5d` |
| Parent approval snapshot | `c0a1561bb1f652bf6c9b2b82825d69472fc077b3a51eac43f13489c0c418fb5d` |
| Parent gate result | `9c3c5f4962c25436dd5f073154816828ca3d464925e6b213441b2a665124574c` |
| Parent runtime classpath | `abef90904cdc90d29c3e1b1019d5f5cbef7982475b8ca727aaf0f77f7ce1ef47` |
| Parent raw report | `0e8b5ffc1af378ebbe9a774799691e860d20955e0f343ae128f1d56fe81a8fac` |
| Parent execution receipt | `b47fa84e253080ecc16bb392fc76b1b0fe6b017ac6650906407ebd7755a964e6` |
| Coordinator verification | `98a38c219bf7a0a5878b1230b4e7d20296b18100cdd2865f38698c32ab0c925f` |

Private paths and raw payloads are not published.

## Remaining mandatory work

Task 427 closes only this observation implementation. Task 425 remains **OPEN** for actual logical before-write/reopen digest binding, compiled structural observations, measured projection/type evidence and canonical API-envelope/report/oracle linkage. The [logical digest](stage-04-logical-digest-checkpoint.md), [cache-only API bridge](stage-04-cache-only-api-checkpoint.md) and [codec exporter](stage-04-codec-evidence-checkpoint.md) remain tooling until their final source-bound captures are complete.

Native runtime pinning, original selected-sign acceptance, GBA title/applicability evidence and complete independent capability oracles remain mandatory. No consumed reservation is reopened. All **43 cells / 44 controls / 308 checks / 660 capability dispositions** and one final eligible corpus remain required. **Zero final stable-source controls are accepted. Stage 4 stays OPEN; Stages 5–6 stay blocked.** No original acquisition, corpus, device/emulator/ADB action, APK, signing or release occurred for this checkpoint.
