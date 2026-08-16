# ZIP/7z ROM Parity and Cache Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load identical ROM payloads from ZIP and 7z and persist large catalogs without constructing a full JSON string.

**Architecture:** Apache Commons Compress supplies a strict single-ROM 7z reader beside the existing ZIP reader. Direct files stay seekable; stream-only sources use a bounded compatibility channel. Catalog JSON is streamed directly through GZIP in both directions while preserving the existing SQLite format.

**Tech Stack:** Kotlin/JVM, Android, Apache Commons Compress, XZ for Java, Gson streaming overloads, SQLite, JUnit 4.

---

### Task 1: Prove real archive parity is currently missing

**Files:**
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/io/RomSourceLoaderTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/storage/DirectRomLibraryIndexerTest.kt`

- [x] Add an opt-in real test accepting `DUALDEX_UNBOUND_ZIP` and `DUALDEX_UNBOUND_7Z`, loading both, and asserting SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
- [x] Run the exact tests and record RED because `.7z` is unsupported/not indexed.

### Task 2: Add strict 7z loading and indexing

**Files:**
- Modify: `parser-core/build.gradle.kts`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomSourceLoader.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/storage/StreamingRomSourceReader.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/storage/DirectRomLibraryIndexer.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/storage/AndroidRomLibraryIndexer.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`

- [x] Add Commons Compress/XZ dependencies.
- [x] Implement single-ROM 7z enumeration and extraction with the same empty/multiple-member rules as ZIP.
- [x] Add `.7z` to both discovery extension sets.
- [x] Reopen direct `file:` sources through the path overload so 7z remains file-backed.
- [x] Stream direct 7z identity/header inspection without materializing the full ROM.
- [x] Run the real Unbound tests and focused storage tests; require both archives to yield the exact same payload identity.

### Task 3: Reproduce and remove the catalog JSON allocation spike

**Files:**
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`

- [x] Add a large-section round-trip regression that exercises the same catalog codec used by Android.
- [x] Record the real Android RED from the exact Unbound crash stack and 51,362,792-byte `StringWriter.toString` allocation.
- [x] Replace `Gson.toJson(...): String` plus `readBytes().toString()` with Writer/Reader overloads directly over GZIP streams.
- [x] Encode/write and fetch/decode one section at a time.
- [x] Re-run the regression and existing cache compatibility tests.

### Task 4: Remove other unbounded duplicate representations

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/analysis/arm7/Arm7Memory.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/analysis/BattleRoleProvenance.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`
- Modify: `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt`

- [x] Reuse the immutable parser ROM in ARM7 proof machines instead of making multiple whole-ROM copies per probe.
- [x] Stream large JSON responses directly to loopback/server connections.
- [x] Stream desktop static files instead of reading them completely before response.
- [x] Run focused ARM7, loopback, and companion-server regressions.

### Task 5: Verify and ship the bounded fix

**Files:**
- Modify only release metadata required for the next RC after verification.

- [x] Run the exact Unbound incremental cache/reopen control.
- [x] Run focused parser, catalog-store, app storage, runtime, and server tests.
- [x] Build the release APK and inspect dependency packaging plus APK identity.
- [ ] Commit the implementation with the evidence documents.
- [ ] Publish the next prerelease only if the full focused gate and release build are green; do not install or perform user-side UI verification.
