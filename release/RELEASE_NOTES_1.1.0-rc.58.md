# DualDex 1.1.0-rc.58

RC58 continues the source-backed Gen III compatibility stage with fail-closed embedded species presentation for headerless unified `SpeciesInfo` binaries.

## Embedded Gen III species presentation

- A compiled name accessor and six-stat consumer must first prove the 260-byte unified species root, stride, active predicate, and species extent.
- The optional presentation tail then validates ROM-native categories, dimensions, terminated Pokédex descriptions, front graphics, and normal palettes independently of family selection.
- Front graphics remain bounded to complete 64×64 GBA frames.
- Embedded palettes may be compressed, short compressed, or raw BGR555 data; compressed bytes are no longer mistaken for palette colors.
- Headerless rows bypass retail sprite and 32/36-byte description codecs instead of weakening those validators.

## Source-backed compatibility

- Dreamstone improves from 15/24 at 62.48% to 17/24 at 70.81%.
- Its catalog now materializes 1,522 ROM-native 64×64 sprites and 1,522 Pokédex descriptions with height and weight.
- An independent Crippling compiled control validates all 1,525 active rows through the same pointer-aligned ABI despite a different accessor field alignment.
- Corrupting more than 20% of description pointers disables only descriptions; names, stats, sprites, routing, maps, and startup remain accepted.
- The other seven characterized Gen III controls retain their routing, feature counts, and compatibility scores.

## Validation and delivery

- Focused Dreamstone, Crippling, malformed-description, sprite-materializer, parser CLI, and catalog persistence gates pass.
- The eight-ROM matrix completed with zero parser, persistence, or decoded cross-reference errors; all six selected catalogs persisted and reopened.
- Parser schema 38 intentionally rebuilds cached catalogs once so newly supported media is materialized.
- RC58 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010058`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
