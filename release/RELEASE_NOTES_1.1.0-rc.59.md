# DualDex 1.1.0-rc.59

RC59 continues the source-backed Gen III compatibility stage with fail-closed embedded abilities for headerless unified species records.

## Embedded Gen III abilities

- The already validated 260-byte species ABI supplies three two-byte ability IDs per species.
- A complete compiled Thumb consumer proves the 28-byte ability-record stride and signed AI-rating field before an ability table is accepted.
- Typed 17-byte names, terminated description pointers, signed ratings, and seven-bit mechanics flags are resolved without ROM identities, fixed table roots, or per-ROM profiles.
- Names/relationships, descriptions, and mechanics are independently gated; a failed optional field disables only its own module.
- Zero or multiple eligible compiled roots leave abilities unavailable without rejecting an otherwise valid species family.

## Source-backed compatibility

- Dreamstone improves from 17/24 at 70.81% to 20/24 at 83.31%.
- Its catalog now materializes 310 named abilities, 310 descriptions, 310 mechanics records, and ROM-native species-to-ability relationships.
- An independent Crippling control validates the same ABI at a different compiled table root.
- Corrupting more than 20% of description pointers disables only descriptions; names, relationships, mechanics, routing, maps, and startup remain accepted.
- The other seven characterized Gen III controls retain their routing, feature counts, compatibility scores, and materialized counts.

## Validation and delivery

- Focused Dreamstone, Crippling, and malformed-description controls pass.
- The eight-ROM matrix completed with zero parser, persistence, or decoded cross-reference errors; all six selected catalogs persisted and reopened.
- Parser schema 39 intentionally rebuilds cached catalogs once so newly supported abilities are materialized.
- RC59 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010059`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
