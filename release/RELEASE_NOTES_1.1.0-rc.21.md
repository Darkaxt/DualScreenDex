# DualDex 1.1.0-rc.21

RC21 corrects a parser regression that caused Radical Red v4.1 to abort during ability-mechanics analysis. It retains RC20's renamed ZIP/7z matching, RC19's dynamic Local maps, and the completed v1.1 companion features.

## Radical Red parser correction

- Battle-field candidates that cannot represent the parser-selected ability-ID domain are now rejected before typed ABI construction.
- Wider candidates remain eligible for the existing structural and semantic proof; the mechanics resolver still fails closed when no complete interpretation survives.
- The exact Radical Red v4.1 ROM now selects the FireRed/LeafGreen family, resolves 19/23 applicable table types (82.21%), and writes/reopens a 15-section, 6,922,240-byte SQLite catalog.
- Its unresolved move catalog identity, learnsets, machine moves, and ability mechanics remain reported as 0% for those table types; no missing table is relabeled as not applicable.

## Compatibility evidence

- The 331-ROM Gen I–III report now records zero parser errors and zero selected catalogs without persistence.
- Radical Red contributes its measured table coverage instead of an artificial all-zero error row.
- The complete parser-core, parser-cli, and catalog-store gate passed with zero failures or errors; the exact Radical Red regression ran against its SHA-256-bound real ROM.

## Delivery

- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- RC21 is an in-place prerelease update of `com.darkaxt.dualdex`; no device installation is performed by the build workflow.
