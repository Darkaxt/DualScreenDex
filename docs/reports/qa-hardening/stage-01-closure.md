# QA Hardening Stage 1 Closure

**Decision:** `COMPLETE`

**Stage branch:** `qa/project-wide-hardening`

**Synchronized baseline:** `722d77bc` (`fork/master`, RC72)

**Stage head before this report:** `f27dae15`

**Requirements:** `AND-01`, `AND-02`, `CAT-02`, `REL-01`

## Completed requirements

### AND-01 — Version all externally produced runtime state

- `f94e9933` introduced a runtime-owned monotonic delivery revision.
- Gateway, `RetroArchView`, and `SaveRamView` changes now invalidate cached state and advance the delivered version only when their value changes.
- Loopback coverage proves a native-only update defeats `sinceVersion` suppression and returns HTTP 200 with newer state.
- Runtime coverage proves equal native state does not manufacture revisions and later gateway actions remain newer.

### AND-02 — Contain and present every manual/asynchronous guide failure

- `60360a9e` placed manual ROM source materialization behind the same narrow `Exception` and `OutOfMemoryError` policy as automatic loading.
- Manual failure records bounded diagnostics, returns sanitized player guidance, and leaves the loopback server alive.
- Asynchronous terminal `state.error` is rendered on the no-catalog screen with retry and manual reselect still reachable.
- Production-web coverage rejects raw path, hash, exception-class, and allocator detail exposure.

### CAT-02 — Invalidate stale catalogs for parser-output changes

- `738a7f02` advanced `CatalogSchema.parserSchemaVersion` from 42 to 43.
- A seeded revision-42 cache is rejected and can be replaced by rebuilt hybrid move output.
- Release policy now pins the required parser-cache decision.

### REL-01 — Execute every JVM/app unit suite in PR CI

- `f27dae15` replaced the selective five-module test list with Gradle's aggregate `test` selector and explicit `:app:testDebugUnitTest`.
- The CI command therefore covers every included JVM module test task plus Android app unit tests while retaining dependency verification, web tests/build, lint, and debug assembly.
- A release-policy regression rejects reintroduction of the selective `:parser-core:test` list or omission of app unit tests.

## RC72 synchronization

`fork/master` advanced during Stage 1. Commit `4d243721` merged RC72 without resetting or discarding either line of work. The only content conflict was in `ProductionCompanionRuntime`; resolution retained both RC72 specimen projection and Stage 1 synchronized native-state versioning. The resolved runtime and packaged companion web compiled successfully.

## Verification evidence

Validation was kept proportional to the changed surfaces; the slow all-parser matrix is now enforced by CI and is not redundantly repeated at each checkpoint.

| Scope | Command | Result |
| --- | --- | --- |
| Android runtime and loopback regressions | `./gradlew :app:testDebugUnitTest --tests 'com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest' --tests 'com.darkaxt.dualdex.web.AndroidLoopbackServerTest'` | PASS in 27 s |
| No-catalog guide recovery | `npm --prefix companion-web test -- --run src/App.production.test.tsx` | 23/23 PASS in 4.74 s |
| Revision-42 invalidation | `./gradlew :catalog-store:test --tests 'com.darkaxt.dualdex.catalog.CatalogStoreTest.revision 42 caches are invalidated so hybrid move details are rebuilt'` | PASS in 11 s |
| CI and cache policy | `node --test tools/release/release-workflow.test.mjs` | 11/11 PASS in 0.15 s |
| RC72 conflict resolution | `./gradlew :app:compileDebugKotlin` | PASS in 1 min 1 s |

## Invariant review

- `INV-01`: expected guide failures disable guide loading without taking down the server or publishing a partial catalog.
- `INV-02`: Stage 1 does not broaden live authority; full immutable identity and epoch fencing remain assigned to Stage 2.
- `INV-03`: Stage 1 cache invalidation does not alter snapshot storage; durable separation remains assigned to Stage 2.
- `INV-04`: no new unbounded workload was introduced.
- `INV-05`: the no-catalog UI now presents sanitized, recoverable guide failure; cross-server envelope parity remains assigned to Stage 6.
- `INV-06`: parser output now has an explicit schema decision and release-policy regression.

## Missing-feature classification

### Blockers

None. Every Stage 1 requirement is implemented with an owning regression, and the synchronized head retains RC72 work.

### Tracked referrals

#### S1-REF-01 — Durable recovery and live identity

- **Requirements:** `CAT-01`, `RUN-01`, `RUN-02`, `RUN-14`; related `INV-02`, `INV-03`
- **Modules:** `catalog-store`, `retroarch-session`, app save/live/setup paths, `save-cli`
- **Reason:** Explicitly assigned to Stage 2 and dependent on the now-versioned runtime state.
- **Target:** Stage 2
- **Dependency:** Completed Stage 1 runtime-state delivery.
- **Acceptance:** Snapshot recovery survives catalog invalidation/migration; basename-only identity never becomes active; queued work cannot cross session epoch or close; every normalized or filesystem-equivalent CLI output/input collision is rejected without changing input hashes.

#### S1-REF-02 — Truthful release and packaged acceptance

- **Requirements:** `REL-02`, `REL-03`; related `INV-06`
- **Modules:** release workflow/tools and reusable Android managed-device bench
- **Reason:** Explicitly assigned to Stage 3 after trust and identity blockers.
- **Target:** Stage 3
- **Dependency:** Completed Stage 1 trust delivery and Stage 2 verified identity/epoch fencing.
- **Acceptance:** Candidates remain nonpublic until exact-artifact promotion, and the installed APK/WebView/loopback recovery matrix produces passing reusable evidence.

#### S1-REF-03 — Catalog and untrusted-input resilience

- **Requirements:** `CAT-03`–`CAT-14`, `AND-08`, `RUN-12`; related `INV-01`, `INV-03`, `INV-04`
- **Modules:** parser, catalog, archive, app guide/save, and parser CLI
- **Reason:** Explicitly assigned to Stage 4; these bounded hardening items do not invalidate Stage 1 delivery or cache revision.
- **Target:** Stage 4
- **Dependency:** Stage 2 durable snapshot isolation.
- **Acceptance:** Every acceptance fixture in the named specification sections fails closed within its bound while valid catalog and recovery state remain available.

#### S1-REF-04 — Runtime recovery and freshness

- **Requirements:** `AND-03`, `RUN-03`–`RUN-11`, `RUN-13`; related `INV-01`, `INV-02`, `INV-04`
- **Modules:** Android storage/save/live/battle/mapper, RetroArch session, battle memory, memory mapper
- **Reason:** Explicitly assigned to Stage 5 and dependent on Stage 2 identity fencing.
- **Target:** Stage 5
- **Dependency:** Stage 2 verified identity/epoch fencing and Stage 4 untrusted-input bounds.
- **Acceptance:** Revoked grants and stale status cannot authorize work; save/config/live/mapper paths recover without crossing identity; idle polling and UDP work stay within deterministic quotas; public arrays cannot mutate retained state.

#### S1-REF-05 — Companion transport and browser behavior

- **Requirements:** `WEB-01`–`WEB-09`, `REL-04`; related `INV-04`, `INV-05`
- **Modules:** Android/desktop servers, simulator, companion web, CI
- **Reason:** Explicitly assigned to Stage 6 after runtime identity and input bounds.
- **Target:** Stage 6
- **Dependency:** Stable Stage 5 runtime authority and state-revision contracts.
- **Acceptance:** Shared API parity, bounded transport/SSE, recoverable polling, persistent routes, catalog-versioned media, correct asset 404s, unique encounter keys, and portable public Chromium coverage all pass their specification fixtures.

#### S1-REF-06 — UX, privacy, evidence, and governance

- **Requirements:** `AND-04`–`AND-07`, `REL-05`–`REL-10`; related `INV-06`
- **Modules:** Android activity/overlay/setup, diagnostics, Gradle dependencies, release evidence, readiness docs
- **Reason:** Explicitly assigned to Stage 7 and bounded away from Stage 1 trust blockers.
- **Target:** Stage 7
- **Dependency:** Completed Stages 2–6 correctness, resilience, runtime, and companion closures.
- **Acceptance:** Every named specification acceptance condition passes, including one-shot picker dispatch, safe rescan/settings fallback, nonreversible diagnostics, source-bound evidence, auditable protection, and one consistent current-readiness index.

#### S1-REF-07 — Integrated invariant closure

- **Requirements:** `INV-01`–`INV-06` plus all prior referrals
- **Modules:** project-wide
- **Reason:** Cross-stage invariants require final revalidation after all implementation stages.
- **Target:** Stage 8
- **Dependency:** Completed Stages 2–7 and their focused owning regressions.
- **Acceptance:** All referrals are closed, every requirement verdict is current on synchronized HEAD, and the complete integrated gate passes once without retained blockers or referrals.

## Final decision

`COMPLETE` — Stage 1 has no blockers. Proceed to Stage 2. No RC is authorized or published by this closure.
