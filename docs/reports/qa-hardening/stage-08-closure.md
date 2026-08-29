# QA Hardening Stage 8 Integrated Closure

**Decision:** `COMPLETE`

**Evidence source:** `66bd216d9c370735666d5eb7438e83796a87eec7`

**Synchronized baseline:** `3290406a561e382203746675e3f669e3d2d6c6e2` (`fork/master`), an ancestor of the evidence source.

**Scope:** Every `INV-*`, `AND-*`, `CAT-*`, `WEB-*`, `RUN-*`, and `REL-*` requirement in `2026-08-27-project-wide-qa-hardening-design.md`; all staged referrals; all 39 post-hardening detections.

## Integrated requirement verdict

| Requirement group | Owning closure/evidence | Integrated verdict |
| --- | --- | --- |
| `INV-01`–`INV-06` | Stage 1–7 closure reports; post-hardening terminal remediation matrix; source-bound corpus and release-evidence policy | `COMPLETE` |
| `AND-01`–`AND-02` | Stage 1 plus `6c17322a`, `0c8f434e`, and packaged guide-failure acceptance | `COMPLETE` |
| `AND-03` | Stage 5 SAF grant quarantine and recovery regressions | `COMPLETE` |
| `AND-04`–`AND-07` | Stage 7 closure: exactly-once overlay picker, safe rescan, settings fallback, accurate protected-storage guidance | `COMPLETE` |
| `AND-08` | Stage 4 plus `6b99a53f`: bounded Area Guide retention and module-local projection failure | `COMPLETE` |
| `CAT-01`–`CAT-02` | Stages 1–2: recovery snapshot separation and parser schema invalidation | `COMPLETE` |
| `CAT-03`–`CAT-14` | Stage 4 plus `9d825e13`/`b5bfd81f`: isolated persistence, canonical serialization, cancellation, identity, malformed optional data, source/archive/cache/LZ77/CLI bounds, digest verification, quarantine, and terminal cache restore | `COMPLETE` |
| `WEB-01`–`WEB-09` | Stage 6 plus `6b99a53f`: bounded Android/desktop transport, fenced navigation/state/media, structured retry, SSE, static 404, sprite availability, simulator identity, and server parity | `COMPLETE` |
| `RUN-01`–`RUN-02` | Stage 2 plus `6c17322a`: verified live identity and monotonic epoch fencing | `COMPLETE` |
| `RUN-03`–`RUN-13` | Stage 5 plus `6c17322a`/`41d25d1d`: SaveRAM identity, recoverable config, terminal live invalidation, command status/socket recovery, mapper identity, idle-poll elimination, UDP bounds, delayed-reply fencing, bounded reads, immutable snapshots | `COMPLETE` |
| `RUN-14` | Stage 2 save-cli path alias rejection | `COMPLETE` |
| `REL-01` | Stage 1 complete JVM/app PR matrix and post-remediation consolidated Gradle gate | `COMPLETE` |
| `REL-02`–`REL-03` | Stage 3 protected draft/promotion policy and reusable packaged Android/WebView acceptance | `COMPLETE` |
| `REL-04` | Stage 6 portable nonprivate Chromium suite in CI/release | `COMPLETE` |
| `REL-05`–`REL-10` | Stage 7 source-bound evidence, privacy, direct-dependency architecture, minimal exit classification, repository policy audit, and current-readiness index | `COMPLETE` |

All 61 named requirements have terminal ownership. No requirement is omitted, reopened, or referred.

## Post-hardening remediation reconciliation

The project-wide post-hardening review produced 32 blocker-class records and 7 referral-class records. `post-hardening-remediation-closure.md` maps every record to implementation and regression ownership:

- all 32 blocker-class records are remediated;
- all 7 referral-class records are closed;
- `S7-BLK-01` is closed by the fresh 333-eligible-input corpus;
- no current matrix status is `BLOCKER`, `PENDING_FINAL_CORPUS`, or `REFERRED`.

The denominator correction does not waive an input. The audited physical inventory contains 334 supported-extension files; the pre-existing scanner policy intentionally excludes one known spin-off and evaluates all 333 eligible inputs. Canonical schema 2 binds the 333-entry multiset, its 331 unique byte identities, and duplicate entries without silently deduplicating them.

## Integrated verification evidence

| Gate | Evidence | Verdict |
| --- | --- | --- |
| Post-remediation Gradle matrix | Parser core/CLI, catalog, RetroArch, mapper, battle, companion core/server/simulator, and app unit tests: `BUILD SUCCESSFUL` in 40m36s, 65 tasks | `PASS` |
| Release/governance matrix after closure packaging | 83 Node tests | `PASS` |
| Companion browser unit/build matrix | 32 Vitest files / 268 tests; TypeScript/Vite build | `PASS` |
| Portable browser E2E after closure packaging | `npm run test:e2e:ci`: 3 tests in 15.4s | `PASS` |
| Packaged Android/WebView | Stage 3 source-bound GitHub managed-device run: 4/4 tests with immutable JUnit/screenshot evidence | `PASS` |
| PR packaged Android acceptance after final test stabilization | 7/7 managed-device tests passed, including canonical overlay picker delivery and production guide retry | `PASS` |
| Final corpus | 333/333 eligible inputs terminal; 0 parser/catalog/compatibility/persistence errors; 278/278 selected catalogs persisted and reopened | `PASS` |
| Downstream evidence hardening | Bounded 1.63 GB streaming summary, corrected denominator, duplicate-aware canonical contract, promotion/readiness/privacy regressions | `PASS` |

The consolidated Gradle and hours-long parser gates ran once after parser/catalog product-source stabilization. They were not repeated for downstream release packaging, deterministic acceptance-test corrections, the replacement of two Android API-33-only read calls with the already-tested API-30-compatible bounded reader, repository-policy alignment with the established single-maintainer signing process, or correction of GitHub policy reads to public nonsecret endpoints. Focused checkpoint/save tests, source contracts, Android test compilation, app lint, the 7/7 PR managed-device suite, and the complete release-governance suite passed afterward. The evidence validator rejects reuse if parser/catalog sources, build logic, wrapper, or corpus-execution tooling changes.

No ad hoc emulator, ADB gesture, physical-device action, credential inspection, signing-material inspection, signing, tagging, or publication was performed for this closure.

## Referral closure

Historical stage referrals were dependency pointers, not unowned deferrals. Their acceptance conditions are closed as follows:

- Stage 1 referral ledger completeness: terminal lint and mutation fixture;
- parser/catalog/archive/snapshot referrals: Stages 4–5 and the post-hardening matrix;
- runtime freshness referrals: Stage 5 and `6c17322a`;
- companion/browser referrals: Stage 6 and `6b99a53f`;
- UX/privacy/evidence/governance referrals: Stage 7;
- integrated invariant referral: this report.

### Blockers

None.

### Tracked referrals

None.

## Final decision

`COMPLETE` — Stage 8 closes with zero blockers and zero referrals. The source is eligible for protected release preparation. This report does not itself create a tag, sign an APK, publish a candidate, promote an artifact, or authorize bypassing GitHub protection.
