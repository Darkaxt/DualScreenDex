# Thor UI Usability Remediation Staged Plan

**Specification:** `docs/superpowers/specs/2026-08-30-thor-ui-usability-remediation-design.md`

**Audit authority:** `docs/testing/thor-critical-ui-usability-audit-2026-08-30.md`

**Delivery branch:** `feat/retroarch-free-ui-qa`

**Goal:** Implement the accepted Thor UI specification in dependency order, closing unreachable and unreadable interactions before broader workflow/accessibility work, then performing one bounded integrated APK gate.

**Plan authority:** The specification defines scope and acceptance. This plan defines order, checkpoints, and verification only. If implementation needs to change a requirement, revise and approve the specification before changing stage scope.

The four delivery stages intentionally split specification Stage A into two safe checkpoints:

- Plan Stages 1–2 close specification Stage A and audit Batch A.
- Plan Stage 3 closes specification Stage B and audit Batch B.
- Plan Stage 4 closes specification Stage C and audit Batch C.

The split does not defer or narrow any specification requirement.

---

## Stage discipline

### Before each implementation checkpoint

1. Fetch `fork/master` and `fork/feat/retroarch-free-ui-qa`.
2. Confirm the branch has no unexplained changes and reconcile `fork/master` without reset, force-push, or discarding another thread's work.
3. Re-read the complete specification, then the requirement IDs assigned to the checkpoint.
4. Add the smallest failing component/layout/E2E regression that proves the defect.
5. Do not run the parser corpus for companion-web-only work.

### After each stage

1. Run focused tests for changed surfaces and the production companion-web build.
2. Run only the stage's exact packaged-APK captures.
3. Re-check every `UI-INV-*` invariant and the preserved views named by the stage.
4. Record a concise closure report at `docs/reports/thor-ui-remediation/stage-0N-closure.md` containing:
   - synchronized base and stage commit;
   - requirement IDs and evidence;
   - red/green commands and results;
   - APK measurements/screenshots;
   - blockers and tracked referrals;
   - `COMPLETE` or `BLOCKED` verdict.
5. Smart-sync again immediately before commit, commit implementation/tests/evidence together, and push the safe checkpoint to `fork/feat/retroarch-free-ui-qa`.
6. Clean disposable build/capture files and snapshot/stop the owned emulator after APK work.

A stage is `BLOCKED` if any assigned requirement lacks its acceptance evidence. A newly discovered issue may be referred only with an ID, target stage, dependency, and measurable acceptance condition. No current-stage requirement may be silently deferred.

No stage merges to `master`, opens a PR, publishes an RC, signs an APK, or promotes stable without separate authorization.

---

## Stage 1 — Trainer access and contrast-safe themes

**Requirements:** `UI-INV-01`–`UI-INV-08`, `UI-NAV-01`, `UI-THEME-01`, `UI-THEME-02`, `UI-VAL-01`–`UI-VAL-03`, and the Trainer/Settings/Setup portion of `UI-VAL-04`

### Objectives

1. Create and retain a reusable CDP packaged-WebView runner with strict geometry, authority, active-tab, measurement, screenshot, and privacy checks.
2. Allocate the complete two-destination Trainer switcher and prove Card/Progress touch reachability.
3. Add a pure contrast derivation utility and semantic CSS surface/action pairs.
4. Migrate Settings, Setup, shared controls, Capability actions, dialogs, and selected controls away from assumptions that ROM theme colors are dark.
5. Preserve the current Pokédex detail, Trainer Card body, Party, Battle, and Specimens compositions.

### Primary implementation surfaces

- `companion-web/src/App.tsx`
- a focused companion-web contrast utility and unit test
- `companion-web/src/components.tsx`
- `companion-web/src/pages/TrainerPage.tsx`
- `companion-web/src/pages/SettingsPage.tsx`
- `companion-web/src/pages/SetupPage.tsx`
- `companion-web/src/styles.css`
- `companion-web/src/App.production.test.tsx`
- `companion-web/src/pages/TrainerCardPage.test.tsx`
- `companion-web/src/pages/TrainerProgressPage.test.tsx`
- `companion-web/src/pages/SettingsPage.production.test.tsx`
- `companion-web/src/pages/SetupPage.test.tsx`
- `companion-web/e2e/rom-derived-theme.spec.ts`
- `companion-web/e2e/ui-space-regressions.spec.ts`
- a host-side runner under `tools/qa/` with no embedded ROM/private data

### Focused gate

```bash
cd companion-web
npm test -- src/App.production.test.tsx src/components.test.ts src/components.test.tsx src/pages/TrainerCardPage.test.tsx src/pages/TrainerProgressPage.test.tsx src/pages/SettingsPage.production.test.tsx src/pages/SetupPage.test.tsx src/layoutStyles.test.ts
npx playwright test e2e/rom-derived-theme.spec.ts e2e/ui-space-regressions.spec.ts
npm run build
cd ..
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:assembleDebug --stacktrace
```

The audit measurements supply the packaged red baseline: Trainer `538/588` overflow and contrast ratios `1.43:1`/`1.17:1`. Focused automated regressions must fail against the pre-fix behavior and pass after the correction. The intermediate packaged gate then captures Trainer Card, Trainer Progress, Settings, and RetroArch Setup at exact Modern Emerald authority.

### Stage blockers

- Progress is still outside the visible viewport or reachable only programmatically.
- Any migrated normal action remains below the required contrast.
- Theme fallback can emit unreadable text or mutate catalog theme data.
- The runner can mislabel a retained tab, accept wrong authority/geometry, or capture private input.
- A preserved view regresses horizontally.

---

## Stage 2 — Map targets, POI clustering, and Area Guide flow

**Depends on:** Stage 1 reusable APK runner and semantic control colors.

**Requirements:** `UI-INV-01`–`UI-INV-08`, `UI-MAP-01`, `UI-MAP-02`, `UI-GUIDE-01`, `UI-GUIDE-02`, `UI-VAL-01`–`UI-VAL-04`; `UI-MODAL-01` is also mandatory in this stage if the chosen cluster member chooser is modal

### Objectives

1. Separate compact map glyph size from effective target size across Local, Atlas, habitat, and map-detail controls.
2. Add a deterministic POI clustering projection for overlapping effective targets.
3. Add the viewport-bounded cluster member chooser without revealing undiscovered information.
4. Make the compact Area Guide body one continuous vertical gesture surface while retaining bounded rendering.
5. Group repeated connected destinations or differentiate equal display names without inventing topology.

### Primary implementation surfaces

- `companion-web/src/pages/MapPage.tsx`
- `companion-web/src/pages/PokemonAreaMap.tsx`
- `companion-web/src/pages/AreaGuideDrawer.tsx`
- a focused pure map-clustering utility and tests
- `companion-web/src/pages/MapPage.test.tsx`
- `companion-web/src/pages/AreaGuideDrawer.test.tsx`
- `companion-web/src/styles.css`
- `companion-web/e2e/map-presentation.spec.ts`
- `companion-web/e2e/ui-space-regressions.spec.ts`
- Stage 1 packaged-WebView runner

### Focused gate

```bash
cd companion-web
npm test -- src/pages/MapPage.test.tsx src/pages/AreaGuideDrawer.test.tsx src/mapEngine.test.ts src/layoutStyles.test.ts
npx playwright test e2e/map-presentation.spec.ts e2e/ui-space-regressions.spec.ts
npm run build
cd ..
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:assembleDebug --stacktrace
```

The packaged gate captures Local Map, the nine Oldale POIs, an opened cluster chooser, Area Guide top, and Area Guide final populated section. It proves every original POI is selectable, targets do not overlap, scrolling remains continuous, and four indistinguishable Oldale buttons no longer appear.

### Stage blockers

- Enlarged targets overlap or interfere with map pan/zoom.
- Clustering changes authoritative POI keys, coordinates, order nondeterministically, or knowledge policy.
- A cluster member cannot be selected individually.
- Area Guide still contains competing vertical gesture owners.
- Equal exit labels remain indistinguishable or grouping changes the navigation destination.

Completion of Stages 1 and 2 forms the audit's complete high-priority **Batch A**. Do not publish an RC between them.

---

## Stage 3 — Compact workflows, dialogs, accessibility, and recovery

**Depends on:** Stage 1 semantic controls and Stage 2 chooser decision.

**Requirements:** `UI-INV-01`–`UI-INV-08`, `UI-DENS-01`, `UI-SET-01`, `UI-SET-02`, `UI-MODAL-01`, `UI-MODAL-02`, `UI-A11Y-01`–`UI-A11Y-03`, `UI-REC-01`, `UI-REC-02`, `UI-VAL-01`–`UI-VAL-04`

### Objectives

1. Make Pokédex row height a shared density value for CSS and virtualization.
2. Replace the compact `2085 px` Settings document with the specified seven-category index and category page.
3. Add direct category/control routing for recovery actions.
4. Consolidate Party, Specimen, and any modal map chooser onto the shared dialog primitive.
5. Prove scrolled multi-specimen dialog placement and focus restoration.
6. Normalize tab keyboard behavior, selection semantics, route headings, landmarks, and focus transitions without adding controller navigation.
7. Give Move List, Specimens, habitat, capability/setup, loading, and error states the specified bounded next action.

### Primary implementation surfaces

- `companion-web/src/App.tsx`
- `companion-web/src/components.tsx`
- a shared dialog component and tests
- `companion-web/src/navigation.ts`
- `companion-web/src/pages/PokedexBrowse.tsx`
- `companion-web/src/pages/PokedexDetail.tsx`
- `companion-web/src/pages/BattlePage.tsx`
- `companion-web/src/pages/SettingsPage.tsx`
- `companion-web/src/pages/PartyPage.tsx`
- `companion-web/src/pages/SpecimensPage.tsx`
- relevant page/component tests
- `companion-web/src/styles.css`
- `companion-web/e2e/companion-resilience.spec.ts`
- `companion-web/e2e/ui-space-regressions.spec.ts`

### Focused gate

```bash
cd companion-web
npm test -- src/components.test.ts src/components.test.tsx src/navigation.test.ts src/pages/PokedexBrowse.test.tsx src/pages/PokedexDetailNavigation.test.tsx src/pages/BattlePage.test.tsx src/pages/SettingsPage.production.test.tsx src/pages/PartyPage.test.tsx src/pages/SpecimensPage.test.tsx src/layoutStyles.test.ts
npx playwright test e2e/companion-resilience.spec.ts e2e/ui-space-regressions.spec.ts
npm run build
cd ..
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:assembleDebug --stacktrace
```

The packaged gate captures Comfortable and Compact Browse, Settings index and one category, Party detail, a scrolled synthetic Specimens detail, one keyboard/focus path, Move List recovery, and non-obscuring background feedback.

### Stage blockers

- Compact virtualization gaps, overlaps, or loses the nearest valid scroll anchor.
- Settings values reset or the two-level Back contract is ambiguous.
- A dialog remains anchored to list scroll content, leaks focus, lacks Escape/close, or loses the triggering card.
- Route focus moves on live polling rather than navigation.
- Recovery invents data, crosses catalog/session identity, or leaves a dead action.
- Feedback still covers or intercepts primary controls.

Completion of Stage 3 forms **Batch B**. It remains a pushed development checkpoint, not release authorization.

---

## Stage 4 — Bounded polish and integrated specification closure

**Depends on:** Stages 1–3 complete with no blockers.

**Requirements:** `UI-POLISH-01`–`UI-POLISH-04`, every `UI-INV-*`, every prior stage requirement, `UI-VAL-01`–`UI-VAL-06`, and every tracked referral.

### Objectives

1. Remove the default-scale Battle Attack micro-scroll without changing Entry or Rarity.
2. Bound long map titles around the centered clock and trailing actions.
3. Give nearby-move cards their two-content-column layout.
4. Enforce the text floor and supported font-scale reachability.
5. Re-read the full specification and reopen any regressed requirement.
6. Run the complete companion-web unit/E2E/build gate and final packaged-APK preserved-view matrix.
7. Close every referral and publish the final closure report with `Blockers: None` and `Tracked referrals: None`.

### Primary implementation surfaces

- `companion-web/src/pages/BattlePage.tsx`
- `companion-web/src/pages/MapPage.tsx`
- `companion-web/src/pages/PartyAnalysisPage.tsx`
- `companion-web/src/styles.css`
- relevant unit/layout/E2E tests
- Stage 1 packaged-WebView runner
- `docs/reports/thor-ui-remediation/stage-04-closure.md`

### Integrated gate

```bash
cd companion-web
npm test
npm run test:e2e
npm run build
cd ..
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

Then run the exact packaged-APK matrix from `UI-VAL-05`, compare preserved views with the accepted audit evidence, validate `85%`, `100%`, and `135%` application font settings, clean the emulator, and stop it.

Do **not** run the parser corpus unless a separate parser/catalog/build-wrapper/corpus-execution change entered the branch and independently requires it.

### Final blockers

- Any specification requirement or referral remains open.
- Any preserved view regresses.
- Any normal route has semantic horizontal clipping, unreachable controls, overlapping targets, unreadable action contrast, or an unbounded nested scroll path.
- Browser results pass but required APK evidence is absent.
- The branch is behind unreconciled `fork/master` at the final commit.

Only a complete Stage 4 makes the branch eligible for a separately authorized candidate decision. Stable promotion still requires the existing signed-candidate lower-display confirmation gate.

---

## Explicit separation from other work

Task #294, clean debug catalog-start heap provisioning, remains separate. This plan may use the retained QA cache but must not hide or opportunistically bundle that provisioning task into UI stages.

Parser compatibility tasks, GB/GBC Local-map expansion, ability/move ABI work, signing, and release promotion remain outside this plan.
