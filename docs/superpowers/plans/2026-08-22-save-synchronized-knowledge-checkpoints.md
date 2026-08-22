# Save-Synchronized Knowledge Checkpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist playthrough knowledge only at validated save-file changes in an exact, portable sidecar bound to the ROM, save identity, and save bytes.

**Architecture:** `SavePollingMonitor` will emit an exact fingerprint and observation kind without rereading the save. `ProductionCompanionRuntime` will keep live discoveries in memory, seed a playthrough only from an exact fingerprinted checkpoint, and return the frozen ledger for one write on `CHANGED`. A portable direct-file store will atomically replace a sibling JSON document; non-atomic sources will use a separate app-private store with the identical envelope.

**Tech Stack:** Kotlin/JVM, Android document access, Gson, SHA-256, Java NIO atomic moves, JUnit 5, Android local unit tests.

---

### Task 1: Extract the deterministic ledger payload codec

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerJsonCodec.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepository.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerJsonCodecTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/FileKnowledgeRepositoryTest.kt`

- [ ] **Step 1: Write the failing round-trip and byte-stability tests**

```kotlin
@Test fun `codec round trips every schema six field deterministically`() {
    val ledger = completeLedgerFixture()
    val first = codec.encode(ledger)
    val second = codec.encode(ledger)
    assertContentEquals(first, second)
    assertEquals(ledger, codec.decode(first))
}

@Test fun `file repository keeps reading schema four five and six documents`() {
    listOf("knowledge-schema-4.json", "knowledge-schema-5.json", "knowledge-schema-6.json")
        .forEach { assertNotNull(repositoryFixture(it).read(ROM_SHA, SAVE_SHA)) }
}
```

- [ ] **Step 2: Run the tests and verify the codec is absent**

Run: `./gradlew :app:testDebugUnitTest --tests "com.darkaxt.dualdex.knowledge.KnowledgeLedgerJsonCodecTest" --tests "com.darkaxt.dualdex.knowledge.FileKnowledgeRepositoryTest"`

Expected: compilation fails because `KnowledgeLedgerJsonCodec` does not exist.

- [ ] **Step 3: Move the existing schema-6 DTO conversion into the codec**

```kotlin
class KnowledgeLedgerJsonCodec(private val gson: Gson = Gson()) {
    fun encode(ledger: KnowledgeLedger): ByteArray =
        gson.toJson(StoredLedger.from(ledger)).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): KnowledgeLedger? = runCatching {
        gson.fromJson(bytes.toString(Charsets.UTF_8), StoredLedger::class.java)
            .takeIf { it.schema in setOf(4, 5, 6) }
            ?.toLedger()
    }.getOrNull()
}
```

Move `StoredLedger`, `StoredSpeciesMoves`, `StoredAreaSpecies`, `StoredMatchup`, and `sanitizePreferences` from `FileKnowledgeRepository` without changing their fields or filtering rules. Keep ROM/save identity validation in `FileKnowledgeRepository`; only the ledger payload moves.

- [ ] **Step 4: Delegate legacy repository serialization to the codec and rerun**

Run the Task 1 command. Expected: both classes pass and existing schema compatibility remains exact.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/darkaxt/dualdex/knowledge app/src/test/java/com/darkaxt/dualdex/knowledge
git commit -m "refactor: isolate knowledge ledger serialization"
```

### Task 2: Define exact save fingerprints and checkpoint envelopes

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpoint.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCodec.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCodecTest.kt`

- [ ] **Step 1: Write rejection-first envelope tests**

```kotlin
@Test fun `checkpoint requires every exact identity`() {
    val bytes = codec.encode(checkpointFixture())
    assertNotNull(codec.decodeExact(bytes, checkpointFixture().key))
    assertNull(codec.decodeExact(bytes, checkpointFixture().key.copy(saveFileSha256 = OTHER_SHA)))
    assertNull(codec.decodeExact(bytes, checkpointFixture().key.copy(saveLastModifiedEpochMs = 1)))
    assertNull(codec.decodeExact(bytes, checkpointFixture().key.copy(saveSize = 1)))
}

@Test fun `legacy ledger is not a checkpoint`() {
    assertNull(codec.decodeExact(legacyLedgerBytes(), checkpointFixture().key))
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointCodecTest"`

Expected: compilation fails on the missing checkpoint types.

- [ ] **Step 3: Implement the immutable types and exact decoder**

```kotlin
data class SaveFileFingerprint(
    val sha256: String,
    val size: Long,
    val lastModifiedEpochMs: Long,
)

data class SaveCheckpointKey(
    val romSha256: String,
    val saveIdentity: String,
    val saveFileSha256: String,
    val saveSize: Long,
    val saveLastModifiedEpochMs: Long,
)

data class SaveKnowledgeCheckpoint(
    val schema: Int = 1,
    val portable: Boolean,
    val key: SaveCheckpointKey,
    val capturedAtEpochMs: Long,
    val ledger: KnowledgeLedger,
)
```

`SaveKnowledgeCheckpointCodec.decodeExact` must normalize all hashes to lowercase, require `[0-9a-f]{64}`, require schema `1`, require nonnegative size/times, compare every key field, decode the nested ledger with `KnowledgeLedgerJsonCodec`, and return `null` on any mismatch.

- [ ] **Step 4: Rerun the exact codec tests**

Expected: all checkpoint codec tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpoint* app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCodecTest.kt
git commit -m "feat: model exact save knowledge checkpoints"
```

### Task 3: Add atomic sibling storage and isolated fallback storage

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/save/SaveDocumentResolver.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolver.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointStore.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointStoreTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/save/DirectSaveDocumentResolverTest.kt`

- [ ] **Step 1: Write real-file atomicity and cleanup tests**

```kotlin
@Test fun `direct save writes complete sibling and leaves no temporary file`() {
    val save = root.resolve("Game.srm").apply { writeBytes(saveBytes) }
    val source = DirectSaveDocumentResolver.discover(entry, listOf(root)).single()
    store.write(source, checkpointFixture())
    assertTrue(root.resolve("Game.srm.dualdex.json").isFile)
    assertTrue(root.listFiles().orEmpty().none { it.name.contains("dualdex.tmp") })
}

@Test fun `failed replacement preserves prior complete sibling`() {
    val original = root.resolve("Game.srm.dualdex.json").apply { writeText("original") }
    assertFails { failingStore.write(source, checkpointFixture()) }
    assertEquals("original", original.readText())
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointStoreTest" --tests "com.darkaxt.dualdex.save.DirectSaveDocumentResolverTest"`

Expected: compilation fails on `AtomicSiblingTarget` and `SaveKnowledgeCheckpointStore`.

- [ ] **Step 3: Extend save sources with an optional atomic sibling target**

```kotlin
interface AtomicSiblingTarget {
    fun read(name: String): ByteArray?
    fun replace(name: String, bytes: ByteArray)
}

data class SaveDocumentSource(
    val id: String,
    val displayPath: String,
    val name: String,
    val size: Long,
    val lastModifiedEpochMs: Long,
    val read: () -> ByteArray,
    val atomicSiblingTarget: AtomicSiblingTarget? = null,
)
```

The direct implementation resolves only `File(name).name`, creates `.$name.dualdex.tmp` in the same parent, writes and flushes it, calls `Files.move(temp, destination, ATOMIC_MOVE, REPLACE_EXISTING)`, and deletes the temp in `finally`. It must reject separators and traversal.

- [ ] **Step 4: Implement portable-first storage with a separate fallback directory**

```kotlin
class SaveKnowledgeCheckpointStore(
    private val fallbackRoot: File,
    private val codec: SaveKnowledgeCheckpointCodec,
) {
    fun readExact(source: SaveDocumentSource, key: SaveCheckpointKey): KnowledgeLedger?
    fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint): CheckpointStorage
}

enum class CheckpointStorage { PORTABLE_SIDECAR, APP_PRIVATE_FALLBACK }
```

Use `${source.name}.dualdex.json` for the sibling. Use `${key.romSha256}.${key.saveIdentity}.${key.saveFileSha256}.json` below `knowledge-checkpoints` for fallback. The fallback also writes through a sibling temp and atomic replace. Never read `filesDir/knowledge` here.

- [ ] **Step 5: Rerun the Task 3 tests and commit**

Expected: all real-file, mismatch, traversal, atomicity, and fallback tests pass.

```bash
git add app/src/main/java/com/darkaxt/dualdex/save app/src/main/java/com/darkaxt/dualdex/knowledge app/src/test/java/com/darkaxt/dualdex/save app/src/test/java/com/darkaxt/dualdex/knowledge
git commit -m "feat: store portable save knowledge checkpoints"
```

### Task 4: Make SaveRAM polling emit one bounded fingerprinted event

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/save/SavePollingMonitor.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/save/SavePollingMonitorTest.kt`

- [ ] **Step 1: Add INITIAL, UNCHANGED, CHANGED, and SWITCHED tests**

```kotlin
@Test fun `first valid observation is initial and a changed payload is changed once`() {
    val first = monitor.poll(context, listOf(source(bytesA, modified = 10)), "ON")
    val unchanged = monitor.poll(context, listOf(source(bytesA, modified = 10)), "ON")
    val changed = monitor.poll(context, listOf(source(bytesB, modified = 20)), "ON")
    assertEquals(SaveObservationKind.INITIAL, first.observation?.kind)
    assertEquals(SaveObservationKind.UNCHANGED, unchanged.observation?.kind)
    assertEquals(SaveObservationKind.CHANGED, changed.observation?.kind)
    assertEquals(sha256(bytesB), changed.observation?.fingerprint?.sha256)
}
```

Also mutate the source ID and parsed save identity independently and require `SWITCHED`.

- [ ] **Step 2: Run the monitor test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.darkaxt.dualdex.save.SavePollingMonitorTest"`

Expected: compilation fails because the result has no observation.

- [ ] **Step 3: Hash the existing parse buffer and publish the observation**

```kotlin
enum class SaveObservationKind { INITIAL, UNCHANGED, CHANGED, SWITCHED }

data class SaveObservation(
    val kind: SaveObservationKind,
    val source: SaveDocumentSource,
    val fingerprint: SaveFileFingerprint,
) {
    fun key(snapshot: SaveSnapshot) = SaveCheckpointKey(
        romSha256 = snapshot.romIdentity.lowercase(),
        saveIdentity = snapshot.saveIdentity.lowercase(),
        saveFileSha256 = fingerprint.sha256.lowercase(),
        saveSize = fingerprint.size,
        saveLastModifiedEpochMs = fingerprint.lastModifiedEpochMs,
    )
}
```

Read each attempted source once into `bytes`, compute `MessageDigest.getInstance("SHA-256").digest(bytes)`, and pass the same `bytes` to the parser. Replace `source.read().copyOf()`; do not introduce another full save allocation. Track the accepted source ID, stable save identity, and exact fingerprint per ROM.

- [ ] **Step 4: Rerun and commit**

Expected: the monitor tests pass, including unchanged short-circuit and malformed-save retention.

```bash
git add app/src/main/java/com/darkaxt/dualdex/save/SavePollingMonitor.kt app/src/test/java/com/darkaxt/dualdex/save/SavePollingMonitorTest.kt
git commit -m "feat: classify exact save file observations"
```

### Task 5: Bind runtime knowledge to one active playthrough in memory

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write lifecycle tests before changing persistence**

```kotlin
@Test fun `same playthrough save change freezes current live knowledge`() {
    runtime.applySaveObservation(initialObservation(), saveA, matchedView)
    runtime.applyBattleTracking(discoveryOfSpecies(25))
    val result = runtime.applySaveObservation(changedObservation(), saveB, matchedView)
    assertTrue(25 in result.checkpointLedger!!.seenSpecies)
}

@Test fun `switching save identity cannot inherit prior live discoveries`() {
    runtime.applySaveObservation(initialObservation(), saveA, matchedView)
    runtime.applyBattleTracking(discoveryOfSpecies(25))
    runtime.applySaveObservation(switchedObservation(), otherSave, matchedView, checkpoint = null)
    assertFalse(25 in runtime.bootstrap().ledger.seenSpecies)
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest"`

Expected: compilation fails on `applySaveObservation` and `checkpointLedger`.

- [ ] **Step 3: Replace continuous repository writes with in-memory dirty tracking**

```kotlin
data class SaveKnowledgeApplication(
    val accepted: Boolean,
    val checkpointLedger: KnowledgeLedger? = null,
)

private data class ActivePlaythrough(
    val romSha256: String,
    val saveIdentity: String,
    val sourceId: String,
)
```

All existing `persistKnowledge(updated)` call sites become `markKnowledgeDirty(updated)`, which updates the gateway ledger only. `applySaveObservation` seeds from the exact supplied checkpoint only for `INITIAL`/`SWITCHED`, preserves the current ledger only when all `ActivePlaythrough` fields match, merges SaveRAM, and returns a checkpoint ledger only for `CHANGED`. Remove production reads/writes of the legacy `KnowledgeRepository`.

- [ ] **Step 4: Add integrity tests for empty, malformed, and cross-ROM seeds**

Require `KnowledgeLedgerSanitizer` before checkpoint application. Cross-ROM or cross-save seeds must behave exactly like no checkpoint.

- [ ] **Step 5: Rerun and commit**

```bash
git add app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "fix: bind live knowledge to the active save"
```

### Task 6: Coordinate exact checkpoint load and save-change writes

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinatorTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write coordinator tests for exact sequencing**

```kotlin
@Test fun `initial observation reads but does not write checkpoint`() {
    coordinator.apply(initialResult, matchedView)
    verify(exactly = 1) { checkpoints.readExact(source, observation.key(snapshot)) }
    verify(exactly = 0) { checkpoints.write(any(), any()) }
}

@Test fun `changed observation writes exactly the runtime frozen ledger`() {
    coordinator.apply(initialResult, matchedView)
    coordinator.apply(changedResult, matchedView)
    verify(exactly = 1) { checkpoints.write(source, match { it.ledger == expectedLedger }) }
}
```

- [ ] **Step 2: Run and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests "com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointCoordinatorTest" --tests "com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest"`

Expected: compilation fails because `SaveKnowledgeCheckpointCoordinator` does not exist.

- [ ] **Step 3: Wire portable-first reads and CHANGED-only writes**

```kotlin
class SaveKnowledgeCheckpointCoordinator(
    private val checkpoints: SaveKnowledgeCheckpointStore,
    private val applyRuntime: (SaveObservation, SaveSnapshot, SaveRamView, KnowledgeLedger?) -> SaveKnowledgeApplication,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun apply(result: SaveMonitorResult, saveView: SaveRamView): Boolean {
        val snapshot = result.snapshot ?: return false
        val observation = result.observation ?: return false
        val checkpoint = if (observation.kind == INITIAL || observation.kind == SWITCHED) {
            checkpoints.readExact(observation.source, observation.key(snapshot))
        } else null
        val application = applyRuntime(observation, snapshot, saveView, checkpoint)
        if (observation.kind == CHANGED && application.checkpointLedger != null) {
            checkpoints.write(
                observation.source,
                SaveKnowledgeCheckpoint(
                    portable = observation.source.atomicSiblingTarget != null,
                    key = observation.key(snapshot),
                    capturedAtEpochMs = clock(),
                    ledger = application.checkpointLedger,
                ),
            )
        }
        return application.accepted
    }
}
```

Construct the store in `DualDexApplication` with `File(filesDir, "knowledge-checkpoints")`, inject the small coordinator into `RetroArchSetupCoordinator`, and replace its direct `runtime.applySaveSnapshot` call with `checkpointCoordinator.apply(result, saveView)`. Catch read/write failures at this optional boundary; continue SaveRAM application and publish diagnostics only through Debug Settings state.

- [ ] **Step 4: Prove restored snapshots and unchanged files never write**

Add exact zero-write assertions for `RESTORED`, `UNCHANGED`, ambiguous saves, invalid saves, and inaccessible saves.

- [ ] **Step 5: Rerun and commit**

```bash
git add app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinator.kt app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt app/src/test/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinatorTest.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "feat: checkpoint knowledge when SaveRAM changes"
```

### Task 7: Complete regression, documentation, and release gates

**Files:**
- Modify: `README.md`
- Create: `docs/reports/save-synchronized-knowledge-checkpoints.md`
- Modify: `release/v1-ready.json`
- Modify: `.github/workflows/release.yml`
- Test: `tools/release/release-workflow.test.mjs`

- [ ] **Step 1: Run the focused Android suites**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.knowledge.*" --tests "com.darkaxt.dualdex.save.*" --tests "com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest" --no-daemon --console=plain
```

Expected: all selected tests pass with zero failures.

- [ ] **Step 2: Run the affected module gate**

```powershell
.\gradlew.bat :save-core:test :companion-core:test :catalog-store:test :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
```

Expected: build succeeds; existing SaveRAM, knowledge, POI, Party, Battle, and catalog tests remain green.

- [ ] **Step 3: Document measured behavior**

The report must record: zero writes on first observation, zero writes on unchanged polling, one write on one validated change, exact restart recovery, cross-save rejection, legacy non-promotion, direct portable sidecar success, fallback success, atomic temp cleanup, and no normal-page diagnostics.

- [ ] **Step 4: Gate the release evidence**

Add `"v11SaveSynchronizedKnowledgeCheckpoints": true` to `release/v1-ready.json`, require it with `jq -e` in `.github/workflows/release.yml`, and assert the workflow requirement plus report asset in `release-workflow.test.mjs`.

- [ ] **Step 5: Run release and diff validation**

```powershell
node --test tools/release/release-workflow.test.mjs tools/release/release-metadata.test.mjs
git diff --check
git status --short
```

Expected: 18 or more release tests pass, `git diff --check` emits nothing, and only planned files are modified.

- [ ] **Step 6: Commit the completed feature**

```bash
git add README.md docs/reports/save-synchronized-knowledge-checkpoints.md release/v1-ready.json .github/workflows/release.yml tools/release/release-workflow.test.mjs
git commit -m "docs: verify save synchronized knowledge checkpoints"
```

Do not publish or install the next RC until the user-facing save/update flow has been validated against a real direct `.sav` or `.srm` file and an app restart.
