# QA Hardening Stage 6 Closure

**Decision:** `COMPLETE`

**Stage branch:** `qa/project-wide-hardening`

**Synchronized baseline:** `8b1f3282` (`fork/master`)

**Stage implementation:** `a6aa85fd`

**Verification-fixture correction:** `3bedc551`

**Requirements:** `WEB-01`–`WEB-09`, `REL-04`

## Completed requirements

### WEB-01 — Bounded Android loopback requests

The Android loopback server now applies request-read and response-write deadlines, bounds queued/active workers, request lines, headers, header count, and request bodies, and closes owned sockets during shutdown. Partial clients cannot retain every worker indefinitely, malformed and over-budget requests fail through the shared API envelope where applicable, and later valid bootstrap requests remain serviceable.

Owning regressions exercise partial-request timeout, header and body limits, worker recovery, shutdown interruption, and normal bootstrap after hostile input.

### WEB-02 — Validated route and history restoration

The companion browser now serializes a validated catalog-bound route stack into the `#dualdex=` marker, limits encoded routes and marker length, rejects malformed, unknown, or stale-catalog routes, and synchronizes `pushState`, `replaceState`, and `popstate` without losing the preserved route-history index.

Android display transfer accepts only validated DualDex route markers. Public Chromium acceptance covers touch navigation, reload restoration, and browser Back behavior.

### WEB-03 — Catalog-correct media

Catalog-owned map, species, ball, trainer, badge, specimen, evolution, guide, and height-comparison media URLs carry catalog identity while preserving existing media-variant query parameters. Static and generated media responses require revalidation rather than one-year immutable reuse.

The public recovery fixture switches between two catalogs that reuse the same logical sprite ID but serve different pixels. Chromium verifies that the second catalog fetches and renders its own bytes without clearing browser data.

### WEB-04 — Structured API errors and bounded reconnect

Android and desktop APIs now share one `ApiErrorView`/`ApiErrorDetailView` envelope for 400, 404, 405, 500, and unavailable outcomes. Browser requests inspect status and content type before parsing, convert malformed responses to stable safe messages, and do not depend on raw server text.

State polling uses one recursive cancellable operation, a five-second watchdog, bounded exponential backoff, and explicit `CONNECTED`, `RECONNECTING`, and `FAILED` states. Recovery performs an authoritative bootstrap so a restarted server cannot retain stale catalog authority or duplicate timers.

### WEB-05 — Bounded desktop SSE

Each desktop SSE client owns a one-slot latest-state signal rather than an unbounded snapshot queue. Writes have deadlines, obsolete intermediate states are conflated, and disconnect/close removes client ownership. Regressions cover a stalled client under repeated publication and later newest-state delivery or bounded disconnection.

### WEB-06 — Correct static and SPA fallback

Both servers return 404 for missing script, stylesheet, image, map, trainer, and encoded-extension assets. SPA fallback is limited to extensionless navigation that accepts `text/html`; existing static assets retain their correct content type and `Cache-Control: no-cache` policy.

The encoded `/assets/missing%2Ejs` regression prevents an encoded static request from being mistaken for an extensionless navigation.

### WEB-07 — Guide sprite availability

Area-guide encounter entries expose whether species artwork exists. The browser requests a species sprite only when the catalog contract marks it available and otherwise renders the deterministic missing-art treatment. Catalog identity is also attached to valid guide artwork requests.

### WEB-08 — Unique simulator encounter identity

Generated simulator encounters include a monotonic ordinal. The desktop runtime no longer resets its encounter ordinal when a catalog reload retains the knowledge ledger, preventing repeated seeded captured encounters from reusing stable keys across reloads.

Owning simulator and runtime regressions cover repeated identical encounters and uniqueness across a catalog reload.

### WEB-09 — Android/desktop parity matrix

`stage-06-web-parity-matrix.md` is the canonical browser-visible server contract matrix. Mirrored Android and desktop regressions cover health/bootstrap/state semantics, unchanged state, server reset, error envelopes, cache policy, media acceptance/rejection, existing and missing assets, SPA fallback, defensive browser parsing, and recovery.

A browser-visible server contract change is incomplete until both owning server tests are updated or the matrix records why behavior is transport-specific.

### REL-04 — Portable mandatory Chromium acceptance

The public Playwright profile uses generated fixtures, installed Chromium, and platform-neutral commands. CI runs it on Ubuntu, and release verification installs Chromium and runs the same nonprivate suite before the unsigned release build. Private ROM-derived evidence remains an explicit separate suite and is not required by the public gate.

Static release-policy tests verify job platform, Chromium installation, public-suite invocation, and ordering before the release build.

## Synchronization

Stage 6 was first smart-synced with the six incoming RC77 UI-conformance commits through `8076b5e9`; overlapping workflow, component, style, and policy-test edits merged without discarding either line of work. Implementation was committed as `a6aa85fd` and pushed to `fork/qa/project-wide-hardening`.

Before closure, `fork/master` advanced again through the passive-insights documentation checkpoint `8b1f3282`. The branch merged that synchronized baseline as `5fdabdf5`; the incoming changes did not alter Stage 6 transport ownership. The stale capability-response test fixture exposed by the formal web gate was corrected as `3bedc551` and pushed.

No reset, overwrite, or unrelated-change discard occurred.

## Verification evidence

| Scope | Command/evidence | Result |
| --- | --- | --- |
| Android parity | Exact `AndroidLoopbackServerTest` | PASS, 19 tests |
| Desktop parity | Exact `ServerContractTest` | PASS, 14 tests |
| Catalog media contract | Exact `ApiViewBuilderTest` | PASS, `BUILD SUCCESSFUL` |
| Simulator/runtime key ownership | Exact `EncounterSimulatorTest` and `DualDexRuntimeTest` regressions | PASS, including repeated captured encounters across catalog reload |
| Browser route, transport, media, and affected views | Exact Vitest suites for gateway, navigation, media, production app, guide, and catalog-versioned views | PASS, 51 focused tests before final synchronization; affected post-sync suites also passed |
| Public resilience fixture | `playwright test e2e/companion-resilience.spec.ts` | PASS, 2 tests |
| Release policy | `node --test tools/release/release-workflow.test.mjs` | PASS, 16 tests |
| Required Stage 6 web gate, unit portion | `npm test -- --run`, then only the exact stale `CapabilityReportPage.test.tsx` fixture after correction | All production and other test files passed in the full invocation; the only four failures were obsolete Response mocks, and the corrected owning file passed 4/4 without repeating completed suites |
| Required Stage 6 web gate, build | `npm run build` | PASS |
| Required Stage 6 web gate, public Chromium | `npm run test:e2e:ci` | PASS, 3 tests |
| Required Stage 6 Gradle gate | `:companion-core:test :companion-server:test :companion-simulator:test :app:testDebugUnitTest --stacktrace` | PASS, `BUILD SUCCESSFUL` in 37m24s; 50 actionable tasks, 7 executed and 43 up-to-date |

No emulator, ADB gesture, physical-device run, release signing, candidate creation, candidate promotion, or RC publication occurred.

## Specification reread and invariant review

The full project-wide specification was reread after implementation stabilized.

- `INV-01`: malformed requests, missing optional assets, server loss, stale routes, SSE stalls, and unavailable artwork fail closed in their owning module without replacing valid catalog state with invented data.
- `INV-02`: route and media restoration are catalog-bound; server restart recovery requires a new authoritative bootstrap, and simulator/runtime encounter identity remains monotonic for the owning runtime lifetime.
- `INV-03`: Stage 6 changes transport and presentation only and does not place user recovery under parser-cache ownership.
- `INV-04`: Android requests, bodies, workers, and deadlines; desktop SSE state and write time; route depth/length; and browser retries/watchdogs are explicitly bounded.
- `INV-05`: Android and desktop API errors share one structured envelope, browser parsing is defensive, and connection failure has visible bounded recovery.
- `INV-06`: portable public Chromium evidence is mandatory in CI and release before build; Stage 6 makes no parser-output compatibility claim and publishes no candidate.

## Missing-feature classification

### Blockers

None. Every current-stage requirement is implemented on synchronized source, focused ownership tests pass, and the complete required Stage 6 Gradle and web gates pass without repeating already-completed suites.

### Tracked referrals

#### S6-REF-01 — Remaining Android UX, privacy, evidence, and governance (`TRACKED_REFERRAL`)

- **Requirements/invariants:** `AND-04`–`AND-07`, `REL-05`–`REL-10`; `INV-05`, `INV-06`
- **Affected modules:** Android activity/overlay/setup and storage UX, diagnostics, Gradle dependencies, release evidence/protection, readiness documentation
- **Reason:** Explicitly assigned to Stage 7; no current-stage requirement is being deferred.
- **Target:** Stage 7
- **Dependency:** Completed correctness, resilience, runtime, and companion stages
- **Acceptance:** Overlay picker routes perform exactly one advertised action; rescan safely commits a new index while retaining the old index on failure; Settings and protected-path guidance recover correctly; release evidence is source/scope bound; diagnostics reveal no reversible player/path detail; direct dependencies, minimal exit classification, protection audit, and one current-readiness entry point satisfy their owning deterministic tests.

#### S6-REF-02 — Integrated invariant closure (`TRACKED_REFERRAL`)

- **Requirements/invariants:** every specification requirement, all `INV-*` requirements, and all prior referrals
- **Affected modules:** project-wide
- **Reason:** Explicitly assigned to the final integrated audit rather than referred from a missing Stage 6 requirement.
- **Target:** Stage 8
- **Dependency:** Stage 7 closure
- **Acceptance:** Every specification requirement and referral is closed on synchronized HEAD and the complete integrated gate passes without blockers.

## Final decision

`COMPLETE` — Stage 6 has no blockers. It is eligible to merge as a completed checkpoint and proceed to Stage 7. No candidate was created, signed, promoted, or published.
