# DualDex Parser Capability Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement and validate every remaining static ROM capability—Pokédex descriptions, evolutions, learnsets, GB/GBC sprites, and applicable abilities—across the 14-ROM GB-to-GBA corpus.

**Architecture:** Keep `FamilyParsers` as the family coordinator, move format-specific table resolution and validation into focused resolver/validator files, and represent every located dataset with the existing `ValidationEvidence` contract. Exact official profiles provide known-good starting offsets; derived Gen III games use structural discovery based on bank/pointer validity, terminators, record shape, and cross-references to the already discovered species and move catalogs.

**Tech Stack:** Kotlin/JVM 2.3.10, Java 17, Gradle 9.1, JUnit 4, immutable `RomImage`, public pret Gen I–III disassembly/decompilation formats, read-only private ROM corpus.

**Status (2026-08-09):** Completed. All 14 in-scope ROMs expose every applicable static capability; GB/GBC correctly report abilities as `N/A`. Direct files and ZIP entries share the streaming input adapter, repeated reports are deterministic apart from measured duration, and all source archives remained byte-identical.

---

## File map

- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`: extend table layouts with pointer encoding and record-layout metadata only where validation needs it.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt`: add bounds-safe Game Boy bank-address conversion.
- `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/CorpusScanner.kt`: stream direct and ZIP-entry input into the portable immutable ROM image without filesystem extraction.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/profile/KnownProfiles.kt`: add verified official offsets for descriptions, combined Gen I/II evolution/learnset pointers, Gen III evolutions/learnsets, and GB/GBC sprite references.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidators.kt`: validate descriptions, evolution records, learnsets, and referenced IDs.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/SpriteValidators.kt`: validate GB/GBC far pointers and bounded compressed streams, plus GBA LZ77 samples.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/DatasetResolvers.kt`: resolve relocated pointer/fixed tables using structural candidates and cross-table constraints.
- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt`: call the resolvers and replace hard-coded missing capabilities with evidence.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/io/RomImageTest.kt`: Game Boy bank-address tests.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidatorsTest.kt`: synthetic description/evolution/learnset validation tests.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/SpriteValidatorsTest.kt`: synthetic platform sprite-table tests.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/DatasetResolversTest.kt`: relocated-table selection and false-positive rejection tests.
- `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriter.kt`: report all capabilities in the compact matrix and summarize complete/static coverage without redefining a smaller core.
- `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriterTest.kt`: enforce complete capability reporting.
- `reports/dualdex-parser-compatibility.{json,md}`: regenerated 14-ROM evidence.

## Task 1: Add platform pointer primitives

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/io/RomImageTest.kt`

- [ ] **Step 1: Write failing bank-address tests**

```kotlin
@Test fun convertsSwitchableGbAddressToRomOffset() {
    val rom = RomImage(ByteArray(0x20000))
    assertEquals(0x12345, rom.gbBankAddress(bank = 4, address = 0x6345))
}

@Test fun rejectsSwitchableAddressInBankZero() {
    val rom = RomImage(ByteArray(0x8000))
    assertNull(rom.gbBankAddress(bank = 0, address = 0x4000))
}
```

- [ ] **Step 2: Run the tests and confirm RED**

Run: `.\gradlew.bat :parser-core:test --tests "*RomImageTest"`

Expected: compilation fails because `gbBankAddress` is absent.

- [ ] **Step 3: Implement bank conversion**

Add `gbBankAddress(bank, address)` with the Game Boy mapping `0000–3FFF -> address` and `4000–7FFF -> bank * 0x4000 + address - 0x4000`; return `null` for invalid banks, addresses, or resulting ROM offsets.

- [ ] **Step 4: Run the focused and full core tests**

Run: `.\gradlew.bat :parser-core:test --tests "*RomImageTest"` and then `.\gradlew.bat :parser-core:test`.

- [ ] **Step 5: Commit**

```powershell
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/io/RomImageTest.kt
git commit -m "feat: resolve Game Boy ROM pointers"
```

## Task 2: Validate descriptions

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidators.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidatorsTest.kt`

- [ ] **Step 1: Write failing tests for all description layouts**

Cover:

```kotlin
@Test fun acceptsGen3PokedexEntriesWithTextPointers() { /* 32-byte entries; category at +0, height +12, weight +14, description pointer +16 */ }
@Test fun rejectsGen3EntriesWhoseTextPointersEscapeRom() { /* one out-of-ROM pointer drops confidence */ }
@Test fun acceptsGen1BankRelativeDexEntryPointers() { /* 2-byte pointer table in a known bank */ }
@Test fun acceptsGen2MultiBankDexEntryPointers() { /* species ranges select the four entry banks */ }
```

Each accepted fixture must contain terminated text decoded through the correct platform codec; each rejected fixture fails for the pointer or terminator invariant, not a bounds exception.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :parser-core:test --tests "*PokemonDatasetValidatorsTest"`

Expected: compilation fails because `PokemonDatasetValidators` is absent.

- [ ] **Step 3: Implement validators**

Implement `gen1Descriptions`, `gen2Descriptions`, and `gen3Descriptions`. Require at least 85% valid entries; check pointer conversion, category/description terminators, nonempty decoded text, plausible height/weight, and table count. Return the table offset and effective record or pointer width.

- [ ] **Step 4: Run focused and full tests**

Run the focused class, then `.\gradlew.bat :parser-core:test`.

- [ ] **Step 5: Commit**

```powershell
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidators.kt parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidatorsTest.kt
git commit -m "feat: validate ROM Pokedex descriptions"
```

## Task 3: Validate evolutions and learnsets

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidators.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidatorsTest.kt`

- [ ] **Step 1: Write failing Gen I/II combined-table tests**

Fixtures use a two-byte pointer table followed by per-species data. Gen I evolution methods consume 3 or 4 bytes before a zero terminator; Gen II methods consume 4 or 5 bytes before a zero terminator. Both then contain ordered `(level, move)` pairs and a second zero terminator. Assert separate `EVOLUTIONS` and `LEARNSETS` evidence from the same source table.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :parser-core:test --tests "*PokemonDatasetValidatorsTest"`.

Expected: unresolved validator symbols or assertions fail.

- [ ] **Step 3: Implement combined-table validation**

Decode each method width by family, require recognized methods, target species in range, move IDs in range, levels `1..100`, nondecreasing levels, both terminators, and at least 90% valid species records. Treat empty evolution lists as valid.

- [ ] **Step 4: Write failing Gen III tests**

```kotlin
@Test fun acceptsGen3FixedEvolutionSlots() { /* speciesCount * slots * three u16 fields */ }
@Test fun acceptsGen3PackedLearnsetPointerTable() { /* GBA pointers to packed level:7/move:9 u16 values ending FFFF */ }
@Test fun rejectsGen3LearnsetWithUnknownMoveIds() { /* move > discovered move count */ }
```

- [ ] **Step 5: Run and confirm RED, implement, and rerun GREEN**

Implement `gen3Evolutions` with configurable slots and `gen3Learnsets` with pointer-table validation. Require method zero slots to be fully zero, recognized nonzero methods, target species bounds, `FFFF` termination, levels `1..100`, move bounds, and at least 90% valid species.

- [ ] **Step 6: Commit**

```powershell
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidators.kt parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidatorsTest.kt
git commit -m "feat: validate evolutions and learnsets"
```

## Task 4: Validate platform sprite tables

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/SpriteValidators.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/SpriteValidatorsTest.kt`

- [ ] **Step 1: Write failing sprite tests**

Cover Gen I base-stat embedded dimensions/front/back addresses, Gen II six-byte bank/address pairs, and Gen III eight-byte pointer/size/tag entries pointing at GBA LZ77 streams. Include truncated and out-of-range compressed fixtures that must fail.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :parser-core:test --tests "*SpriteValidatorsTest"`.

- [ ] **Step 3: Implement bounded stream validation**

For Gen I/II, validate dimensions and banked pointers, then parse enough of the platform compression command stream to prove a bounded terminator without output overrun. For GBA, parse the `0x10` LZ77 header and control groups, requiring exactly the declared output length and in-range back-references. Sample at least 16 distributed species and require 90% valid table pointers plus every sampled stream valid.

- [ ] **Step 4: Run focused and full tests**

Run focused sprite tests, then `.\gradlew.bat :parser-core:test`.

- [ ] **Step 5: Commit**

```powershell
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/SpriteValidators.kt parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/SpriteValidatorsTest.kt
git commit -m "feat: validate Pokemon sprite tables"
```

## Task 5: Resolve official and relocated datasets

Before dataset resolution, extend `RomImage` with an `InputStream` factory and make `CorpusScanner` use it for both direct files and ZIP entries. Parsing remains random-access after ingestion, but callers—including a future Android `ContentResolver` adapter—do not need to extract archives or pre-build a `ByteArray`. Cover chunked direct input and ZIP entry input in focused tests.

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/profile/KnownProfiles.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/DatasetResolvers.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/DatasetResolversTest.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/profile/KnownProfilesTest.kt`

- [ ] **Step 1: Record verified official layouts and failing profile assertions**

Derive every offset from the local exact-hash ROM and its corresponding pret source format. Assert that all 11 profiles contain applicable description, evolution, learnset, and sprite layouts; abilities remain absent only for Gen I/II.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :parser-core:test --tests "*KnownProfilesTest"`.

- [ ] **Step 3: Add profile layouts**

Populate the factual offsets, banks, widths, counts, evolution-slot counts, and description record sizes. Do not add hack-specific success overrides.

- [ ] **Step 4: Write failing relocated-table resolver tests**

Synthetic ROMs contain a plausible decoy plus one candidate whose pointers and decoded records cross-reference the discovered species/move counts. Assert the valid candidate wins; conflicting equally valid candidates return failed evidence with a conflict diagnostic.

- [ ] **Step 5: Implement structural resolvers**

Implement aligned scans for Gen III description arrays, fixed evolution arrays, packed learnset pointer tables, and ability-name tables. Gen I/II use their exact profile first, then scan for pointer tables whose target records validate in a common bank/range. Rank candidates by valid-record ratio, cross-reference coverage, and proximity to inherited offsets; never select conflicting top candidates silently.

- [ ] **Step 6: Run focused and full tests, then commit**

```powershell
git add parser-core
git commit -m "feat: resolve remaining Pokemon datasets"
```

## Task 6: Wire every capability into family parsing

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestratorTest.kt`

- [ ] **Step 1: Write failing probe capability tests**

Create one synthetic Gen II profile-shaped ROM and one Gen III profile-shaped ROM. Assert descriptions, evolutions, learnsets, and sprites are independently `AVAILABLE`; corrupting only learnsets leaves the other capabilities available.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :parser-core:test --tests "*ParserOrchestratorTest"`.

- [ ] **Step 3: Replace hard-coded missing evidence**

Resolve and validate each dataset before `buildCapabilities`, pass all evidence explicitly, and retain `NOT_APPLICABLE` only for abilities in Gen I/II. Add dataset-specific diagnostics for inherited, relocated, conflicted, and structurally invalid candidates.

- [ ] **Step 4: Run focused and full core tests, then commit**

```powershell
git add parser-core
git commit -m "feat: expose complete static parser capabilities"
```

## Task 7: Report honest full capability coverage

**Files:**
- Modify: `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriter.kt`
- Modify: `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/ReportWriterTest.kt`

- [ ] **Step 1: Write failing report tests**

Assert the compact matrix contains Descriptions, Evolutions, Learnsets, Sprites, and Abilities; summary completeness requires every applicable static capability, while `NOT_APPLICABLE` does not count as failure.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :parser-cli:test --tests "*ReportWriterTest"`.

- [ ] **Step 3: Implement complete reporting**

Remove `CORE_CAPABILITIES`; calculate full applicable coverage from capability states. Preserve the `yes`/`N/F`/`N/A` legend and ancestry-score distinction.

- [ ] **Step 4: Run CLI and full tests, then commit**

```powershell
git add parser-cli
git commit -m "fix: report complete parser capability coverage"
```

## Task 8: Scan the corpus and investigate derivatives

**Files:**
- Modify parser source/tests only for evidence-backed structural fixes.
- Regenerate: `reports/dualdex-parser-compatibility.json`
- Regenerate: `reports/dualdex-parser-compatibility.md`

- [ ] **Step 1: Hash every source archive before scanning**

Create `D:\Temp\dualdex-parser-capability-before.json` containing source path, size, and SHA-256.

- [ ] **Step 2: Install the CLI and run all 14 in-scope ROMs**

Run `.\gradlew.bat :parser-core:test :parser-cli:test :parser-cli:installDist`, then invoke the installed parser against the three existing corpus roots and write reports to `D:\Temp` first.

- [ ] **Step 3: Investigate every derivative `N/F`**

For Modern Emerald, use its public `resetes12/pokeemerald` Release3.5 source. For Unbound, inspect the ROM first and use `alpacaonthehill/unbounddex` only as corroborating data because it is not a ROM parser. For Sword and Shield Ultimate Plus, search for a public source repository; if none exists, retain only direct structural evidence. For each failure, state one hypothesis, add a failing regression test, and implement only a confirmed general structural fix.

- [ ] **Step 4: Repeat until no simple applicable gap remains**

Do not force all hacks to green. A remaining `N/F` is acceptable only when direct inspection plus relevant public source search shows a changed/unknown format that cannot be validated generically without a dedicated engine profile; document the exact failed invariant in the report.

- [ ] **Step 5: Verify determinism and ROM immutability**

Run the complete scan twice. Normalize only durations in JSON, require identical JSON and byte-identical Markdown, regenerate the source archive manifest, and require no hash/size differences.

- [ ] **Step 6: Publish final reports and commit**

Copy the verified reports into `reports/`, rerun tests, and commit parser changes plus reports.

## Task 9: Completion audit and publication

**Files:**
- No expected source changes.

- [ ] **Step 1: Search for implementation placeholders**

Run `rg -n "not implemented|sprite pointer validation is only implemented|CORE_CAPABILITIES" parser-core parser-cli reports`.

Expected: no implementation placeholder remains; report diagnostics may contain only concrete structural failures.

- [ ] **Step 2: Run final verification**

Run `.\gradlew.bat :parser-core:test :parser-cli:test :parser-cli:installDist --rerun-tasks` and inspect JUnit XML totals for zero failures/errors.

- [ ] **Step 3: Audit all 12 static capabilities**

For each capability, inspect implementation entry point, focused tests, and all 14 report rows. Confirm every `N/A` is conceptually absent and every `N/F` has a concrete validator diagnostic rather than an implementation placeholder.

- [ ] **Step 4: Commit, push, and update the draft PR**

Push `codex/dualdex-parser-spec` to `fork` and update draft PR #1 with the new named coverage, unresolved derived formats if any, test totals, and immutable-input evidence.
