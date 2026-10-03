# Gen I bank-local core-table support

## Approved scope

The October 3 library audit recommends `LIB26-G1-CORE`, beginning with PureRGB's six distinct builds. The user requested that first step. This checkpoint restores compiled table authority for that cohort, with synthetic fail-closed tests and host-only related-cohort regression. Yellow Legacy and Yellow Kaizo remain separate investigations; source inspection shows that their four binaries do not use this consumer ABI. No full 342-ROM scan, device testing, ROM distribution, or release-signing change is included.

## Observed failure and compiled proof

All six PureRGB builds pass their Gen I header gates but fail inherited species/name/move roots. The inspected source and each compiled ROM agree on three changes:

1. A home-bank far call selects a bank-local fixed-name consumer. Its repeated-add stride, copy length, writable destination, restart copy loop, and appended terminator can all be verified.
2. A home-bank save/switch/call/restore wrapper selects a bank-local base-data consumer. Its index decrement, repeated-add stride, copy length, and source bank agree. The compiled stride is 35 bytes; all 151 standard Dex IDs and stat/type rows validate in order. The inherited 28-byte table is not authority for this ABI.
3. A home move selector calls a register-preserving far wrapper. The leaf reader indexes a same-bank 16-bit name-pointer table and copies the selected string. The input/output WRAM fields and destination link the leaf to the home selector. All 165 pointers reproduce the physical terminated-string order.

The [PureRGB source](https://github.com/Vortyne/pureRGB) is an oracle for instruction meaning, not a runtime profile or a claim that the local checkout is the exact new release source. The implementation must derive banks, addresses, widths, and counts from each ROM. Restart-vector numbers and routine addresses may differ in synthetic fixtures and must not be fixed to this project.

## Design

Add a bounded Gen I compiled-bank-call helper. Scan only the home bank for complete far-call or direct bank-switch wrappers. Validate the actual bank-store helper, its MBC register window, matching restoration, and the indirect-HL caller. Expose only scalar caller and target offsets. Verify repeated-add and copy helpers by their complete loops, following a restart vector's jump when necessary.

Extend the existing Gen I name, base-data, and move resolvers rather than adding a new ROM profile or changing the family competition thresholds. Preserve the existing inline and official consumer paths. Combine candidate layouts and require unique agreement; contradictory proven layouts fail closed.

For bank-local fixed names, require a complete leaf consumer, matching source/copy widths, a valid WRAM destination, a proven repeated-add helper, a proven copy loop, a codec-matching appended terminator, and bank-contained validated records.

For bank-local base data, scan only a bounded window inside a proven banked target. Require matching index/copy stride, a proven repeated-add helper, a proven restart copy loop, a writable destination, bank-contained rows, sequential Dex IDs, and the existing full-table stat/type validator. Preserve the origin of that resolution: a changed bank-local record ABI cannot inherit old sprite banks, while the already-supported inline consumer path retains its existing validated sprite behavior. This checkpoint does not claim new graphics banks or non-Dex form statistics.

A table-count check alone does not establish the species/name/stat join. The first bounded run exposed duplicate form entries in PureRGB's compiled index table: the old content scanner requires a permutation, falls back to identity, and pairs internal names with incorrect Dex rows. For the new bank-local ABI, require the complete compiled base/index call chain. Accept both verified tail-jump and call-plus-return bank restoration. Bind the index leaf's WRAM field to the base consumer, validate its same-bank byte lookup and full canonical Dex coverage, and prove the non-Dex exclusion branch through its complete list-search helper and shared-copy control flow. Excluded forms remain named but do not inherit ordinary Dex statistics or types. Missing or contradictory index authority must publish unavailable numbering/stats, never identity fallback or numeric-row guessing. Preserve the existing legacy resolver outside this newly admitted bank-local ABI.

For pointer-based move names, require the complete home-selector/far-wrapper/leaf chain, a valid same-bank pointer domain, and a proven terminated-string copy routine. Derive the table count from its first payload boundary and validate every pointer against successive decoded strings. Only a pointer table that proves the existing linear-name representation is admitted. Reordered, aliased, crossing-bank, ambiguous, or malformed tables remain unsupported; no new persistence or generic pointer-layout format is needed.

## Validation and closure

- First run the new synthetic positive tests against unchanged production code and retain their RED result.
- Add negative tests for broken bank restoration, non-MBC bank stores, wrong helpers, disagreeing strides, false Dex IDs, crossing-bank names, pointer reordering/aliasing, broken selector linkage, and ambiguous proven roots.
- Run the focused new tests, existing Gen I compiled-table tests, native-name geometry tests, and affected family/authority guards. Do not run unrelated broad suites.
- After implementation is final and source-bound, parse the six targets and the affected Gen I corpus cohort. The first 30-input run also included the four still-unmatched Yellow controls and 20 related controls; it exposed the index-join blocker and three sprite regressions. Limit correction acceptance to the six targets and those three regressed controls, retaining exact earlier provenance for the other 21 rows. Do not repeat the 30-input cohort for documentation edits or run the full 342-ROM corpus.
- Check unique family selection, materialized species/name/stat/type/move counts, reference closure, and optional-module failure isolation; selection alone is not completion.
- Publish exact affected identities and unchanged/regressed controls, update the compatibility ledger, and preserve the frozen release benchmark.
- Retain compact evidence; transactionally remove only the registered disposable workspace.

`LIB26-G1-CORE` closes for PureRGB only when all six builds produce coherent core catalogs without weakening the existing controls. The Yellow subtargets remain explicitly open until independently proven.
