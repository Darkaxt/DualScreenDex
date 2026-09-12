# Stage 4 — shared G3 metadata binding

Date: 2026-09-09. Task 430 checkpoint; Stage 4 remains open.

The shared `official_matrix.g3_capture` contract now connects the exact-restored-catalog capture from [the preceding checkpoint](stage-04-api-restored-digest-checkpoint.md) to both the offline validator and metadata-only plan assembler:

- The report row is joined by `result.sha256`, never array position. Report16 CLI persistence before/after digests, normalized reopen-parity digests, and the captured restored logical digest must agree using format1. Missing, boolean, string, or unsupported versions fail closed.
- `bootstrap.captureProvenance.binding` must equal all five current run identities exactly. Restore method, cache digest, parser/SQL/logical versions, and DISCOVERED knowledge mode are checked.
- The real `bootstrap.response` catalog identity, family, language, codec projection, ROM_DEFAULT authority, CACHE_REOPEN phase, and `state.settings.knowledgeMode` are checked. Caller-selected alias pointers cannot stand in for production fields. Parser invocations must be integer zero.
- The existing canonical bootstrap checksum still covers the **whole envelope**. Matching checksums cannot waive a stale embedded binding or a false response identity.
- Neither consumer rewrites captures or invents expectations. The builder still never opens or stats cache files. Historical report14/report15 artifacts remain untouched and are ineligible as final current evidence.

Only fabricated fixtures were updated to current report16/parser64 and the actual nested envelope shape. Their expected envelope digests come from explicit fixture declarations, not captured response values. Run splitting rebinds these declarations to each generated report/receipt pair. This is test machinery, not an artifact-upgrade adapter.

## Verification

- Existing fixture compatibility: **97/97** before production changes.
- Genuine focused RED: **8 tests, six expected failures**, zero errors/skips. Old consumers incorrectly accepted mismatched CLI/restored digests, a stale nested report binding, and a forged alias identity. Both shuffled-row controls passed.
- Initial focused GREEN attempt: 15 passed; one test-fixture error tried to deepcopy unittest's output stream. The fix snapshots only the seven metadata arguments; no production workaround.
- Full GREEN: **113/113**, zero failures/errors/skips, 129.461 seconds. Includes both actual CLI success/failure invocations, split runs, deterministic/non-mutating assembly, SQLite read-only guards, eight direct metadata tests with negative subcases, and both SHA-order controls.
- Main verified all 113 successful log entries and all four unchanged Python source pins. No original-ROM read, corpus, Android/device/emulator/ADB, APK, signing, release, or PR occurred.

The private retained evidence root remains outside public assets. Frozen pre-commit HEAD: `7213d877ca9e1f60b797cb4c9c3fdd555df3722e`; source pins bind the tested uncommitted implementation separately.

| Artifact | SHA-256 |
| --- | --- |
| RED log | `4c5fffc932bd11a4add8d3c73f148ae8a4c97faf744e27c8eeea393663c26802` |
| GREEN log | `08a1769b8652b17bc18a222b71c4c78da291a622ef8a334cff08b57330697129` |
| GREEN result | `17750e3d30097fc00e0234b0e21561aab4025179fb9e12c6c54c819c7ce6d3b7` |
| GREEN source pins | `0e30239e8bc29672bdd6329219f139aa83dc4338efe5bebebbb32dcb203d567b` |

## Remaining mandatory scope

Task 430 still requires measured all-15-group projection/API isolation, type consistency, normalized actual API fields, and binding those measurements into the shared checks. No enum-size success counter or overlay backfill is acceptable. Consistency observations do not replace independent semantic oracles. The stopped draft remains untouched; implementation continues directly and serially.

Zero final stable-source controls are accepted. Final matrix, remaining native applicability gaps and the final eligible corpus remain required before Stage 4 closure; Stages 5–6 stay sequentially blocked. No parser/cache/report/codec version bump in this checkpoint. Generated Python fixture directories were automatically cleaned; small logs and source pins are retained. The owned Kotlin gate cache remains reserved for the next observer checkpoint.
