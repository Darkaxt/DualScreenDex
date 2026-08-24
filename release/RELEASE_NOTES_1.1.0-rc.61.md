# DualDex 1.1.0-rc.61

RC61 completes Dreamstone's source-backed capture-ball checkpoint with compiled-authorized expanded split assets and per-ROM item relationships.

## Expanded Gen III capture balls

- Complete Thumb consumers prove separate eight-byte graphics and palette tables and their shared ball-index stride.
- Complete bounded item getters prove each ROM's 80-byte item records, item count, secondary ball ID, pocket, and type fields before the relationship is accepted.
- The parser derives the contiguous ball-index-to-item-ID mapping from each ROM; it uses no filename, title, project identity, hash, fixed table root, or per-ROM profile.
- Every graphics/palette row decodes independently. The short Strange Ball palette remains explicitly unavailable without suppressing the other 27 balls.
- Existing standard 12-ball and integrated 44-byte capture-ball paths are unchanged.

## Source-backed compatibility

- Dreamstone improves from 22/24 at 91.64% to 23/24 at 95.81%, with 28 mapped ball records and 27 decoded sprites.
- Independent Crippling controls prove the same ABI at different roots, with a different item bound and pocket value; it also maps 28 records and decodes 27 sprites.
- Dreamstone's remaining tracked gap is the bounded Local-map raster overage.
- The other seven characterized Gen III controls retain their routing, feature counts, compatibility scores, and prior materialized counts.

## Validation and delivery

- Focused Dreamstone/Crippling real-ROM tests, existing standard ball tests, and catalog-store tests pass.
- The one-job eight-ROM matrix completed with zero parser, persistence, or decoded cross-reference errors; all six selected catalogs persisted and reopened.
- Parser schema 41 intentionally rebuilds cached catalogs once so expanded capture-ball records are materialized.
- RC61 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010061`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
