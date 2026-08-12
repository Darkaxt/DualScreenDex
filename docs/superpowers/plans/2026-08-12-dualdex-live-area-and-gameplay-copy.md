# DualDex Live Area and Gameplay Copy Implementation Plan

**Goal:** Use live ROM-proven Gen III location data for weighted area rarity, show ROM-derived place names, and remove technical/debug prose from gameplay screens.

**Architecture:** Parser-core publishes immutable runtime metadata. Catalog-store persists it. Android battle polling reads the ROM-proven SaveBlock1 pointer and location. Companion-core selects live area before matched SaveRAM. The web layer renders only concise gameplay copy.

## Task 1: Parser runtime metadata

- Add tests for a unique compiled SaveBlock1 pointer global and ambiguous/no-proof cases.
- Extract the existing SaveBlock1 consumer proof into `Gen3SaveBlock1PointerResolver` and reuse it from the learnset selector.
- Add a structural Gen III map-location resolver using selected encounter map keys, MapHeader region identifiers, and validated ROM text.
- Add `CatalogRuntimeMetadata` to `ParsedCatalog` and materialize it once.
- Run focused parser tests, including exact Modern Emerald.

## Task 2: Catalog persistence

- Add a failing catalog round-trip test for the runtime pointer and map-name map.
- Add a `runtime_metadata` section, bump parser schema, and include it in progressive write phases.
- Verify old parser-schema caches fail closed and current metadata reopens exactly.

## Task 3: Live battle area

- Add failing battle coordinator tests for pointer-region plus live-location reads, pointer changes, and invalid pointer rejection.
- Extend `BattleCatalogContext` and `BattleMemorySample` with runtime metadata/current area.
- Read the pointer on discovery and every cached cycle; read location from full EWRAM or a bounded location region.
- Propagate live area into `BattleState` in `ProductionCompanionRuntime`.

## Task 4: API and rarity presentation

- Add API tests proving live area wins over SaveRAM, SaveRAM is fallback, and area name is resolved without raw hex.
- Publish `currentAreaName` and use the selected base ID for `RarityEvaluator`.
- Add web tests for two-word success, innate-only failure, and the absence of `UNKNOWN` and diagnostic reasons.

## Task 5: Organic gameplay copy

- Add focused UI tests for Battle, Pokédex detail/browse, Move Detail, and Ability Detail.
- Replace policy/debug phrases with the approved concise copy.
- Retain technical diagnostics only on the Diagnostics screen.
- Run the complete web test suite and production build.

## Task 6: Completion

- Run full Kotlin/Android tests, lint, release assembly, release tooling, and `git diff --check`.
- Update release notes/readiness evidence and version to RC15.
- Commit and push the branch and `v1.0.0-rc.15` tag.
- Publish a public non-draft prerelease, verify the downloaded APK/signature/checksum, install only that APK on `bfa98654`, and perform no other device interaction.
