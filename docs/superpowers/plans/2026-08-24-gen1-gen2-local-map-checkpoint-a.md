# Gen I/II Local-Map Parity Checkpoint A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Session constraint:** Execute inline in the owning thread. The user prohibited agents for this reverse-engineering work.

**Goal:** Give Gen I and Gen II the RC53 continuous Local-map experience—connection-derived scenes, existing live player following, preserved Gen II lighting, and fail-closed ROM-derived overworld markers—then publish a signed Checkpoint A APK.

**Architecture:** Keep the generation-neutral catalog, API, and `MapPage` renderer authoritative. Extract Gen III's proven placement behavior into a shared constraint solver; add bounded Gen I and Gen II compiled-connection decoders that feed it; add isolated GB/GBC trainer-asset adapters behind the existing trainer catalog. Rebuild stale catalogs once, validate every stage against the approved specification, and reconcile with `fork/master` before every commit.

**Tech Stack:** Kotlin/JVM 17, JUnit 4, Android/Gradle, SQLite catalog cache, Preact/TypeScript/Vitest, GitHub Actions protected Android signing.

**Specification:** `docs/superpowers/specs/2026-08-24-gen1-gen2-local-map-parity-design.md`

**Source oracles (never production inputs):**

- `D:/Temp/PokemonHacks/sources/Official/pokered/macros/scripts/maps.asm`
- `D:/Temp/PokemonHacks/sources/Official/pokecrystal/data/maps/attributes.asm`
- `D:/Temp/PokemonHacks/sources/Official/pokered/home/overworld.asm`
- `D:/Temp/PokemonHacks/sources/Official/pokecrystal/engine/overworld/overworld.asm`
- `D:/Temp/PokemonHacks/sources/Official/pokecrystal/data/sprites/sprites.asm`
- `D:/Temp/PokemonHacks/sources/Official/pokecrystal/engine/gfx/color.asm`

---

## File structure

### Create

- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapSceneBuilder.kt` — generation-neutral constraint canonicalization, safe placement, and deterministic partitioning.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1MapSceneResolver.kt` — bounded Gen I 11-byte cardinal-connection decoder.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2MapSceneResolver.kt` — bounded Gen II 12-byte cardinal-connection decoder.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/GbTrainerAssetResolver.kt` — structurally resolves Gen I/II normal walking frames and Gen II object palettes.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapSceneBuilderTest.kt` — shared placement and partition contract.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1MapSceneResolverTest.kt` — synthetic Gen I ABI and malformed-record isolation.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2MapSceneResolverTest.kt` — synthetic Gen II ABI and malformed-record isolation.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/GbTrainerAssetResolverRealControlTest.kt` — official compiled-ROM player-frame controls.
- `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/GbGbcLocalMapMatrix.kt` — private-input, public-safe deterministic GB/GBC corpus evidence runner.
- `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md` — stage-by-stage specification audit and blocker/deferral ledger.
- `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a.md` — final public-safe validation evidence.

### Modify

- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3MapSceneResolver.kt` — retain Gen III decoding while delegating normalized constraints to the shared builder.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3MapSceneResolverTest.kt` — characterization coverage before and after extraction.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1LocalMapResolver.kt` — retain structural connection sources and publish scenes without changing raster output.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolver.kt` — retain structural connection sources, publish scenes, and preserve indexed lighting assets.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1LocalMapResolverRealControlTest.kt` — exact official scene geometry plus existing raster controls.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolverRealControlTest.kt` — exact official scene geometry plus unchanged four-palette hashes.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/TrainerAssetModels.kt` — support validated single-appearance GB/GBC and native 16×16 frames without weakening Gen III validation.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModelsTest.kt` — trainer-asset invariants for one/two appearances and GB/GBC/GBA dimensions.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt` — generation-dispatched fail-closed trainer assets.
- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt` — select a sole overworld asset when the engine has no gender identity.
- `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt` — sole-asset API behavior and native dimensions.
- `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt` — bump parser schema from 34 to 35.
- `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt` — Gen I/II scene and trainer-asset round trips plus schema assertions.
- `companion-web/src/pages/MapPage.test.tsx` — native 16×16 marker and Gen II timed-scene presentation regression.
- `parser-cli/build.gradle.kts` — register the deterministic GB/GBC matrix runner.
- `release/v1-ready.json` and `.github/workflows/release.yml` — add Checkpoint A release evidence only after it passes.
- `release/RELEASE_NOTES_${VERSION_NAME}.md` — dynamically named from the next available RC after final master/tag reconciliation.

---

## Mandatory reconciliation procedure

Run this immediately before **every** commit in the tasks below:

```bash
git fetch fork master feature/unified-map-navigation --tags --prune
printf 'HEAD=%s\nMASTER=%s\nREMOTE_BRANCH=%s\nMERGE_BASE=%s\n' \
  "$(git rev-parse HEAD)" \
  "$(git rev-parse fork/master)" \
  "$(git rev-parse fork/feature/unified-map-navigation)" \
  "$(git merge-base HEAD fork/master)"
git log --oneline --decorate HEAD..fork/master
git diff --name-status HEAD..fork/master
```

If `fork/master` advanced after local edits began:

```bash
git stash push -u -m 'checkpoint-a-pre-master-sync'
git rebase fork/master
git stash pop
git status --short
git diff --check
```

Inspect every overlap; do not use blanket `--ours`/`--theirs`. Rerun tests for both the incoming subsystem and the current task. Commit only when `fork/master` is an ancestor of the integrated tree. Never force-push.

---

### Task 1: Establish the stage ledger and freeze Gen III behavior

**Files:**
- Create: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3MapSceneResolverTest.kt`

- [ ] **Step 1: Create the audit ledger**

```markdown
# Gen I/II Map Parity Checkpoint A Audit

Specification: `docs/superpowers/specs/2026-08-24-gen1-gen2-local-map-parity-design.md`

| Stage | Requirement | Evidence | Classification | Target / acceptance |
|---|---|---|---|---|
| Baseline | Reconcile with `fork/master` before every commit | HEAD/master/merge-base recorded in commit evidence | PASS | Re-run at every commit |
| Shared solver | Gen III output remains unchanged | Pending | BLOCKER | Existing and added Gen III tests pass exactly |
| Gen I scenes | Compiled connections produce bounded scenes | Pending | BLOCKER | Red/Blue/Yellow strict controls pass |
| Gen II scenes | Compiled connections preserve four palettes | Pending | BLOCKER | Gold/Silver/Crystal strict controls pass |
| Live player | Existing area and X/Y drive shared scene marker | Pending | BLOCKER | Android and API tests pass |
| Overworld marker | Structurally resolved frame or compact-dot fallback | Pending | BLOCKER | Official controls and fail-closed tests pass |
| Discovery / Atlas | RC53 hidden-image and fallback contract remains intact | Pending | BLOCKER | Web tests pass |
| Persistence | Existing catalogs rebuild once and round-trip | Pending | BLOCKER | parser schema 35 cache tests pass |
| GB/GBC corpus | No accepted Local raster regresses | Pending | BLOCKER | deterministic matrix reports zero parser errors/regressions |
```

Do not mark a row `PASS` from intent. Link it to a command, test, or bounded real-ROM result. A `DEFERRED` row must include a task ID, target stage, and acceptance condition; a deferral that violates Checkpoint A stays a blocker.

- [ ] **Step 2: Add Gen III characterization for contradictory partitioning**

Add a test that freezes the current safe-greedy result and exact scene key:

```kotlin
@Test
fun partitionsContradictoryBranchesDeterministically() {
    val maps = listOf(
        localMap(0x0001, 10, 10),
        localMap(0x0002, 10, 20),
        localMap(0x0003, 20, 10),
    )
    val bytes = ByteArray(0x1000)
    writeConnections(
        bytes,
        header = 0x100,
        connections = 0x300,
        entries = 0x380,
        connectionsToWrite = listOf(
            TestConnection(EAST, 0, 0x0002),
            TestConnection(SOUTH, 0, 0x0003),
        ),
    )

    val scenes = Gen3MapSceneResolver.resolve(
        RomImage(bytes),
        mapOf(0x0001 to 0x100, 0x0002 to 0x200, 0x0003 to 0x280),
        maps,
    )

    assertEquals("scene/0001", scenes.single().key)
    assertEquals(listOf(0x0001, 0x0002), scenes.single().placements.map { it.baseAreaId })
}
```

- [ ] **Step 3: Run the characterization tests**

Run:

```bash
./gradlew :parser-core:test --tests '*Gen3MapSceneResolverTest' --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`; no production file has changed.

- [ ] **Step 4: Reconcile with master and commit**

Run the mandatory reconciliation procedure, then:

```bash
git add docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3MapSceneResolverTest.kt
git diff --cached --check
git commit -m $'test: freeze connected map scene behavior\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

Expected: normal non-force push.

---

### Task 2: Extract the shared deterministic scene builder

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapSceneBuilder.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapSceneBuilderTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3MapSceneResolver.kt`
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`

- [ ] **Step 1: Write failing shared-solver tests**

Use a normalized constraint type and verify canonical agreement, ambiguity rejection, overlap isolation, deterministic ordering, and partitioning:

```kotlin
private fun constraint(source: Int, target: Int, x: Int, y: Int) =
    LocalMapSceneConstraint(source, target, x, y)

@Test
fun reciprocalAgreementBuildsOneScene() {
    val scenes = LocalMapSceneBuilder.build(
        maps = listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
        constraints = listOf(constraint(1, 2, 10, 2), constraint(2, 1, -10, -2)),
    )
    assertEquals(listOf(Triple(1, 0, 0), Triple(2, 10, 2)),
        scenes.single().placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) })
}

@Test
fun ambiguousPairIsDiscarded() {
    val scenes = LocalMapSceneBuilder.build(
        listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
        listOf(constraint(1, 2, 10, 0), constraint(2, 1, -10, -2)),
    )
    assertTrue(scenes.isEmpty())
}
```

- [ ] **Step 2: Run tests to verify the new type is absent**

Run:

```bash
./gradlew :parser-core:test --tests '*LocalMapSceneBuilderTest' --no-daemon --console=plain
```

Expected: compilation failure for unresolved `LocalMapSceneBuilder`/`LocalMapSceneConstraint`.

- [ ] **Step 3: Move only normalized placement logic into the new file**

Create this public-to-module boundary:

```kotlin
internal data class LocalMapSceneConstraint(
    val sourceId: Int,
    val targetId: Int,
    val deltaX: Int,
    val deltaY: Int,
)

internal object LocalMapSceneBuilder {
    fun build(
        maps: List<LocalMap>,
        constraints: List<LocalMapSceneConstraint>,
    ): List<LocalMapScene>
}
```

Implement `build` by moving the current `ConnectionKey`, canonical-delta grouping, adjacency, connected-component, safe-greedy placement, normalization, overlap checks, 8192-cell bounds, and deterministic scene-key logic from `Gen3MapSceneResolver` without semantic changes. Filter constraints whose source or target is absent from `maps` before canonicalization.

- [ ] **Step 4: Adapt Gen III decoding to emit normalized constraints**

Keep `readConnections` and cardinal decoding inside `Gen3MapSceneResolver`; replace its placement implementation with:

```kotlin
val constraints = buildList {
    // Existing bounded Gen III decoding.
    // Convert each accepted delta to LocalMapSceneConstraint(sourceId, targetId, delta.x, delta.y).
}.filter { it.sourceId !in invalidSources && it.targetId !in invalidSources }
return LocalMapSceneBuilder.build(maps, constraints)
```

Do not change Gen III direction constants, connection bounds, source invalidation, scene keys, or greedy placement order.

- [ ] **Step 5: Run shared and Gen III tests**

Run:

```bash
./gradlew :parser-core:test \
  --tests '*LocalMapSceneBuilderTest' \
  --tests '*Gen3MapSceneResolverTest' \
  --no-daemon --console=plain
```

Expected: all tests pass with exact pre-extraction Gen III output.

- [ ] **Step 6: Audit the stage**

Update the `Shared solver` row to `PASS` with the exact Gradle command. Search the diff for generation checks outside the decoder:

```bash
git diff -- parser-core/src/main | rg 'generation|EngineFamily|romName|sha256'
```

Expected: no renderer or solver ROM-identity branch. Any behavioral mismatch is a blocker.

- [ ] **Step 7: Reconcile and commit**

Run the mandatory reconciliation procedure and both test suites again, then:

```bash
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapSceneBuilder.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3MapSceneResolver.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapSceneBuilderTest.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3MapSceneResolverTest.kt \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git diff --cached --check
git commit -m $'refactor: share local map scene placement\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 3: Decode and publish Gen I scenes

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1MapSceneResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1MapSceneResolverTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1LocalMapResolver.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1LocalMapResolverRealControlTest.kt`
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`

- [ ] **Step 1: Write synthetic ABI tests**

Build two synthetic maps whose headers contain 11-byte records in north/south/west/east flag order. Assert that signed X/Y alignment—not filenames or IDs—produces the constraint. Include malformed pointer, wrong connected width, odd alignment, truncated record, and unknown target cases; each must leave maps available and omit only invalid evidence.

Use the production source boundary:

```kotlin
Gen1MapSceneResolver.Source(
    baseAreaId = 0x00,
    headerBank = 1,
    header = 0x4100,
    blockBank = 1,
    blocks = 0x4200,
)
```

Expected east placement for source width 20 and target Y offset 4:

```kotlin
assertEquals(
    listOf(Triple(0x00, 0, 0), Triple(0x0C, 20, 4)),
    resolution.scenes.single().placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) },
)
```

- [ ] **Step 2: Verify tests fail before the decoder exists**

Run:

```bash
./gradlew :parser-core:test --tests '*Gen1MapSceneResolverTest' --no-daemon --console=plain
```

Expected: unresolved resolver/source type.

- [ ] **Step 3: Implement the bounded Gen I decoder**

Use the compiled ABI:

```kotlin
internal object Gen1MapSceneResolver {
    data class Source(
        val baseAreaId: Int,
        val headerBank: Int,
        val header: Int,
        val blockBank: Int,
        val blocks: Int,
    )

    data class Resolution(
        val scenes: List<LocalMapScene>,
        val skippedReasons: List<String>,
    )

    private data class Direction(val mask: Int, val kind: Kind)
    private enum class Kind { NORTH, SOUTH, WEST, EAST }

    private val directions = listOf(
        Direction(0x08, Kind.NORTH),
        Direction(0x04, Kind.SOUTH),
        Direction(0x02, Kind.WEST),
        Direction(0x01, Kind.EAST),
    )
}
```

For every set flag, advance exactly 11 bytes and validate independently:

- byte 0: target map ID,
- bytes 1–2: strip pointer resolved in the target block bank and inside target block data,
- bytes 3–4 and 9–10: WRAM destination/view pointers,
- byte 5: non-zero bounded strip length,
- byte 6: target block width equals `target.gridWidth / 2`,
- bytes 7–8: signed Y/X alignment,
- north/south X alignment and west/east Y alignment are even,
- north Y equals `target.gridHeight - 1`, south Y equals 0,
- west X equals `target.gridWidth - 1`, east X equals 0.

Derive the grid-cell displacement:

```kotlin
val alongEdge = when (kind) {
    Kind.NORTH, Kind.SOUTH -> -signedByte(xAlignment)
    Kind.WEST, Kind.EAST -> -signedByte(yAlignment)
}
val constraint = when (kind) {
    Kind.NORTH -> LocalMapSceneConstraint(sourceId, targetId, alongEdge, -target.gridHeight)
    Kind.SOUTH -> LocalMapSceneConstraint(sourceId, targetId, alongEdge, source.gridHeight)
    Kind.WEST -> LocalMapSceneConstraint(sourceId, targetId, -target.gridWidth, alongEdge)
    Kind.EAST -> LocalMapSceneConstraint(sourceId, targetId, source.gridWidth, alongEdge)
}
```

Call `LocalMapSceneBuilder.build(maps, constraints)` and return bounded diagnostics. Do not throw outside the optional resolver.

- [ ] **Step 4: Retain connection authority in Gen I descriptors**

Extend `MapDescriptor` with `headerBank`, `header`, `blockBank`, and `blocks`. Populate them in `readDescriptor`; do not change `blockIds`, dimensions, keys, names, or rendering. After raster rendering, call the scene resolver with rendered maps only:

```kotlin
val sceneResolution = runCatching {
    Gen1MapSceneResolver.resolve(
        session.rom,
        authority.descriptors.map { it.toSceneSource() },
        maps,
    )
}.getOrElse { failure ->
    Gen1MapSceneResolver.Resolution(emptyList(), listOf("Gen I scenes: ${failure.message}"))
}
val catalog = LocalMapCatalog(
    maps = maps,
    assets = assets,
    scenes = sceneResolution.scenes,
).validate()
```

Append scene/diagnostic reasons, but do not increment `skippedMaps` for optional connection failures.

- [ ] **Step 5: Add strict official scene controls**

Extend the existing Red/Blue/Yellow test to assert:

- existing map counts and ARGB hashes remain byte-for-byte unchanged,
- Pallet Town (`0x00`) and Route 1 (`0x0C`) belong to one scene,
- their exact placement delta matches compiled connection alignment,
- all scenes are bounded, overlap-free, and assign each map at most once.

Compute the expected placement once from the compiled record, independently compare it with the public source macro, then freeze the literal geometry in the control. Do not derive the expected value by calling production decoder helpers.

- [ ] **Step 6: Run synthetic and official controls**

Run:

```bash
./gradlew :parser-core:test \
  --tests '*Gen1MapSceneResolverTest' \
  --tests '*Gen1LocalMapResolverRealControlTest' \
  --no-daemon --console=plain
```

Expected: synthetic suite passes; Red/Blue/Yellow run when their environment variables exist and otherwise report JUnit assumptions, never failures.

- [ ] **Step 7: Audit, reconcile, and commit**

Mark `Gen I scenes` `PASS` only with exact official evidence. Any changed raster hash or missing official chain is a blocker. Run the mandatory reconciliation procedure, rerun tests, then:

```bash
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1MapSceneResolver.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1LocalMapResolver.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1MapSceneResolverTest.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1LocalMapResolverRealControlTest.kt \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git diff --cached --check
git commit -m $'feat: connect Gen I local maps\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 4: Decode and publish Gen II scenes without changing lighting

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2MapSceneResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2MapSceneResolverTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolver.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolverRealControlTest.kt`
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`

- [ ] **Step 1: Write synthetic Gen II connection tests**

Use the 12-byte ABI and group/map target identity:

```kotlin
Gen2MapSceneResolver.Source(
    baseAreaId = 0x1804,
    attributesBank = 0x25,
    attributes = 0x94000,
    blockBank = 0x12,
    blocks = 0x48000,
)
```

Test all four directions, signed alignment, conflicting reciprocal evidence, wrong target block width, target strip pointers outside target block data, truncated records, and unknown group/map targets. A malformed connection must not remove either `LocalMap` or indexed asset.

- [ ] **Step 2: Verify the tests fail before implementation**

Run:

```bash
./gradlew :parser-core:test --tests '*Gen2MapSceneResolverTest' --no-daemon --console=plain
```

Expected: unresolved resolver/source type.

- [ ] **Step 3: Implement the Gen II decoder**

Mirror the Gen I resolver boundary but decode:

- bytes 0–1: target group/map,
- bytes 2–3: strip pointer in the target block bank,
- bytes 4–5: WRAM destination,
- byte 6: strip length,
- byte 7: target block width,
- bytes 8–9: signed Y/X alignment,
- bytes 10–11: WRAM view pointer.

Connection flags remain ordered `0x08`, `0x04`, `0x02`, `0x01`. Apply the same alignment-derived grid displacement and cardinal edge delta as Gen I, then delegate to `LocalMapSceneBuilder`.

- [ ] **Step 4: Retain Gen II structural sources and attach scenes**

Extend `MapDescriptor` with attributes/block authority, populate it in `readDescriptor`, and assemble:

```kotlin
val catalog = LocalMapCatalog(
    maps = maps,
    indexedAssets = assets,
    scenes = sceneResolution.scenes,
).validate()
```

Do not convert assets to PNG, precompose scenes, alter `lightingPolicy`, alter `MapLightingPalettes`, or change `gen2TimeOfDayWramOffset`.

- [ ] **Step 5: Extend official Gold/Silver/Crystal controls**

Freeze exact compiled geometry for New Bark Town (`0x1804`), Route 29 (`0x1803`), and their connected scene. Retain every existing map count, named-map count, time-of-day WRAM offset, day hash, and four New Bark palette hashes. Add global uniqueness/overlap/bounds assertions.

- [ ] **Step 6: Run Gen II and shared regression tests**

Run:

```bash
./gradlew :parser-core:test \
  --tests '*Gen2MapSceneResolverTest' \
  --tests '*Gen2LocalMapResolverRealControlTest' \
  --tests '*LocalMapSceneBuilderTest' \
  --tests '*Gen3MapSceneResolverTest' \
  --no-daemon --console=plain
```

Expected: success; all four existing palette hashes are unchanged.

- [ ] **Step 7: Audit, reconcile, and commit**

Mark `Gen II scenes` `PASS` only when exact geometry and palette preservation pass. Run the mandatory reconciliation procedure and tests, then:

```bash
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2MapSceneResolver.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolver.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2MapSceneResolverTest.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolverRealControlTest.kt \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git diff --cached --check
git commit -m $'feat: connect Gen II local maps\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 5: Generalize trainer assets and sole-appearance API selection

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/TrainerAssetModels.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModelsTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`

- [ ] **Step 1: Write failing model tests**

Add tests proving one appearance and native GB/GBC dimensions are valid, while invalid gender keys and dimensions remain rejected:

```kotlin
@Test
fun trainerAssetsAcceptOneNativeGbOverworldAppearance() {
    val catalog = TrainerAssetCatalog(
        overworldAssetKeys = mapOf(0 to "trainer/overworld/player"),
        assets = mapOf("trainer/overworld/player" to RgbaSprite(16, 16, IntArray(256))),
    )
    assertEquals(setOf(0), catalog.overworldAssetKeys.keys)
}
```

- [ ] **Step 2: Write the failing sole-asset API test**

Build a Gen I catalog with one 16×16 overworld asset and an `AppSnapshot` with no trainer gender. Assert:

```kotlin
assertEquals("/api/trainer-assets/trainer%2Foverworld%2Fplayer.png", state.trainerMapSpriteUrl)
assertEquals(16, state.trainerMapSpriteWidth)
assertEquals(16, state.trainerMapSpriteHeight)
```

- [ ] **Step 3: Verify failures**

Run:

```bash
./gradlew :parser-core:test --tests '*CatalogModelsTest' \
  :companion-core:test --tests '*ApiViewBuilderTest' \
  --no-daemon --console=plain
```

Expected: model rejects the partial gender map and 16×16 frame; API returns null.

- [ ] **Step 4: Narrowly generalize invariants and API selection**

Change overworld validation to:

```kotlin
require(overworldAssetKeys.keys.isNotEmpty())
require(overworldAssetKeys.keys.all { it in 0..1 })
require(overworldAssetKeys.values.distinct().size == overworldAssetKeys.size)
```

Allow native `(16×16)`, `(16×32)`, and `(32×32)` overworld sprites. Keep avatar and badge invariants unchanged.

Change map-sprite selection to:

```kotlin
private fun trainerMapSpriteAssetKey(snapshot: AppSnapshot, catalog: ParsedCatalog?): String? {
    val keys = catalog?.trainerAssets?.overworldAssetKeys.orEmpty()
    val gender = snapshot.trainer?.gender ?: snapshot.trainerIdentity?.gender
    return gender?.let(keys::get) ?: keys.values.distinct().singleOrNull()
}
```

Do not use the sole-asset fallback for Trainer Card portraits.

- [ ] **Step 5: Run model, API, and Gen III trainer regressions**

Run:

```bash
./gradlew :parser-core:test \
  --tests '*CatalogModelsTest' \
  --tests '*Gen3TrainerAssetResolverRealControlTest' \
  :companion-core:test --tests '*ApiViewBuilderTest' \
  --no-daemon --console=plain
```

Expected: all existing dual-gender Gen III controls still pass or are assumption-skipped.

- [ ] **Step 6: Reconcile and commit**

Run the mandatory reconciliation procedure and tests, then:

```bash
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/TrainerAssetModels.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModelsTest.kt \
  companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt \
  companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt
git diff --cached --check
git commit -m $'feat: support native GB map avatars\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 6: Resolve Gen I/II normal walking frames fail-closed

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/GbTrainerAssetResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/GbTrainerAssetResolverRealControlTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`

- [ ] **Step 1: Write official real-ROM controls**

For Red, Blue, Yellow, Gold, Silver, and Crystal, assert:

- Gen I and Gold/Silver publish one 16×16 normal walking frame,
- Crystal publishes male and female 16×16 frames,
- assets contain transparent background pixels and a bounded occupied-pixel count,
- frame ARGB hashes are exact and independently confirmed against the public source-built graphics,
- failure to resolve assets does not affect `LOCAL_MAP` availability.

- [ ] **Step 2: Implement the Gen I walking-loader contract**

Scan for the unique three-loader sequence from `LoadWalkingPlayerSpriteGraphics`, `LoadSurfingPlayerSpriteGraphics`, and `LoadBikePlayerSpriteGraphics`:

```text
11 <walk ptr> 21 <VRAM> 18 0e
11 <surf ptr> 21 <same VRAM> 18 06
11 <bike ptr> 21 <same VRAM>
d5 e5 01 0c <graphics bank> cd <copy routine>
```

Require all three ROM pointers to be distinct, bank-bounded for 12 tiles, and the rendered first 2×2-tile frame to meet occupancy bounds. Render index 0 transparent with a neutral DMG palette:

```kotlin
private val dmgObjectPalette = intArrayOf(
    0x00000000,
    0xffaaaaaa.toInt(),
    0xff555555.toInt(),
    0xff000000.toInt(),
)
```

Publish `mapOf(0 to "trainer/overworld/player")`. No title, hash, or absolute offset enters selection.

- [ ] **Step 3: Implement the Gen II sprite-table contract**

Resolve the unique `GetSprite` consumer pattern:

```text
21 <OverworldSprites> 3d 4f 06 00 3e 06 cd <AddNTimes>
2a 5f 2a 57 2a cb 37 4f 46 2a 6e 67 c9
```

Validate each six-byte table row as pointer, 12-tile length, bank, walking type, and palette index. Read row 0 for the male player. For Crystal only, accept row `0x5f` for the female player when the same ABI validates; otherwise publish only the male frame.

Resolve the unique `MapObjectPals` consumer pattern that indexes four time blocks of eight four-color palettes. Use the day block for this static UI asset and the row's palette index. Reject colors above BGR555 and render index 0 transparent.

- [ ] **Step 4: Dispatch trainer assets by generation**

Replace the Gen III-only branch in `CatalogParser` with:

```kotlin
val trainerAssets = runCatching {
    when (layout.generation) {
        1, 2 -> GbTrainerAssetResolver.resolve(rom, layout.family)
        3 -> Gen3TrainerAssetResolver.resolve(rom, layout.family)
        else -> null
    }
}.getOrNull() ?: TrainerAssetCatalog()
```

A resolver exception must produce an empty trainer catalog, not a parser failure.

- [ ] **Step 5: Run official and failure-isolation tests**

Run:

```bash
./gradlew :parser-core:test \
  --tests '*GbTrainerAssetResolverRealControlTest' \
  --tests '*Gen3TrainerAssetResolverRealControlTest' \
  --tests '*Gen1LocalMapResolverRealControlTest' \
  --tests '*Gen2LocalMapResolverRealControlTest' \
  --no-daemon --console=plain
```

Expected: exact official controls pass or assumption-skip; Local maps remain available when a synthetic trainer resolver has no unique candidate.

- [ ] **Step 6: Audit, reconcile, and commit**

Mark `Overworld marker` `PASS` with exact controls and fallback evidence. Any official unresolved frame is a blocker because its compiled ABI is available. Run the mandatory reconciliation procedure and tests, then:

```bash
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/GbTrainerAssetResolver.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/GbTrainerAssetResolverRealControlTest.kt \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git diff --cached --check
git commit -m $'feat: resolve GB and GBC map avatars\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 7: Invalidate stale caches and prove persistence

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`

- [ ] **Step 1: Extend real-ROM cache tests before the version bump**

For official Red and Crystal round trips, add:

```kotlin
assertEquals(catalog.localMaps.scenes, reopened.localMaps.scenes)
assertEquals(catalog.trainerAssets.overworldAssetKeys, reopened.trainerAssets.overworldAssetKeys)
assertEquals(catalog.trainerAssets.assets, reopened.trainerAssets.assets)
```

Compare sprite ARGB arrays explicitly if `RgbaSprite` array equality is not structural.

- [ ] **Step 2: Update schema assertions to expect 35 and verify stale rejection**

Change parser-schema assertions from 34 to 35 and retain the test that a database with `parser_schema_version = 34` is rejected.

- [ ] **Step 3: Run tests to prove the bump is required**

Run:

```bash
./gradlew :catalog-store:test --tests '*CatalogStoreTest' --no-daemon --console=plain
```

Expected: failures report current parser schema 34.

- [ ] **Step 4: Bump only the parser schema**

```kotlin
object CatalogSchema {
    const val version = 1
    const val parserSchemaVersion = 35
}
```

Do not alter the SQLite schema or generic section formats.

- [ ] **Step 5: Run persistence tests**

Run the same command. Expected: complete success; official tests may assumption-skip if ROM variables are absent.

- [ ] **Step 6: Audit, reconcile, and commit**

Mark `Persistence` `PASS`. Run the mandatory reconciliation procedure and tests, then:

```bash
git add catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt \
  catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git diff --cached --check
git commit -m $'fix: rebuild catalogs for GB map scenes\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 8: Prove shared API, runtime, lighting, discovery, and Atlas behavior

**Files:**
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`

- [ ] **Step 1: Add an API test with a Gen II timed scene**

Use a catalog with two `indexedAssets`, one scene, and one 16×16 sole overworld asset. Assert each placement is `dynamicLighting == true`, scene pixel geometry is exact, and the state exposes the sole asset without gender.

- [ ] **Step 2: Add the 16×16 marker page regression**

Render the connected catalog with:

```ts
trainerMapSpriteUrl: '/api/trainer-assets/trainer%2Foverworld%2Fplayer.png',
trainerMapSpriteWidth: 16,
trainerMapSpriteHeight: 16,
```

Assert the marker exists at native minimum dimensions and recentering preserves the current scale.

- [ ] **Step 3: Strengthen the existing timed-scene test**

Retain the existing assertion that both placement URLs change from `?lighting=DAY` to `?lighting=NIGHT` while scene transform/viewport remain unchanged. Also assert Organic mode contains no hidden image URL and no Atlas underlay.

- [ ] **Step 4: Run shared presentation and Android live-position tests**

Run:

```bash
./gradlew :companion-core:test --tests '*ApiViewBuilderTest' \
  :app:testDebugUnitTest --tests '*BattleMemoryCoordinatorTest' \
  --no-daemon --console=plain
npm --prefix companion-web test -- --run src/pages/MapPage.test.tsx src/mapEngine.test.ts
npm --prefix companion-web run build
```

Expected: Kotlin, web, and production web build succeed. Existing Gen I/II area/X/Y tests prove runtime publication; no new memory transport is introduced.

- [ ] **Step 5: Audit, reconcile, and commit**

Mark `Live player` and `Discovery / Atlas` `PASS` only with the commands above. Run mandatory reconciliation and all focused tests again, then:

```bash
git add companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt \
  companion-web/src/pages/MapPage.test.tsx \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git diff --cached --check
git commit -m $'test: verify GB connected map presentation\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 9: Run deterministic GB/GBC corpus validation

**Files:**
- Create: `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/GbGbcLocalMapMatrix.kt`
- Modify: `parser-cli/build.gradle.kts`
- Create: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a.md`
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`

- [ ] **Step 1: Implement the private-input matrix runner**

Register:

```kotlin
tasks.register<JavaExec>("gbGbcLocalMapMatrix") {
    group = "verification"
    description = "Runs deterministic GB/GBC Local-map scene regression evidence"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.enrpau.dualscreendex.parser.cli.GbGbcLocalMapMatrix")
}
```

The runner accepts `DUALDEX_GB_GBC_MANIFEST` and `DUALDEX_GB_GBC_MAP_OUTPUT`, rehashes every input, keeps only parser-selected Gen I/II rows, parses each ROM twice from fresh bytes, and emits public-safe JSON containing only manifest index, generation/family, hash, Local capability, map/asset/scene counts, deterministic scene signature, and bounded diagnostics. It must not emit filenames, absolute paths, extracted entries, ROM bytes, or source repository paths.

- [ ] **Step 2: Run the complete GB/GBC subset**

Run:

```bash
./gradlew :parser-cli:gbGbcLocalMapMatrix --no-daemon --console=plain
```

Expected:

- every selected row is deterministic across both parses,
- parser errors are zero,
- every previously accepted map and raster remains accepted,
- official strict controls resolve scenes,
- unsupported connection ABIs safely emit zero scenes rather than failing Local maps.

- [ ] **Step 3: Add strict source-backed hack controls where binaries match**

Cross-reference `D:/Temp/PokemonHacks/sources` only as an oracle. For each source-backed compiled hack selected for strict evidence, independently verify one connection record and freeze its scene signature in the matrix baseline. Do not add production identities or per-hack branches.

- [ ] **Step 4: Write public-safe evidence**

Document counts, deterministic results, official controls, source-backed structural families, zero regressions/errors, and bounded unresolved scene modules. Do not include private paths, filenames, credentials, or ROM hashes beyond already public official controls.

- [ ] **Step 5: Audit all results**

Mark `GB/GBC corpus` `PASS` only when accepted raster regressions and parser errors are both zero. A source-backed ABI that should match but fails due to implementation is a blocker. A genuinely different optional ABI may be deferred only with task ID, target stage, safe fallback, and acceptance condition.

- [ ] **Step 6: Reconcile and commit**

Run mandatory reconciliation. If incoming master changed parser selection, map resolution, serialization, or release evidence, rerun the entire matrix. Then:

```bash
git add parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/GbGbcLocalMapMatrix.kt \
  parser-cli/build.gradle.kts \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a.md \
  docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git diff --cached --check
git commit -m $'test: validate GB and GBC connected maps\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

---

### Task 10: Run the Checkpoint A specification audit and close blockers

**Files:**
- Modify: `docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md`
- Modify discovered production/test files only when fixing a recorded blocker.

- [ ] **Step 1: Trace every Checkpoint A specification requirement**

Review these sections line by line: delivery checkpoint A, architecture, player location/assets, Gen II lighting, discovery/Atlas, persistence, failure isolation, validation, concurrent-work reconciliation, and post-stage audit. Every requirement gets evidence and `PASS`, `BLOCKER`, or valid `DEFERRED` classification.

- [ ] **Step 2: Fix every blocker before closure**

For each blocker:

1. Add a failing focused test.
2. Run it to reproduce the gap.
3. Apply the smallest generic structural fix.
4. Run focused tests, official controls, and the affected corpus subset.
5. Update the ledger with exact evidence.

Do not downgrade a Checkpoint A requirement to a deferral to unblock release.

- [ ] **Step 3: Validate every deferral**

Every remaining deferral must include stable task ID, exact missing behavior, structural family, safe fail-closed result, named target stage, and concrete acceptance test. If any field is absent, reclassify it as a blocker.

- [ ] **Step 4: Run the full local gate**

Run:

```bash
./gradlew test :app:testDebugUnitTest --no-daemon --console=plain
npm --prefix companion-web test -- --run
npm --prefix companion-web run build
node --test tools/release/*.test.mjs
git diff --check
```

Then rerun all six official real-ROM controls and the GB/GBC matrix. Expected: success, assumption skips only where an environment variable is genuinely unavailable, zero parser errors, and zero accepted-raster regressions.

- [ ] **Step 5: Reconcile and commit the closed audit**

Run mandatory reconciliation and, if master advanced, rerun all affected gates. Then:

```bash
git add docs/reports/2026-08-24-gen1-gen2-map-parity-checkpoint-a-audit.md
git add -u
git status --short
git diff --cached --check
git commit -m $'test: close Gen I and II map parity checkpoint\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

Skip the commit only when the audit and tree are already unchanged; never create an empty commit.

---

### Task 11: Integrate, sign, verify, and clean the validation release

**Files:**
- Modify: `release/v1-ready.json`
- Modify: `.github/workflows/release.yml`
- Create: `release/RELEASE_NOTES_${VERSION_NAME}.md`
- Modify: `README.md` only if the existing release link is designed to track the newest prerelease.

- [ ] **Step 1: Reconcile one final time and select the next available RC**

```bash
git fetch fork master --tags --prune
git log --oneline --decorate HEAD..fork/master
git diff --name-status HEAD..fork/master
git tag --list 'v1.1.0-rc.*' --sort=-v:refname | head -n 5
```

If master advanced, integrate it deliberately and rerun all affected tests plus the final audit. Select one more than the highest published RC only after this fetch.

- [ ] **Step 2: Add release evidence**

Add a release-readiness key named for the selected RC, for example:

```json
"v11Rc54Gen1Gen2ConnectedLocalMaps": true
```

Add a matching `jq` release-policy assertion and release notes covering scene navigation, Gen II lighting preservation, player markers/fallbacks, cache rebuild, corpus evidence, known explicit deferrals, and signer isolation. Use the actual selected RC number rather than assuming RC54 if another thread advanced tags.

- [ ] **Step 3: Run release gates**

```bash
node --test tools/release/*.test.mjs
./gradlew test :app:testDebugUnitTest --no-daemon --console=plain
npm --prefix companion-web test -- --run
npm --prefix companion-web run build
```

Expected: all pass.

- [ ] **Step 4: Reconcile before the release commit**

Run the mandatory reconciliation procedure again. If `fork/master` changed, inspect overlaps, integrate, and rerun affected gates. Then:

```bash
git add release/v1-ready.json .github/workflows/release.yml release/RELEASE_NOTES_*.md README.md
git diff --cached --check
git commit -m $'release: prepare Gen I and II map parity candidate\n\nCo-Authored-By: Claude <noreply@anthropic.com>'
git push fork HEAD:feature/unified-map-navigation
```

Stage only paths that actually changed.

- [ ] **Step 5: Fast-forward `fork/master` safely**

```bash
git fetch fork master --prune
git merge-base --is-ancestor fork/master HEAD
git push fork HEAD:master
```

If the ancestor check fails, stop, reconcile, rerun affected tests, and create a new integrated commit through the mandatory procedure. Never overwrite master.

- [ ] **Step 6: Create and push the signed source tag**

Create the selected annotated tag on the exact integrated commit and push it. Do not retag an existing name:

```bash
git tag -a "$RELEASE_TAG" -m "DualDex ${RELEASE_TAG#v}"
git push fork "$RELEASE_TAG"
```

- [ ] **Step 7: Dispatch the protected release workflow**

```bash
gh workflow run release.yml --repo Darkaxt/DualScreenDex --ref "$RELEASE_TAG" -f tag="$RELEASE_TAG"
```

Wait for the workflow completion event rather than polling with sleeps. The protected GitHub environment reconstructs the persistent signing key; never inspect signing secrets.

- [ ] **Step 8: Verify the published APK independently**

Download the release APK to a disposable directory and verify:

- package `com.darkaxt.dualdex`,
- version name equals the selected RC,
- version code matches release metadata,
- APK SHA-256 matches the published artifact,
- certificate SHA-256 equals `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA`.

- [ ] **Step 9: Install only with explicit ADB ownership**

Claim one device/emulator for this thread before installation or interaction. Re-capture current UI before every gesture after pauses/failures. Do not unlock secure keyguards or enter credentials. Validate one official Gen I and one official Gen II connected-map flow, including Gen II time-aware lighting and Organic hidden-image behavior.

- [ ] **Step 10: Clean disposable artifacts and release device ownership**

Remove downloaded APK copies, temporary extraction directories, screenshots that are not retained evidence, Gradle disposable outputs that are safe to regenerate, and ADB forwards. Snapshot and stop an owned emulator after validation. Keep source, reports, protected signing material, and the published APK intact.

---

## Completion condition

Checkpoint A is complete only when:

- the audit has no blockers,
- every deferral is valid and tracked,
- all official and corpus gates pass,
- `fork/master` contains the reconciled implementation,
- a signed APK is published and independently signer-verified,
- live validation confirms one Gen I and one Gen II connected flow,
- Checkpoint B remains separately tracked for POIs and collection evidence.
