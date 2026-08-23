# DualDex 1.1.0-rc.45

RC45 corrects the Gen III runtime regression that forced a valid local map back to the Atlas view after RC44 enabled the unified live SaveBlock path.

## Local map continuity

- The unified SaveBlock1 snapshot now supplies the current map identity and player coordinates directly to the live-location resolver.
- A valid decoded local area is no longer overwritten with an empty legacy-window result later in the same heartbeat.
- The existing local-map catalog, discovery ledger, fog of war, zoom, tracking, and rendering contracts are unchanged.
- The parser schema and catalog cache identity are unchanged, so this hotfix does not force a ROM reparse.

## Verification

- A regression test reproduces the RC44 unified-live-state sequence and requires the final published area and position to remain valid.
- The complete `BattleMemoryCoordinatorTest` suite passes with both unified and legacy memory paths covered.
- Production web, module, lint, release compilation, signing, checksum, and provenance gates remain enforced by the protected release workflow.

## Delivery

- RC45 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010045`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of this release preparation.
