# Gen III Empty Pokédex Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Gen III pre-party saves from fabricating Pokédex discoveries and translate live Pokédex flags into catalog species IDs before Organic state changes.

**Architecture:** Keep the heuristic layout resolver for anchored saves, but bypass it when a decoded party is positively empty. Centralize Dex-to-species translation in `SaveKnowledgeMapper` and make the live action contract explicitly species-ID based.

**Tech Stack:** Kotlin, JUnit 4, Gradle, GitHub Actions protected Android signing workflow

---

### Task 1: Fail closed before a party anchor exists

**Files:**
- Modify: `save-core/src/test/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PokedexCodecTest.kt`
- Modify: `save-core/src/main/kotlin/com/darkaxt/dualdex/save/gen3/Gen3PokedexCodec.kt`
- Modify: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecsTest.kt`
- Modify: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecs.kt`

- [x] **Step 1: Write the failing real-symptom regression**

Add `emptyPartyDoesNotPromoteNearbySaveBlockBytesToPokedexFlags`, with an expanded catalog, an empty party, and a decoy flag block at `0x38`. Assert empty seen/caught sets and a null `ownedOffset`. Add a live-codec regression proving an unavailable party does not become an available empty Pokédex anchor.

- [x] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :save-core:test --tests '*Gen3PokedexCodecTest.emptyPartyDoesNotPromoteNearbySaveBlockBytesToPokedexFlags' :battle-memory:test --tests '*Gen3LiveMemoryCodecsTest.unavailablePartyDoesNotResolvePokedexFromUnanchoredBytes'`

Expected: FAIL because the current scorer selects the decoy and returns false discoveries.

- [x] **Step 3: Implement the narrow guard**

Make `Gen3PokedexSnapshot.ownedOffset` nullable and the party argument nullable. Return unavailable when party evidence is unavailable; return a snapshot with `ownedOffset = null`, `seenDexNumbers = emptySet()`, and `caughtDexNumbers = emptySet()` before candidate scoring when the decoded live party is positively empty. Pass the nullable live party through without `orEmpty()`. Preserve checksum-validated SaveRAM recovery through an explicit persisted-evidence mode.

- [x] **Step 4: Verify GREEN and anchored-layout preservation**

Run: `./gradlew :save-core:test --tests '*Gen3PokedexCodecTest' :battle-memory:test --tests '*Gen3LiveMemoryCodecsTest'`

Expected: all codec tests pass, including the existing expanded `0x2C` layout control.

### Task 2: Translate live Dex numbers before ledger updates

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/SaveKnowledgeMapper.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [x] **Step 1: Write the failing runtime regression**

Change the live-player test catalog so Dex 25 belongs to an internal species ID other than 25. Assert the translated internal species becomes seen/caught while no nonexistent ID 25 state is created.

- [x] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProductionCompanionRuntimeTest.unifiedPlayerFieldsPopulateApiIndependentlyWithoutSaveRam'`

Expected: FAIL because the current gateway treats Dex 25 as internal species ID 25.

- [x] **Step 3: Centralize translation and correct the action contract**

Add `SaveKnowledgeMapper.pokedexFlagNumbersBySpeciesId(catalog)` and `speciesIdsForPokedexFlags(catalog, dexNumbers)`. Preserve ROM-extracted National-Dex numbers for expansion/unified layouts and separate official Emerald's National save flags from its regional display numbering. Use the shared translation from `merge` and `ProductionCompanionRuntime.applyResolvedPlayerState`. Rename the action fields to `seenSpeciesIds` and `caughtSpeciesIds`, and keep the gateway ledger species-ID based.

- [x] **Step 4: Verify GREEN**

Run: `./gradlew :companion-core:test :app:testDebugUnitTest --tests '*ProductionCompanionRuntimeTest.unifiedPlayerFieldsPopulateApiIndependentlyWithoutSaveRam'`

Expected: the translated runtime test and mapper tests pass.

### Task 3: Release RC62

**Files:**
- Create: `release/RELEASE_NOTES_1.1.0-rc.62.md`
- Modify only if required by the established release ledger: `README.md`, `release/v1-ready.json`

- [x] **Step 1: Run local verification**

Run focused tests, `git diff --check`, release-policy tests, the complete Gradle unit suite, secure dependency verification, debug lint, and unsigned release assembly with `dualdexVersionName=1.1.0-rc.62` and `dualdexVersionCode=1010062`.

- [x] **Step 2: Commit implementation and release notes**

Commit the functional correction separately from the release metadata so the tagged source remains auditable.

- [ ] **Step 3: Push and publish without replacement**

Fast-forward `fork/master`, create signed tag `v1.1.0-rc.62`, push the branch and tag, and dispatch `.github/workflows/release.yml` from the tag with input `v1.1.0-rc.62`.

- [ ] **Step 4: Verify the public artifact**

Require a successful workflow, prerelease metadata, APK filename/version/code/application ID, pinned certificate SHA-256, provenance commit/tag, and checksum match. Do not install or launch the APK.
