# DualDex 1.1.0-rc.57

RC57 begins the source-backed Gen III compatibility stage with generic support for the integrated Nature layout used by current pokeemerald-expansion-derived binaries.

## Integrated Gen III Natures

- The parser now recognizes one structurally validated 25-record, 20-byte Nature ABI.
- ROM-native names and raised/lowered stat fields are accepted only with the exact canonical matrix and a compiled Thumb consumer proving record indexing, field reads, comparisons, and 110/90/100 scaling.
- Existing separate Nature stat, name, and flavor tables remain unchanged for official Emerald/FireRed and established sibling controls.
- Multiple eligible roots or simultaneous ABI proofs fail ambiguous instead of guessing.
- Integrated flavor affinity remains explicitly unknown until a compiled mapping is proven.

## Source-backed compatibility

- Exact Battle Theater 2.3.0 source establishes the structural oracle; the compiled ROM remains production authority.
- An independent Dreamstone compiled sibling proves the same ABI without any runtime project identity.
- Battle Theater improves from 22/23 at 95.63% to 23/23 at 99.97%.
- Dreamstone improves from 14/24 at 58.31% to 15/24 at 62.48%.
- Six additional characterized controls retain their routing, feature counts, and compatibility scores.
- HackDex CFRU and pokeemerald-expansion metadata is used only for discovery and priority clustering, never parser selection.

## Validation and delivery

- Focused Nature controls cover Battle Theater, Dreamstone, official Emerald/FireRed, Modern, Classic, Unbound, and Odyssey.
- The eight-ROM matrix completed with zero parser, persistence, or decoded cross-reference errors; all six selected catalogs persisted and reopened.
- Parser schema 37 intentionally rebuilds cached catalogs once so newly supported Nature data is materialized.
- Nature failures remain isolated from maps, other catalog modules, APIs, UI, and app startup.
- RC57 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010057`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
