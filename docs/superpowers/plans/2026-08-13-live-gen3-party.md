# Live Gen III Party Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Derive the active Gen III team from live core memory before an in-game save exists.

**Architecture:** Extend parser-owned runtime metadata with an optional structurally resolved party count/base pair. Read the bounded party window through the existing RetroArch memory coordinator, decode it with `Gen3PokemonCodec`, and overlay it on SaveRAM-derived knowledge while preserving stored Pokémon and fail-closed fallback.

**Tech Stack:** Kotlin/JVM, Android, Gradle, RetroArch Network Commands, JUnit 4.

---

### Task 1: Resolve and persist the live-party layout

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3RuntimeMemoryLayoutResolver.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3RuntimeMemoryLayoutResolverTest.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/ModernEmeraldEncounterLiveRomTest.kt`
- Test: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] Add optional count and party addresses to the typed Gen III runtime layout.
- [ ] Select the unique reference-ranked adjacent EWRAM pair and reject ties or absent evidence.
- [ ] Bump the parser cache schema so older catalogs are reparsed.
- [ ] Cover synthetic uniqueness, ambiguity, exact Modern addresses, and cache round-trip.

### Task 2: Decode and publish the bounded live party

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/battle/Gen3LivePartyDecoder.kt`
- Modify: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3RuntimeMemoryDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/battle/Gen3LivePartyDecoderTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`

- [ ] Read exactly one count byte and 600 record bytes when typed metadata and a save parse context exist.
- [ ] Decode every occupied record with checksum/species/level validation and publish only complete snapshots.
- [ ] Suppress duplicate publications and clear live authority on session changes.

### Task 3: Overlay live team knowledge

**Files:**
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapper.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapperTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] Replace the party subset while preserving boxed ownership and accumulated knowledge.
- [ ] Reapply live authority after every stale SaveRAM refresh.
- [ ] Persist newly proven seen/caught ownership without inventing invalid records.

### Task 4: One final verification and release

**Files:**
- Modify: `README.md`
- Modify: `release/RELEASE_NOTES_1.0.0.md`

- [ ] Run the exact Modern parser regression plus all affected module tests, Android lint, web tests/build, and release assemble in one final verification sequence.
- [ ] Query the running unsaved Modern Emerald session and confirm the starter appears as `team=true`.
- [ ] Commit, publish signed `v1.0.0-rc.23`, install it in place, and recheck All Files access.
