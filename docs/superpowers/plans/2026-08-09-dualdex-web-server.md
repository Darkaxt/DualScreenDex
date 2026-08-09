# DualDex Local Web Server Implementation Plan

**Goal:** Serve the production companion state and ROM-derived assets over a loopback-only development server that can later be hosted unchanged behind an Android WebView gateway.

**Architecture:** `companion-core` owns immutable state and actions. `companion-server` adapts HTTP/JSON, SSE, streamed ROM/ZIP loading, and PNG encoding. It binds only to loopback, uses no arbitrary cancellation timeout, and never exposes ROM paths or bytes to the browser.

## Task 1: Add companion-core state and gateway

**Files:**

- Modify `settings.gradle.kts`.
- Add `companion-core/build.gradle.kts`.
- Add `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`.
- Add `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt`.
- Add `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt`.

**Steps:**

1. Test monotonic snapshot versions, immutable updates, and stale-event rejection fields.
2. Define `AppSnapshot`, actions, settings, catalog/load states, knowledge policy, browse/detail/battle navigation, capabilities, and diagnostics.
3. Implement an in-process gateway with `bootstrap`, `dispatch`, and listener subscription.
4. Run tests; commit as `feat: add companion state gateway`.

## Task 2: Implement knowledge and capture presentation selectors

**Files:**

- Add `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/knowledge/KnowledgePolicy.kt`.
- Add `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/owned/PreferredIndividualSelector.kt`.
- Add focused tests.

**Steps:**

1. Test Organic/Discovered/Hidden visibility and global move knowledge independently.
2. Test preferred individual selection by exact IV sum or normalized DV sum, then stable party/box key; prove level/EV/current stats cannot change the winner.
3. Return the selected capture-ball ID only when the runtime capability and catalog ball record are both valid; otherwise return generic ROM ball art.
4. Commit as `feat: implement companion knowledge selectors`.

## Task 3: Add loopback HTTP and SSE adapter

**Files:**

- Add `companion-server/build.gradle.kts`.
- Add `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt`.
- Add `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/Routes.kt`.
- Add `companion-server/src/test/kotlin/com/enrpau/dualscreendex/server/ServerContractTest.kt`.

**Steps:**

1. Write contract tests for `GET /api/bootstrap`, `POST /api/actions`, `GET /api/events`, sprite endpoints, error bodies, and loopback binding.
2. Use Ktor CIO or the JDK HTTP server with JSON serialization; choose the smaller Android-portable dependency surface and document the choice in code.
3. Stream monotonic snapshots through SSE and use heartbeat comments only for liveness, never cancellation.
4. Add cache headers keyed by catalog hash for immutable sprite responses.
5. Run tests; commit as `feat: serve companion gateway locally`.

## Task 4: Stream direct ROM and ZIP content

**Files:**

- Add `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/RomSourceLoader.kt`.
- Add `companion-server/src/test/kotlin/com/enrpau/dualscreendex/server/RomSourceLoaderTest.kt`.

**Steps:**

1. Test direct `.gb`, `.gbc`, `.gba`, one supported ROM inside ZIP, multiple candidates, corrupt archives, and unrelated ZIP entries.
2. Feed selected archive entry bytes directly to `RomImage`; never extract to a temp folder.
3. Accept a startup `--rom` path for the local POC and a browser upload action without returning the local path to the client.
4. Commit as `feat: stream ROM and ZIP sources`.

## Task 5: Encode ROM pixels as PNG

**Files:**

- Add `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/PngEncoder.kt`.
- Add `companion-server/src/test/kotlin/com/enrpau/dualscreendex/server/PngEncoderTest.kt`.

**Steps:**

1. Test exact PNG signature, decoded dimensions/pixels, transparency, deterministic bytes, and invalid-size rejection.
2. Encode `RgbaSprite` without mutating parser models. Do not use an emoji, font glyph, remote image, or generated placeholder.
3. Serve `/api/sprites/species/{id}/{form}.png` and `/api/sprites/balls/{id}.png`.
4. Commit as `feat: serve ROM-derived PNG sprites`.

## Task 6: Server acceptance

**Steps:**

1. Start the server with a user-owned direct ROM and ZIP source.
2. Verify bootstrap/catalog, SSE updates, one Pokémon sprite, one ball sprite where supported, and a simulated action sequence.
3. Verify the listening address is loopback only and stop the process cleanly through normal lifecycle handling.
4. Run all Gradle tests and `git diff --check`.
5. Commit as `test: validate local companion server`.

