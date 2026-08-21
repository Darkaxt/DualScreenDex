# Local Map Rendering Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RC29 connected Local maps sharp, discovery-safe, player-centered, and self-identifying without changing the surrounding Map UI.

**Architecture:** Keep the existing logical viewport and parser-produced placement geometry. Resize the map plane to its final display dimensions instead of scaling a composited parent, render only knowledge-eligible Local rasters, and calculate recenter/POI positions from scene coordinates.

**Tech Stack:** Preact, TypeScript, CSS, Vitest, Testing Library, Vite, Helium/Playwright real-browser verification.

---

### Task 1: Camera math

**Files:**
- Modify: `companion-web/src/mapEngine.ts`
- Test: `companion-web/src/mapEngine.test.ts`

- [x] **Step 1: Write the failing test**

Add a test that calls a new `centerMapPoint` helper with a non-default viewport scale and asserts the scale is unchanged while the requested scene point maps to stage center.

- [x] **Step 2: Run test to verify it fails**

Run: `npm.cmd test -- mapEngine.test.ts`

Expected: FAIL because `centerMapPoint` is not exported.

- [x] **Step 3: Write minimal implementation**

Implement:

```ts
export function centerMapPoint(
  viewport: MapViewport,
  intrinsic: MapRect,
  fit: { width: number; height: number },
  point: MapPoint,
): MapViewport {
  return {
    scale: viewport.scale,
    panX: -((point.x / intrinsic.width) - 0.5) * fit.width * viewport.scale,
    panY: -((point.y / intrinsic.height) - 0.5) * fit.height * viewport.scale,
  };
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `npm.cmd test -- mapEngine.test.ts`

Expected: PASS.

### Task 2: Discovery-safe Local scene DOM

**Files:**
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/pages/MapPage.tsx`

- [x] **Step 1: Write failing page tests**

Assert that Organic mode creates no image for an undiscovered placement, creates one opaque placeholder, never creates `.map-scene-atlas-fallback`, exposes only eligible `.map-local-poi-label` elements, and exposes all placements/labels in Discovered mode.

- [x] **Step 2: Run tests to verify RED**

Run: `npm.cmd test -- MapPage.test.tsx`

Expected: FAIL because RC29 renders all placement images, renders the Atlas underlay, and has no Local POI labels.

- [x] **Step 3: Implement eligible placement selection**

Derive `visibleScenePlacements` from `revealedBaseIds` in Organic and all placements in Discovered. Map only that list to `<img>` and named POI elements. Map the remaining list to opaque placeholders. Remove the Local-scene Atlas image entirely.

- [x] **Step 4: Run page tests to verify GREEN**

Run: `npm.cmd test -- MapPage.test.tsx`

Expected: PASS.

### Task 3: Sharp raster layout and player recentering

**Files:**
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/styles.css`

- [x] **Step 1: Write failing behavior tests**

Assert that the plane width and height include `viewport.scale`, its transform contains translation only, the ROM-derived trainer avatar replaces the player dot at no less than its intrinsic 64×64 size, maximum Local zoom stops when one source tile reaches that size, and recenter preserves the zoomed scale while changing pan to the live player coordinate.

- [x] **Step 2: Run tests to verify RED**

Run: `npm.cmd test -- MapPage.test.tsx`

Expected: FAIL because RC29 uses parent `scale()` and resets recenter to placement-fit scale.

- [x] **Step 3: Implement final-size raster layout**

Set plane dimensions to `fit.width * viewport.scale` and `fit.height * viewport.scale`; remove `scale()` from the transform. Use `centerMapPoint` for recenter, with current placement center as the no-player fallback. Remove `will-change: transform`; retain `image-rendering: pixelated`; make hidden placeholders `#000`.

- [x] **Step 4: Run focused tests and build**

Run: `npm.cmd test -- mapEngine.test.ts MapPage.test.tsx`

Run: `npm.cmd run build`

Expected: both commands PASS.

### Task 4: Real Modern Emerald verification

**Files:**
- Create only ignored evidence under: `output/playwright/rc29-local-map-rendering/`

- [x] **Step 1: Start the existing companion server with the exact Modern Emerald ROM**

Use the parser-produced catalog and map assets; do not use a synthetic map fixture.

- [x] **Step 2: Verify the user-visible contract in Helium**

Measure initial/current-map size, maximum zoom raster sampling, recenter scale and player screen offset, player marker dimensions, requested map-image URLs, hidden placement DOM, fog color, and visible POI labels.

- [x] **Step 3: Capture report and screenshots**

Store JSON plus Organic/Discovered/max-zoom screenshots under the evidence directory.

- [x] **Step 4: Run final focused regression and commit**

Run: `npm.cmd test -- mapEngine.test.ts MapPage.test.tsx`

Run: `npm.cmd run build`

Run: `git diff --check`

Commit production, tests, spec, and plan together after all evidence is green.
