# DualDex Simulator and Thor UI Implementation Plan

**Goal:** Deliver the real browser POC using production state contracts and ROM-derived data, with a deterministic plausible encounter simulator replacing only unavailable live memory mapping.

**Architecture:** `companion-simulator` produces actions and runtime snapshots through `companion-core`. `companion-web` is a TypeScript/Vite/Preact client served by `companion-server`; its compiled bundle is the future WebView asset. Simulator controls live outside the 1080×1240 Thor surface and are excluded from production builds.

## Task 1: Deterministic simulator domain

**Files:**

- Modify `settings.gradle.kts`.
- Add `companion-simulator/build.gradle.kts`.
- Add `companion-simulator/src/main/kotlin/com/enrpau/dualscreendex/simulator/SplitMix64.kt`.
- Add `companion-simulator/src/main/kotlin/com/enrpau/dualscreendex/simulator/EncounterSimulator.kt`.
- Add tests under `companion-simulator/src/test/kotlin/...`.

**Steps:**

1. Pin the PRNG output sequence and complete snapshots for identical seed/catalog/action sequences.
2. Generate one or two opponents from validated species, optionally restricted to a parsed area.
3. Generate plausible levels and move histories only from level-up moves at or below that level; empty eligible pools stay empty.
4. Generate synthetic IVs/DVs, player party, seen/captured states, and multiple captured individuals. Assign only ROM-valid capture-ball IDs and select the highest innate-quality record through production code.
5. Compute matchup truth from parsed records and pass it through the real knowledge policy.
6. Commit as `feat: add deterministic encounter simulator`.

## Task 2: Scaffold the production web client

**Files:**

- Add `companion-web/package.json`, `vite.config.ts`, `tsconfig.json`, and `index.html`.
- Add `companion-web/src/gateway.ts`, `models.ts`, `main.tsx`, and `styles.css`.
- Add frontend unit-test configuration and tests.

**Steps:**

1. Test gateway bootstrap, action dispatch, SSE version ordering, reconnect state replacement, and accessible error states.
2. Add a single responsive app root constrained by the physical Thor reference surface.
3. Use semantic buttons/labels and CSS-drawn eye state where needed; every Pokémon and ball image must come from server sprite endpoints.
4. Commit as `feat: scaffold DualDex web client`.

## Task 3: Out-of-combat browse and detail

**Files:**

- Add `companion-web/src/pages/PokedexBrowse.tsx` and `PokedexDetail.tsx`.
- Add components for search, capability-gated filters, ROM type chips, capture/eye markers, tabs, and sprite images.
- Add component tests.

**Steps:**

1. Test Organic omission of unseen species and Discovered slashed-eye rows.
2. Implement All/Caught/Seen/Team/Area filters, preserving scroll/filter state on Back.
3. Render the selected best individual's ball sprite for captured species, generic colored ball when the per-individual ID is unavailable, and grayscale generic art for seen-only rows.
4. Implement Entry/Stats/Moves/More as separate mounted views with internal scrolling only.
5. Commit as `feat: build Thor Pokédex pages`.

## Task 4: Battle tabs

**Files:**

- Add `companion-web/src/pages/BattlePage.tsx`.
- Add `EntryTab.tsx`, `AttackTab.tsx`, `RarityTab.tsx`, and `MovesTab.tsx`.
- Add component tests.

**Steps:**

1. Test singles/doubles target selection, capability-gated tabs, and automatic return after battle.
2. Render only one task at a time: identity/entry, selected move metadata/effectiveness, qualitative recruitment label, or observed move frequency.
3. Keep unknown, unavailable, and no-effect states distinct.
4. Ensure hidden opponent move slots never enter the view model.
5. Commit as `feat: build focused battle companion`.

## Task 5: Settings and external simulator controls

**Files:**

- Add `companion-web/src/pages/SettingsPage.tsx`.
- Add `companion-web/src/dev/SimulatorPanel.tsx`.
- Add tests.

**Steps:**

1. Implement policy, Attack/Rarity/Moves toggles, font scale, Auto/Comfortable/Compact density, theme/contrast, automatic target opening, caught markers, and local reset actions.
2. Keep simulator inputs outside the Thor frame: ROM, seed, single/double, level range, player reference, area, captured/seen state, generate/act/resolve/end actions.
3. Exclude the simulator panel from production builds through a compile-time flag.
4. Commit as `feat: add settings and simulator controls`.

## Task 6: Visual and behavioral acceptance

**Files:**

- Add `companion-web/e2e/dualdex.spec.ts`.
- Replace README design images with verified POC screenshots under `docs/images/`.
- Update `README.md` with exact run instructions and implementation status.

**Steps:**

1. Build the web bundle and start the server with a real parsed ROM.
2. Use Playwright at the scaled reference and 1080×1240 viewport to verify no unintended horizontal/vertical body overflow; only declared content regions may scroll.
3. Capture browse, detail, single battle, double battle, settings, and simulator screenshots. Inspect them visually for artifacts, clipping, unreadable density, and missing ROM sprites.
4. Assert no visible text badge says `Captured` or `Not captured`, no favorite star/global bottom bar exists, and no emoji appears in production DOM or screenshots.
5. Run frontend unit/e2e tests, all Gradle tests, production bundle build, and placeholder/emoji scans.
6. Commit as `feat: deliver DualDex browser POC`, push the branch, and update the draft PR. Do not publish an APK until the later Android-host milestone.

