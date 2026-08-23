# DualDex 1.1.0-rc.50

RC50 corrects the RC49 game-initialization gate without changing the parser schema or invalidating an existing catalog cache.

## Game initialization

- Plausible default map bytes no longer open the companion before a Gen III save has initialized in live memory.
- Gen III now requires a coherent live location and a decoded live trainer identity. A cached or separately parsed save cannot satisfy that live-session check.
- `00:00` remains a valid in-game time; it is evidence of the reported uninitialized snapshot, not a hard-coded rejection rule.
- Initialization is one-way for the active ROM session. Later polling samples cannot return the companion to the waiting screen.
- The gate resets only when the active session ends or a different ROM/catalog begins.
- Generation I and II retain their existing live-area initialization behavior.

## Validation

- The exact Modern Emerald failure is covered with Littleroot Town coordinates `8,5`, a `00:00` clock, and no live trainer identity.
- A second regression proves that later uninitialized-looking samples cannot hide an already initialized companion.
- Companion-core and Android app unit suites pass.
- All 186 companion-web tests pass, including the loading-to-waiting transition.

## Delivery

- RC50 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010050`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- The APK is not installed or launched as part of release publication.
