# Stage 4 — CLI persistence logical-digest checkpoint

Date: 2026-09-09. Task 429 completes the CLI persistence portion of Task 425; Stage 4 remains open.

## Implemented boundary

`Main.persistCatalog` now computes `CatalogLogicalDigest.sha256(catalog)` immediately before the existing cache write. After the writer closes and `cache.readComplete` independently reopens the database, it retains the existing exact catalog equality check, hashes the returned catalog, and requires digest parity. Both digest computations are outside the existing write/reopen timers.

Successful persistence metrics require `logicalDigestVersion`, `beforeCatalogSha256`, and `afterCatalogSha256`. There is no second original load/parse, alternate hash fallback, or replacement of logical payload evidence with SQLite bytes. A pre-write digest failure prevents the database write. Later failures publish no successful persistence metrics through the unchanged `persistenceError`/`manualReviewRequired` policy; they do not silently delete a valid written database.

CorpusReport and receipt-generator schema advance from **15 to 16**. Receipt envelope remains **1**; parser/storage/codec revisions remain **64/2/1**, and logical digest format remains **1**. Historical reports and receipts remain immutable. Future matrix evidence must use freshly matching bindings, not relabeled historical artifacts.

## Focused verification

- Genuine private RED: seven tests, five expected assertion failures, zero errors/skips. The unchanged-catalog equality and non-persisted-row guards already passed.
- Private GREEN: 53/53. The earlier interrupted attempt (`exit 143`, no test result) is retained and is not counted as a pass.
- Fresh parent gate: **53/53**, zero failures/errors/skips, **294.577 seconds**, 12 executed Gradle tasks.
- Main directly verified all **817 source pins and retained copies**, six XML files with the exact selected methods, 13 runtime JARs, embedded source commit, and all three packaged source/classpath/report/receipt bindings.
- Four unit regressions exercise actual SQLite write/close/reopen, pre-write size failure, changed reopened catalog rejection, and mutation at first database open. The last case proves that equal post-write models cannot masquerade as an actual pre-write digest.
- Three packaged regressions exercise a wholly generated input that reaches `SELECTED RED_BLUE` and real persistence, absence of digest observations on non-persisted rows, and report/receipt/runtime binding. The prior 46 header/report/receipt tests remain included.

The generated positive catalog contains 190 species, 151 stat records, 165 moves, and 12 type matchups; its SQLite output has 17 sections. It has no accepted localized-name authority. This is persistence plumbing verification, **not official-ROM or linguistic acceptance**.

## Retained evidence

Local gate root: `D:/Temp/dualdex-task429-parent-b8w4k_pn`, attempt `green-1`.

| Artifact | SHA-256 |
| --- | --- |
| Parent launch snapshot | `025cfbda9ad97875cc31be64e8147118d6a89aae6ff059fe9e4774c8ce01acbc` |
| Gate result | `a702ea543b76bcf0dd30aa85990478651665fedca114ada1ee86f931a888a97e` |
| Main verification | `3def5ef25d5f2f48d3d611069b96c2dc3bd0b0c619d3849556f6c37e4a69be84` |
| Runtime classpath aggregate | `79f40aa84c1bf5ed5ee29461bc19d5571a33f108bb5b77dc27e783803f3be663` |
| Packaged persistence report | `6b6274bd1e66089fd925c173b7fd6b1abfe71b235f2e8775b9f67d6fae720a68` |
| Packaged persistence receipt | `3453bacbaee3dfe4d6bec7573213420d588d26bad6217b675e6fc1b35470886e` |

The gate's embedded source commit is the frozen pre-commit HEAD `44f12eef191e6b826d9c5fe3aa06c3f70617cc73`; the launch snapshot separately binds the exact uncommitted implementation. It is not a claim that the pre-commit tree alone contains this change.

## Remaining closure

Task 430's G3 API integration and Task 425's structural/final matrix bindings remain open, as do native applicability and the final eligible corpus. **Zero final stable-source controls are accepted.** Stage 4 is not closed; Stages 5–6 remain blocked.

No original-ROM reads, runtime inventory, corpus run, device/emulator/ADB operation, APK, signing, or release occurred for this checkpoint. Parallel work is parked; follow-on checkpoints are handled serially.
