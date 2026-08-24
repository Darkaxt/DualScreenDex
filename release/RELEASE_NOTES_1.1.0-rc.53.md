# DualDex 1.1.0-rc.53

RC53 restores automatic Rarity-tab focus for source-defined Gen III first wild battles.

## Wild encounter classification

- The Gen III `FIRST_BATTLE` modifier no longer causes a wild opponent to be classified as unknown.
- The independent trainer flag remains authoritative for trainer ownership.
- Wild opponents with usable IV rarity therefore open directly on Rarity; trainer battles continue opening on Entry.

## Cache compatibility

- Runtime normalization also corrects catalogs persisted by earlier candidates, which included the first-battle bit in their non-wild mask.
- The parser emits the corrected mask for newly built catalogs.
- No parser or catalog schema change is required, so an existing valid ROM catalog is not rebuilt for RC53.

## Validation

- The live RC52 failure was reproduced with Modern Emerald battle flags `0x14`, a resolved opponent, and a usable one-star rarity result while the UI remained on Entry.
- Regression coverage now carries those exact flags through the runtime memory decoder and production battle coordinator as a wild encounter.
- Existing trainer, ambiguous non-wild, rarity transition, parser, runtime, and Android tests remain part of the release gate.

## Delivery

- RC53 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010053`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
