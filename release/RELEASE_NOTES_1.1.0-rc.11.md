# DualDex 1.1.0-rc.11

RC11 repairs two user-visible RC10 regressions without changing the parser or catalog compatibility baseline.

## Live game clock

- Live clock windows are now read and published even when a validated Generation III runtime layout does not expose dynamic SaveBlock pointers.
- The fix is exercised against the same pointerless layout shape used by Modern Emerald: a source-derived IWRAM clock window produces the validated in-game `HH:MM` value without using Android wall time.
- Pointer-driven unified live snapshots and checksum-validated party reads retain their existing behavior.

## Pokédex header

- The root Pokédex title now keeps its compact left inset when Trainer, Party, Map, and Settings actions are present.
- Non-root headers continue reserving the normal back-button column.

## Verification boundary

- Read-only ADB evidence on RC10 confirmed that Modern Emerald exposed valid clock bytes while the app state remained null, and that the generic action-header CSS won the cascade over the root rule.
- Focused coordinator, unified live-state, Pokédex production UI, and CSS regression suites cover the repairs.
- The signed APK is built and signed only by the protected GitHub release workflow. It is not installed or launched on a device by this release task; device acceptance remains with the user.
