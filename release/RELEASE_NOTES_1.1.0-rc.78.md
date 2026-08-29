# DualDex 1.1.0-rc.78

RC78 is the release candidate for the completed project-wide QA hardening program. It closes the remaining Android setup, parser/catalog, runtime authority, companion transport, privacy, and release-governance gaps, then binds them to the final source-bound compatibility corpus.

## Android setup and recovery

- Recover cleanly when a guide cannot be loaded instead of crashing the app or leaving the guide surface in a stale state.
- Reconcile direct-storage and folder-picker access without discarding the last valid index during a failed rescan.
- Quarantine revoked folder grants, route package-specific settings safely, and explain protected `Android/data` and `Android/obb` limitations accurately.
- Deliver overlay picker results exactly once across cold starts, new intents, retries, and activity recreation.
- Keep Area Guide projection failures local to that optional module.

## Parser and catalog resilience

- Bound complete-ROM probes, archive extraction, catalog payloads, concurrent corpus work, and detached Gen I species discovery.
- Add cancellation checks to long parser passes and replenish ordered corpus work after any completion so one slow input cannot stall unrelated inputs.
- Verify catalog identity and canonical content digests before activation, quarantine invalid snapshots, and fail closed on malformed optional data.
- Invalidate pre-hardening parser catalogs through schema revision 46 and retain seeded rebuild coverage.

## Runtime authority and companion safety

- Require verified ROM identity and monotonic session epochs before live memory or SaveRAM state can become authoritative.
- Fence queued mapper, socket, command, delayed-reply, and checkpoint work against stale sessions.
- Recover RetroArch configuration and command sockets transactionally while bounding memory reads, UDP drains, and retained snapshots.
- Bound Android and desktop companion transport, fence navigation/state/media responses, preserve structured retries, and isolate optional feature failures.
- Remove private paths, reversible player-state fingerprints, raw failures, stacks, workspace identifiers, and device identifiers from normal diagnostics and public evidence.

## Source-bound QA closure

- Audit 334 supported-extension files while evaluating all 333 scanner-eligible mainline and hack inputs; one known spin-off remains intentionally excluded by scanner policy.
- Reach terminal parser outcomes for 333/333 inputs: 278 selected, 2 ambiguous, 53 without a family match, and 0 parser errors.
- Record 20 complete, 302 partial, and 11 unresolved data-compatibility outcomes with 0 compatibility errors.
- Materialize, persist, close, reopen, and decode all 278 selected catalogs with 0 catalog or persistence errors.
- Close QA Stages 7 and 8 with zero blockers and zero referrals.

## Measured validation

- Post-remediation Kotlin and Android gate: 65 tasks passed in 40m36s across parser, catalog, CLI, runtime, companion, and app unit suites.
- Companion web gate: 32 Vitest files and 268 tests passed; the TypeScript/Vite production build passed.
- Portable Chromium acceptance: 3/3 Playwright tests passed.
- Release and governance gate: 83/83 Node tests passed.
- Public QA evidence: 7/7 assets passed structural privacy validation.
- Protected GitHub managed-device acceptance, signing, and Thor validation remain mandatory before candidate promotion.

## Delivery

- This candidate uses Android version code `1010078`.
- The candidate is built and signed only through the protected GitHub Actions environment; production signing material is never exposed to the repository or local workspace.
- DualDex remains read-only and sends no game commands or emulator-memory writes.
