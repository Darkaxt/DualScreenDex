# DualDex 1.1.0-rc.46

RC46 restores the intended initial Combat view for wild encounters without making area encounter data a prerequisite for the IV-derived rarity rating.

## Combat initial view

- Every proven wild battle opens the enabled Rarity tab first.
- Trainer and unknown battles continue to open the front Entry tab.
- IVs or DVs remain the primary source of the base `0–5` star rating.
- Area encounter data remains an optional half-star adjustment and no longer gates initial Rarity selection.
- Manual tab selection during an active battle remains undisturbed by later battle updates.

## Verification

- The regression test was observed failing for a wild encounter whose area adjustment was unavailable, then passing after the selection gate was corrected.
- The complete companion gateway and production battle-lifecycle suites pass.
- No ROM parsing or parser-schema change is involved in this correction.
- Production web, module, lint, release compilation, signing, checksum, and provenance gates remain enforced by the protected release workflow.

## Delivery

- RC46 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010046`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of this release preparation.
