# Trainer License Unlock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reveal and permanently retain the Trainer Card for the active ROM-save playthrough as soon as one valid party Pokemon is observed.

**Architecture:** Store the milestone in `KnowledgeLedger`, set it only from catalog-validated live/save party records, and persist it through the existing ledger checkpoint. Publish the milestone separately from Trainer Card completeness, then construct a partial card from live trainer identity so unresolved facts do not suppress navigation.

**Tech Stack:** Kotlin/JUnit, Gson checkpoint serialization, Preact/TypeScript/Vitest, Android Gradle, GitHub Actions release workflow.

---

### Task 1: Persist the one-way Trainer license

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapper.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/SaveKnowledgeMapper.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerJsonCodec.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/LivePartyKnowledgeMapperTest.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/SaveKnowledgeMapperTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerJsonCodecTest.kt`

- [x] **Step 1: Write failing milestone tests**

Add assertions that a valid catalog species sets `trainerCardUnlocked`, an invalid species does not, a later empty live party preserves true, a valid save-party record unlocks, and the codec round-trips true.

```kotlin
assertTrue(LivePartyKnowledgeMapper.merge(KnowledgeLedger(), catalog, validParty, 3).trainerCardUnlocked)
assertFalse(LivePartyKnowledgeMapper.merge(KnowledgeLedger(), catalog, invalidParty, 3).trainerCardUnlocked)
assertTrue(LivePartyKnowledgeMapper.merge(KnowledgeLedger(trainerCardUnlocked = true), catalog, emptyList(), 3).trainerCardUnlocked)
assertTrue(SaveKnowledgeMapper.merge(KnowledgeLedger(), catalog, snapshot).trainerCardUnlocked)
assertTrue(codec.decode(codec.encode(KnowledgeLedger(trainerCardUnlocked = true)))!!.trainerCardUnlocked)
```

- [x] **Step 2: Run focused tests and confirm RED**

Run:

```powershell
.\gradlew.bat :companion-core:test --tests "*LivePartyKnowledgeMapperTest" --tests "*SaveKnowledgeMapperTest" :app:testDebugUnitTest --tests "*KnowledgeLedgerJsonCodecTest"
```

Expected: compilation fails because `trainerCardUnlocked` does not exist yet.

- [x] **Step 3: Implement the ledger latch and schema revision**

Add `val trainerCardUnlocked: Boolean = false` to `KnowledgeLedger`. In both mappers, preserve the previous flag and OR it with the presence of a validated party record. Add the Boolean to `StoredLedger`, map it in both directions, bump `CURRENT_SCHEMA` to 7, and accept schemas 4 through 7 so older documents decode as false.

```kotlin
trainerCardUnlocked = previous.trainerCardUnlocked || liveOwned.isNotEmpty()
trainerCardUnlocked = previous.trainerCardUnlocked || owned.any(OwnedPokemon::party)
```

- [x] **Step 4: Run focused tests and confirm GREEN**

Run the command from Step 2. Expected: all mapper and codec tests pass.

- [x] **Step 5: Commit the milestone layer**

```powershell
git add companion-core app/src/main/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerJsonCodec.kt app/src/test/java/com/darkaxt/dualdex/knowledge/KnowledgeLedgerJsonCodecTest.kt
git commit -m "feat: persist Trainer Card license milestone"
```

### Task 2: Publish an honest partial Trainer Card

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`

- [x] **Step 1: Write failing API tests**

Create a snapshot with `trainerCardUnlocked = true`, a `TrainerIdentity`, and no full `TrainerSnapshot`. Assert the state view publishes the unlock, identity, nullable unread numeric facts, and badges with unknown earned state.

```kotlin
val view = ApiViewBuilder.state(
    AppSnapshot(
        ledger = KnowledgeLedger(trainerCardUnlocked = true),
        trainerIdentity = TrainerIdentity("MAY", 1),
    ),
    catalog,
)
assertTrue(view.trainerCardUnlocked)
assertEquals("MAY", view.trainer!!.name)
assertNull(view.trainer.publicTrainerId)
assertTrue(view.trainer.badges.all { it.earned == null })
```

- [x] **Step 2: Run the API test and confirm RED**

Run `./gradlew.bat :companion-core:test --tests "*ApiViewBuilderTest"`. Expected: compilation fails because the view contracts do not yet expose the new fields.

- [x] **Step 3: Implement the partial API contract**

Add `trainerCardUnlocked` to `StateView`. Make unresolved Trainer numeric facts and badge earned state nullable. Build the trainer view from the complete snapshot when present, otherwise from `trainerIdentity`; never invent zero values for unread facts.

```kotlin
val trainer = snapshot.trainer
val identity = snapshot.trainerIdentity ?: trainer?.let { TrainerIdentity(it.name, it.gender) } ?: return null
```

- [x] **Step 4: Run the API test and confirm GREEN**

Run the command from Step 2. Expected: all API view tests pass.

- [x] **Step 5: Commit the API contract**

```powershell
git add companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt
git commit -m "feat: expose partial unlocked Trainer Card"
```

### Task 3: Drive the UI from the license rather than parser completeness

**Files:**
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/pages/TrainerCardPage.tsx`
- Test: `companion-web/src/App.production.test.tsx`
- Test: `companion-web/src/pages/TrainerCardPage.test.tsx`

- [x] **Step 1: Write failing production UI tests**

Assert the header hides the action before unlock, reveals it after unlock even with a partial card, and the partial page displays `—` for unread facts without parser/debug language.

```tsx
expect(screen.queryByRole('button', { name: 'Trainer Card' })).toBeNull();
expect(await screen.findByRole('button', { name: 'Trainer Card' })).toBeTruthy();
expect(screen.getAllByText('—').length).toBeGreaterThan(0);
expect(document.body.textContent).not.toMatch(/parser|capability/i);
```

- [x] **Step 2: Run focused web tests and confirm RED**

Run `npm test -- --run src/App.production.test.tsx src/pages/TrainerCardPage.test.tsx` from `companion-web`. Expected: the header remains coupled to `state.trainer`, and nullable fields are unsupported.

- [x] **Step 3: Implement the UI contract**

Add backward-compatible `trainerCardUnlocked?: boolean` to `State`, use `state.trainerCardUnlocked === true` for the header action, and render nullable facts as `—`. Treat a badge as earned only when `earned === true`; when null, label its status as unknown without exposing why.

```tsx
onTrainer={state.trainerCardUnlocked === true ? () => send('OPEN_TRAINER') : undefined}
```

- [x] **Step 4: Run focused tests and build the web bundle**

Run `npm test -- --run src/App.production.test.tsx src/pages/TrainerCardPage.test.tsx` and `npm run build` from `companion-web`. Expected: tests and build pass.

- [x] **Step 5: Commit the UI behavior**

```powershell
git add companion-web/src/models.ts companion-web/src/pages/PokedexBrowse.tsx companion-web/src/pages/TrainerCardPage.tsx companion-web/src/App.production.test.tsx companion-web/src/pages/TrainerCardPage.test.tsx
git commit -m "feat: unlock Trainer Card with first party Pokemon"
```

### Task 4: Validate and publish RC52

**Files:**
- Modify: `README.md`
- Create: `release/RELEASE_NOTES_1.1.0-rc.52.md`

- [x] **Step 1: Run affected tests and release policy**

Run `npm test -- --run` and `npm run build` from `companion-web`, then run `node --test tools/release/*.test.mjs` and `./gradlew.bat verifySecureBuildDependencies test :app:lintDebug :app:assembleRelease -PdualdexVersionName=1.1.0-rc.52 -PdualdexVersionCode=1010052 --stacktrace`. Expected: every test/build command exits zero.

- [x] **Step 2: Write the release identity and notes**

Update the README candidate link and add release notes describing the first-party milestone, per-playthrough persistence, and partial card facts without diagnostics. The protected workflow derives `versionName = "1.1.0-rc.52"` and `versionCode = 1010052` from the tag; do not hardcode them in Gradle.

- [x] **Step 3: Build the release APK**

Run `./gradlew.bat :app:assembleRelease -PdualdexVersionName=1.1.0-rc.52 -PdualdexVersionCode=1010052`. Expected: `app/build/outputs/apk/release/app-release-unsigned.apk` exists.

- [ ] **Step 4: Commit, tag, push, and publish**

```powershell
git add README.md release/RELEASE_NOTES_1.1.0-rc.52.md docs/superpowers/plans/2026-08-24-trainer-license-unlock.md
git commit -m "release: prepare v1.1.0-rc.52"
git push fork fix/rc29-local-map-rendering
git tag -a v1.1.0-rc.52 -m "v1.1.0-rc.52"
git push fork v1.1.0-rc.52
gh workflow run release.yml --repo Darkaxt/DualScreenDex --ref v1.1.0-rc.52 -f tag=v1.1.0-rc.52
```

- [ ] **Step 5: Verify the public artifact**

Wait for the release workflow to succeed, then verify the public tag, APK filename, APK SHA-256, and commit. Do not install the APK.
