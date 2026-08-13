# Instant Gen III Battle Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the post-battle-flag full-memory scan by publishing a ROM-derived Gen III battle-mon address and using the existing bounded live decoder.

**Architecture:** Extend Gen III runtime metadata with an optional structurally resolved `battleMonsAddress`. The parser proves a unique related-global reference cluster; the coordinator converts it to the existing `ResolvedBattleLayout` only after the live battle flag activates, retaining full-memory discovery as the compatibility fallback.

**Tech Stack:** Kotlin/JVM, Android, JUnit 4, Gradle, RetroArch Network Commands, GitHub Actions protected release signing.

---

### Task 1: Prove the ROM-derived battle address

**Files:**
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3RuntimeMemoryLayoutResolverTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3RuntimeMemoryLayoutResolver.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`

- [ ] Add a synthetic unique-cluster assertion, an ambiguous-cluster fail-closed assertion, and the exact Modern Emerald `0x0200143C` assertion.
- [ ] Run `:parser-core:test --tests *Gen3RuntimeMemoryLayoutResolverTest` and confirm RED because runtime metadata has no battle address.
- [ ] Add optional `battleMonsAddress` metadata and select only one strongest complete related-global reference cluster.
- [ ] Rerun the focused parser test and confirm GREEN.

### Task 2: Skip full-memory discovery when the layout is proven

**Files:**
- Modify: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3RuntimeMemoryDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`

- [ ] Add a coordinator regression that completes the bounded runtime read with `inBattle=true` and asserts the next request is the small `battle-window`, with no `ewram` or `iwram` request.
- [ ] Run the focused coordinator test and confirm RED because the coordinator still enters discovery.
- [ ] Map the parser field into runtime context and derive the existing Gen III layout only when lifecycle becomes active.
- [ ] Rerun the focused coordinator test and confirm GREEN.

### Task 3: Cache migration and full verification

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/ModernEmeraldEncounterLiveRomTest.kt`
- Modify: `README.md`
- Modify: `release/RELEASE_NOTES_1.0.0.md`

- [ ] Bump the catalog schema so RC23 caches are reparsed and add round-trip coverage for `battleMonsAddress`.
- [ ] Run the complete parser, battle-memory, catalog-store, companion-core, app test, lint, web build and release-assembly command with the exact Modern Emerald control.
- [ ] Confirm `git diff --check` is clean and commit the implementation.

### Task 4: Publish and validate RC24

**Files:**
- No additional production files.

- [ ] Push commit and protected tag `v1.0.0-rc.24`.
- [ ] Dispatch the protected release workflow from the exact tag and require both jobs to succeed.
- [ ] Download the signed APK and verify its SHA-256, version, package and signing certificate against `provenance.json`.
- [ ] Verify `MANAGE_EXTERNAL_STORAGE` is allowed, install in place, and verify it remains allowed.
- [ ] With RetroArch content active, verify the live API opens battle without the full discovery request path.
