# Stage 4 matrix evidence validator checkpoint

Status: **offline validator passes synthetic acceptance; no current official matrix is ratified**.

## Scope

Task 387 adds `tools/localization/official_matrix.py` and its executable synthetic contract, `test_official_matrix.py`. This replaces the need to reuse an unparameterized historical Western audit script. It does not parse ROMs, run the corpus, create missing observations, or infer semantic authority.

The validator requires an independently pinned plan with explicit source, schema, control and codec expectations. It checks **44 exact identities covering 43 family-language cells**, including independently represented Korean Gold and Silver. Separate Western/native report runs are supported only when their source and evidence bindings agree.

Inputs include pinned manifests, parser reports and execution receipts, read-only SQLite caches, normalized mandatory-check observations, actual captured API responses, and independently reviewed field samples and coverage/exclusion references. The module docstring defines the versioned input contract; `Fixture` in the test module is a complete synthetic example, not an official oracle.

## Enforced boundaries

- Reject missing, duplicated or substituted identities, incorrect cell/release multiplicity and source/report/receipt/cache mismatches.
- Check all 15 localized capability inventories and count/disposition consistency. Independent samples do not need to enumerate every valid record, but complete coverage decisions and every claimed exclusion need explicit evidence references.
- Require mandatory header, codec-vector, structural-authority, reopen-parity, isolation, type-semantic and API-bootstrap checks. Skipped required checks and contradictory status/counts block acceptance.
- Require `ROM_DEFAULT`, zero parser invocations and independently expected API/cache field agreement. Source-proven wholly inapplicable fields require observed empty cache/API fields; unsupported absence is not a waiver.
- Use SQLite read-only/immutable mode, `query_only`, a transaction, integrity/schema/section/chunk checks and encoded digests. Reject sidecars and schema-table substitution; bound file, decompression and SQLite work.
- Emit allowlisted public fields and fixed blocker codes, never raw paths, prose or exception messages. A blocked result publishes no partial positive control list.

**Evidence integrity is not semantic truth.** Digest, source-slice and reference checks establish provenance and contract consistency; they cannot establish that an external oracle or applicability decision is correct. Independent source/compiled-ROM review remains required. No adapter fabricates those decisions from parser status or equal round-trips.

The CLI writes no files. Exit zero means `EVIDENCE_VALIDATED`; exit one means `BLOCKED`. Its independently reviewed plan digest and full source commit are mandatory explicit inputs. Actual plans, source evidence, caches, raw reports and captured responses remain private.

## Verification and concrete correction

The implementation owner ran synthetic red/green cases. Coordinator inspection then found that the supposed malformed-deflate test contained literal backslash escapes, so it tested invalid gzip magic rather than a valid gzip header with an invalid deflate body. A fresh coordinator reproduction escaped the sanitizer as `zlib.error`.

The corrected fixture uses real header bytes. The owner reproduced that error and a boolean receipt-schema acceptance failure, then added the narrow decompression exception handling and strict integer schema checks. No additional general review round was performed.

The coordinator independently ran:

```sh
python -B tools/localization/test_official_matrix.py
```

**Result: 62/62 passed in 88.505 seconds, zero failures/errors/skips.** Coverage includes complete synthetic 44-control acceptance, 35+9 partitions, provenance and inventory rejection, malformed compressed data, read-only cache behavior, explicit exclusion accounting, missing/contradictory checks, sample disagreement, CLI exit behavior and output sanitization. The source and test log are retained outside public assets.

## Remaining integration

Task 373 still needs current same-source official reports/receipts/caches, normalized mandatory-check observations and actual API captures, independently reviewed field samples/coverage/exclusion evidence, and a pinned plan. These inputs have not been synthesized or executed by this checkpoint.

Tasks 385–386 retain species-description applicability and other native text-capability gaps. Task 388's learnset-as-move-prose defect remains a required parser correction. Passing synthetic validator tests is not a claim that any missing field is non-applicable or that the real matrix passes.

Stage 4 stays open; Stage 5 remains blocked. The final current corpus waits until executable changes are final. No real matrix/corpus, Gradle, device/emulator/ADB, signing, ROM publication or cleanup action is part of this tooling checkpoint.
