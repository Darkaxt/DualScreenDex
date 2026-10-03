# Gen I Yellow copy-consumer support

## Scope and evidence

Continue the authorized `LIB26-G1-CORE` step after the six-build PureRGB acceptance. The two Yellow Legacy builds have the existing fixed-name consumer, but their home base-data routine saves the bank, calls a complete bank-store helper, and later restores through that same helper. The two Yellow Kaizo builds additionally use a restart instruction instead of a three-byte call for fixed-name and base-data copying. All four compiled copies reach the same optimized 23-byte byte-copy loop; its internal call and relative branches can be verified. These are separate instruction layouts from PureRGB's relocated 35-byte base ABI.

The local [Yellow Legacy source](https://github.com/cRz-Shadows/Pokemon_Yellow_Legacy) supplies instruction semantics, not binary identity. The actual home consumers, bank stores, restoration, restart-vector targets, helper loops, widths, and table rows were inspected in each retained binary. No exact source match is assumed for Kaizo.

## Design

Preserve the existing inline name/base consumers. Derive the copy-instruction length from a call or validated restart and use that length to locate the complete terminator/restoration suffix. Admit the helper-call bank prologue only when its HRAM field, MBC bank store, saved bank, and restoration call agree. Validate the full optimized copy routine, including its self-relative internal call, or the existing simple loop. For the newly supported forms, require the complete repeated-add and copy helpers; do not relax table validators, counts, family thresholds, or codec authority.

Keep these home consumers on the established inline base-resolution path so their existing independently validated sprite behavior is preserved. The PureRGB bank-local index and non-Dex exclusion authority remain separate. No per-ROM names, hashes, addresses, profiles, model/schema changes, emulator, device work, or release is included.

## Verification and closure

Write synthetic positive RED cases for a helper-called base consumer and restart-copy fixed-name/base consumers, plus negatives for mismatched restoration, invalid MBC stores, and broken optimized copy loops. Run only the affected resolver/native-layout/materializer tests. After final source is committed, run one bounded cohort of the four Yellow targets and five relevant controls: one accepted PureRGB build, Beyond Red, Nova, Pink, and Static Yellow. Do not repeat the earlier 30-input cohort or the full 342-ROM corpus.

Close each Yellow subtarget only after unique family routing, coherent species/name/stat/type/move joins, no decoded reference errors, and no control regression. Retain the source-bound receipts and compact evidence; publish remaining optional gaps as named ledger entries. The frozen release benchmark and both retained corpora remain unchanged.
