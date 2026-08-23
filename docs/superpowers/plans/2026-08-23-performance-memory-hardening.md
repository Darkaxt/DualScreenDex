# Performance and Memory Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove sustained allocation churn, bound peak heap during ROM/map/catalog work, and keep long-session retained state within explicit limits.

**Architecture:** Preserve the current parser, loopback API, and UI architecture. First reuse immutable runtime projections and make polling/version checks allocation-light; then introduce bounded streaming and caches around large data; finally bound UI/background/service residency. Every stage ends in a requirement matrix checked against the companion specification before the next stage begins.

**Tech Stack:** Kotlin/JVM, Android WebView and loopback HTTP, SQLite, Preact/TypeScript, Vitest, JUnit, Gradle.

---

**Specification:** `docs/superpowers/specs/2026-08-23-performance-memory-hardening-design.md`

## File map

| Responsibility | Primary files |
| --- | --- |
| Immutable runtime projections | `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt` |
| State heartbeat transport | `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`, `companion-web/src/gateway.ts`, `companion-web/src/App.tsx` |
| Live-memory scheduling and UDP parsing | `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`, `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/NetworkCommandClient.kt`, `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReader.kt` |
| ROM/archive ingestion | `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`, `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt`, `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomSourceLoader.kt` |
| Map render/residency | `app/src/main/java/com/darkaxt/dualdex/web/LocalMapAssetRenderer.kt`, `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`, `companion-web/src/pages/MapPage.tsx` |
| Catalog streaming | `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`, `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogWriter.kt` |
| Long-lived UI/background state | `companion-web/src/pages/PokedexBrowse.tsx`, `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`, `app/src/main/java/com/darkaxt/dualdex/mapper/MapperSession.kt`, `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`, `app/src/main/java/com/darkaxt/dualdex/FloatingCompanionService.kt` |

## Stage 1 — Stop continuous churn

### Task 1: Cache catalog-scoped battle and save contexts

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write the identity and invalidation RED tests**

Add tests that call each context twice for the same catalog and require reference identity, then replace the catalog and require a different identity. Include saved-trainer replacement in the battle key:

```kotlin
val firstSave = requireNotNull(runtime.saveParseContext())
val firstBattle = requireNotNull(runtime.battleCatalogContext())
assertSame(firstSave, runtime.saveParseContext())
assertSame(firstBattle, runtime.battleCatalogContext())

runtime.loadCatalog("second.gba", secondCatalog)
assertNotSame(firstSave, runtime.saveParseContext())
assertNotSame(firstBattle, runtime.battleCatalogContext())
```

- [ ] **Step 2: Run the focused test and require RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest --rerun-tasks
```

Expected: the identity assertions fail because both projections are rebuilt.

- [ ] **Step 3: Add one catalog-keyed cache per projection**

Use reference identity for `ParsedCatalog`; include the saved trainer in the battle cache key because it is the only non-catalog input:

```kotlin
private data class CatalogValue<T>(val catalog: ParsedCatalog, val value: T)
private data class BattleContextValue(
    val catalog: ParsedCatalog,
    val trainer: TrainerSnapshot?,
    val value: BattleCatalogContext?,
)

private var cachedSaveParseContext: CatalogValue<SaveParseContext>? = null
private var cachedBattleCatalogContext: BattleContextValue? = null
```

Return the cached value when `cached.catalog === current`; otherwise build once and replace it. During `catalogPublicationInProgress`, continue returning `null`. Clear both holders in `close()` so the released catalog graph is not retained.

- [ ] **Step 4: Verify GREEN and commit**

Run the focused test, then commit only the runtime/test change:

```powershell
git add app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "perf: reuse catalog runtime contexts"
```

### Task 2: Suppress unchanged state serialization and rendering

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt`
- Modify: `companion-web/src/gateway.ts`
- Test: `companion-web/src/gateway.test.ts`
- Modify: `companion-web/src/App.tsx`
- Test: `companion-web/src/App.production.test.tsx`

- [ ] **Step 1: Write server and client RED tests**

Server test: fetch `/api/state?sinceVersion=<current>` and require HTTP 204, zero body bytes, and no JSON content type; fetch with an older version and require HTTP 200 plus the current JSON.

Client test:

```typescript
const currentVersion = vi.fn(() => 2);
const fetchMock = vi.fn(async () => ({ status: 204, ok: true }));
const onState = vi.fn();
const stop = events(currentVersion, onState);
await vi.advanceTimersByTimeAsync(750);
expect(fetchMock).toHaveBeenCalledWith('/api/state?sinceVersion=2');
expect(onState).not.toHaveBeenCalled();
stop();
```

Add an App assertion that an equal-version event never invokes the state updater path.

- [ ] **Step 2: Run RED tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.web.AndroidLoopbackServerTest --rerun-tasks
Push-Location companion-web; npm test -- --run src/gateway.test.ts src/App.production.test.tsx; Pop-Location
```

Expected: server returns 200/body and the client lacks the version callback/204 behavior.

- [ ] **Step 3: Implement conditional state responses**

Parse `sinceVersion` as a non-negative `Long`. Compare it with the cached state view before serialization:

```kotlin
private fun stateResponse(request: Request): Response {
    val view = runtime.stateView()
    val since = request.query["sinceVersion"]?.toLongOrNull()
    require(since == null || since >= 0) { "state version is invalid" }
    return if (since != null && view.version <= since) emptyResponse(204) else jsonResponse(view)
}
```

Add `204 -> "No Content"` to response writing. `emptyResponse` must set `Content-Length: 0` and write no body.

Change the client API to `events(currentVersion: () => number, onState)` and skip JSON parsing for 204. In `App`, maintain a version ref and accept only `incoming.version > current.version`, never equality.

- [ ] **Step 4: Verify GREEN and commit**

Run both focused suites, then:

```powershell
git add app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt companion-web/src/gateway.ts companion-web/src/gateway.test.ts companion-web/src/App.tsx companion-web/src/App.production.test.tsx
git commit -m "perf: skip unchanged companion state bodies"
```

### Task 3: Restore adaptive polling and eliminate packet churn

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`
- Modify: `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/NetworkCommandClient.kt`
- Test: `retroarch-session/src/test/kotlin/com/darkaxt/dualdex/retroarch/NetworkCommandClientTest.kt`
- Modify: `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReader.kt`
- Test: `retroarch-session/src/test/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReaderTest.kt`

- [ ] **Step 1: Write polling and parser RED tests**

Require the existing configurable 1–20 ms interval only during discovery and a 25 ms cadence for cached/ineligible operation:

```kotlin
assertEquals(1L, battleHeartbeatDelayMillis(true, true, 1))
assertEquals(20L, battleHeartbeatDelayMillis(true, true, 99))
assertEquals(25L, battleHeartbeatDelayMillis(true, false, 1))
assertEquals(25L, battleHeartbeatDelayMillis(false, true, 1))
```

Add a transport test using an injected buffer factory/counter and require repeated empty polls to allocate the receive buffer once. Add reader tests for mixed whitespace, lowercase/uppercase hex, wrong addresses, `ERROR`, invalid nibbles, short payloads, and a 1,024-byte response without regex helpers.

- [ ] **Step 2: Run RED tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.battle.BattleMemoryCoordinatorTest :retroarch-session:test --rerun-tasks
```

Expected: cached polling remains configurable, UDP buffers are created per poll, and the byte cursor does not exist.

- [ ] **Step 3: Implement state-aware scheduling and reusable receive storage**

```kotlin
internal fun battleHeartbeatDelayMillis(eligible: Boolean, discovering: Boolean, pollingIntervalMs: Int): Long = when {
    !eligible -> 25L
    discovering -> pollingIntervalMs.coerceIn(1, 20).toLong()
    else -> 25L
}
```

Create the 4 KiB `ByteBuffer` once in `UdpNetworkCommandTransport`, call `clear()` before each nonblocking read, and copy only the received byte count returned across the transport interface.

- [ ] **Step 4: Parse core-memory replies in one byte pass**

Replace `responseParts`, `replyAddress`, and the second parse with one `CoreMemoryReplyParser` that validates the ASCII command/address and decodes each two-nibble byte directly into the request destination. It must reject malformed data with the existing user-safe failure reasons and never materialize token strings.

- [ ] **Step 5: Verify GREEN and commit**

Run the focused suites and commit:

```powershell
git add app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/NetworkCommandClient.kt retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReader.kt retroarch-session/src/test/kotlin/com/darkaxt/dualdex/retroarch/NetworkCommandClientTest.kt retroarch-session/src/test/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReaderTest.kt
git commit -m "perf: bound live memory polling churn"
```

### Task 4: Validate Stage 1 against the specification

**Files:**
- Create: `docs/superpowers/plans/2026-08-23-performance-memory-stage-1-validation.md`
- Modify if an omitted requirement is found: `docs/superpowers/specs/2026-08-23-performance-memory-hardening-design.md`

- [ ] **Step 1: Run the complete automated gate**

```powershell
Push-Location companion-web; npm test -- --run; npm run build; Pop-Location
.\gradlew.bat verifySecureBuildDependencies test :app:lintDebug :app:assembleRelease '-PdualdexVersionName=1.1.0-rc.48' '-PdualdexVersionCode=1010048'
node --test tools/release/*.test.mjs
```

- [ ] **Step 2: Write the matrix**

Record R1–R4 with observed construction identities/counts, unchanged response bytes and callback count, discovery/steady delays, receive-buffer construction count, and parser cases. Record R5–R15 as `DEFERRED` with their exact target stage. Any failed R1–R4 item is `BLOCKER`, not deferred.

- [ ] **Step 3: Cross-check and commit**

Read every R1–R15 line against the matrix. Add any omitted discovered behavior to the specification before categorizing it. Commit the matrix and any spec correction:

```powershell
git add docs/superpowers/specs/2026-08-23-performance-memory-hardening-design.md docs/superpowers/plans/2026-08-23-performance-memory-stage-1-validation.md
git commit -m "docs: validate performance hardening stage 1"
```

Proceed only with zero Stage 1 blockers.

## Stage 2 — Bound peak heap

### Task 5: Stream and bound ROM/archive ingestion

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomSourceLoader.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/io/RomSourceLoaderTest.kt`

- [ ] RED: require fixed and chunked uploads to spool into an owned temporary file, reject compressed input above 64 MiB, reject extracted content above 32 MiB, and delete the spool on success/failure.
- [ ] GREEN: replace `Request.body: ByteArray` with a closeable body source; stream chunks directly into the spool; pass a `Path` into the loader; retain one extracted ROM array; preserve raw/ZIP/7z identity behavior.
- [ ] Verify real raw/ZIP/7z controls and commit `perf: stream bounded ROM ingestion`.

### Task 6: Add bounded map render and browser residency caches

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/LocalMapAssetRenderer.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-web/src/pages/MapPage.tsx`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt`
- Test: `companion-web/src/pages/MapPage.test.tsx`

- [ ] RED: require one render for concurrent/successive identical keys, LRU eviction above 32 MiB encoded bytes, rendering outside the runtime state lock, and browser mounting limited to current/adjacent placements within 32 MiB decoded pixels.
- [ ] GREEN: add a synchronized key-to-future LRU render cache and a current/adjacent placement window; minute changes replace only mounted timed URLs.
- [ ] Verify Modern Emerald/official Emerald local-map controls and commit `perf: bound local map raster residency`.

### Task 7: Assemble catalog chunks incrementally

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogWriter.kt`
- Test: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] RED: assert ordered cursor-to-GZIP streaming without a retained chunk list, reject missing/duplicate indices, and write zero unchanged checkpoint bytes.
- [ ] GREEN: expose ordered chunk iteration from the database adapter, feed it directly into the decoder, and compare encoded section digests before checkpoint replacement.
- [ ] Verify complete persisted/reopened official Emerald, Odyssey, and Unbound catalogs; commit `perf: stream catalog sections`.

### Task 8: Validate Stage 2

Create `docs/superpowers/plans/2026-08-23-performance-memory-stage-2-validation.md`. Record R5–R9 evidence, retain R1–R4 PASS evidence, and keep R10–R15 explicitly deferred to Stage 3. Run the full gate and proceed only with zero blockers.

## Stage 3 — Bound retained state

### Task 9: Virtualize Pokédex rows

**Files:**
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/styles.css`
- Test: `companion-web/src/pages/PokedexBrowse.test.tsx`

- [ ] RED: render 900 records and require no more than 60 mounted rows/images while preserving counts, filters, search, focus, and scroll restoration.
- [ ] GREEN: implement a fixed-height overscanned window derived from scroll position and viewport height; set sprite images to lazy decode/load.
- [ ] Verify and commit `perf: virtualize Pokedex browsing`.

### Task 10: Make storage and mapper work change-first and bounded

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/SavePollingMonitor.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/mapper/MapperSession.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/mapper/MapperSessionStore.kt`
- Test: corresponding coordinator/mapper tests under `app/src/test/java/com/darkaxt/dualdex/`

- [ ] RED: unchanged ROM/save identities must perform zero hashing/context/SQLite/deserialization operations; 33rd mapper snapshot or the 16 MiB boundary must evict oldest history without a complete-history rewrite.
- [ ] GREEN: persist cheap file identity metadata, move it before expensive context/database work, and use an append-plus-compaction mapper store capped by both count and bytes.
- [ ] Verify and commit `perf: bound background storage state`.

### Task 11: Bound WebView and loopback ownership

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/FloatingCompanionService.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`
- Test: lifecycle/server tests under `app/src/test/java/com/darkaxt/dualdex/`

- [ ] RED: prove only one surface is active, hidden JavaScript is paused, superseded WebViews are destroyed, worker count never exceeds four, and the ninth simultaneous connection is rejected without another worker.
- [ ] GREEN: centralize active-surface ownership, pause/resume timers with lifecycle state, destroy superseded WebViews, and replace the cached pool with a four-worker/eight-connection bounded executor.
- [ ] Verify and commit `perf: bound companion runtime ownership`.

### Task 12: Validate Stage 3 and release

Create `docs/superpowers/plans/2026-08-23-performance-memory-stage-3-validation.md`. Cross-check R1–R15, add and classify every omitted finding, and require zero blockers/unassigned requirements. Run official Gen I–III parser regressions and performance references for official Emerald, Odyssey, and Unbound. Run the complete web/Gradle/release-policy gate, prepare the next monotonically numbered prerelease, publish through the protected signing workflow, and independently verify public package identity, checksum, signer, provenance, and exact tag commit. Do not install or launch the APK without separate authorization.
