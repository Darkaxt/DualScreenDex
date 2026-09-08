# Stage 4 Western GBA revision-61 real-control run

Status: **diagnostic integrity passed; real item-name availability remains 0/1,660; semantic acceptance is not achieved**.

Task 413 executes the production parser at `6d8f5d31fc0e6fc8b442a02e950c1c70ed6bb27d`, following the [Tasks 411–412 synthetic consumer checkpoint](stage-04-western-gen3-item-consumer-checkpoint.md). This is a new changed-source run, not a reinterpretation of the immutable [Task 397 diagnostic](stage-04-western-gen3-item-name-diagnostic.md).

## Scope and execution

The fifteen exact official controls cover English, French, German, Italian and Spanish, each with Ruby/Sapphire, Emerald and FireRed/LeafGreen. Each original is bound to the existing manifest/inventory pins and read once by the diagnostic harness. No Task 401 or Task 406 acquisition runner or reservation was reused.

The existing test adapter still required parser revision 60 and would reject revision 61 before reading any original. Only two test-source files changed: current schema receipts and SQLite assertions now use `CatalogSchema`, and historical comparison labels identify the actual current revision. Historical revision-49 inputs remain unchanged. Production sources, storage schema 2 and codec version 1 are unchanged.

One offline Gradle invocation selected:

- `WesternGen3BaselineCaptureTest`: **23 synthetic adapter methods**.
- `WorldMapCatalogApiRealControlTest.westernOfficialGen3DiagnosticFifteenControls`: **one real-control method covering all fifteen originals**.

Result: **24/24 JUnit methods passed**, zero failures, skips, missing, unexpected or duplicate methods; exit **0**, **1,257.080 seconds**, no timeout or retry. This duration includes compilation. The thirty-minute process-tree watchdog did not fire. All **792 pinned source/configuration files** remained unchanged during execution.

## Actual current results

| Family | Controls | Requested IDs per control | Total requested | Available names |
|---|---:|---:|---:|---:|
| Ruby/Sapphire | 5 | 101 | 505 | 0 |
| Emerald | 5 | 104 | 520 | 0 |
| FireRed/LeafGreen | 5 | 127 | 635 | 0 |
| **Total** | **15** | | **1,660** | **0** |

Retained post-run reconciliation compared every current requested-ID list with its corresponding Task 397 list, not just the counts. All fifteen domains are identical. Each selected original route is unchanged: Ruby/Sapphire uses `NotInvoked`; Emerald and FireRed/LeafGreen use `InvokedNominated`. No incidental route replaced a selected route.

All fifteen selected authorities are `Unavailable(reason="incomplete original item getter candidate")`. The previous Task 397 selected authority reported `no original compiled item candidates`. The new recognizers therefore do not establish real-control name authority; the changed rejection reason does not prove why an individual candidate is incomplete. That must be traced before another production correction.

The real-control integrity checks did establish:

- **15 original payload reads**, one original analysis and one item-producer invocation per control.
- **10,545 passing diagnostic assertions**, including immutable original-route/authority checks and complete producer key domains.
- Materialization, actual SQLite write/close/reopen, whole-catalog and numeric parity, schema 61/2, integrity and foreign-key checks.
- Cache-only API startup and projection parity with **zero reparses**.
- Preservation of **4,470 numeric ball/item-POI API occurrences**.

The adapter permits unavailable names and explicitly records `semanticAcceptance=false`, `acceptedNames=0`, and `requiredSemanticCompletion=NOT_ACCEPTED`. Its successful JUnit result is **not** successful item-name acceptance. Presentation-only `Ball <id>` fallbacks do not count as native names.

## Remaining acceptance boundary

Task 390 / `LNG-B002` retains all **1,660 mandatory names**. The next correction must explain the actual incomplete-candidate rejection and preserve conservative competing-candidate handling. An independent, exact language/version-correct expected-name reference is still required; neither decoded parser output nor public display-list row membership may manufacture that oracle. Complete names must then survive producer, overlay/projection, SQLite reopen and cache-only API comparisons against those expectations.

Task 386 retains the other localized capability gaps. Tasks 373–375 retain the current official matrix, final current corpus after executable changes stabilize, and published Stage 4 closure. Stages 5–6 remain blocked. This run does not authorize or perform a full-corpus rerun, device testing, APK/RC build, signing or release.

## Fork reconciliation

The pre-commit fetch found `fork/master` at `2fef59df95f6deb0ae861aad42cff52eff739a86`, already an ancestor of tested production checkpoint `6d8f5d31` (55 ahead, zero behind). The remote feature branch matched that checkpoint. No incoming delta, merge, reset or force-push was necessary.

## Retained evidence

Full commands/environment, source snapshots, patch, stdout/stderr, fresh XML, control receipts, requested domains and private catalog/SQLite/API observations are retained outside the repository. Parent reconciliation inspected retained JSON/XML and source pins without rereading originals or rerunning Gradle; it passed on its first execution.

| Artifact | SHA-256 |
|---|---|
| Current fifteen-control batch | `56dfcd2a1ff747e5dcf36e06da8f991e7c3a0b49218ad54f484457c93890d522` |
| Adapter JUnit XML | `7edffba7bb5f31a2102cb2dbf0db3fce5df13655fb4dc31cc67bf931996734b7` |
| Real-control JUnit XML | `61d69842b8e228c3fe6327f793a96e6f02d4968dd69f68a855181b9c632a94ef` |
| Parent retained-results reconciliation | `d49b0b3b8879a792e5833b83317bce590ba762de6f93e0ceb7a2a123d1f26447` |
