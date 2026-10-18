# Corpus / issue33 release qualification — October 18, 2026

Status: **IN_PROGRESS — corpus, hosted Android and signed RC4 verification complete. The user's October18 physical-validation waiver resumes protected publication; no physical pass is claimed.**

## Packaged acceptance closure

After specific user authorization, [hosted run39115516225](https://github.com/Darkaxt/DualScreenDex/actions/runs/39115516225) executed the existing API35 AOSP managed-device workflow at the exact frozen source36d6c2d4. All **11 cases passed**, with zero failures/errors/skips; actual JUnit SHA256 `11f100f231ce0eef3591435ad10b5dde683e1c6548eb85af118fd7b9aba6621d`. All four required downloaded screenshots were inspected. The job and its disposable runner completed; no physical or shared local device was used. This is debug packaged acceptance, not production signing or signed-candidate device validation.

A bounded read of the already-audited generated language manifests independently counts286 resolved/15 unknown catalogs and verifies their manifest digests and overlay presence. The [new source-bound localization summary](2026-10-18-full342-localization-evidence.json) preserves the same corpus execution/receipt and adds this census plus actual packaged evidence. The earlier schema2 aggregate remains unchanged as historical preparation evidence.

The old release validator's exact-eight assertion was stale: the current eleven-case suite retains the original eight and adds guide-failure isolation plus two picker-delivery controls. A focused RED reproduced rejection of the genuinely passed11 profile. The prospective policy now accepts only the known8/11 profiles, still rejecting unknown counts, fractional/string counts and every failure/error/skip; no source/hash/closure or signed-promotion requirement changes. All26 focused evidence/metadata tests pass. This corrects assistant-maintained qualification bookkeeping without inventing an eight-test result or rerunning Android/corpus suites.


The [execution receipt](2026-10-18-full342-execution.json) and [aggregate summary](2026-10-18-full342-evidence.json) bind one complete qualification to frozen source `36d6c2d4ee690e9069cc0af6d1ddb48f9ad4fc89`, parser revision 139, SQLite schema 2 and report schema 16. They do not combine earlier affected runs into a fictitious full execution. Later release-summary tooling, challenge expectations and documentation do not change that parser/catalog runtime.

## Completed qualification

| Gate | Inspected outcome |
|---|---|
| Fresh canonical corpus | 342 distinct inputs; 301 selected, 2 ambiguous, 39 unmatched; zero execution/catalog/persistence errors. Originals passed both raw hash/stat checks unchanged. |
| Independent persisted audit | All 301 catalogs / 5,403 sections / 10,296,409 checked references; zero integrity, logical-digest or checked-reference errors. |
| Latest-accepted baseline reconciliation | All 342 rows compared; 300/301 normalized whole payloads match exactly. The remaining payload differs only in zero confidence for an empty unresolved ability domain, with no lost records. A second identity has only more precise diagnostic wording. All three audit flags are individually adjudicated; no new production regression remains open. |
| Initial regression investigation | All 64 original observations closed: 15 reporting-only, 4 unresolved-only, 23 repaired/revalidated, 22 corrected-domain. Rejected historical heuristics are not restored as support. |
| Guarded full host / security / build | 3,600 passed, 303 explicitly skipped, zero failures/errors; toolchain, dependency security, lint and debug assembly passed. Optional original controls are not counted passing. |
| Web and production browser | 365 web tests and 5 production-browser checks passed, with screenshots inspected. The source-owned height artwork fix removes the reproduced reset race. |
| Final generated-catalog app binding | 11 host tests passed, zero skips/failures/errors; actual app reader, role resolver and binder consume copies of final SQLite outputs without opening originals or rerunning the parser. |
| Release-policy tooling | 97 tests passed. Reproduced schema-16 summary rejection is repaired with report/receipt schema equality and all existing source, digest and privacy checks preserved; legacy schema 13 remains supported. |

The independent audit deliberately exited 1 when it found three comparison flags. Its original output remains retained; the subsequent exact adjudication closes those flags without rewriting the failed audit or changing historical/catalog data. The earlier full-batch attempt interrupted by host memory pressure remains a consumed, incomplete attempt, not accepted evidence. The completed qualification used a separately reviewed fresh reservation after the user resumed work.

### Challenge domains

An independent census verifies every eligible area and collection flag before the actual app binding check. Historical fixtures are corrected only where that evidence establishes staleness.

| Control | Challenges | Eligible collectible areas | Species | Badges | Leader/adapter challenges |
|---|---:|---:|---:|---:|---:|
| Modern Emerald | 89 | 86 | 428 | 8 | 0 / 0 |
| Unbound | 176 | 173 | 1,266 | 8 | 0 / 0 |
| Odyssey | 25 | 22 | 409 | 8 | 0 / 0 |

Modern's 11 named hidden-only areas with unproved collection flags remain in the catalog but do not become collectible challenges. Unbound and Odyssey's older fixture counts were stale; the final generated records and actual app bindings agree with the table. No missing leader or adapter domain is invented. Other official control identities absent from this corpus are not silently counted passing.

### Unbound scope

The confirmed map-admission regression is repaired in this release: all seven lost historical encounter-backed maps are restored. The corrected expanded graph has 458 maps; 444 are retained, including every bound encounter map, historical map still in that graph and proved cardinal-connection participant. Fourteen lower-priority maps remain explicitly budget-skipped under unchanged pixel/asset caps. Composite compatibility is 99.87%, **not complete support or gameplay verification**. Remaining Unbound work belongs to the separately requested follow-up release.

### Issue33 scope

DualDex now distinguishes selected-file readback from RetroArch's loaded config, requires a valid command status and disconnect/reconnect handshake for restart verification, and explains persistence, upstream listener availability and manual-catalog fallback. Focused qualification passed 68 host and 64 web tests. Upstream RetroArch issue 19568 identifies Android 1.22.2's missing command-listener build flag; no installed RetroArch APK was executed or repaired here, and a nightly report is not installed-package verification.

### Cache and historical QA provenance

Stable `v1.2.2` uses parser revision 83. Current revision 139 intentionally invalidates older persisted output while retaining SQLite schema 2. The executed seeded regression `revision83CachesWithoutCompiledGen2RelationshipsAreRejectedAndCurrentReopens` proves old-reader rejection/cache miss and exact current rewrite/reopen.

The release manifest retains Stage 7/8 infrastructure closures attributed to their original QA baseline `66bd216d9c370735666d5eb7438e83796a87eec7`. Those historical closures are not relabeled as this release's Android or signing acceptance. Current host/web/corpus evidence and the separately source-bound hosted Android/localization closure are described above; remaining candidate/signing gates below are mandatory.

## Candidate build status

RC1 run39116529839 was intentionally canceled during verification, before signing, when a second exact-eight assertion was found in published-asset promotion validation. Its original tag/run are preserved, not claimed as a completed candidate. A focused RED reproduces that promotion rejection; both evidence and published-asset paths now accept only the known8/11 profiles while preserving all source/hash/zero-gap and signed-promotion requirements.

RC2 [run39116907924](https://github.com/Darkaxt/DualScreenDex/actions/runs/39116907924) then failed `ProductionCompanionRuntimeTest.authorityTransitionCancellationDefersACompletionUntilItsEpochIsStale`: 688 app cases reached, one failure and55 skips. Its hosted packaged job succeeded, but signing was skipped; RC2 is not a signed or accepted candidate. The original failed run/log and tag remain immutable.

A deterministic synthetic reproduction drains the interrupted parser worker before advancing the session epoch. It fails in0.104 seconds, proving a real completion-ownership race rather than the fixture's ignored two-second timeout: the worker's exception handler steals the deferred callback while the old epoch is still current. Cancellation now transfers completion ownership before interruption; only the transition owner releases that callback, outside the runtime/task monitors and once only. All95 focused runtime/session tests pass (87 runtime,8 epoch-gate; zero failures/errors/skips), including deferred completion, stale-publication rejection, duplicate completion, nonblocking checkpoint cancellation and lock-order controls. Installed gate1475 verifies bounded resources/fail-fast and actual single-use daemon exit. Inline scope review finds no additional required consolidation.

RC3 [run39118451823](https://github.com/Darkaxt/DualScreenDex/actions/runs/39118451823) passed the full required build and hosted Android jobs. Protected runner-side signing and checksum/provenance generation succeeded, but the public-asset privacy step rejected the new localization summary's shape; release creation was skipped. No downloadable signed candidate or accepted release resulted. The original failed run and RC3 tag remain preserved.

The privacy failure is reproduced locally using the actual manifest-selected summary, not only a historical fixture:14 tests pass and that one acceptance test fails. Its added fields are the already-public compatibility aggregate and exact hosted-run ID/URL, not private data. The prospective validator accepts only those named optional shapes: aggregate keys stay closed and require nonnegative integer counts with exact totals; hosted-run metadata must be a paired positive integer ID and the exact matching repository Actions URL. Unknown nested fields, malformed counts, missing/mismatched/foreign run URLs, private paths and local identifiers still reject. All100 release-policy tests pass, with no errors/skips. A source-only preflight also validates all64 actual static public payloads selected by the workflow, including the manifest-selected corpus summary/receipt and new notes; dynamic signed APK/provenance/checksums/policy metadata still require the real protected run. The regression now reads the manifest-selected artifact so future evidence changes are checked before the build/signing stage. No evidence bytes, production app source, corpus output or historical failed run is rewritten by this correction.

The replacement `v1.2.3-rc.4` is now a production-signed **draft prerelease**, release ID416388339, built from `dfadcc2369763ec8f0f9c3136458771c48af3329`. [Protected run39119545357](https://github.com/Darkaxt/DualScreenDex/actions/runs/39119545357) passed all three jobs: full verification/build, hosted packaged Android and protected signing/draft creation. No earlier tag or asset was moved or replaced. The parser/catalog/build/corpus-execution source and frozen342 outputs remain unchanged; no corpus rerun was performed.

Independent downloads verify the exact68-asset GitHub set, every asset digest/size and all67 checksum entries. Provenance binds the repository, exact source/run/tag, version, compatibility evidence and protected signing authority. All68 downloaded assets pass public-privacy validation. Host-only cryptographic/package inspection confirms:

| Signed candidate identity | Verified value |
|---|---|
| Application / version / code | `com.darkaxt.dualdex` / `1.2.3-rc.4` / `1020304` |
| APK SHA256 | `6D373B3A6C60D08C233541F66CC3DBBC18D03E6A0C8B88D770E8063B321F7A3E` |
| Pinned certificate SHA256 | `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Provenance SHA256 | `2E620102B25B6D8A188AAE44F26E69E67B4A8787B5067FE52C4F7A7CAB455A08` |

The fresh candidate-source API35 debug-package JUnit reports **11 tests, zero failures/errors/skips**, SHA256 `e9409bdb57bc12d2125f4d876d6d174c592c02b79366804432f03377f284b5ee`. All four downloaded screenshots were visually inspected: bootstrap, intentional indexing/guide recovery and synthetic cache reopen render without a crash dialog. The recovery banner is an injected control, not a production failure. Source-bound evidence artifact12133133706 has GitHub digest `sha256:0b3f5a1c7926c3fb9d9dbd8abec93812c97a121d9bdc225389a8118d38f07488`; results artifact12133328196 retains JUnit/screenshots. This is debug packaged execution on a disposable hosted emulator, **not physical execution of the signed APK**. Host APK verification used no install mode or device serial.

## Remaining release gates

1. The existing handoff initially blocked on physical validation. The user then explicitly instructed “Skip, the validation.” The prospective specification revision and committed `packaged-android-user-waiver` record now waive that check only for this exact RC4 and metadata-only stablev1.2.3. It pins the candidate source/APK/provenance/signer and successful hosted run/artifact, requires dated user authority and rejects a claimed physical pass. Issue33 config/session/restart and cancellation changes are not mislabeled passive/parser-only. The actual full68-asset cryptographic/evidence validator passes this mode, and all103 release-policy tests pass with zero failures/skips. Physical Thor remains untouched.
2. Integrate the exact68 immutable asset IDs/hashes and applicable release tooling into the default branch, then dispatch protected candidate promotion with its reviewer requirement unchanged. Never replace candidate assets. Stable `v1.2.3` remains uncreated; recheck availability and satisfy the metadata-only source-tree policy before protected stable signing/publication.
3. Retain required compact proof/consumers and the signed draft, smart-sync and publish source, apply exact reviewed cleanup tickets when those consumers no longer need the outputs, and reconcile the current-release criteria. The downloaded candidate/package proof is retained locally beyond the hosted artifact expiry; no active evidence is deleted to manufacture cleanup completion.
4. Only after the current release, investigate and qualify remaining Unbound fixes, then publish the separate requested follow-up release. Do not automatically resume unfinished Pokescape work.

No acceptance policy, live-device gate, unsupported module or partial compatibility limit is waived by this report. Stage-closure review finds no additional acceptance-required consolidation: production challenge policy remains unchanged, release schemas use one supported set with explicit equality, and the prior shared setup/connection policy and source-owned artwork fixes remain intact.
