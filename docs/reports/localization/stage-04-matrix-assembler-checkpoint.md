# Stage 4 deterministic matrix-plan assembler checkpoint

## Scope

Task 422 / Task 373 G6 / `LNG-B003` adds `tools/localization/build_official_matrix_plan.py` and its fabricated-input tests. It assembles an independently supplied, pinned expectation set and complete normalized run references into the existing validator's v1 plan. It does not collect evidence, manufacture expectations or call the validator.

The required inventory remains **44 exact controls across 43 cells**, including separate Korean Gold and Silver, **308 check records** and **660 capability dispositions**. Runs must partition the exact inventory. Missing, stale, conflicting, duplicate or unpinned inputs block assembly. Source, report, receipt, generator, cache-identity and independent proof bindings are checked; required checks and API assertions receive metadata preflight. Distinct paths or hashes alone do not prove semantic independence: independent oracle review remains mandatory.

Only bounded pinned metadata and reviewed source-evidence bytes are read. Manifest original paths are inert; known ROM/cache/dump input suffixes are rejected. No cache payload is read or inspected by the assembler. Actual cache bytes, schema, section inventory, reopened fields and final acceptance remain the unchanged validator's responsibility.

The CLI emits `PLAN_ASSEMBLED` with **`acceptance=false`**, fixed counts and a plan digest. Existing output is never overwritten; failed writes remove only a newly created partial output. Private paths and payloads are not included in stdout. The module docstring documents the request, expected controls, run references and CLI.

## Verification

The isolated implementation retained its genuine missing-adapter RED and a later output-close regression RED, followed by **35/35 fabricated metadata tests**. The coordinator reviewed the complete implementation/tests and verified the handoff, patch, sources, logs and unchanged validator hashes before integration.

The fresh parent gate ran:

```text
python -X utf8 -B -m unittest discover -s tools/localization -p test_*official_matrix*.py -v
```

**97/97 tests passed**, zero failures/errors/skips, in **147.393 seconds** of unittest execution (**147.882 seconds** wrapper elapsed). This includes the 35 assembler tests and the 62 unchanged validator tests, whose SQLite inputs are synthetic fixtures. All four Python source hashes remained unchanged and were independently rechecked with the retained log hash after completion.

- Parent result SHA256: `cb18eea71a6f417586695b41d4e4eb3cf8330f64e9393c3016b4d983ddb6d14a`
- Test log SHA256: `437b9cde0aab7c0f8dccac124f46707fc709f6bbe38a398b15ff9acf1e875970`
- Assembler SHA256: `91c153680a69b1f55210bd9a5f3ae36fe803fc081d5c0cb9adcc74b497d05248`
- Assembler tests SHA256: `6513abf0d5e6362dfb236ee966d6db01684f50100164a3acb36471fb05b3e9d6`
- Unchanged validator SHA256: `cb6375bb803480117964f5f8322e867abdfb807fb74b8b26a01c77bfa99e457d`

No original input, real cache, corpus, Android device/emulator, APK, signing or release was executed for this checkpoint. Pending Japanese parser changes are not part of this publication. Published parser/storage revisions remain **63/2**, codec revisions **1**.

## Remaining mandatory work

Task 422's assembly tooling is complete in this checkpoint, not the final matrix. Task 373 still requires G0 final source-bound runs, G1 actual normalized checks, G2 freshly executed codec vectors, G3 actual same-capture normalized API exports and G4 complete independently reviewed capability oracles/proofs. Task 386's remaining Japanese sign, GBA title and other required-content/applicability gaps remain open. An assembled plan is not a waiver for missing content or incomplete evidence.

There are **zero final accepted controls** on the final stable-source matrix. Tasks 373–375 retain matrix ratification, one final eligible corpus run after executable changes stabilize, ledger audit and published Stage 4 closure. Stage 4 stays **OPEN**; Stages 5–6 remain blocked.
