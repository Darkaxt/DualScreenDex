# QA Hardening Stage 7 Closure

**Decision:** `COMPLETE`

**Evidence source:** `66bd216d9c370735666d5eb7438e83796a87eec7`

**Synchronized baseline:** `3290406a561e382203746675e3f669e3d2d6c6e2` (`fork/master`), which is an ancestor of the evidence source.

**Requirements:** `AND-04`–`AND-07`, `REL-05`–`REL-10`; post-hardening remediation record `S7-BLK-01`.

## Requirement closure

| Requirement | Implementation and acceptance evidence | Verdict |
| --- | --- | --- |
| `AND-04` | `0c8f434e` routes overlay requests through production route mappings and an exactly-once activity-result registry. `OverlayPickerDeliveryInstrumentedTest` and `SetupPickerRequestTest` own cold/new-intent and duplicate-delivery regressions. | `COMPLETE` |
| `AND-05` | Existing safe rescan behavior retains the last valid direct/SAF index until replacement commit; the Stage 4 storage regressions and the consolidated post-remediation app gate passed. | `COMPLETE` |
| `AND-06` | `41d25d1d` resolves package-specific settings, falls back safely, and returns explicit terminal outcomes. `AllFilesSettingsLauncherTest` owns unavailable-intent behavior. | `COMPLETE` |
| `AND-07` | Current setup guidance states that protected `Android/data` and `Android/obb` content needs public shared storage or the supported folder picker rather than implying universal All Files access. | `COMPLETE` |
| `REL-05` | `8bfaedd2` added source/generator/raw-report lineage, canonical multiset digest, cache decision, error-channel, persistence/reopen, and Stage 7/8 closure validation. This checkpoint corrects the denominator to the actual 333 scanner-eligible inputs in the 334-file physical inventory and streams the 1.63 GB raw report instead of exceeding Node's string limit. | `COMPLETE` |
| `REL-06` | `c2362789`, `41d25d1d`, and the post-hardening privacy tests remove reversible player-state fingerprints, paths, full hashes, raw messages, and stacks from normal diagnostics. | `COMPLETE` |
| `REL-07` | `0c8f434e` makes architecture ownership declaration-aware, ignores commented dependencies, recognizes `fun interface`, and verifies direct project imports against active direct dependencies. | `COMPLETE` |
| `REL-08` | `41d25d1d` records only bounded prior-exit category/time/memory buckets through the injectable platform facade; focused exit-recorder tests passed. | `COMPLETE` |
| `REL-09` | `8bfaedd2` and the release workflows verify immutable tag rules, the tag-gated signing environment, the reviewer-gated default-branch promotion environment, and zero promotion-workflow signing-secret references through public nonsecret policy data without reading or publishing signing material. The release validator preserves the established single-maintainer approval model. | `COMPLETE` |
| `REL-10` | `docs/current-readiness.md` is the canonical current entry point; the RC9 matrix remains archived historical evidence. | `COMPLETE` |
| `S7-BLK-01` | The fresh source-bound execution reached all 333 eligible inputs with 0 parser, catalog, compatibility, or persistence errors. All 278 selected catalogs were materialized, persisted, closed, reopened, and decoded. | `CLOSED` |

## Final corpus evidence

The physical corpus inventory contains 334 supported-extension files. `CorpusScanner(includeAllRomNames = true)` intentionally excludes one known spin-off, leaving 333 eligible mainline/hack inputs. The owning scanner regression predates this run and confirms that `--all-roms` does not disable known-spin-off exclusion.

The release evidence is bound to:

- parser source commit `66bd216d9c370735666d5eb7438e83796a87eec7`;
- parser schema 13 and generator digest `5a19f4866925c4310e7c1938acea127c937118c4d0186a354a4da90b94585879`;
- raw-report digest `6c6e66d371544f16b66099085ce94c715d387e253878227329a76d46ae9db414`;
- canonical 333-entry multiset digest `974500a1b568bab72954e253c0a1efae25ea4208657804ed5f81ff3409e79d57`;
- 331 unique ROM byte identities. Repeated byte-identical inputs remain separate multiset entries rather than being silently deduplicated.

Results:

- parser outcomes: 278 selected, 2 ambiguous, 53 no family match, 0 errors;
- compatibility outcomes: 20 complete, 302 partial, 11 unresolved, 0 errors;
- catalogs: 278 materialized and 278 persisted/reopened, with 0 catalog or persistence errors.

The aggregate evidence contains no ROM identity, ROM name, source path, or ROM bytes. The retained private raw report is not a release asset.

A post-run inventory comparison found the same 333 eligible names and one intentional exclusion. Two external corpus copies changed bytes after the completed execution, so the immutable parser receipt and its raw-report multiset—not the subsequently mutated working directory—remain authoritative for what was measured. This does not relabel or combine old evidence.

## Verification evidence

| Scope | Result |
| --- | --- |
| Post-remediation consolidated Gradle gate | `BUILD SUCCESSFUL` in 40m36s; 65 tasks, including parser, catalog, CLI, runtime, companion, and app unit suites. |
| Companion web gate | 32 Vitest files / 268 tests passed; TypeScript/Vite production build passed. |
| Release/governance gate at source stabilization | 80 tests passed. |
| Current release/governance gate after closure packaging | 83 Node tests passed. |
| Fresh corpus summarization | Streaming raw-report hash, source/generator lineage, canonical multiset, terminal outcomes, and persistence/reopen checks passed. |

The expensive parser and consolidated Gradle gates ran once after parser/catalog product-source stabilization. They were not repeated for downstream release packaging, acceptance-test stabilization, the later replacement of two Android API-33-only bounded reads with the shared API-30-compatible reader, repository-policy alignment with the established single-maintainer signing process, correction of GitHub policy reads to use public nonsecret endpoints instead of an integration token lacking administration scope, or binding the comparison range to the cache decision's prior parser schema. Focused checkpoint/save tests, the source-contract regression, Android test compilation, app lint, the PR managed-device suite, and all 83 release-governance tests passed after those corrections. `NONPARSER_REUSE` is explicit and does not permit parser, catalog, build, wrapper, or corpus-execution changes.

## Missing-feature classification

### Blockers

None.

### Tracked referrals

None. Every historical referral in the Stage 1 ledger is closed by the terminal remediation matrix.

## Final decision

`COMPLETE` — Stage 7 has zero blockers and zero referrals. No candidate, tag, APK, signing operation, promotion, or release is authorized by this document.
