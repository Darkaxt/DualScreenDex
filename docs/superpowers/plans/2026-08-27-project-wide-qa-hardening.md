# Project-Wide QA Hardening Staged Plan

**Specification:** `docs/superpowers/specs/2026-08-27-project-wide-qa-hardening-design.md`

**Goal:** Implement the specification in dependency order, closing the highest-risk user-visible and data-integrity defects first while preserving fail-closed behavior and avoiding isolated release candidates.

**Delivery model:** Use one cumulative branch, `qa/project-wide-hardening`. Each stage is independently tested, audited against the specification, committed, smart-synced with `fork/master`, and pushed before the next stage begins.

---

## Stage discipline

### Before each stage

1. Fetch and reconcile current `fork/master`.
2. Confirm the worktree has no unexplained changes.
3. Re-read the specification sections assigned to the stage.
4. Run the focused baseline tests for the modules being changed.
5. Do not discard or overwrite unrelated changes from other threads.

### After each stage

Re-read the complete specification, not only the current section, and produce a stage closure report under:

```text
docs/reports/qa-hardening/stage-XX-closure.md
```

The report must contain:

- stage commit and synchronized `fork/master` baseline;
- requirement IDs scheduled for the stage;
- implementation and test evidence for each completed requirement;
- missing-feature classification;
- verification commands and results;
- final decision: `COMPLETE` or `BLOCKED`.

Every missing feature must be classified as exactly one of:

### Blocker

Use `BLOCKER` when:

- a requirement assigned to the current stage is incomplete;
- its acceptance condition is not proven;
- tests are failing or absent;
- an `INV-*` invariant is violated;
- the gap can crash the APK, corrupt durable data, publish stale/wrong authority, or invalidate the stage’s release claim.

A stage with blockers remains open. Commit and push the blocked checkpoint for durability, but do not mark the stage complete, integrate it as a completed checkpoint, start a dependent stage, or publish a candidate.

### Tracked referral

Use `TRACKED_REFERRAL` only for a requirement already assigned to a later stage or a newly discovered bounded issue that does not invalidate the current stage.

Every referral must record:

- unique referral ID;
- related specification requirement or invariant;
- affected module;
- reason for referral;
- target stage;
- dependency;
- measurable acceptance condition.

A current-stage requirement cannot be referred merely to close the stage. Changing stage scope requires an explicit specification revision.

### Commit and synchronization rule

After the closure audit:

1. Commit implementation, tests, and closure evidence.
2. Fetch `fork/master` again.
3. Merge current master if needed; never reset over it.
4. Rerun tests affected by the merge.
5. Push the checkpoint to `fork/qa/project-wide-hardening`.
6. Merge to `fork/master` only when the stage has no blockers.

No stage signs, tags, promotes, or publishes an RC without separate authorization.

---

## Stage 1 — RC71 trust blockers

**Requirements:** `AND-01`, `AND-02`, `CAT-02`, `REL-01`

**Objectives:**

1. Make native `RetroArchView` and `SaveRamView` changes advance the state revision observed by the WebView.
2. Contain manual source-loading `Exception`/`OutOfMemoryError` and expose sanitized no-catalog failure plus retry/reselect.
3. Increment the parser-cache schema so pre-hybrid-decoder catalogs cannot remain active.
4. Make PR CI execute every JVM module test suite plus Android app unit tests.

**Primary modules:** `app`, `companion-web`, `companion-core`, `catalog-store`, CI/release policy tests.

**Exit gate:**

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew verifySecureBuildDependencies test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
cd companion-web && npm test -- --run && npm run build
node --test tools/release/*.test.mjs
```

**Stage blocker examples:** corrected storage state still needs an extra action to appear; expected guide failure escapes or has no visible retry; schema-42 cache remains a hit; any tested JVM/app module remains absent from CI.

---

## Stage 2 — Durable recovery and live identity

**Requirements:** `CAT-01`, `RUN-01`, `RUN-02`, `RUN-14`

**Objectives:**

1. Separate SaveRAM recovery snapshots from disposable catalog-cache cleanup and migrations, including safe legacy migration.
2. Treat basename-only/null-CRC resolution as unverified discovery rather than live authorization.
3. Introduce a monotonic session identity/epoch that fences queued save, checkpoint, journal, and publication work across ROM changes and coordinator close.
4. Reject `save-cli` output paths that alias ROM, SaveRAM, probe, or another output.

**Primary modules:** `catalog-store`, `retroarch-session`, `app` setup/save/knowledge/live paths, `save-cli`.

**Exit gate:**

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :catalog-store:test :retroarch-session:test :save-cli:test :app:testDebugUnitTest --stacktrace
```

**Stage blocker examples:** catalog cleanup can still erase a valid snapshot; null-CRC basename match becomes `ACTIVE`; delayed work mutates a later session; any CLI path alias can overwrite input.

---

## Stage 3 — Truthful release and packaged acceptance

**Requirements:** `REL-02`, `REL-03`

**Objectives:**

1. Create RCs as draft prereleases and promote only the exact already-signed artifact after required validation evidence.
2. Add a reusable Gradle Managed Device job covering the installed APK, bundled WebView, loopback bootstrap/state, native-to-web revision, guide failure/retry, and catalog reopen.
3. Keep signing material confined to the protected GitHub environment.

**Primary modules:** release workflows/tools, signing documentation, Android instrumentation tests, app Gradle configuration.

**Exit gate:**

```bash
node --test tools/release/*.test.mjs
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:qaApi35DebugAndroidTest --stacktrace
```

The managed-device gate may run in GitHub when local virtualization is unavailable; absence of a runnable environment is not a pass.

**Stage blocker examples:** candidate is public before promotion; promotion can rebuild/replace the APK; exact hash/certificate evidence is absent; the packaged acceptance job does not run or fails.

Do not publish a candidate during this stage.

---

## Stage 4 — Catalog, parser, and untrusted-input resilience

**Requirements:** `CAT-03`–`CAT-14`, `AND-08`, `RUN-12`

**Objectives:**

1. Isolate checkpoint/cache failures from valid in-memory catalog publication.
2. Serialize shared database access and verify requested catalog identity.
3. Cancel superseded parser work.
4. Share index/load retained-byte identity rules.
5. Bound and isolate optional relationship and guide-projection failures.
6. Bound archive traversal, decompression, catalog chunks/inflation, LZ77 allocation, CLI workers, and SaveRAM reads.
7. Verify catalog section digests and quarantine corrupt snapshots without blocking live recovery.
8. Ensure async cache restore always terminates.

**Primary modules:** `parser-core`, `parser-cli`, `catalog-store`, `app` storage/web/save paths, `companion-core` area guide.

**Exit gate:**

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test :parser-cli:test :catalog-store:test :save-core:test :companion-core:test :app:testDebugUnitTest --stacktrace
```

**Stage blocker examples:** optional data can still abort the catalog; stale parse monopolizes the worker; cache mismatch is accepted; an untrusted source allocates/reads before its bound; persistence failure hides a valid guide; projection failure takes down `/api/state`.

---

## Stage 5 — Runtime recovery and session freshness

**Requirements:** `AND-03`, `RUN-03`–`RUN-11`, `RUN-13`

**Objectives:**

1. Quarantine SAF indexes when their persisted grant is absent.
2. Hash SaveRAM before declaring content unchanged.
3. Make RetroArch config installation recoverable across interruption and retry.
4. Invalidate stale live authority on terminal memory/status failure.
5. Bind mapper captures and delayed responses to one verified session.
6. Recreate broken command sockets with backoff and bound UDP drain work.
7. Eliminate idle battle/mapper polling.
8. Prevent mutation of retained memory snapshots through public array references.

**Primary modules:** `app` setup/save/battle/live/mapper paths, `retroarch-session`, `memory-mapper-lab`, `battle-memory`.

**Exit gate:**

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :retroarch-session:test :memory-mapper-lab:test :battle-memory:test :app:testDebugUnitTest --stacktrace
```

**Stage blocker examples:** stale content remains active; config backup can be overwritten; mapper/save work crosses identities; command transport cannot recover; UDP work is unbounded; idle schedulers continue waking; returned arrays mutate retained state.

---

## Stage 6 — Companion transport, navigation, and browser coverage

**Requirements:** `WEB-01`–`WEB-09`, `REL-04`

**Objectives:**

1. Bound Android socket reads/writes and desktop SSE state retention.
2. Standardize Android/desktop JSON error behavior.
3. Add defensive polling, reconnect/backoff, and cancellation.
4. Preserve validated routes across refresh and display transfer.
5. Version catalog media URLs and expose sprite availability.
6. Return 404 for missing static assets while preserving SPA navigation fallback.
7. Make simulator encounter keys unique.
8. Add Android/desktop API parity tests.
9. Run a portable nonprivate Chromium E2E suite in CI/release.

**Primary modules:** Android loopback server, `companion-server`, `companion-core`, `companion-simulator`, `companion-web`, CI/release workflows.

**Exit gate:**

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :companion-core:test :companion-server:test :companion-simulator:test :app:testDebugUnitTest --stacktrace
cd companion-web
npm ci
npm test -- --run
npm run build
npm run test:e2e:ci
```

**Stage blocker examples:** partial requests can exhaust workers; SSE remains unbounded; polling rejection is unhandled; transferred routes reset; catalog B can display catalog A media; missing assets return index HTML; E2E requires private/host-specific fixtures.

---

## Stage 7 — Remaining UX, privacy, evidence, and governance

**Requirements:** `AND-04`–`AND-07`, `REL-05`–`REL-10`

**Objectives:**

1. Make overlay folder actions open the intended picker once.
2. Add safe game rescan and Settings-intent fallback.
3. Document protected shared-storage limitations accurately.
4. Bind compatibility evidence to source commit, generator schema, input digest, and change scope.
5. Remove reversible player-state diagnostics and raw path/hash/error leakage.
6. Record only minimal local prior-exit classification.
7. Declare direct Gradle dependencies for direct imports.
8. Make tag/environment protection auditable without exposing signing material.
9. Replace the stale “current” RC9 requirement index with a current-readiness entry point while preserving historical evidence.

**Primary modules:** Android activity/overlay/setup, diagnostics, Gradle module declarations, release workflow/tools, readiness documentation.

**Exit gate:**

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
node --test tools/release/*.test.mjs
```

**Stage blocker examples:** overlay action still needs a second tap; rescan destroys the prior index on failure; diagnostic output exposes player/path information; parser changes can reuse stale evidence; current release documents contradict one another.

---

## Stage 8 — Integrated specification closure

**Requirements:** All `INV-*`, `AND-*`, `CAT-*`, `WEB-*`, `RUN-*`, and `REL-*` IDs plus every tracked referral.

**Objectives:**

1. Re-read the complete specification and all prior stage reports.
2. Revalidate every requirement against current synchronized HEAD.
3. Reopen any regressed requirement as a blocker.
4. Close every tracked referral; final closure cannot retain referrals.
5. Run the complete unit, lint, build, browser, release-policy, and managed-device gates.
6. Produce `docs/reports/qa-hardening/stage-08-closure.md` with the full verdict matrix and exact evidence.

**Integrated gate:**

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew verifySecureBuildDependencies test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
node --test tools/release/*.test.mjs
cd companion-web
npm ci
npm test -- --run
npm run build
npm run test:e2e:ci
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:qaApi35DebugAndroidTest --stacktrace
```

**Exit condition:** Every specification requirement is complete, all referrals are closed, all gates pass after the final smart-sync, and the report states `Blockers: None` and `Tracked referrals: None`.

Only then is the branch eligible for a separately authorized release-candidate decision. This plan itself does not authorize publication.
