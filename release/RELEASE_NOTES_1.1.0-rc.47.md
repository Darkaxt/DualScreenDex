# DualDex 1.1.0-rc.47

RC47 keeps late-confirmed wild battles on the intended Rarity view, replaces the Local Map portrait with the ROM's gender-selected overworld sprite, and adds compact Pokédex result counts.

## Combat continuity

- A battle first observed with unknown ownership now promotes to Rarity as soon as passive memory proves it is wild.
- A player-selected Combat tab is never replaced by that late classification.
- Trainer and still-unknown battles retain the Entry view.

## Local Map trainer marker

- Local Maps use separately parsed male and female overworld frames instead of the 64x64 Trainer Card portrait.
- Standard official Gen III and Odyssey retain native 16x32 dimensions; Unbound retains its native 32x32 dimensions.
- The marker follows the live trainer gender, scales with the raster, and never renders below the ROM asset's native size.
- When the map-specific role cannot be proven, the existing compact player dot remains the fail-safe.

## Pokédex counts

- All, Seen, and Area show the concise current-result count as `owned / found`.
- Caught shows only its total.
- Team shows neither the search control nor a redundant counter.
- Search and active filters update the displayed count immediately.

## Parser and cache compatibility

- Parser schema 34 adds persisted gender-specific overworld sprite roles and native dimensions.
- Existing schema-33 catalogs revalidate once after this parser update; complete schema-34 catalogs continue to use the exact-ROM cache bypass.
- Real-ROM controls pass for the official Gen I-III set, Modern Emerald, Odyssey 4.1.1, and Unbound 2.1.1.1.
- Official Emerald, FireRed, LeafGreen, Odyssey, and Unbound catalogs complete all 16 required SQLite sections and reopen with integrity and foreign-key checks clean.

## Delivery

- RC47 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010047`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of this release preparation.
