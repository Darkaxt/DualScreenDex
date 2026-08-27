# QA Hardening Stage 2 Closure

**Decision:** `COMPLETE`

**Stage branch:** `qa/project-wide-hardening`

**Synchronized baseline:** `e8f45f52` (`fork/master`)

**Stage head before this report:** `da3069b4`

**Requirements:** `CAT-01`, `RUN-01`, `RUN-02`, `RUN-14`

## Completed requirements

### CAT-01 — Separate recovery snapshots from disposable parser databases

Commit `b7a61022` moved SaveRAM recovery snapshots into independently versioned `catalogs/save-snapshots/<sha>.sqlite` databases. `SaveSnapshotStore` migrates valid legacy rows before catalog cache use, while catalog schema rebuilds no longer create or drop snapshot tables. Production constructs and migrates the snapshot store before opening the catalog cache.

Owning regressions prove that inactive-cache cleanup, corrupt-catalog deletion, and a catalog schema rebuild leave the original recovery snapshot readable. Legacy snapshots migrate with their source and refresh timestamps intact.

### RUN-01 — Require verified live ROM identity

Commit `dbabb991` introduced `SessionResolution.Unverified`. Basename-only matches without a RetroArch CRC remain discovery evidence and never become the coordinator's authorized entry. The coordinator consequently stops battle authority, does not activate a guide automatically, and does not start SaveRAM polling. Explicit CRC mismatch still fails without basename fallback.

Resolver regressions cover raw/archive basenames, duplicate same-SHA sources, exact CRC matches, different hashes, and mismatched CRCs.

### RUN-02 — Fence queued work with a monotonic session epoch

Commit `18f82479` added `SessionEpochGate`, whose immutable work token contains the monotonic epoch, ROM SHA-256, and active source ID. Identity changes, loss of verified identity, and coordinator close invalidate every prior token.

Automatic guide activation, source reads, catalog callbacks, save discovery/refresh, SaveRAM reads and persistence, selection mutation, recovery publication, checkpoint/journal application, and error publication all revalidate the captured token. Stale activation ownership is cancelled without latching a false guide failure, and pending catalog publication is cancelled on identity loss or close. Persisted journals now restore only after recovery identity is accepted.

Owning regressions prove token invalidation across switch/loss/close, no snapshot persistence after expiry during a save read, no recovery read for an expired session, stale activation release, and no journal restore after rejected recovery identity.

### RUN-14 — Prevent save-cli output/input aliasing

Commit `da3069b4` validates all report targets before ROM/save evaluation. It resolves normalized absolute paths through existing parent ancestry, checks `Files.isSameFile` for hard links and existing aliases, rejects parent/child aliases, and rejects JSON/Markdown collisions.

Filesystem regressions cover normalized relative aliases, hard links, symlinked parents where supported, parent aliases, and output-to-output collisions while verifying source bytes remain unchanged.

## Synchronization

Stage 2 retained and merged all intervening `fork/master` work. The final synchronization merge is `c11c4d0e`; it adds unrelated UI-conformance documentation without touching Stage 2 production paths.

## Verification evidence

| Scope | Command | Result |
| --- | --- | --- |
| Snapshot isolation/migration | `./gradlew :catalog-store:test --tests 'com.darkaxt.dualdex.catalog.SaveSnapshotStoreTest'` | PASS, 5 tests |
| Verified live identity | `./gradlew :retroarch-session:test --tests 'com.darkaxt.dualdex.retroarch.RomSessionResolverTest'` | PASS |
| Epoch/save/journal fencing | `./gradlew :app:testDebugUnitTest --tests SavePollingMonitorTest --tests SessionEpochGateTest --tests GuideActivationGateTest --tests SaveKnowledgeCheckpointCoordinatorTest --tests ProductionCompanionRuntimeTest` | PASS in 10 s |
| CLI collision policy | `./gradlew :save-cli:test --tests SaveCliPathPolicyTest --tests SaveCliOptionsTest` | PASS |
| Production wiring | `./gradlew :app:compileDebugKotlin` | PASS |

Validation remained scoped to owning modules; the complete all-module matrix remains enforced by CI rather than repeated locally after each checkpoint.

## Invariant review

- `INV-01`: stale session work and rejected identity fail closed without false guide authority.
- `INV-02`: live/save authority now requires verified resolution plus a current immutable epoch token.
- `INV-03`: recovery snapshots now outlive disposable catalog cleanup and migration.
- `INV-04`: Stage 2 introduces no unbounded workload; remaining input bounds stay assigned to Stage 4/5.
- `INV-05`: no API error-contract change is introduced; cross-server standardization remains assigned to Stage 6.
- `INV-06`: Stage 2 does not alter candidate publication or release evidence.

## Missing-feature classification

### Blockers

None. All Stage 2 requirements and their acceptance boundaries are implemented on the synchronized branch.

### Tracked referrals

#### S2-REF-01 — Truthful release and packaged acceptance

- **Requirements:** `REL-02`, `REL-03`; related `INV-06`
- **Target:** Stage 3
- **Dependency:** Completed Stage 1 state/retry delivery and Stage 2 identity fencing
- **Acceptance:** Candidate remains nonpublic until exact signed-artifact promotion, and a reusable installed-APK/WebView/loopback managed-device matrix passes.

#### S2-REF-02 — Catalog and untrusted-input resilience

- **Requirements:** `CAT-03`–`CAT-14`, `AND-08`, `RUN-12`; related `INV-01`, `INV-03`, `INV-04`
- **Target:** Stage 4
- **Dependency:** Independent snapshot namespace established by `CAT-01`
- **Acceptance:** Every named parser, cache, archive, decompression, restore, projection, and SaveRAM bound fails closed while preserving valid catalog/recovery state.

#### S2-REF-03 — Runtime recovery and freshness

- **Requirements:** `AND-03`, `RUN-03`–`RUN-11`, `RUN-13`; related `INV-01`, `INV-02`, `INV-04`
- **Target:** Stage 5
- **Dependency:** Verified identity and epoch tokens from `RUN-01`/`RUN-02`
- **Acceptance:** Revoked grants/stale status cannot authorize work; config/live/mapper transports recover within bounds; idle polling stops; mutable arrays cannot alter retained state.

#### S2-REF-04 — Companion transport and browser coverage

- **Requirements:** `WEB-01`–`WEB-09`, `REL-04`; related `INV-04`, `INV-05`
- **Target:** Stage 6
- **Dependency:** Stable runtime identity and state revision contracts
- **Acceptance:** Shared structured API behavior, bounded sockets/SSE, recoverable polling, persistent routes, versioned media, asset 404s, and portable Chromium coverage pass.

#### S2-REF-05 — UX, privacy, evidence, and governance

- **Requirements:** `AND-04`–`AND-07`, `REL-05`–`REL-10`; related `INV-06`
- **Target:** Stage 7
- **Dependency:** Earlier correctness and recovery stages
- **Acceptance:** Every named specification fixture passes for picker/rescan/settings UX, diagnostic privacy, evidence binding, dependency declaration, protection auditability, and current-readiness documentation.

#### S2-REF-06 — Integrated invariant closure

- **Requirements:** `INV-01`–`INV-06` plus all prior referrals
- **Target:** Stage 8
- **Dependency:** Stages 3–7
- **Acceptance:** Every requirement and referral is closed on synchronized HEAD and the one complete integrated gate passes without blockers.

## Final decision

`COMPLETE` — Stage 2 has no blockers. Proceed to Stage 3. No RC is authorized or published by this closure.
