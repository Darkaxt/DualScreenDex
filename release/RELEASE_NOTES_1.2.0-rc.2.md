# DualDex 1.2.0-rc.2

DualDex 1.2.0 RC2 packages the complete six-stage localization system. Manual Settings choices provide deterministic interface and ROM-content language selection even when `AUTO` cannot resolve a supported device or live-game language. It supersedes the failed RC1 candidate with release-gate assertions aligned to the localized interface and typed presentation-message contract.

## Interface language

- Add device-global **Automatic**, English, French, German, Italian, and Spanish interface choices.
- Translate the complete typed companion-WebView contract and matching native Android startup, recovery, Toast, export, accessibility, and debug-QA surfaces.
- Persist an explicit manual choice independently of the active ROM and synchronize the packaged document language without changing routes, control IDs, enums, or protocol values.
- Keep manual Settings selection available as the authoritative fallback when automatic device-locale resolution is unavailable or unsupported.

## ROM-content language

- Add independent ROM-content language projections for supported official English, French, German, Italian, Spanish, Japanese, and Korean releases.
- Select only parser-proven persisted projections; missing, stale, disconnected, invalid, or unsupported live evidence fails closed to the parser-proven ROM default.
- Preserve ROM-native names and descriptions without browser translation, while keeping shared numeric, geometry, media, and gameplay data singular.
- Allow users to select a proven ROM-content projection without reparsing the ROM or changing the interface language.

## Validation and delivery

- Ratify 43/43 official family-language cells, 44/44 exact controls, 308/308 mandatory checks, and 660/660 capability dispositions.
- Account for all 276 selected corpus rows through 274 consolidated persistence/reopen observations plus two exact source-bound, one-input recoveries with matching logical digests.
- Pass the packaged API 35 Android localization acceptance gate: 8/8 tests, zero failures, errors, or skips.
- Align the complete 355-test Web release gate with localized Settings labels and typed API presentation messages.
- Preserve protected GitHub-only production signing, pinned-certificate verification, immutable checksums, provenance, and non-replacing publication.
- This candidate uses Android version code `1020002`.
- DualDex remains read-only, includes no ROM or private memory data, and sends no game commands or emulator-memory writes.
