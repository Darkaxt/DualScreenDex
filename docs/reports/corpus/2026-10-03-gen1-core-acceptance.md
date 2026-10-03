# October 3, 2026 Gen I compiled-core acceptance

## Result and scope

`LIB26-G1-CORE` is **accepted for bounded host/static catalog support**: six PureRGB builds, two Yellow Legacy builds, and two Yellow Kaizo builds now uniquely select and materialize coherent base catalogs. All ten were previously unmatched. The current 342-identity composite matrix rises from **286 to 296 selected**, with **2 ambiguous, 44 unmatched, and 0 parser errors**. Resolved language manifests rise from 271 to **281**; 15 remain unknown and none proves multiple ROM-content projections.

- [Machine-readable acceptance, counters, and execution receipts](2026-10-03-gen1-core-acceptance.json)
- [Updated per-ROM matrix](2026-10-03-library-compatibility.md)
- [Remaining expansion priorities and ledger](2026-10-03-library-refresh.md)
- [Bank-local core design](../../superpowers/specs/2026-10-03-gen1-bank-local-core-design.md)
- [Independent Yellow copy-consumer design](../../superpowers/specs/2026-10-03-gen1-yellow-copy-core-design.md)

The latest implementation checkpoint is `3f2b95e86d4fb84ebec75a06b404827ab819d974`. There was **no full-corpus rerun, Android/emulator/device test, APK build, or release**. These direct CLI runs validate in-memory materialization and reference closure; they did not request SQLite persistence/reopen, SaveRAM, or live-WRAM acceptance. Official standalone ROMs were not rerun in this batch; native compiled-layout fixtures and the named hack controls provide the bounded host regression evidence.

## Ten newly supported builds

Species/stat counts below are navigable canonical records, not every internal name slot. Move counts are materialized detailed records, not a retail constant.

| ROM | Family | Coverage | Named species / stats | Detailed moves | Sprites | Dex descriptions |
|---|---|---:|---:|---:|---:|---:|
| PureRed (22.08.26).gbc | Red/Blue | 75.00% | 151 / 151 | 165 | 0 | 0 |
| PureRed (03.09.26).gbc | Red/Blue | 75.00% | 151 / 151 | 165 | 0 | 0 |
| PureBlue (22.08.26).gbc | Red/Blue | 75.00% | 151 / 151 | 165 | 0 | 0 |
| PureBlue (03.09.26).gbc | Red/Blue | 75.00% | 151 / 151 | 165 | 0 | 0 |
| PureGreen (22.08.26).gbc | Red/Blue | 75.00% | 151 / 151 | 165 | 0 | 0 |
| PureGreen (03.09.26).gbc | Red/Blue | 75.00% | 151 / 151 | 165 | 0 | 0 |
| Yellow Kaizo (1.0.4 QOL).gb | Yellow | 87.50% | 151 / 151 | 166 | 151 | 0 |
| Yellow Kaizo (1.0.4).gb | Yellow | 87.50% | 151 / 151 | 166 | 151 | 0 |
| Yellow Legacy+ (08.09.25).gb | Yellow | 100.00% | 151 / 151 | 165 | 151 | 151 |
| Yellow Legacy (17.03.26).gb | Yellow | 100.00% | 151 / 151 | 165 | 151 | 151 |

All ten resolve one English ROM-language projection and report zero decoded reference errors. Canonical Dex/name/stat joins were checked independently; family selection and reference closure alone are insufficient.

Kaizo's **166 moves are intentional compiled content**: TWISTER occupies move 165 and STRUGGLE occupies move 166. Both names have matching six-byte numeric records, and the name extent ends before non-name padding. Legacy ends at STRUGGLE, move 165. The private acceptance verifier's original assumption of 165 for every Yellow derivative was corrected only after this raw compiled-table check; no parser adjustment or extra parser run was needed.

## Structural changes and corrected failures

### PureRGB

Complete home-bank call wrappers establish bank-local fixed names, 35-byte base records, and move-name pointer consumers. Pointer names are admitted as the existing linear variable-length layout only when every pointer proves consecutive physical string order. No ROM-name/hash profile, fixed table address, new pointer schema, or relaxed family threshold was added.

The initial candidate exposed two defects:

1. **Incorrect species/stat joins.** PureRGB's index has duplicate positive Dex entries for forms. The inherited bijection scanner rejected it and fell back to identity, joining names to unrelated stats. The corrected resolver binds the actual compiled species-index lookup to the base consumer and its separate non-Dex exclusion branch. All 151 canonical records are correctly joined; 13 separately handled form IDs do not receive guessed ordinary stats/types. Unproven bank-local indexing now fails closed.
2. **Three sprite-control regressions.** An unconditional stride-equality guard withheld sprites on Beyond Red, Beyond Blue, and Nova. Base resolution now retains bank-local versus home authority, and the guard applies only to the new incompatible bank-local ABI. The existing home path preserves its validated metadata.

PureRGB's 190 internal name slots are not 190 navigable canonical species. The accepted counters are 151 named/statted navigable records. Graphics, Dex text, maps, and separately handled form stats remain explicitly unsupported in this batch.

### Yellow

Legacy's base consumer calls a proven bank-store helper instead of inlining it. Kaizo's home name/base consumers use a one-byte restart-copy instruction instead of a three-byte CALL. Both variants are handled independently through complete bank-store/restoration, arithmetic, and copy proofs, including the optimized 23-byte copy routine. Suffix offsets follow the actual instruction width; new paths require bank-contained data and writable destination extents. Existing classic inline/CALL behavior is retained.

## Verification and checkpoint provenance

The final affected synthetic suite contains **69 tests, 0 failures, 0 errors, and 0 skipped**: compiled name/base/move consumers, malformed/ambiguous authority, index decoys/exclusions, materializers, native geometry, and family integration. The new positive cases were observed failing before their fixes. The suite completed successfully in 53 seconds.

| Checkpoint | Exact inputs | Outcome | Accepted use |
|---|---:|---|---|
| `d61fb188e52f9ef67016ca3e5c6bea581e7c5fa6` | 30 | 26 selected, 4 unmatched, 0 parser errors | Diagnostic initial run; retain only 15 unaffected controls in the final composite. Initial PureRGB joins and three sprite regressions were rejected. |
| `f5e2a101a20968d5563a5b77e21df2b2132ab9b8` | 9 | 9 selected, 0 parser errors | Six PureRGB targets and three sprite controls corrected; six rows remain the newest evidence after overlap with the Yellow run. |
| `3f2b95e86d4fb84ebec75a06b404827ab819d974` | 9 | 9 selected, 0 parser errors | Four Yellow targets and five controls; nine rows retained. |

This is **48 parser executions over 30 distinct identities**, not 48 identities or a new 342-ROM scan. Each receipt binds the exact source commit, CLI artifact digest, raw report digest, and input count. Private normalized identity multisets were verified. Not all rows were scanned against the final checkpoint, and the broader library retains dated evidence.

The 20 controls are Beyond Red/Blue, Red++ normal/hard, Shin Red/Blue/Green, three Celebrations Green variants, four Indigo/IndigoLite variants, Red Kaizo, Blue Yellow Backport, Pink, Nova, Grape, and Static Yellow Gen 1. Exact filenames, counters, capability comparisons, and per-row source commits are in the acceptance JSON. Final accepted rows have no family/language/capability-status regression or material coverage decrease relative to their prior evidence. Beyond Red/Blue restore 151 sprites at 92.53%; Nova restores 151 sprites at 98.45%.

## Remaining work and privacy

- `LIB26-G1-OPTIONAL`: all six PureRGB builds' sprites, Dex descriptions, World Map, and Local Map; both Yellow Kaizo builds' Dex descriptions and Local Map; retain the existing Static Yellow/Christmas Kaizo optional targets. Each dataset requires compiled-consumer authority and independent malformed-module failure isolation.
- `LIB26-G1-FORMS`: the six PureRGB builds' 13 separately handled non-Dex form IDs. Close only with proven alternate-stat/index/type semantics and reference-safe materialization; do not attach canonical stats by numeric position.
- Gen II core, Gen III core, and other map priorities remain separate unimplemented ledger entries. This batch does not revive Rogue-only work.

Original archives, retained corpora, and the frozen release identity/matrix are unchanged. Public artifacts contain filenames, aliases, aggregate counters, capability evidence, and execution digests—not ROM bytes, bulk decoded tables/text, sprites, saves, trainer data, credentials, raw memory, or private paths. Compact private evidence is retained before disposable raw reports and scratch directories are transactionally reclaimed.
