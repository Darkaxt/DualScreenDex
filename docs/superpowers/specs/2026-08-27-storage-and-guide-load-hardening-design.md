# Storage and Guide Load Hardening Design

## Goal

DualDex must truthfully retain Android All Files Access after it is granted and must fail safely when ROM opening or guide parsing exhausts memory. A failed load must not crash-loop, publish partial catalog data, or expose implementation diagnostics in ordinary UI.

## Requirements

### Storage truth

- **SG-01:** `storageGrant` represents only Android's authoritative All Files Access result: `GRANTED` or `MISSING`.
- **SG-02:** Direct/SAF ROM discovery remains independently represented by `romGrant`: `MISSING`, `INDEXING`, `GRANTED`, or `FAILED`.
- **SG-03:** Starting or failing indexing never changes a still-granted `storageGrant` to `INDEXING` or `FAILED`.
- **SG-04:** The grant action is visible only while `storageGrant == MISSING`.
- **SG-05:** Indexing and indexing failure use concise player-facing language. Raw state names and exception details remain absent from normal pages.
- **SG-06:** A retained direct index remains usable if refresh fails. If no direct index remains, an already-granted SAF index remains the fallback.

### Transactional guide loading

- **GL-01:** Catalog publication remains atomic. A parser checkpoint or partial repository write never becomes the active catalog.
- **GL-02:** A completed persisted catalog is never replaced by partial output. The active catalog is cleared while switching to a different ROM so data from the prior game cannot be shown.
- **GL-03:** Ordinary exceptions and `OutOfMemoryError` at ROM-source and parser boundaries become recoverable load failures. Other JVM `Error` subclasses are not swallowed.
- **GL-04:** Memory-pressure failures use one static player-facing explanation; exception class and final stage are retained only in the persisted Debug performance log.
- **GL-05:** A failed automatic activation is latched by exact source identity so the two-second heartbeat cannot repeatedly reopen the same failing guide.
- **GL-06:** A visible `RETRY OPENING GAME GUIDE` action clears only that failure latch and retries the currently resolved source. New or reindexed source identity is eligible automatically.
- **GL-07:** Every success, ordinary failure, and memory failure clears the in-flight activation marker exactly once.

## Architecture

`RetroArchSetupCoordinator` remains the owner of permission, indexing, active-source resolution, and retry state. Permission and indexing are projected separately into the existing `RetroArchView`; no new storage permission mechanism is introduced.

`GuideLoadFailure` is the small shared failure policy used by ROM-source activation and `ProductionCompanionRuntime`. It converts only expected recoverable failures into player-facing exceptions while preserving the original failure for Debug metrics. The parser worker catches `Exception` and `OutOfMemoryError` separately, records the original class/stage, publishes a failed loading transition, and invokes the completion callback with the sanitized failure.

`NativeSetupRoute.RETRY_GUIDE` bridges the existing WebView route to `RetroArchSetupCoordinator.retryGuideLoad()`. It adds no timer, second poller, background service, or memory copy.

## Failure behavior

| Condition | Normal UI | Debug evidence | Retry behavior |
| --- | --- | --- | --- |
| Permission missing | Needs access and grant action | Existing setup state | User grants access |
| Permission granted, indexing | Storage Ready; finding-games status | Existing indexed count/stage | Automatic completion |
| Indexing failed | Storage still Ready; game discovery warning | Existing failure path | Folder fallback or subsequent refresh |
| Parser exception | Guide could not be opened | Final stage plus exception class | Explicit retry |
| Memory exhaustion | Not enough free memory to open guide | Final stage plus `OutOfMemoryError` | Close other apps, explicit retry |

## Verification

- Preact tests cover missing, granted, indexing, failed, and retry presentation.
- Kotlin policy tests prove permission/index separation and failure fallback.
- Runtime tests inject `OutOfMemoryError`, prove no escape, no partial catalog, persisted diagnostic type/stage, and sanitized callback/UI text.
- Native-route tests prove exact retry routing and reject malformed routes.
- Focused app/web tests run before full release gates.
- No APK installation, emulator launch, or physical-device action is part of this implementation.

## Exclusions

- No parser compatibility changes or ROM-specific exception.
- No catch-all `Throwable` boundary.
- No automatic retry timer, retry count, or timeout.
- No normal-page stack trace, exception name, parser stage, ROM path, CRC, or SHA.
