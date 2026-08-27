# QA Hardening Stage 5 Closure

**Decision:** `COMPLETE`

**Stage branch:** `qa/project-wide-hardening`

**Synchronized baseline:** `d4ba6b3e` (`fork/master`)

**Stage implementation:** `ccffefb3`

**Requirements:** `AND-03`, `RUN-03`–`RUN-11`, `RUN-13`

## Completed requirements

### AND-03 — Persisted SAF grant eligibility

Stored SAF entries participate in startup loading, session resolution, and guide retry only while the exact stored tree URI has a current readable persisted grant. A revoked grant quarantines the cached in-memory entries, invalidates the session epoch, cancels activation and pending catalog work, stops battle and SaveRAM authority, clears active content identity, and publishes missing ROM access without deleting the recoverable on-disk index.

A later grant transition reloads the retained stored index. Exact URI matching is covered by `StoredSafIndexEligibilityTest`; the production revocation, retry, quarantine, and restoration paths were included in the final blocker review.

### RUN-03 — Fresh SaveRAM content identity

Save metadata now prioritizes the previous candidate but never proves content equality. Every candidate is bounded-read and SHA-256 hashed before `UNCHANGED`. Changed valid bytes become `CHANGED`; changed invalid bytes retain the last valid snapshot as `STALE`.

Regressions cover unchanged reuse after a fresh read, same-metadata valid replacement, and same-metadata invalid replacement with retained recovery.

### RUN-04 — Durable RetroArch configuration transactions

Config installation now records original and intended SHA-256 identities with monotonic `PREPARED`/`APPLIED` revisions, preserves the first verified recovery until commit, resumes interrupted writes, and restores verified original bytes before retry. Direct-file replacement uses a synchronized temporary file and atomic move where supported.

SAF persistence uses alternating self-validating recovery and transaction generations. A malformed or partially written generation is ignored while the previous valid generation remains authoritative. Initial interrupted recovery writes can advance to the other slot instead of permanently blocking retry. Legacy sidecars remain readable and every owned generation is deleted only after verified commit.

Regressions cover write/readback failure, retry without backup replacement, process restart under `PREPARED`, already-configured ownership, partial recovery generations, and partial transaction generations.

### RUN-05/RUN-06 — Live-memory and command-status freshness

Terminal memory transport failure immediately closes the transport, records bounded metrics, marks the tracker missed, and suspends live authority. This includes failure of the initial synchronous send in the same heartbeat. Later valid samples can restore live authority.

Only a fresh non-malformed `GET_STATUS` resets content freshness. Config and unknown replies cannot retain stale status; expiry clears connection status and active-content authority.

Tests cover poll failure, initial send failure, later recovery, transport exception sanitization, and stale status under non-status traffic.

### RUN-07 — Atomic mapper identity authority

Mapper enable/capture requires an active connection, supported system/core, verified content SHA-256, session epoch, and mapper epoch. Identity is checked before capture and after memory completion. Snapshot history mutation and store persistence execute inside `SessionEpochGate.commitIfCurrent`, so an external session transition cannot occur between final validation and commit.

A rejected commit fence disables the mapper without recording a snapshot or writing a mapper session. Core/content changes require re-enable and cannot merge exports across identities.

### RUN-08 — Active-only schedulers

Battle polling starts only when the coordinator becomes eligible and uses one-shot scheduling. It cancels on disconnect, ineligibility, terminal failure, completion, and close. Mapper scheduling exists only while a capture is active and is cancelled on every terminal path.

Fake-scheduler regressions observe no idle callbacks and verify bounded operation ownership.

### RUN-09 — Recoverable command monitoring

`CommandMonitorLifecycle` removes and closes a failed monitor before retry, recreates a fresh monitor, resets failures after success, and applies capped exponential backoff from 2 seconds to 30 seconds. Failure publishes `RETRYING` and clears stale system, content, source, save-directory, and resolution authority.

The owning regression proves that a permanently failing first monitor is replaced by a succeeding second monitor without restart.

### RUN-10/RUN-11 — Bounded UDP work and capture ownership

Core-memory and network-command polling enforce per-heartbeat packet quotas, carry remaining packets forward, and publish packet, ignored-packet, and quota-hit diagnostics. The default core-memory quota remains bounded while accommodating normal GBA discovery cadence.

Every mapper capture owns a fresh UDP transport. All completion, cancellation, disable, identity-failure, exception, and close paths close that transport, so delayed replies from a cancelled capture cannot satisfy the next capture.

Tests cover ten thousand irrelevant packets, quota carryover, eventual matching replies, fresh transport ownership, and delayed-reply isolation.

### RUN-13 — Detached retained memory

Public core-memory completions clone region buffers, and mapper snapshots, records, and exports deeply detach retained `ByteArray` values. Mutating returned data cannot alter later hashes, diffs, snapshots, or exports.

## Synchronization

Stage 5 began after the Stage 4 closure and was first fast-forwarded to `5edfab5c`. Before final verification it was smart-synced again to `d4ba6b3e`; the incoming README and passive-insights documentation did not overlap Stage 5 production or test paths. A final fetch immediately before the implementation commit confirmed `HEAD`, `fork/master`, and their merge base were all `d4ba6b3e`.

No reset, overwrite, or unrelated-change discard occurred. The synchronized implementation was committed as `ccffefb3` and pushed to `fork/qa/project-wide-hardening`.

## Verification evidence

| Scope | Command/evidence | Result |
| --- | --- | --- |
| Focused Stage 5 regressions | Exact config, session, network, core-memory, mapper-session, SaveRAM, file-store, command-lifecycle, epoch-gate, SAF-eligibility, mapper-coordinator, and battle-coordinator suites | PASS, `BUILD SUCCESSFUL` in 14s after final mapper fixes |
| Recovery generation fix | Exact `RetroArchConfigInstallerTest` plus `FileRetroArchConfigStoreTest` | PASS, `BUILD SUCCESSFUL` in 17s |
| Android instrumentation source | `:app:compileDebugAndroidTestKotlin --stacktrace` | PASS, `BUILD SUCCESSFUL` in 14s |
| Blocker-focused review | SAF revocation, immediate initial-send failure, durable transaction/recovery slots, mapper epoch commit | All identified blockers fixed; final targeted recovery review found no remaining concrete issue |
| Required Stage 5 gate, completed modules | `:retroarch-session:test :memory-mapper-lab:test` from the exact gate invocation | PASS before the host killed the process after its ten-minute foreground allowance |
| Required Stage 5 gate, unfinished modules | `:battle-memory:test :app:testDebugUnitTest --parallel --stacktrace` on unchanged synchronized source | PASS, `BUILD SUCCESSFUL` in 31m03s |
| Diff integrity | `git diff --check` before commit | PASS |

The exact combined gate was externally killed without a test failure after RetroArch and mapper tests had completed and battle/app work had begun. To avoid repeating completed work, only the unfinished battle-memory and app suites were resumed on identical source. Together the two invocations cover every task in the specified Stage 5 gate. No additional broad matrix was run.

No emulator, ADB gesture, physical-device run, release signing, candidate creation, candidate promotion, or RC publication occurred.

## Specification reread and invariant review

The full project-wide specification was reread after implementation stabilized.

- `INV-01`: revoked grants, SaveRAM failure, config interruption, memory transport failure, mapper mismatch, and command-monitor failure disable only their owning modules while retaining independently valid recovery.
- `INV-02`: SaveRAM hashes, fresh status, verified SHA-256, mapper/session epochs, and atomic commit fencing prevent stale or cross-session live authority.
- `INV-03`: Stage 5 does not move user recovery into parser-cache ownership; SaveRAM and config recovery remain separately retained until verified replacement/commit.
- `INV-04`: SaveRAM reads retain Stage 4 bounds, UDP work has explicit packet quotas, schedulers are active-only, and retained arrays are detached at public boundaries.
- `INV-05`: retrying/unavailable runtime states remain structured; cross-server JSON parity, reconnect UI, and browser navigation remain assigned to Stage 6.
- `INV-06`: Stage 5 changes runtime behavior only, makes no parser-output compatibility claim, and publishes no candidate.

## Missing-feature classification

### Blockers

None. Every current-stage requirement is implemented on synchronized source, focused ownership tests pass, the complete Stage 5 gate passes cumulatively after the host interruption, and the final blocker review is closed.

### Tracked referrals

#### S5-REF-01 — Companion transport, navigation, and browser coverage (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `WEB-01`–`WEB-09`, `REL-04`; `INV-04`, `INV-05`
- **Affected modules:** Android and desktop servers, companion web/simulator, CI/release browser jobs
- **Reason:** Explicitly assigned to Stage 6; no current-stage requirement is being deferred.
- **Target:** Stage 6
- **Dependency:** Stable Stage 5 runtime authority and existing packaged Android acceptance bench
- **Acceptance:** Socket/SSE work is bounded, API errors are structurally identical, polling reconnects without duplicate timers, routes/media remain catalog-correct, missing assets return 404, simulator keys are unique, and portable nonprivate Chromium tests pass.

#### S5-REF-02 — Remaining UX, privacy, evidence, and governance (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `AND-04`–`AND-07`, `REL-05`–`REL-10`; `INV-05`, `INV-06`
- **Affected modules:** Android UX/settings/storage, diagnostics, Gradle dependencies, release evidence/protection, readiness documentation
- **Reason:** Explicitly assigned to Stage 7 and independent of Stage 5 runtime closure.
- **Target:** Stage 7
- **Dependency:** Completed correctness, resilience, runtime, and browser stages
- **Acceptance:** Every named picker/rescan/settings, storage-documentation, privacy, evidence-binding, dependency, process-exit, protection-audit, and readiness fixture passes its specification acceptance condition.

#### S5-REF-03 — Integrated invariant closure (`TRACKED_REFERRAL`)

- **Requirements/invariants:** all `INV-*` requirements and all prior referrals
- **Affected modules:** project-wide
- **Reason:** Explicitly assigned to the final integrated audit rather than referred from a missing Stage 5 requirement.
- **Target:** Stage 8
- **Dependency:** Stages 6–7
- **Acceptance:** Every specification requirement and referral is closed on synchronized HEAD and the complete integrated gate passes without blockers.

## Final decision

`COMPLETE` — Stage 5 has no blockers. It is eligible to merge as a completed checkpoint and proceed to Stage 6. No candidate was created, signed, promoted, or published.
