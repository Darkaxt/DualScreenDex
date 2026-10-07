# Pokescape packed-move acceptance — October 7, 2026

**Accepted host/static checkpoint; parent and current ROM remain IN_PROGRESS.** Source: `8ca3489ca9b9207b590cc74df54be825f65e428b`, parser revision **88**, SQL schema **2**. [Machine-readable evidence](2026-10-07-pokescape-moves-acceptance.json). [Active parent ledger](../../superpowers/plans/2026-10-07-all-rom-compatibility-completion-plan.md).

## Verified gain

| Field | Before | Accepted |
|---|---:|---:|
| Weighted applicable-capability coverage | 79.14% | **83.31%** |
| Named moves | 828 | 828 |
| Detailed moves | 0 | **828** |
| Moves with native categories | 0 | **828** |
| Move descriptions | 823 | 823 |
| Local Maps | 520 | 520 |

The independently established move domain has 829 physical rows, including reserved row zero, and 828 active named moves. Every active move's type, category, power, accuracy, PP, signed priority and effect ID agrees with the original compiled data: **5,796 independently checked scalar values**. No score, threshold, denominator or applicability rule changed.

## Actual compiled contract

The retained build does not match the current project's public widened-power layout. Its distinct 20-byte contract has a widened effect, byte power/type fields, a halfword target, signed priority at byte 10, category at byte 11, halfword argument at 12, Z-effect at 14 and an opaque packed tail at 15–19. Padding remains strictly checked.

Selection requires complete native stride/width/field witnesses and all active rows, not scalar shape or reference popularity alone. The collector scans the entire image under deterministic root/candidate/work limits rather than trusting truncated indexed reference sites. High-register address addition, exact unsigned-halfword normalization and multiple independent reads retain register-clobber protection and fresh loaded-value identities. Signed byte interpretation is backed by unchanged value provenance through bounded forward local paths to exact sign extension. Calls, indirect transfers, loops and unknown instruction traversal do not provide that proof; this is not a global CFG or gameplay-execution claim.

All complete competing roots reject rather than being ranked. Partial, malformed, truncated and budget-exhausted optional contracts fail closed; cancellation propagates. Existing ABI paths are retained. The failed-move-data recovery does not replace a working exact, published or expansion route.

[Expansion 1.6.2 at the pinned public source](https://github.com/rh-hideout/pokeemerald-expansion/blob/e2811035752b8db36ad8a85be35b72260b5c63b8/include/pokemon.h) is a **structural oracle only**, not exact-build or per-flag semantic authority. Packed bits are retained opaquely; no traditional retail flag meanings or unsupported move mechanics are inferred.

## Verification and preservation

- Observed codec RED: 5/5 failures on the missing ABI; then GREEN.
- Observed selector RED: 3 functional failures across 10 cases; then GREEN. An earlier test-property compile error was corrected and is not counted as functional RED.
- Observed core-integration RED: 1 functional failure across 2 cases; then GREEN.
- Additional relocated, loaded-register alias, normalization, branch/call/clobber, conflicting-root, partial-field, padding, extent, budget and cancellation tests pass.
- Fresh affected parser suite: **328 cases, 9 skips**; full storage: **119, 6 skips**; full CLI: **76, 1 skip**. **Zero failures/errors.** Post-commit packaging rebuilt the source-stamped JAR; the CLI test task then remained up-to-date after its earlier fresh complete run.
- Every Gradle execution acquired the installed shared Windows gate. Observed two workers, no parallel projects and 3 GB Gradle heap; requested 3 GB Kotlin heap; one configured 512 MB test fork. No memory failure. These are not total-host or native-memory guarantees.
- One exact packaged parser execution, one retained target, zero other real-ROM controls. Seven separately reviewed one-use original-read admissions across diagnosis, parser acceptance and independent verification; zero abandoned reservations. Generated-only preflights are not original reads.
- **84,452 complete decoded-reference checks, zero errors.** Actual complete SQLite write/reopen structural equality and equal logical digests; independent canonical payload digest equality and SQLite integrity check.
- **16 complete sections unchanged.** Reversing only the seven recovered move fields, `MOVE_DETAILS` capability evidence and parser-revision envelope reproduces the retained prior whole-catalog digest exactly. This proves prior core, World Map, all 520 Local Maps, names, descriptions, acquisitions, runtime metadata and other stored data are preserved without retaining or rerunning a second baseline database.
- Only Pokescape's matrix row changes. **341 unrelated row objects**, the 342-input denominator, aliases and historical baseline/cohort/World Map receipts remain unchanged. This remains composite evidence, not a fresh full-corpus run.

## Still required

Pokescape is **83.31%, not complete**. Dex text, five move-description gaps, ambiguous ability names and missing ability descriptions/mechanics, the missing native type label and remaining applicable Local/encounter/POI text remain active obligations. Existing represented-domain limits cannot be replaced with invented names or geometry.

No Android/device/emulator/ADB, SaveRAM, APK, signing or release acceptance occurred. Original archives and retained corpora remain protected. Compact proof retention and registered output cleanup follow publication; that closure must not stop the parent task. **Next implementation target remains this ROM's next unresolved native contract.**
