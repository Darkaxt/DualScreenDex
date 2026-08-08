# DualDex Parser POC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a pure Kotlin/JVM ROM-parser proof of concept that competitively identifies GB/GBC/GBA mainline Pokémon engine families, validates independently extractable datasets, scans the user's Pokémon ROM corpus read-only, and emits JSON and Markdown compatibility reports.

**Architecture:** Add an Android-independent `parser-core` module and a thin `parser-cli` application module. Family parsers share immutable ROM, profile, scoring, validation, and capability models; the CLI supplies filesystem/ZIP discovery and report serialization. The POC uses exact official profiles, family probes, validated inherited offsets, and relocatable text-table anchors to measure how far the approach works on derived ROMs without claiming unsupported data.

**Tech Stack:** Kotlin 2.3.10/JVM, Java 17 toolchain, Gradle 9.1, JUnit 4, Gson 2.10.1, Java ZIP and cryptography APIs.

---

## File map

- `settings.gradle.kts`: include the parser modules.
- `build.gradle.kts`: declare the Kotlin/JVM plugin for submodules.
- `parser-core/build.gradle.kts`: dependency-free parser library and tests.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`: platform, family, profile, score, capability, dataset, and result models.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt`: immutable random-access ROM wrapper, hashes, and bounds-safe reads.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/detect/RomHeader.kt`: GB/GBC/GBA header decoding and rejection.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/profile/KnownProfiles.kt`: official fingerprints, revisions, counts, and table layouts.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/PokemonTextCodec.kt`: English Gen 1–3 text decoding and validation.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/TableValidators.kt`: names, stats, moves, and pointer/table validators.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt`: seven parser probes and capability extraction.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`: exact fast path, candidate competition, threshold/margin rules, and final result.
- `parser-core/src/test/...`: synthetic tests for all parser boundaries.
- `parser-cli/build.gradle.kts`: CLI application and Gson dependency.
- `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/CorpusScanner.kt`: direct file and ZIP-entry discovery.
- `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriter.kt`: deterministic JSON and Markdown output.
- `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/Main.kt`: argument parsing and execution.
- `parser-cli/src/test/...`: temporary-corpus and report tests.
- `reports/dualdex-parser-compatibility.json`: complete structured corpus evidence.
- `reports/dualdex-parser-compatibility.md`: human-readable findings and capability matrix.

## Task 1: Create parser modules

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Create: `parser-core/build.gradle.kts`
- Create: `parser-cli/build.gradle.kts`

- [ ] **Step 1: Add module build files**

```kotlin
// settings.gradle.kts
include(":app", ":parser-core", ":parser-cli")

// root build.gradle.kts plugins block
id("org.jetbrains.kotlin.jvm") version "2.3.10" apply false

// parser-core/build.gradle.kts
plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { useJUnit() }

// parser-cli/build.gradle.kts
plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":parser-core"))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}
application { mainClass.set("com.enrpau.dualscreendex.parser.cli.MainKt") }
tasks.test { useJUnit() }
```

- [ ] **Step 2: Verify module configuration**

Run: `./gradlew.bat :parser-core:tasks :parser-cli:tasks --quiet`

Expected: both task lists complete with exit code 0.

- [ ] **Step 3: Commit**

```powershell
git add settings.gradle.kts build.gradle.kts parser-core/build.gradle.kts parser-cli/build.gradle.kts
git commit -m "build: add Kotlin parser modules"
```

## Task 2: Implement immutable ROM and header primitives

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/detect/RomHeader.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/detect/RomHeaderTest.kt`

- [ ] **Step 1: Write failing header and bounds tests**

```kotlin
@Test fun detectsGbaHeader() {
    val bytes = ByteArray(0xC0)
    "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
    "BPEE".toByteArray().copyInto(bytes, 0xAC)
    val header = RomHeaderReader.read(RomImage(bytes))
    assertEquals(Platform.GBA, header.platform)
    assertEquals("BPEE", header.gameCode)
}

@Test(expected = RomBoundsException::class)
fun boundedReadsRejectOverflow() {
    RomImage(ByteArray(16)).u32le(14)
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew.bat :parser-core:test --tests "*RomHeaderTest"`

Expected: compilation fails because parser primitives do not exist.

- [ ] **Step 3: Implement the primitives**

Define:

```kotlin
enum class Platform { GB, GBC, GBA, UNKNOWN }
enum class EngineFamily { RED_BLUE, YELLOW, GOLD_SILVER, CRYSTAL, RUBY_SAPPHIRE, EMERALD, FIRERED_LEAFGREEN }
enum class RomCapability { SPECIES_CATALOG, SPECIES_NAMES, SPECIES_TYPES, TYPE_CHART, BASE_STATS, SPRITES, POKEDEX_DESCRIPTIONS, EVOLUTIONS, MOVE_CATALOG, MOVE_DETAILS, LEARNSETS, ABILITIES }

data class RomHeader(val platform: Platform, val title: String, val gameCode: String?, val revision: Int)
```

`RomImage` must copy its input once, expose `size`, `sha256`, `crc32`, `slice`, `u8`, `u16le`, `u24le`, `u32le`, and `findAll`, and reject every out-of-range read with `RomBoundsException`.

`RomHeaderReader` reads GBA title/code/revision at `0xA0/0xAC/0xBC`, and GB/GBC title/CGB flag/revision at `0x134/0x143/0x14C`. Inputs too short for a header return `Platform.UNKNOWN` rather than throwing.

- [ ] **Step 4: Run tests**

Run: `./gradlew.bat :parser-core:test --tests "*RomHeaderTest"`

Expected: all header and bounds tests pass.

- [ ] **Step 5: Commit**

```powershell
git add parser-core
git commit -m "feat: add immutable ROM and header primitives"
```

## Task 3: Add official profiles and text codecs

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/profile/KnownProfiles.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/PokemonTextCodec.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/profile/KnownProfilesTest.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/PokemonTextCodecTest.kt`

- [ ] **Step 1: Write failing profile and codec tests**

```kotlin
@Test fun recognizesKnownEmeraldHash() {
    val profile = KnownProfiles.bySha256("a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af")
    assertEquals(EngineFamily.EMERALD, profile?.family)
    assertEquals(386, profile?.dexSpeciesCount)
}

@Test fun decodesGbaTerminatedText() {
    assertEquals("ABC", PokemonTextCodec.gbaEnglish.decode(byteArrayOf(0xBB.toByte(), 0xBC.toByte(), 0xBD.toByte(), 0xFF.toByte())))
}
```

- [ ] **Step 2: Verify failure**

Run: `./gradlew.bat :parser-core:test --tests "*KnownProfilesTest" --tests "*PokemonTextCodecTest"`

Expected: compilation fails because profiles and codecs do not exist.

- [ ] **Step 3: Implement profile and codec data**

`RomProfile` contains the exact SHA-256, title/code/revision, family, ROM size, internal/dex species counts, move count, text codec, and optional table descriptors for species names, base stats, move names, move data, learnsets/evolutions, type chart, sprites, descriptions, and abilities.

Populate the 11 local official hashes from the verified corpus. Table descriptors use factual offsets and record sizes cross-checked against public decompilation symbols and the local ROMs. Do not copy GPL parser implementation.

Implement English GB/GBC and GBA character maps, terminators, fixed-width decoding, valid-character ratios, and normalized display strings. Unknown control bytes are diagnostic-invalid rather than silently converted into printable characters.

- [ ] **Step 4: Run tests**

Run: `./gradlew.bat :parser-core:test --tests "*KnownProfilesTest" --tests "*PokemonTextCodecTest"`

Expected: all profile and codec tests pass.

- [ ] **Step 5: Commit**

```powershell
git add parser-core
git commit -m "feat: add official ROM profiles and text codecs"
```

## Task 4: Implement dataset validators

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/TableValidators.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/TableValidatorsTest.kt`

- [ ] **Step 1: Write failing synthetic validation tests**

```kotlin
@Test fun rejectsNamesWithInvalidCharacterRatio() {
    val result = TableValidators.names(ByteArray(110) { 0x01 }, offset = 0, count = 10, width = 11, codec = PokemonTextCodec.gbaEnglish)
    assertFalse(result.compatible)
}

@Test fun acceptsPlausibleGen3Stats() {
    val record = ByteArray(28)
    record[0] = 45; record[1] = 49; record[2] = 49; record[3] = 45; record[4] = 65; record[5] = 65
    record[6] = 12; record[7] = 4
    val result = TableValidators.gen3BaseStats(record, 0, 1, 28, validTypeIds = 0..17)
    assertTrue(result.compatible)
}
```

- [ ] **Step 2: Verify failure**

Run: `./gradlew.bat :parser-core:test --tests "*TableValidatorsTest"`

Expected: compilation fails because validators do not exist.

- [ ] **Step 3: Implement validators and evidence**

Define `ValidationEvidence(compatible, validRecords, totalRecords, confidence, reasons, offset, recordSize)`. Implement bounds-safe validators for fixed-width names, Gen 1/2/3 base-stat layouts, move names, Gen 1/2/3 move records, pointer tables, and internal-ID cross references. A validator catches `RomBoundsException` and returns failed evidence.

- [ ] **Step 4: Run tests**

Run: `./gradlew.bat :parser-core:test --tests "*TableValidatorsTest"`

Expected: all validator tests pass.

- [ ] **Step 5: Commit**

```powershell
git add parser-core
git commit -m "feat: validate independently extractable ROM datasets"
```

## Task 5: Implement competitive family parsers

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestratorTest.kt`

- [ ] **Step 1: Write failing competition tests**

```kotlin
@Test fun refusesCloseRunnerUp() {
    val result = ParserOrchestrator.select(listOf(probe(80), probe(74)))
    assertEquals(SelectionStatus.AMBIGUOUS, result.status)
}

@Test fun selectsClearValidatedWinner() {
    val result = ParserOrchestrator.select(listOf(probe(88), probe(50)))
    assertEquals(SelectionStatus.SELECTED, result.status)
}
```

- [ ] **Step 2: Verify failure**

Run: `./gradlew.bat :parser-core:test --tests "*ParserOrchestratorTest"`

Expected: compilation fails because probes and orchestrator do not exist.

- [ ] **Step 3: Implement family probes**

Each of the seven family parsers must:

- reject the wrong platform as a hard gate;
- score cartridge identity, ROM size, family-specific header values, exact hash, shared code/data anchors, and inherited-table validation;
- search for relocatable species-name and move-name anchors when inherited offsets fail;
- report every evidence component rather than only a total;
- extract capabilities independently from the best validated table candidates.

`ParserOrchestrator` runs every platform-compatible parser, sorts deterministically by score then family, requires score `>= 75` and margin `>= 10`, and returns `SELECTED`, `AMBIGUOUS`, or `UNSUPPORTED`. Exact known hashes bypass the margin but still run dataset validators.

- [ ] **Step 4: Run tests**

Run: `./gradlew.bat :parser-core:test --tests "*ParserOrchestratorTest"`

Expected: all competition tests pass.

- [ ] **Step 5: Commit**

```powershell
git add parser-core
git commit -m "feat: compete Pokemon engine-family parsers"
```

## Task 6: Implement read-only corpus scanner

**Files:**
- Create: `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/CorpusScanner.kt`
- Create: `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/CorpusScannerTest.kt`

- [ ] **Step 1: Write failing direct-file and ZIP tests**

```kotlin
@Test fun discoversOnlyPokemonRomInputs() {
    val found = CorpusScanner().scan(tempRoot)
    assertEquals(listOf("Pokemon Test.gba", "Pokemon Pack.zip!Pokemon Hack.gbc"), found.map { it.displayName }.sorted())
}
```

The fixture includes ROM headers, an image, a save, a non-Pokémon ROM, and a ZIP with one ROM entry. Assert that inputs remain byte-identical after scanning.

- [ ] **Step 2: Verify failure**

Run: `./gradlew.bat :parser-cli:test --tests "*CorpusScannerTest"`

Expected: compilation fails because the scanner does not exist.

- [ ] **Step 3: Implement scanner**

Recursively inspect `.gb`, `.gbc`, `.gba`, and `.zip`. Select Pokémon candidates case-insensitively using both `Pokemon` and `Pokémon` in the outer or inner filename. Read ZIP entries into memory without extraction, skip directories/encrypted/unreadable entries with structured errors, and return deterministic path order.

- [ ] **Step 4: Run tests**

Run: `./gradlew.bat :parser-cli:test --tests "*CorpusScannerTest"`

Expected: all scanner tests pass and the fixture hashes are unchanged.

- [ ] **Step 5: Commit**

```powershell
git add parser-cli
git commit -m "feat: scan Pokemon ROM archives read-only"
```

## Task 7: Implement deterministic reports and CLI

**Files:**
- Create: `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriter.kt`
- Create: `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/Main.kt`
- Create: `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriterTest.kt`

- [ ] **Step 1: Write failing report test**

```kotlin
@Test fun markdownIncludesCandidatesAndCapabilitiesWithoutExtractedText() {
    val report = ReportWriter.markdown(CorpusReport(listOf(sampleResult())))
    assertTrue(report.contains("FIRERED_LEAFGREEN"))
    assertTrue(report.contains("SPECIES_NAMES"))
    assertFalse(report.contains("BULBASAUR"))
}
```

- [ ] **Step 2: Verify failure**

Run: `./gradlew.bat :parser-cli:test --tests "*ReportWriterTest"`

Expected: compilation fails because report writer and CLI do not exist.

- [ ] **Step 3: Implement report writer and command line**

Accepted syntax:

```text
parser-cli <root> [<root> ...] --json <path> --markdown <path>
```

The JSON contains scan metadata, input identity, all probe scores/evidence, selection, capability evidence, counts, offsets, diagnostics, and duration. Markdown contains summary counts, an overall matrix, and detailed failures. Never serialize decoded names, descriptions, sprites, or ROM bytes.

- [ ] **Step 4: Run CLI tests and help command**

Run: `./gradlew.bat :parser-cli:test :parser-cli:run --args="--help"`

Expected: tests pass and help exits successfully with the documented syntax.

- [ ] **Step 5: Commit**

```powershell
git add parser-cli
git commit -m "feat: report parser compatibility evidence"
```

## Task 8: Run the private corpus and publish findings

**Files:**
- Create: `reports/dualdex-parser-compatibility.json`
- Create: `reports/dualdex-parser-compatibility.md`
- Modify parser files and tests only when the first corpus run reveals a concrete false positive, crash, or official-ROM regression.

- [ ] **Step 1: Run all tests**

Run: `./gradlew.bat :parser-core:test :parser-cli:test`

Expected: all tests pass.

- [ ] **Step 2: Record input hashes before scanning**

Run a PowerShell read-only manifest over Pokémon `.zip/.gb/.gbc/.gba` inputs in the three roots using `Get-FileHash -Algorithm SHA256`; keep it outside the repository under `D:\Temp\dualdex-parser-input-hashes-before.json`.

- [ ] **Step 3: Run the POC against all three roots**

```powershell
./gradlew.bat :parser-cli:run --args='"H:\My Drive\Roms\Nintendo - Game Boy" "H:\My Drive\Roms\Nintendo - Game Boy Color" "H:\My Drive\Roms\Nintendo - Game Boy Advance" --json reports/dualdex-parser-compatibility.json --markdown reports/dualdex-parser-compatibility.md'
```

Expected: the CLI evaluates every Pokémon candidate, reports spin-offs as unsupported, and exits 0 even when individual ROMs are partial or unsupported.

- [ ] **Step 4: Fix only evidence-backed parser defects**

For each official misclassification, crash, or confident false positive, first add a synthetic or private-manifest regression test, verify it fails, apply the smallest parser fix, and rerun both module test suites. Do not add per-hack success overrides merely to improve the percentage.

- [ ] **Step 5: Verify source ROMs are unchanged**

Generate `D:\Temp\dualdex-parser-input-hashes-after.json` with the same command shape and compare the two manifests with `Compare-Object`.

Expected: no differences.

- [ ] **Step 6: Review report safety and determinism**

Run the corpus command a second time to alternate output paths, compare normalized JSON excluding durations, and search both committed reports for decoded species names or long hexadecimal byte sequences.

Expected: structural output is identical and no extracted game content appears.

- [ ] **Step 7: Commit implementation and reports**

```powershell
git add parser-core parser-cli reports settings.gradle.kts build.gradle.kts docs/superpowers/plans/2026-08-08-dualdex-parser-poc.md
git commit -m "feat: evaluate universal Pokemon ROM parsing"
```

- [ ] **Step 8: Clean private temporary manifests**

Delete only the two verified files `D:\Temp\dualdex-parser-input-hashes-before.json` and `D:\Temp\dualdex-parser-input-hashes-after.json` after the unchanged-input assertion passes.

## Task 9: Final verification

**Files:**
- No expected changes.

- [ ] **Step 1: Run parser verification**

Run: `./gradlew.bat :parser-core:test :parser-cli:test :parser-cli:installDist`

Expected: exit code 0, passing tests, and a runnable distribution under `parser-cli/build/install/parser-cli`.

- [ ] **Step 2: Verify Git state and report presence**

Run: `git status --short` and `git log --oneline -8`.

Expected: clean worktree, implementation commits present, and both reports tracked.

- [ ] **Step 3: Summarize actual compatibility**

Report official coverage, derived-ROM capability coverage, ambiguous/unsupported cases, spin-off rejection, test counts, and exact report paths. Distinguish parser POC evidence from unimplemented runtime-memory support.
