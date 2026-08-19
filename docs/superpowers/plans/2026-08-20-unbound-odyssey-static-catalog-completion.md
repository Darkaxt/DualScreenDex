# Unbound and Odyssey Static Catalog Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Unbound's exact 922-move description domain and Odyssey's exact 411-species Pokédex-description domain without ROM-identity selection.

**Architecture:** Keep resolution inside the existing shared `RomAnalysisSession`, typed layout, and `CatalogParser` materialization paths. Real ROMs establish the failure and source repositories explain the expected semantics; production admits only candidates nominated by compiled consumers or an independently selected typed parent record, and preserves partial/fail-closed outcomes when the binary evidence is incomplete or contradictory.

**Tech Stack:** Kotlin, JUnit 4, Gradle, existing Gen III reference index/description codecs, `CatalogParser`, SQLite `CatalogStore`, exact Unbound/Odyssey ROM controls.

---

### Task 1: Freeze both exact real-ROM failures

**Files:**
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/UnboundOdysseyStaticCompletionLiveRomTest.kt`

- [ ] Load `DUALDEX_UNBOUND_ROM` and assert SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
- [ ] Parse Unbound through the normal `CatalogParser` path and require 922 move identities/details, then assert the intended 922/922 descriptions. Retain the observed RED showing only 868/922.
- [ ] Load `DUALDEX_ODYSSEY_ROM` and assert SHA-256 `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0`.
- [ ] Parse Odyssey through the normal path and require 411 active species, then assert the intended 411/411 descriptions. Retain the observed RED showing only 409/411.
- [ ] Record the exact missing IDs, typed layout roots/strides/pointer fields, and relevant compiled-reference targets in assertion output so diagnosis follows real evidence.

Run:

```powershell
$env:DUALDEX_UNBOUND_ROM='D:\Temp\PokemonHacks\corpus\expanded\roms\0199-a275be0f927e\Unbound (v2.1.1.1).gba'
$env:DUALDEX_ODYSSEY_ROM='D:\Temp\PokemonHacks\corpus\expanded\roms\0123-5e7ce46db2ce\Odyssey (v4.1.1).gba'
.\gradlew.bat :parser-core:test --tests '*UnboundOdysseyStaticCompletionLiveRomTest' --rerun-tasks --no-daemon --console=plain
```

Expected RED: Unbound reports exactly 868/922 described moves and Odyssey exactly 409/411 described species.

### Task 2: Prove the two source-shaped description semantics

**Files:**
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/UnboundOdysseyStaticCompletionLiveRomTest.kt`
- Reference read-only: `D:/Temp/PokemonHacks/sources/Dynamic-Pokemon-Expansion-Unbound`
- Reference read-only: `D:/Temp/PokemonHacks/sources/Complete-Fire-Red-Upgrade`
- Reference read-only: `D:/Temp/PokemonHacks/sources/Pokemon-Odyssey-Docs-App`

- [ ] For each unresolved Unbound move ID, compare its selected 48-byte move record and description pointer with the DPE/CFRU semantic model; determine whether the binary expresses a shared pointer, fallback pointer, alternate text encoding, or a truly absent description.
- [ ] Prove the selected move root/stride and the exact 1..922 ordinary domain from existing typed layout evidence; do not infer a new domain from the description table.
- [ ] For the two unresolved Odyssey species IDs, compare the selected description rows, species-to-Dex mapping, and creator-authored workbook entries; determine whether the ROM uses shared/fallback Dex rows, sparse IDs, or alternate pointer pages.
- [ ] Add exact assertions over the real binary relationships that distinguish the correct behavior from an ordinary null/malformed row. SHA and source paths remain test-only.
- [ ] If either source corpus lacks a corresponding static record, stop that individual row as unresolved and update the numeric target honestly rather than manufacturing text.

### Task 3: Implement Unbound move-description completion

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializer.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializerTest.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/UnboundOdysseyStaticCompletionLiveRomTest.kt`

- [ ] Add the minimal source-shaped decoder behavior demonstrated by the real Unbound RED.
- [ ] Scope recovery to the independently selected unified move table and its exact move domain; do not scan arbitrary raw text to fill IDs.
- [ ] Preserve original move IDs and decoded text provenance. A shared/fallback pointer may populate multiple IDs only when the ROM record itself points to it.
- [ ] Reject invalid pointers, unterminated/invalid text, contradictory candidate semantics, and out-of-domain IDs.
- [ ] Run the exact Unbound control and require 922/922 described moves, `MOVE_DESCRIPTIONS` fully covered, zero reference errors, and identical semantic hashes across two fresh parses.
- [ ] Run the existing move-description tests to preserve Gen I/II/III behavior.

Run:

```powershell
.\gradlew.bat :parser-core:test --tests '*MoveDescriptionMaterializerTest' --tests '*UnboundOdysseyStaticCompletionLiveRomTest.unbound*' --rerun-tasks --no-daemon --console=plain
```

### Task 4: Implement Odyssey Pokédex-description completion

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/descriptions/DescriptionResolver.kt`
- Modify if the binary proof requires it: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/descriptions/DescriptionCodec.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/descriptions/DescriptionResolverTest.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/UnboundOdysseyStaticCompletionLiveRomTest.kt`

- [ ] Add the minimal typed row/fallback behavior demonstrated by the two exact Odyssey rows.
- [ ] Require the selected description table, selected species-to-Dex mapping, and active species domain to agree before recovering either row.
- [ ] Keep malformed pointers, unmapped species, contradictory shared rows, and unproven workbook-only text unresolved.
- [ ] Run the exact Odyssey control and require 411/411 described active species, full capability coverage, zero reference errors, and identical semantic hashes across two fresh parses.
- [ ] Run the existing description codec/resolver/live tests to preserve partial-table semantics.

Run:

```powershell
.\gradlew.bat :parser-core:test --tests '*DescriptionResolverTest' --tests '*DescriptionLiveRomTest' --tests '*UnboundOdysseyStaticCompletionLiveRomTest.odyssey*' --rerun-tasks --no-daemon --console=plain
```

### Task 5: Verify catalog persistence and publish numeric evidence

**Files:**
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Create: `docs/reports/2026-08-20-unbound-odyssey-static-completion.md`

- [ ] Persist and reopen both exact catalogs and compare move/species description ID-to-text hashes before and after SQLite.
- [ ] Assert 14 persisted sections, `PRAGMA quick_check = ok`, zero foreign-key violations, and zero parser/catalog reference errors.
- [ ] Run the focused parser and catalog-store tests, then the complete affected modules.
- [ ] Report before/after numerically: Unbound 868/922 to 922/922; Odyssey 409/411 to 411/411; parser capability totals become 20/23 for each until maps and mechanics are implemented.
- [ ] Run `git diff --check`, review for production ROM names/SHA/source symbols/absolute offsets, and commit the static completion as one stage.

Verification commands:

```powershell
.\gradlew.bat :parser-core:test --tests '*MoveDescription*' --tests '*Description*' --tests '*UnboundOdysseyStaticCompletionLiveRomTest' --no-daemon --console=plain
.\gradlew.bat :parser-core:test :catalog-store:test --no-daemon --console=plain
git diff --check
```

The next stage begins from this committed result and receives its own map-specific implementation plan; no partial static result is released as complete compatibility.
