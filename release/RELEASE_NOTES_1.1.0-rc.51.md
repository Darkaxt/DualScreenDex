# DualDex 1.1.0-rc.51

RC51 corrects the remaining RC50 title-screen initialization false positive without changing the parser schema or invalidating an existing catalog cache.

## Game initialization

- The Gen III five-byte live clock now retains its seconds field instead of discarding it after validation.
- When a supported live clock is present, exact `00:00:00` is treated as an uninitialized title-screen sample even if stale WRAM bytes decode as a plausible location and trainer identity.
- The first advancing second allows initialization, so a legitimate midnight save waits at most one second.
- ROMs without a resolved live clock retain the existing coherent live-location and trainer-identity gate.
- Initialization remains one-way for the active ROM session; later partial or zero-valued samples cannot return the companion to the waiting screen.

## Validation

- The observed Modern Emerald title-screen state is covered with Littleroot Town coordinates, an identity-like memory value, an empty party, and `00:00:00`.
- A separate regression proves that `00:00:01` unlocks the initialized game.
- The existing one-way initialization regression remains covered.
- Battle-memory and Android app unit suites pass.

## Delivery

- RC51 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010051`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
