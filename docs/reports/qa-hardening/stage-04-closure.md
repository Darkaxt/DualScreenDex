# QA Hardening Stage 4 Closure

**Decision:** `COMPLETE`

**Stage branch:** `qa/project-wide-hardening`

**Synchronized baseline:** `a70648f6` (`fork/master`)

**Synchronization merge:** `d4266680`

**Stage head before this report:** `7ad82ce6`

**Requirements:** `CAT-03`–`CAT-14`, `AND-08`, `RUN-12`

## Completed requirements

### CAT-03/CAT-04 — Best-effort persistence with canonical writer ownership

Commit `7e0a7f86` makes parser checkpoints best effort: repository failure disables later checkpoint writes for that load, records bounded cache evidence, and does not suppress the valid in-memory catalog. Incomplete persisted catalogs remain unavailable.

The same checkpoint adds one coordinator per canonical database identity, encodes outside the writer critical section, and retries only bounded transient SQLite lock failures. Catalog and SaveRAM snapshot writers therefore share ownership instead of relying on unrelated instance locks.

Regressions cover one/every checkpoint failure, blocked concurrent catalog/snapshot writers, canonical path aliases, bounded transient-lock retry, and non-lock failure containment.

### CAT-05 — Superseded parser cancellation

Commit `308ebb8a` adds session-owned parser cancellation tokens and retained `Future` ownership to Android and desktop runtimes. Supersession calls both token cancellation and `Future.cancel(true)`, while family probes, lazy candidate consumption, GBA scans, catalog materialization, Gen III Local-map raster work, and PNG row encoding observe bounded checkpoints.

Generation/token fences guard progress, persistence, catalog publication, and commit callbacks. Completion callbacks are exactly once. The acceptance regression blocks parser A, requests B, proves A observes cancellation and releases the single worker before its external release latch opens, and verifies that only B persists and commits.

### CAT-06/CAT-09 — Shared retained-byte and archive policy

Commit `2c8c46e9` routes direct indexing, raw loading, ZIP inspection, and 7z inspection through one retained-byte/addressability policy. It enforces raw source size, archive entry count, aggregate extracted bytes, nonselected-member drain limits, and early ambiguity rejection.

Regressions prove index/load SHA and CRC agreement for an addressable GBA with a permitted trailer, metadata-first rejection of oversized raw files, bounded rejection of oversized deflated non-ROM members, and archive entry-count rejection.

### CAT-07/CAT-13 — Catalog identity and terminal asynchronous restore

Commit `66c8da40` rejects persisted catalogs whose embedded ROM SHA-256 differs from the requested identity and defends again before runtime publication. Both synchronous and asynchronous reopen paths use the same identity condition.

Asynchronous restore now contains invalid identity and repository failures and always publishes a terminal state for the still-current generation. Tests cover a valid B catalog placed at A's path and throwing asynchronous restores.

### CAT-08 — Optional relationship and description isolation

Commit `f8a70e87` bounds terminator scans and contains optional description, evolution, learnset, machine, egg-move, and move-acquisition failures at dataset or record scope. Failed records carry unavailable/partial evidence rather than authoritative empty lists, while valid neighboring records remain usable.

The final specification reread identified this as a parser-output change. Commit `7ad82ce6` therefore advances `CatalogSchema.parserSchemaVersion` from 43 to 44, adds a revision-43 invalidation/rebuild regression, and updates the release-policy schema guard. Existing revision-43 catalogs cannot hide the corrected optional-data evidence.

### CAT-10 — Verified and bounded catalog decoding

Commit `2bfa92fd` verifies persisted section digests while streaming and applies section count, chunk count, encoded-byte, per-section inflate, and aggregate inflate limits before JSON deserialization. Valid-gzip digest substitution and every configured size/count overflow reject deterministically.

### CAT-11 — Contract-specific compressed asset bounds

Commit `b9a7768d` requires explicit decoded-size contracts before GBA LZ77/SMOL allocation for species, palettes, balls, trainers, Local maps, and world maps. Optional over-limit assets become unavailable without aborting catalog materialization. Tests cover allocation-before-limit regressions and retained official controls.

### CAT-12 — Corrupt snapshot quarantine

Commit `7e0a7f86` treats malformed retained snapshot payloads as absent, removes only the corrupt row, records bounded diagnostics, and continues current candidate polling. A valid live candidate can produce `MATCHED` and replace the quarantined snapshot; schema/database failures are not misclassified as row corruption.

### CAT-14 — Bounded parser CLI work

Commit `26ea3ccf` reuses the core archive policy, streams corpus discovery through a bounded running-plus-queued window, caps effective workers to the documented maximum, and preserves ordered output. Regressions cover oversized archives, excessive archive entries, lazy discovery backpressure, defensive concurrency caps, and excessive `--jobs` values.

### AND-08 — Area Guide projection isolation

Commit `308ebb8a` preflights Area Guide point, encounter, encounter-slot, scene-placement, objective, area, and retained-output counts. Projection returns an explicit available/unavailable outcome. Expected `Exception` and `OutOfMemoryError` are contained only at the presentation boundary and expose bounded sanitized stage/class evidence.

Android caches a failed projection key to avoid repeating the same failure, retains the catalog and unrelated pages, and retries when catalog/state/objective input changes. Tests inject ordinary failure, explicit limit failure, OOM, repeated same-key access, and later valid recovery.

### RUN-12 — Bounded SaveRAM streams

Commit `308ebb8a` replaces eager provider byte suppliers with fresh `InputStream` openers. Save reads reject trusted oversized metadata before opening and otherwise consume at most 128 KiB plus one byte, so missing or false-low metadata cannot bypass enforcement. Exact-limit saves remain valid; oversized, endless, allocation, and provider failures retain the last valid snapshot as `STALE`.

Regressions cover pre-open metadata rejection, false metadata over an endless stream, an exact max-plus-one read bound, stale-snapshot retention, and a valid exact-maximum save with absent size metadata. An independent focused review found no concrete RUN-12 correctness defect.

## Synchronization

Stage 4 fetched `fork/master` before every checkpoint. Before the final implementation commit, `fork/master` had advanced by seven passive-insights commits to `a70648f6`. All uncommitted Stage 4 work, including untracked cancellation sources/tests, was retained in a named stash; `a70648f6` merged cleanly as `d4266680`; the stash reapplied without conflicts and was dropped only after the synchronized implementation was committed safely.

The synchronized source retains both the passive challenge expansion and QA hardening changes. No reset, overwrite, or unrelated-change discard occurred. `fork/master` was fetched again before commits `308ebb8a` and `7ad82ce6`, with zero commits missing from the synchronized branch.

## Verification evidence

| Scope | Command/evidence | Result |
| --- | --- | --- |
| Parser cancellation checkpoints | Exact `ParserCancellationTest`, `CandidateSelectorTest.cancellationStopsLazyCandidateConsumption`, and `CatalogParserTest.optionalResolverDoesNotConvertParserCancellationIntoUnavailableEvidence` | PASS in 31s |
| Android supersession, Area Guide, SaveRAM, and desktop runtime | Exact owning tests across `companion-core`, `companion-server`, and `app` | PASS in 4m58s |
| Parser schema red phase | `:catalog-store:test --tests '*revision 43 caches are invalidated*'` before the schema bump | EXPECTED FAIL at the revision assertion |
| Parser schema green phase | The same catalog-store regression after revision 44, plus the release schema guard | PASS |
| Required Stage 4 gate | `:parser-core:test :parser-cli:test :catalog-store:test :save-core:test :companion-core:test :app:testDebugUnitTest --parallel --stacktrace` | PASS, `BUILD SUCCESSFUL` in 28m46s |
| Diff/worktree integrity | `git diff --check`; clean synchronized branch after pushed checkpoints | PASS |

One initially overbroad filtered command selected the whole `CatalogParserTest` class and was terminated rather than spending more time on an unintended development matrix; exact cancellation methods then passed. The first closure gate was also stopped when the specification reread found the missing schema decision, so completion was not claimed on stale source. The table records the single final gate on revision 44. No emulator, ADB gesture, physical-device run, release signing, candidate creation, or candidate promotion occurred.

## Invariant review

- `INV-01`: checkpoint, optional parser data, compressed assets, corrupt snapshots, SaveRAM reads, and Area Guide projection fail closed at module scope while retaining independently valid state.
- `INV-02`: parser task generation/token fences prevent stale persistence/publication; Stage 4 does not weaken the Stage 2 content/session authority boundary.
- `INV-03`: snapshot quarantine removes only malformed recovery rows, and canonical catalog invalidation remains separate from durable snapshot storage.
- `INV-04`: current-stage raw/archive/cache/inflate/LZ77/CLI/SaveRAM/projection/parser workloads have explicit byte, count, queue, or checkpoint limits.
- `INV-05`: Area Guide availability is explicit and recoverable. Cross-server JSON error parity and reconnect behavior remain assigned to Stage 6.
- `INV-06`: parser-output-affecting optional-data isolation advances the cache schema to 44 and is guarded by release tests. Stage 4 publishes no candidate or compatibility claim.

## Missing-feature classification

### Blockers

None. Every current-stage requirement and acceptance condition has an owning regression on synchronized source, and the required Stage 4 gate passes.

### Tracked referrals

#### S4-REF-01 — Runtime recovery and session freshness (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `AND-03`, `RUN-03`–`RUN-11`, `RUN-13`; `INV-01`, `INV-02`, `INV-04`
- **Affected modules:** Android storage/session/save/live/battle/mapper paths, RetroArch session, memory mapper, battle memory
- **Reason:** Explicitly assigned to Stage 5; Stage 4 establishes the bounded source and persistence foundations but does not claim runtime freshness/recovery behavior.
- **Target:** Stage 5
- **Dependency:** Verified Stage 2 identity/epoch fences and Stage 4 untrusted-input bounds
- **Acceptance:** Revoked/stale authority cannot drive work; config/live/mapper transports recover within deterministic bounds; idle polling stops; delayed replies cannot cross captures; retained arrays cannot be mutated externally.

#### S4-REF-02 — Companion transport and browser coverage (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `WEB-01`–`WEB-09`, `REL-04`; `INV-04`, `INV-05`
- **Affected modules:** Android and desktop servers, companion web/simulator, CI/release browser jobs
- **Reason:** Explicitly assigned to Stage 6; Area Guide structured availability does not replace cross-server error parity, transport limits, reconnect, route, media, or browser coverage.
- **Target:** Stage 6
- **Dependency:** Stable Stage 5 runtime authority and the reusable packaged Android bench
- **Acceptance:** Socket/SSE work is bounded, API errors are structurally identical, polling reconnects without duplicate timers, routes/media remain catalog-correct, missing assets return 404, and portable nonprivate Chromium tests pass.

#### S4-REF-03 — Remaining UX, privacy, evidence, and governance (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `AND-04`–`AND-07`, `REL-05`–`REL-10`; `INV-05`, `INV-06`
- **Affected modules:** Android UX/settings/storage, diagnostics, Gradle dependencies, release evidence/protection, readiness documentation
- **Reason:** Explicitly assigned to Stage 7 and independent of current-stage resilience closure. Stage 4 makes no release claim and exposes only bounded Area Guide stage/class diagnostics.
- **Target:** Stage 7
- **Dependency:** Completed correctness, resilience, runtime, and browser stages
- **Acceptance:** Every named picker/rescan/settings, privacy, evidence-binding, dependency, process-exit, protection-audit, and readiness requirement passes its specified acceptance fixture.

#### S4-REF-04 — Integrated invariant closure (`TRACKED_REFERRAL`)

- **Requirements/invariants:** all `INV-*` requirements and all prior referrals
- **Affected modules:** project-wide
- **Reason:** Explicitly assigned to the final integrated audit rather than referred from any missing Stage 4 requirement.
- **Target:** Stage 8
- **Dependency:** Stages 5–7
- **Acceptance:** Every specification requirement and referral is closed on synchronized HEAD and the complete integrated gate passes without blockers.

## Final decision

`COMPLETE` — Stage 4 has no blockers. It is eligible to merge as a completed checkpoint and proceed to Stage 5. No candidate was created, signed, promoted, or published.
