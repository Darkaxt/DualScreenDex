# Thor UI Remediation Stage 3 Closure

**Verdict:** `COMPLETE`

**Specification:** `docs/superpowers/specs/2026-08-30-thor-ui-usability-remediation-design.md`

**Plan:** `docs/superpowers/plans/2026-08-30-thor-ui-usability-remediation.md`

## Synchronized checkpoint

- Synchronized `fork/master`: `4e6be87dac2f5c4496c4da47ac9ed516754d4652`
- Stage 2 closure: `ae648e58`
- Loopback peer-disconnect containment: `086544df`
- Packaged keyboard-focus evidence: `a3363499`
- Compact density and Settings navigation: `e776c731`
- Shared dialogs, accessibility, recovery, corrected exact evidence, and implementation closure: `4592d489`
- Delivery branch: `feat/retroarch-free-ui-qa`
- Smart-sync immediately before the Stage 3 closure commit must reconfirm that `fork/master` remains an ancestor and the delivery branch matches its remote checkpoint. No reset, force-push, merge, or discarded work is authorized.

## Requirement closure

| Requirement | Evidence | Result |
| --- | --- | --- |
| `UI-INV-01` | All 11 packaged captures retained the exact `538×445` layout viewport and `538.103×445.312` visual viewport. Browse, Settings, Party/Specimen dialogs, recovery content, and reserved global feedback remained contained. | PASS |
| `UI-INV-02` | The packaged scenario audited 46 actionable target instances. No authored `44px` target measured below `43.997px`, within the established `0.01px` device-rounding tolerance, and no audited pair overlapped. | PASS |
| `UI-INV-03` | Settings uses one `.settings-content` vertical owner. Dialog content scrolls inside its shared fixed-viewport window; opening and closing a Specimen dialog preserves the underlying list scroll position. Global feedback occupies a reserved grid row instead of becoming an overlay owner. | PASS |
| `UI-INV-04` | Existing semantic tokens remain authoritative. Final captures retained readable header, surface, selected, disabled, focus, dialog, and error-feedback contrast; no ROM-derived token became semantic authority. | PASS |
| `UI-INV-05` | Runtime re-attestation held `DISCOVERED` mode against exact Modern Emerald authority for every capture. Recovery copy and Settings routes expose no undiscovered species or topology. | PASS |
| `UI-INV-06` | The bounded request helper converts timed-out or failed optional requests into typed recovery outcomes. Unsupported optional modules remain isolated, and the one-shot QA failure exercises the real App error path without changing production behavior. | PASS |
| `UI-INV-07` | The full `35`-file web suite passed `308/308`; exact-viewport space regressions passed `8/8`, and the ROM-derived theme traversal passed `1/1`. Existing map, Trainer, Setup, and Battle geometry remained covered alongside Stage 3 paths. | PASS |
| `UI-INV-08` | Server screen, client route, action payload, ROM/session identity, and game time remain native-state authority. Density, focus-return selectors, category grouping, and feedback placement are presentation state only. | PASS |
| `UI-DENS-01` | One shared row-height contract drives CSS and virtual offsets: authored Comfortable remains `94px`, and Compact uses `68px`. The corrected packaged gate first normalizes the app font setting to `100%`, then measures device-rounded rows at `93.997px` and `67.995px`, showing approximately `2.7` Comfortable rows and at least `3.7` Compact rows while retaining number, name, portrait, types, and discovery state. Start/middle/end virtualization regressions verify recalculated offsets and bounded anchors; larger font scales remain assigned to the final Stage 4 matrix. | PASS |
| `UI-SET-01` | Compact Settings presents seven labelled categories and one category view. Back returns category → index → initiating route, while direct recovery opens Information and focuses Move List without bypassing the two-level Back hierarchy. | PASS |
| `UI-SET-02` | Category rows, Back, segmented density choices, toggles, selects, display choices, and actions use effective heights of at least `44px`. Routine, diagnostic, and destructive actions retain distinct styling; visible focus surrounds the actual control. | PASS |
| `UI-MODAL-01` | Party and Specimen details share the labelled modal primitive with `aria-modal`, an explicit visible `44×44` close, Escape, Tab/Shift+Tab trap, inert screen and feedback background, internal long-content scrolling, and trigger-focus restoration. | PASS |
| `UI-MODAL-02` | Synthetic multi-card tests open scrolled first, middle, and last visible specimen cards, keep the dialog in the visible host, preserve list scroll, and restore focus to the same card. Stage B exact APK evidence retains the list and opens the packaged dialog; the specification assigns the final packaged scrolled-list matrix to Stage 4 through `UI-VAL-05`. | PASS |
| `UI-A11Y-01` | Shared tab behavior gives one tab stop to the selected tab, supports Left/Right, wrapped Up/Down, Home/End, skips disabled activation, and retains labelled `tabpanel` associations. Exact packaged evidence moves Pokédex Detail focus and selection from Entry to Stats with ArrowRight. | PASS |
| `UI-A11Y-02` | Filters, tabs, density choices, and exclusive controls expose selected state through `aria-pressed`, `aria-selected`, native checked state, or equivalent semantics rather than visual classes alone. Packaged active-state assertions cover filters, density, tabs, and focused recovery controls. | PASS |
| `UI-A11Y-03` | Audited routes expose one programmatic `<h1>`. Forward navigation focuses the route heading or intentional initial control; client-route and server-screen Back restore the actual initiating action. Reactive pending restoration prevents delayed heading focus from stealing the returned focus, while live refreshes do not refocus. | PASS |
| `UI-REC-01` | Move-list absence presents reason and direct next action to Settings → Information → Move List. Failed actions retain exact action and destination identity for bounded Retry, with Dismiss available. Capability, Setup, and Specimen requests use bounded recovery without blocking unrelated routes. | PASS |
| `UI-REC-02` | Persistent feedback has a reserved `.global-feedback` row below `.screen-host`; passive feedback is pointer-transparent and actionable Retry/Dismiss controls alone accept pointers. Exact packaged evidence keeps header actions, list controls, search dock, and feedback actions unobscured. | PASS |
| `UI-VAL-01` | Focused red regressions preceded shared tabs, shared modal semantics, scrolled Specimen restoration, route/Back focus, bounded recovery, global feedback geometry, density virtualization, and Settings hierarchy. Focused and full green gates passed. | PASS |
| `UI-VAL-02` | The reusable packaged runner retains package, transport, scenario, exact ROM/session, geometry, stability, privacy, evidence-budget, touch, overlap, active-state, and contrast checks. Its `13/13` suite now also permits only a bounded one-shot same-origin next-action HTTP failure step. | PASS |
| `UI-VAL-03` | Only companion-web tests/build, exact browser tests, packaged-runner tests, Android debug assembly, and exact packaged Stage 3 gates ran. Parser, catalog, build-wrapper, and corpus-execution code did not change; the parser corpus did not run. | PASS |
| Stage B portion of `UI-VAL-04` | Exact packaged evidence covers both Browse densities, Settings index/category/direct recovery, Party and Specimen dialogs, keyboard tab navigation, and non-obscuring actionable global recovery. | PASS |

Stage C-only `UI-POLISH-*`, `UI-TEXT-01`, and final `UI-VAL-05` requirements remain assigned to Stage 4 rather than deferred defects in this checkpoint.

## Red and green evidence

### Red baselines

- Compact density originally changed visible styling without changing the shared virtualization row geometry. Dedicated regressions required one authoritative row height and verified start, middle, end, and density-switch offsets.
- Compact Settings was one approximately `2085px` document. Navigation regressions established the seven-category index, category replacement, two-level Back hierarchy, and direct-control recovery contract.
- Party and Specimen details were independent overlays without one complete modal contract. Shared-component regressions required labelled dialog semantics, focus trapping, inert backgrounds, visible close, Escape, long-content containment, and trigger restoration.
- The scrolled Specimens path initially coupled detail placement to list content. Synthetic long-list regressions required viewport-host mounting and preservation of both scroll position and exact triggering card identity.
- Action failures initially reduced to transient message text. App regressions required the failed action and destination identity to survive Retry and required Dismiss to clear the recovery state.
- Settings category Back initially focused the category-index heading rather than the initiating category row. A focused red regression reproduced the loss of position; category-row references now restore the exact previous category before Settings can close.
- Initial server-screen Back evidence exposed a focus race: a transient pending-return ref was cleared before follow-up App renders, allowing delayed destination-heading autofocus to steal focus. Reactive pending restoration keeps the destination Header suppressed for its mount, after which re-enabling the context cannot satisfy the first-render/focus-key condition.
- The packaged global-error state required a deterministic failure without changing production transport. The runner first rejected the new step, then accepted only a field-bounded, one-shot interception of the next same-origin `POST /api/actions`; the original fetch is restored before the synthetic `503` is returned.

### Final gates

| Gate | Result |
| --- | --- |
| Focused App production tests | `38/38` tests passed |
| Focused Stage 3 Vitest gate | `4/4` files, `70/70` tests passed |
| Full Vitest suite | `35/35` files, `308/308` tests passed |
| Exact Stage 3 space-regression Playwright gate | `8/8` tests passed at the exact `538×445` test viewport |
| ROM-derived theme Playwright gate | `1/1` test passed after traversing the compact Settings category hierarchy |
| Production companion build | TypeScript and Vite passed; `41` modules transformed |
| Packaged-WebView runner tests | `13/13` tests passed |
| Stage 3 scenario validation | All `11` captures accepted by the bounded schema |
| Android debug assembly | `BUILD SUCCESSFUL`; no production signing or release publication performed |
| Exact packaged Stage 3 scenario | `11/11` captures accepted through the actual debug APK |

## Exact packaged-APK evidence

Evidence root: `docs/reports/thor-ui-remediation/evidence/stage-03-packaged-webview/`

Runtime attestation:

- Application ID: `com.darkaxt.dualdex.debug`
- Transport: `SANITIZED_RAW_MEMORY`
- Scenario: `modern-normal`
- Source: `Modern Emerald (v3.5).gba`
- SHA-256: `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895`
- CRC32: `8C7DBECA`
- Knowledge mode: `DISCOVERED`
- Resolution: `ACTIVE`
- Indexed ROMs: `1`
- Session epoch: `1`
- Integer layout viewport: `538×445`
- Visual viewport: `538.103×445.312`
- DPR: `2.3062500953674316`

| Capture | Product evidence | Audited targets | Minimum target dimension | Privacy |
| --- | --- | ---: | ---: | --- |
| [Comfortable Browse](evidence/stage-03-packaged-webview/browse-comfortable.png) | Accepted shared geometry retains approximately `2.7` full rows with all filter actions reachable. | 5 | `43.997px` | safe |
| [Compact Browse](evidence/stage-03-packaged-webview/browse-compact.png) | `68px` shared rows expose at least `3.7` rows while preserving the complete compact identity projection. | 5 | `43.997px` | safe |
| [Settings index](evidence/stage-03-packaged-webview/settings-index.png) | The first six fully contained categories and Back are independently actionable; Advanced remains reachable through the single Settings scroll owner. | 7 | `51.999px` | safe |
| [Accessibility Settings](evidence/stage-03-packaged-webview/settings-accessibility.png) | Direct category content, density choices, font-scale control, toggles, and Back remain contained. | 5 | `43.997px` | safe |
| [Party dialog](evidence/stage-03-packaged-webview/party-detail.png) | Shared modal mounts against the visible host with inert background and focused explicit close. | 1 | `43.997px` | safe |
| [Keyboard tabs](evidence/stage-03-packaged-webview/pokedex-keyboard-tabs.png) | ArrowRight selects and focuses Stats with all wrapped tab targets contained. | 5 | `43.997px` | safe |
| [Move-list recovery](evidence/stage-03-packaged-webview/move-list-recovery.png) | Terminal state names the reason and exposes the direct Settings recovery action. | 6 | `43.997px` | safe |
| [Move-list Settings](evidence/stage-03-packaged-webview/move-list-settings.png) | Direct recovery focuses the Move List select while retaining the Settings Back hierarchy. | 2 | `43.997px` | safe |
| [Specimens list](evidence/stage-03-packaged-webview/specimens-list.png) | List Back and specimen card remain contained before modal activation. | 2 | `53.997px` | safe |
| [Specimen dialog](evidence/stage-03-packaged-webview/specimen-dialog.png) | The same shared modal contract is applied from the Specimens route with focused close. | 1 | `43.997px` | safe |
| [Global error feedback](evidence/stage-03-packaged-webview/global-error-feedback.png) | Actual App failure UI occupies the reserved row; Retry/Dismiss remain actionable without covering header, rows, or search. | 7 | `43.997px` | safe |

Every capture passed selector quotas, visual-stability checks, visual-viewport containment, touch-size and overlap checks, screenshot budgets, authority re-attestation, active-state checks, and privacy classification. Total screenshot evidence is `938,440` bytes. Visual inspection found no clipping, overlap, displaced content, stale modal background, or private material. The report contains no ROM bytes, save bytes, memory bytes, credentials, personal details, private paths, or signing material.

## Blockers and referrals

- Stage 3 blockers: None.
- Task #311 — loopback peer-disconnect containment is complete at `086544df`; Stage 3 required no additional server changes.
- Task #294 — clean-start QA catalog provisioning remains a separate concern and was not bundled.
- Stage 4 remains responsible for Battle Attack fit, map-title collision resistance, nearby-move two-column geometry, text/font-scale floors, the final preserved-view/font-scale matrix, and the requested authoritative in-game clock on normal content-page headers where it does not displace required actions.
- Final Stage 4 validation also owns the refreshed representative README screenshot set captured from the finished UI through the owned Thor emulator.
- This checkpoint does not authorize an RC, signed artifact, stable promotion, PR, merge, or parser/compatibility work.
