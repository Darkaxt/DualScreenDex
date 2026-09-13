# DualDex 1.2.0

DualDex 1.2.0 adds complete language selection for the companion interface and supported ROM content. The two settings are independent, and manual choices remain authoritative when automatic detection cannot resolve a supported device or game language.

## Interface language

- Add **Automatic**, English, French, German, Italian, and Spanish interface choices.
- Translate the companion WebView and matching native Android startup, recovery, export, accessibility, and diagnostic surfaces.
- Persist a manual interface choice independently of the active ROM.

## ROM-content language

- Add independent ROM-content language projections for supported official English, French, German, Italian, Spanish, Japanese, and Korean releases.
- Use only parser-proven persisted projections; missing, stale, disconnected, invalid, or unsupported evidence fails closed to the parser-proven ROM default.
- Preserve ROM-native names and descriptions without browser translation.
- Allow changing a proven ROM-content projection without reparsing the ROM or changing the interface language.

## Validation and delivery

- Validate 43/43 official family-language cells, 44/44 exact controls, 308/308 mandatory checks, and 660/660 capability dispositions.
- Account for all 276 selected corpus rows through 274 consolidated persistence checks and two exact bounded recoveries with matching logical digests.
- Pass all eight packaged Android localization acceptance tests on API 35, plus the Web, desktop-server, Windows CI, release, and asset-validation gates.
- Preserve GitHub-managed production signing, pinned-certificate verification, immutable checksums, provenance, and non-replacing publication.
- DualDex remains read-only, bundles no ROM or private memory data, and sends no game commands or emulator-memory writes.
