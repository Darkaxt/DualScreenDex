# README Live Feature Tour Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic RC66 screenshot gallery with a five-category feature tour that documents every captured product tab using 18 real source-resolution frames.

**Architecture:** Reuse the verified recording and seven committed assets, extract eleven additional settled frames by sequential frame index, and reorganize the README into Local Map, Wild Encounter, Pokédex, Party, and Trainer Card sections. Each category owns its explanatory paragraph and labelled screenshot grid.

**Tech Stack:** FFmpeg, Python 3, Pillow, GitHub-flavored Markdown, Git

---

### Task 1: Record the superseding feature-tour contract

**Files:**
- Create: `docs/superpowers/specs/2026-08-26-readme-live-feature-tour-design.md`

- [ ] **Step 1: Record all five categories and 18 source-frame mappings**

Write the exact category, tab, filename, and frame-index tables from the approved design.

- [ ] **Step 2: Record layout and presentation boundaries**

Specify category paragraphs, labelled grids, source-resolution lossless WebP, approved Trainer Card inclusion, and exclusion of system/debug/loading states.

- [ ] **Step 3: Validate the specification**

Run:

```powershell
rg -n "Local Map|Wild Encounter|Pokédex|Party|Trainer Card|18 unique|1240 × 1080|No system/debug" docs/superpowers/specs/2026-08-26-readme-live-feature-tour-design.md
```

Expected: every category and acceptance boundary is present.

### Task 2: Extract the eleven missing tab captures

**Files:**
- Create: `docs/images/live/dualdex-rc66-wild-entry.webp`
- Create: `docs/images/live/dualdex-rc66-wild-moves.webp`
- Create: `docs/images/live/dualdex-rc66-pokedex-browser.webp`
- Create: `docs/images/live/dualdex-rc66-pokedex-entry.webp`
- Create: `docs/images/live/dualdex-rc66-pokedex-stats.webp`
- Create: `docs/images/live/dualdex-rc66-pokedex-moves.webp`
- Create: `docs/images/live/dualdex-rc66-pokedex-area.webp`
- Create: `docs/images/live/dualdex-rc66-pokedex-evolutions.webp`
- Create: `docs/images/live/dualdex-rc66-party-overview.webp`
- Create: `docs/images/live/dualdex-rc66-nature-detail.webp`
- Create: `docs/images/live/dualdex-rc66-party-ability-detail.webp`

- [ ] **Step 1: Verify the recording identity**

Run:

```powershell
(Get-FileHash -Algorithm SHA256 D:\Temp\DualDex-captures\screen-20260826-111430.mp4).Hash
```

Expected: `B485F6F0CD2BCA3BB773A4567611F1593C30932F344EE523458BF81E62C09053`.

- [ ] **Step 2: Extract all 18 reviewed source frames sequentially**

Use the frame map in the specification. Decode the source as 1240 × 1080 RGB in frame order and save selected frames with Pillow using `format="WEBP", lossless=True, method=6`. Sequential extraction is required because timestamp seeking fails after the recording's presentation-timestamp discontinuity.

- [ ] **Step 3: Audit all feature-tour assets**

Run a Pillow audit that asserts:

```python
assert len(images) == 18
assert all(image.format == "WEBP" for image in images)
assert all(image.size == (1240, 1080) for image in images)
```

Expected: `18 assets; all WEBP 1240x1080`.

### Task 3: Replace the generic gallery with feature categories

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Preserve the Thor-first introduction and add the feature-tour heading**

Keep the existing small-screen design paragraph, remove the generic image rows, and add `## Live feature tour`.

- [ ] **Step 2: Add Local Map documentation**

Add the live-map paragraph, centered map capture, and `Local map` caption.

- [ ] **Step 3: Add all Wild Encounter tabs**

Add a concise live-target/Organic-policy paragraph followed by two rows containing Entry, Attack, Rarity, and Moves with visible tab captions.

- [ ] **Step 4: Add all Pokédex tabs and captured subviews**

Add the ROM-catalog/live-knowledge paragraph followed by Browser, Entry text, height comparison, Stats, Moves, Area, ability behavior, and evolutions/locations. Use one 70% browser image and paired 46% rows for the remaining captures, with the final evolution image centered at 70%.

- [ ] **Step 5: Add all Party views**

Add the unified-snapshot paragraph followed by Overview, Pokémon detail, Nature detail, and Ability detail in two labelled rows.

- [ ] **Step 6: Add Trainer Card documentation and provenance**

Add the live-card paragraph and centered approved card capture, then close the tour with the signed-RC66/Modern-Emerald provenance statement.

### Task 4: Verify and publish the feature tour

**Files:**
- Modify: `docs/superpowers/plans/2026-08-26-readme-live-feature-tour.md`

- [ ] **Step 1: Audit README structure and references**

Assert five `###` category headings, 18 unique `docs/images/live/dualdex-rc66-*.webp` references, one visible caption for every asset, and no references to the former seven-image gallery block.

- [ ] **Step 2: Render-review a contact sheet**

Create a temporary contact sheet in documented category order and verify all 18 frames are settled, legible, correctly categorised, and free of system/debug UI.

- [ ] **Step 3: Validate the exact repository change**

Run:

```powershell
git diff --check
git status --short
```

Expected: `README.md`, the design, the plan, and exactly 11 new WebP files change.

- [ ] **Step 4: Cross-check every acceptance criterion**

Read the specification from top to bottom and block publication if any category, tab, paragraph, caption, dimension, or presentation boundary is missing.

- [ ] **Step 5: Commit and push**

Run:

```powershell
git add README.md docs/images/live docs/superpowers/specs/2026-08-26-readme-live-feature-tour-design.md docs/superpowers/plans/2026-08-26-readme-live-feature-tour.md
git commit -m "docs: expand live feature tour"
git push fork HEAD:master
```

Expected: the feature-tour documentation commit is published to `fork/master`; no APK release is created.

## Self-review

- Spec coverage: Tasks 1–4 cover every category, all 18 captures, explanatory text, labelling, provenance, presentation boundaries, verification, and publication.
- Placeholder scan: all filenames, frame indexes, commands, expected results, and category contents are explicit.
- Type consistency: the plan and specification use the same five categories, 18 filenames, WebP format, and 1240 × 1080 dimensions.
