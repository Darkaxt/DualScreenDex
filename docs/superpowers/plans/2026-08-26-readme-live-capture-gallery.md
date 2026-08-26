# README Live-Capture Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a seven-frame README gallery derived from the real signed RC66 recording and remove the obsolete debug/reference screenshots from the README presentation.

**Architecture:** A temporary frame-order analysis pipeline reduces the complete recording to stable unique UI states. Seven reviewed frame indexes are then extracted at source resolution as lossless WebP assets, and the README references those assets in three readable semantic rows.

**Tech Stack:** FFmpeg/FFprobe, Python 3, Pillow, GitHub-flavored Markdown, Git

---

### Task 1: Record and verify the evidence contract

**Files:**
- Create: `docs/superpowers/specs/2026-08-26-readme-live-capture-gallery-design.md`

- [ ] **Step 1: Record the source recording identity and signed APK version**

Document the recording dimensions, duration, SHA-256, `versionName=1.1.0-rc.66`, and `versionCode=1010066` in the specification.

- [ ] **Step 2: Record selection, privacy, and acceptance rules**

Document sequential full-frame analysis, stable-state deduplication, the seven target features, approved Trainer Card inclusion, image format, and README acceptance checks.

- [ ] **Step 3: Verify the specification contains every required contract**

Run:

```powershell
rg -n "frame sequentially|seven-frame|Trainer Card|1240 × 1080|lossless WebP|v1.1.0-rc.66" docs/superpowers/specs/2026-08-26-readme-live-capture-gallery-design.md
```

Expected: matches for frame-order analysis, seven-frame coverage, approved Trainer Card inclusion, dimensions, encoding, and signed version.

### Task 2: Extract representative live assets

**Files:**
- Create: `docs/images/live/dualdex-rc66-local-map.webp`
- Create: `docs/images/live/dualdex-rc66-wild-rarity.webp`
- Create: `docs/images/live/dualdex-rc66-wild-attack.webp`
- Create: `docs/images/live/dualdex-rc66-trainer-card.webp`
- Create: `docs/images/live/dualdex-rc66-party-detail.webp`
- Create: `docs/images/live/dualdex-rc66-height-comparison.webp`
- Create: `docs/images/live/dualdex-rc66-ability-behavior.webp`

- [ ] **Step 1: Decode and deduplicate the complete recording**

Decode all 11,802 frames in frame order, retain stable runs lasting at least 0.25 seconds, and collapse perceptually equivalent states. Expected result: 82 readable stable runs and 65 unique states.

- [ ] **Step 2: Select settled, public-safe feature frames**

Use these reviewed frame indexes:

```text
5105  local map
5799  wild rarity
6490  wild selected attack
7925  trainer card
8361  party detail
9792  height comparison
10757 parsed ability behavior
```

- [ ] **Step 3: Extract source-resolution lossless assets**

Stream the original video sequentially at 1240 × 1080, write only the seven selected frame indexes, and encode each image as lossless WebP. Frame-order extraction is required because timestamp seeking is not reliable for this recording.

- [ ] **Step 4: Verify the gallery assets**

Run a Pillow audit that opens every `docs/images/live/dualdex-rc66-*.webp` file and asserts:

```python
assert len(images) == 7
assert all(image.format == "WEBP" for image in images)
assert all(image.size == (1240, 1080) for image in images)
```

Expected: `7 assets; all WEBP 1240x1080`.

### Task 3: Replace the README gallery

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the current signed candidate**

Change the top implementation summary from `v1.1.0-rc.63` to `v1.1.0-rc.66` without changing its compatibility language.

- [ ] **Step 2: Replace the obsolete screenshot rows**

Create three centered rows. Row one contains local map, wild rarity, and wild attack at `31%` width. Row two contains Trainer Card and Party detail at `46%` width. Row three contains height comparison and ability behavior at `46%` width.

- [ ] **Step 3: Add compact feature captions**

Use one centered caption per row:

```text
Live local map · Wild rarity · Selected attack
Trainer Card · Party detail
Height comparison · Parsed ability behavior
```

- [ ] **Step 4: Replace the obsolete capture description**

State that the frames come from the signed RC66 APK running live on the AYN Thor with a real Modern Emerald session. Preserve the ROM-authoritative data statement and remove the debug-APK/reference-viewport claim.

### Task 4: Cross-check the specification and publish

**Files:**
- Modify: `docs/superpowers/plans/2026-08-26-readme-live-capture-gallery.md`

- [ ] **Step 1: Audit README references and obsolete paths**

Run:

```powershell
rg -n "docs/images/live/dualdex-rc66-|v1.1.0-rc.66|signed RC66" README.md
rg -n "dualdex-v1-(pokedex-browse|charizard-entry|move-detail|settings|memory-mapper)" README.md
```

Expected: seven live asset references and no obsolete gallery reference.

- [ ] **Step 2: Render-review the final seven images**

Create one temporary contact sheet from the committed WebP assets and visually verify that every frame is settled, readable, distinct, and free of system/debug overlays. The approved Trainer Card remains unredacted.

- [ ] **Step 3: Run repository validation**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the README, two workflow documents, and seven gallery assets are changed.

- [ ] **Step 4: Cross-check against the specification**

Confirm every acceptance criterion in `docs/superpowers/specs/2026-08-26-readme-live-capture-gallery-design.md`. Any unmet criterion blocks publication; unrelated enhancement ideas are deferred outside this documentation change.

- [ ] **Step 5: Commit and push**

Run:

```powershell
git add README.md docs/images/live docs/superpowers/specs/2026-08-26-readme-live-capture-gallery-design.md docs/superpowers/plans/2026-08-26-readme-live-capture-gallery.md
git commit -m "docs: showcase live DualDex features"
git push fork HEAD:master
```

Expected: one documentation commit is published to `fork/master`; no APK release is created for this README-only change.

## Self-review

- Spec coverage: Tasks 1–4 cover evidence identity, full-frame deduplication, all seven features, approved Trainer Card inclusion, asset format, README replacement, visual review, and publication.
- Placeholder scan: the plan contains concrete files, frame indexes, commands, expected results, and no incomplete implementation markers.
- Type consistency: every asset path uses the same `docs/images/live/dualdex-rc66-*.webp` convention and every validation targets the documented 1240 × 1080 WebP contract.
