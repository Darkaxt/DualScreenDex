# QA Hardening Stage 3 Closure

**Decision:** `COMPLETE`

**Stage branch:** `qa/project-wide-hardening`

**Synchronized baseline:** `997091f0` (`fork/master`)

**Stage head before this report:** `5e17e857`

**Requirements:** `REL-02`, `REL-03`

## Completed requirements

### REL-02 — Reconcile candidate draft and validation policy

Commit `1d34a785` makes every release candidate a nonpublic draft prerelease. Final releases retain their existing nondraft behavior.

The new protected `promote-candidate.yml` workflow accepts only an existing draft candidate and changes only its `draft`/`prerelease` flags. Before promotion it:

- downloads the already-published APK, provenance, and checksum list without building, signing, uploading, replacing, or creating a release;
- verifies the actual APK bytes against the promotion record, provenance, and `SHA256SUMS.txt`;
- runs `apksigner verify --verbose --print-certs` and requires the cryptographically verified signer to match provenance, the promotion record, and the pinned public certificate fingerprint;
- binds the signing workflow to the exact repository, workflow path, source commit, successful completion, and run URL;
- requires either immutable packaged-Android evidence plus an exact-artifact physical Thor record, or the explicitly authorized passive-catalog substitution with retained source-bound evidence and at least five unique exact ROM SHA-256 controls;
- re-fetches the release immediately before promotion and requires the original release ID and APK asset ID to remain unchanged.

Release policy tests cover successful evidence in both modes and reject mismatched APKs/signers, wrong physical identities, missing workflow evidence, incomplete outcomes, and missing immutable artifact digests. Documentation now describes draft creation and exact-artifact promotion rather than public-before-validation candidates.

The live `release-promotion` GitHub environment was reverified after implementation:

- required reviewer: `Darkaxt`;
- custom deployment policy: branch `master` only;
- environment secrets: zero;
- production `release-signing` remains separate and tag-protected.

### REL-03 — Add reusable packaged Android/WebView acceptance

Commits `1d34a785`, `ae5acb22`, `92edc7ea`, and `5e17e857` add and stabilize the reusable `packaged-android.yml` workflow and `qaApi35` Gradle Managed Device.

CI and release both invoke the reusable workflow. Release signing depends on its success, so no signed candidate can be created after a packaged-acceptance failure. The workflow installs the debug target and test APK on an API 35 AOSP managed device, runs `:app:qaApi35DebugAndroidTest`, verifies JUnit XML and all bounded screenshots, then uploads immutable evidence and diagnostic results artifacts.

`PackagedAcceptanceInstrumentedTest` exercises the actual packaged application and WebView:

- bundled WebView bootstrap and named JavaScript asset delivery;
- loopback health and state routes;
- monotonic native-to-web state revision and `sinceVersion` consumption;
- Settings-return storage/index failure and recovery through an injected permission/root facade;
- visible sanitized index failure in the packaged WebView;
- missing-asset HTTP 404;
- manual invalid-source containment;
- deterministic sanitized guide failure, visible retry route, native route interception, and recovery;
- real Android SQLite catalog persistence and cache reopen from synthetic raw GBA bytes;
- active bootstrap catalog identity after reopen.

The custom instrumentation application injects only deterministic storage and guide-failure state. Production storage remains Android-backed, and no ADB/appops mutation or signing material is used.

Final GitHub run [33068537786](https://github.com/Darkaxt/DualScreenDex/actions/runs/33068537786) passed on exact source commit `5e17e857cb36dbd133b15afab16d94ee77076183`:

- Windows all-module/unit/build job: `SUCCESS`;
- packaged Android managed-device job: `SUCCESS`;
- managed-device JUnit: 4 tests, 0 failures, 0 errors, 0 skipped;
- packaged acceptance test duration: 8.007 seconds;
- immutable evidence artifact digest: `sha256:2e5a29069b7e5dffd8e95480afb70a0a4168a8317a8383c4c498db581a542983`;
- results artifact digest: `sha256:031120d19f638b885389e7b56631e2caf8fa8fc104e1c1de34c0c1cf4357831d`;
- screenshots: `01-packaged-bootstrap.png`, `02-index-failure.png`, `03-guide-failure.png`, `04-cache-reopen.png`.

The first source-bound run failed rather than producing false evidence: it found one stale pre-snapshot-separation catalog test and one instrumentation assertion that expected an internal index message rather than the sanitized UI contract. The focused corrections retained the independent snapshot tests, asserted the real packaged UI text, and added deterministic guide-failure recovery. The final run then passed without suppressing either gate.

## Synchronization

Stage 3 began from `997091f0`, identical to `fork/master`. Before every commit, `fork/master` was fetched and compared; it did not advance during implementation. No reset, overwrite, or unrelated-change discard occurred.

## Verification evidence

| Scope | Command/evidence | Result |
| --- | --- | --- |
| Candidate and promotion policy | `node --test tools/release/*.test.mjs` | PASS, 33 tests |
| Android test sources and custom runner | `./gradlew :app:compileDebugAndroidTestKotlin --stacktrace` | PASS |
| Corrected revision-34 cache regression | `./gradlew :catalog-store:test --tests '*revision 34 caches are invalidated so GB map scenes and trainer assets are rebuilt' --stacktrace` | PASS |
| Complete CI/unit/build gate | GitHub run `33068537786`, job `test` | PASS |
| Installed APK/WebView managed device | GitHub run `33068537786`, `:app:qaApi35DebugAndroidTest` | PASS, 4/4 |
| Published JUnit and bounded screenshots | `dualdex-packaged-android-results` artifact | PRESENT |
| Source-bound immutable evidence | `dualdex-packaged-android-evidence` artifact | PASS, exact source/task/package/screenshots |
| Live promotion protection | GitHub environment API | reviewer present, `master` only, zero secrets |

Local verification stayed focused. The only complete broad matrix was the required GitHub stage gate; no ad hoc emulator, ADB gesture, physical-device run, candidate creation, signing, or promotion occurred.

## Invariant review

- `INV-01`: packaged index/guide failures remain module-local and recoverable; no partial catalog becomes authoritative.
- `INV-02`: Stage 3 does not weaken the Stage 2 identity/epoch authority boundary.
- `INV-03`: the stale catalog regression was corrected to the separate snapshot architecture; the dedicated snapshot durability tests remain authoritative.
- `INV-04`: packaged acceptance uses bounded HTTP waits, managed-device timeout, screenshots, and artifacts; later untrusted-input bounds remain assigned to Stages 4–6.
- `INV-05`: Stage 3 validates current loopback/WebView behavior; cross-server structured error parity remains assigned to Stage 6.
- `INV-06`: candidates remain nonpublic until protected exact-artifact evidence validation, and signing is gated by installed-package acceptance.

## Missing-feature classification

### Blockers

None. Both current-stage requirements and their acceptance conditions pass on synchronized source.

### Tracked referrals

#### S3-REF-01 — Catalog and untrusted-input resilience (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `CAT-03`–`CAT-14`, `AND-08`, `RUN-12`; `INV-01`, `INV-03`, `INV-04`
- **Affected modules:** parser, catalog store, Android guide/save projection, parser CLI
- **Reason:** Explicitly assigned to Stage 4; Stage 3 neither expands nor claims these bounds.
- **Target:** Stage 4
- **Dependency:** Stage 2 snapshot isolation and Stage 3 complete CI/package gate
- **Acceptance:** Every specified parser, persistence, archive, decompression, cache, projection, and SaveRAM bound fails closed while retaining independently valid state.

#### S3-REF-02 — Runtime recovery and freshness (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `AND-03`, `RUN-03`–`RUN-11`, `RUN-13`; `INV-01`, `INV-02`, `INV-04`
- **Affected modules:** Android storage/session/save/live/battle/mapper paths
- **Reason:** Explicitly assigned to Stage 5 and dependent on Stage 2 identity fencing.
- **Target:** Stage 5
- **Dependency:** Verified identity/epoch gate and Stage 4 input bounds
- **Acceptance:** Revoked/stale authority cannot drive work; config/live/mapper transports recover within bounds; idle polling stops; retained arrays cannot be mutated externally.

#### S3-REF-03 — Companion transport and browser coverage (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `WEB-01`–`WEB-09`, `REL-04`; `INV-04`, `INV-05`
- **Affected modules:** Android/desktop servers, companion web/simulator, CI/release
- **Reason:** Explicitly assigned to Stage 6; Stage 3 establishes the packaged Android bench it will extend.
- **Target:** Stage 6
- **Dependency:** Stable runtime state and the reusable packaged workflow
- **Acceptance:** Bounded sockets/SSE, structured parity, reconnect recovery, persistent routes, versioned media, asset 404s, and portable nonprivate Chromium tests pass.

#### S3-REF-04 — UX, privacy, evidence, and governance (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `AND-04`–`AND-07`, `REL-05`–`REL-10`; `INV-06`
- **Affected modules:** Android UX, diagnostics, dependencies, release evidence, readiness documentation
- **Reason:** Explicitly assigned to Stage 7 and does not invalidate truthful candidate promotion or installed-package acceptance.
- **Target:** Stage 7
- **Dependency:** Earlier correctness, resilience, and browser stages
- **Acceptance:** Every named picker/rescan/settings, privacy, evidence-binding, dependency, protection-audit, and readiness fixture passes.

#### S3-REF-05 — Integrated invariant closure (`TRACKED_REFERRAL`)

- **Requirements/invariants:** all `INV-*` requirements and all prior referrals
- **Affected modules:** project-wide
- **Reason:** Explicitly assigned to the final integrated audit.
- **Target:** Stage 8
- **Dependency:** Stages 4–7
- **Acceptance:** Every specification requirement and referral is closed on synchronized HEAD and the complete integrated gate passes without blockers.

## Final decision

`COMPLETE` — Stage 3 has no blockers. It is eligible to merge as a completed checkpoint and proceed to Stage 4. No candidate was created, signed, promoted, or published.
