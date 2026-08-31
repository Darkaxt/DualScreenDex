# Thor UI Remediation Stage 4 Closure

**Verdict:** `COMPLETE`

**Specification:** `docs/superpowers/specs/2026-08-30-thor-ui-usability-remediation-design.md`

**Plan:** `docs/superpowers/plans/2026-08-30-thor-ui-usability-remediation.md`

## Synchronized checkpoint

- Synchronized `fork/master`: `4e6be87dac2f5c4496c4da47ac9ed516754d4652`
- Stage 3 closure: `9eac6419`
- Corrected discovered-filter policy: `478ae98f`
- Stage 4 implementation: `cba49e5d`
- Delivery branch: `feat/retroarch-free-ui-qa`
- Final closure was smart-synced immediately before commit. No reset, force-push, merge, or discarded peer work was used.

## Requirement closure

| Requirement | Evidence | Result |
| --- | --- | --- |
| `UI-INV-01` | All `40` final-package captures retained the exact `538×445` layout viewport, `538.103×445.312` visual viewport, and `2.3062500953674316` DPR. Header, route, dialog, map, recovery, and feedback geometry remained contained. | PASS |
| `UI-INV-02` | The final packaged matrix audited `152` actionable target instances. The smallest audited target dimension was `43.997px`, within the established `0.01px` device-rounding tolerance for the authored `44px` floor; no audited targets overlapped. | PASS |
| `UI-INV-03` | Every asserted owner reached its endpoint: Settings `74.58/75`, Party Analysis `2541.355/2542`, and Specimens `683.794/684`. Battle Attack and Rarity truthfully reported non-scrolling `0/0` content owners. | PASS |
| `UI-INV-04` | Exact browser and packaged evidence retained semantic header, surface, selected, disabled, focus, dialog, map, rarity, and recovery contrast. ROM-derived theme tokens remain presentation inputs rather than semantic authority. | PASS |
| `UI-INV-05` | Every packaged run re-attested `DISCOVERED`, `ACTIVE`, and one exact Modern Emerald catalog. Discovered All remained the complete parsed `428`-species catalog; Caught, Seen, and Team remain enabled only when truthful projected entries exist, while unavailable empty filters fail closed. | PASS |
| `UI-INV-06` | Setup, recovery, optional detail modules, and the one-shot synthetic action failure remained bounded and isolated. The final error capture retained Retry and Dismiss without obscuring the active route. | PASS |
| `UI-INV-07` | Runner tests passed `14/14`, Vitest passed `35/35` files and `309/309` tests, Playwright passed `52/52`, and the complete Android security/unit/lint/debug-assembly gate ended `BUILD SUCCESSFUL`. | PASS |
| `UI-INV-08` | Native state remains authoritative for server screen, action payloads, ROM/session identity, route game time, battle state, and projected catalog knowledge. Welcome and Setup intentionally remain outside the normal-route clock contract. | PASS |
| `UI-POLISH-01` | The exact packaged Battle Attack capture retained its complete selected-move evidence with `.battle-content` at `0/0`; Entry and Rarity also remained contained. Browser regressions independently froze Attack and Rarity containment. | PASS |
| `UI-POLISH-02` | Exact long-title browser cases keep the centered clock clear and preserve trailing actions. The packaged Local Map and both dense chooser paths remained reachable at exact geometry. | PASS |
| `UI-POLISH-03` | Party Analysis uses dedicated two-column nearby-move geometry. The packaged final card remained actionable after the single owner reached `2541.355/2542`. | PASS |
| `UI-POLISH-04` | All `19` Stage 4 text-audited captures exceeded the runner floor of `11.19px` and route-average floor of `12px`. At 85%, Settings measured `11.78px` minimum and `12.042px` average; at 100%, `11.78px` and `12.904px`; at 135%, `15.39px` and `17.25px` at the category top, with the final category still reachable. | PASS |
| Normal-route clock extension | The authoritative `state.gameTime` clock is retained on Pokédex, Battle, Party, Trainer, Local Map, Specimens, Move, Ability, Nature, and Party Analysis routes. Exact captures cover the newly extended drill-downs without displacing route actions. Welcome and Setup remain explicitly exempt. | PASS |
| `UI-VAL-01` | Focused red regressions preceded rarity containment, Height Comparison floors, Settings range-label floors, and the route-local Settings average-text floor. Each failed for the intended missing contract before the minimal CSS or runner correction passed. | PASS |
| `UI-VAL-02` | The reusable runner now enforces visible-text minimum/average audits, scroll-end assertions, center-visible swipe endpoints, exact package/transport/ROM/session authority, geometry, stability, privacy, evidence budgets, target size/overlap, active state, and contrast. Its final suite passed `14/14`. | PASS |
| `UI-VAL-03` | Parser, catalog, build-wrapper, and corpus-execution code did not change, so the parser corpus was not rerun. Validation was bounded to web tests/build, exact browser tests, packaged runner/scenarios, and Android security/unit/lint/debug assembly. | PASS |
| `UI-VAL-04` | The same final debug APK passed Stage 1 (`5/5`), Stage 2 (`5/5`), Stage 3 (`11/11`), Stage 4 overworld (`15/15`), and Stage 4 Battle (`4/4`) matrices. | PASS |
| `UI-VAL-05` | Durable exact-APK evidence covers Browse Comfortable/Auto and Compact; Pokédex Entry and More; Battle Entry/Attack/Rarity/Moves; Party overview/detail/analysis; Trainer Card/Progress; Local Map/two dense choosers/Area Guide; Settings index/category at 85/100/135%; Setup/recovery; Specimens expanded/scrolled list and dialog; Ability and Nature drill-downs. | PASS |
| `UI-VAL-06` | This report records synchronized commits, requirement closure, red/green evidence, fresh gate commands/results, durable APK screenshots/measurements, preserved-view coverage, and explicit referrals. | PASS |

## Red and green evidence

### Red baselines closed in Stage 4

- Battle Attack retained a `19px` micro-scroll. A focused exact-viewport regression required the complete selected-move card to fit; the final packaged owner is `0/0`.
- Rarity's transformed decorative pseudo-element expanded horizontal scroll width to `589px` inside a `512px` card. The red containment assertion reproduced it; removing transformed overflow restored exact containment without changing the visual state.
- The first final Pokédex Entry scenario inherited the previously selected More tab. Contact-sheet review exposed the false coverage; the scenario now explicitly selects Entry, waits for its panel, and asserts `aria-selected="true"`.
- Corrected Entry coverage exposed a `10.83px` Height Comparison heading. Focused style contracts required both the heading and ruler labels to use the shared physical floor before the packaged Entry capture passed at `11.4px` minimum.
- The initial 85% capture did not audit text. Enabling the audit exposed `10.296px` Settings range labels. A red style contract moved them to the shared floor.
- That minimum-only correction produced a packaged `11.78px` minimum but `11.789px` average. A second focused red contract established a route-local `12.4px` Settings floor; the final exact APK measured `11.78px` minimum and `12.042px` average.
- Area Guide endpoint swipes accepted a barely intersecting final row. The runner now requires the endpoint center to enter the visual viewport both before and after each swipe; the final owner reached `74.58/75`.

### Final gates

| Gate | Result |
| --- | --- |
| Focused physical-floor Vitest gate | `22/22` tests passed |
| Full Vitest suite | `35/35` files, `309/309` tests passed |
| Full exact-browser Playwright suite | `52/52` tests passed |
| Packaged-WebView runner tests | `14/14` tests passed |
| Stage 1 final-package scenario | `5/5` captures accepted |
| Stage 2 final-package scenario | `5/5` captures accepted |
| Stage 3 final-package scenario | `11/11` captures accepted |
| Stage 4 overworld scenario | `15/15` captures accepted |
| Stage 4 Battle scenario at `move-selected` | `4/4` captures accepted |
| Android security/unit/lint/debug assembly | `verifySecureBuildDependencies`, all Gradle tests, `:app:lintDebug`, and `:app:assembleDebug` passed; `BUILD SUCCESSFUL` in `57s` |

## Exact packaged-APK authority

Evidence root: `docs/reports/thor-ui-remediation/evidence/stage-04-packaged-webview/`

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
- Physical QA display: `1240×1025`, `369 dpi`
- Android font scale: `0.95`
- Aggregate captures: `40`
- Aggregate audited targets: `152`
- Aggregate screenshots: `4,311,244` bytes
- Privacy result: all captures safe

The evidence retains selector names, rounded geometry, text sizes, scroll positions, active-state assertions, and screenshots. It contains no ROM bytes, save bytes, raw-memory bytes, captured text values from the text audit, credentials, personal details, private paths, or signing material.

## Stage 4 text and endpoint matrix

| Capture | Minimum text | Average text | Endpoint |
| --- | ---: | ---: | ---: |
| Settings at 85% | `11.78px` | `12.042px` | not scroll-required |
| Settings at 100% | `11.78px` | `12.904px` | not scroll-required |
| Settings at 135% top | `15.39px` | `17.25px` | not scroll-required |
| Settings at 135% end | `12.697px` | `14.971px` | `74.58/75` |
| Pokédex Entry | `11.4px` | `13.11px` | not scroll-required |
| Pokédex More | `11.4px` | `13.234px` | not scroll-required |
| Party overview | `11.4px` | `12.471px` | not scroll-required |
| Party detail | `11.4px` | `12.991px` | dialog contained |
| Nature detail | `11.4px` | `13.797px` | not scroll-required |
| Ability detail | `11.4px` | `13.356px` | not scroll-required |
| Party Analysis final move | `11.4px` | `12.077px` | `2541.355/2542` |
| Specimens final card | `11.4px` | `12.402px` | `683.794/684` |
| Specimen dialog from scroll | `11.4px` | `12.991px` | dialog contained |
| Setup | `11.78px` | `12.122px` | not scroll-required |
| Recovery | `11.4px` | `12.629px` | not scroll-required |
| Battle Entry | `11.4px` | `13.419px` | not scroll-required |
| Battle Attack | `11.4px` | `13.314px` | `0/0` |
| Battle Rarity | `11.4px` | `14.542px` | `0/0` |
| Battle Moves | `11.4px` | `13.538px` | truthful empty state |

## Preserved-view evidence index

- [Stage 1 final-package matrix](evidence/stage-04-packaged-webview/stage-1/report.json): Trainer Card/Progress, Settings, destructive-action distinction, and Setup.
- [Stage 2 final-package matrix](evidence/stage-04-packaged-webview/stage-2/report.json): Local Map, west/east dense point choosers, Area Guide top, and final section.
- [Stage 3 final-package matrix](evidence/stage-04-packaged-webview/stage-3/report.json): both Browse densities, Settings hierarchy/recovery, Party and Specimen dialogs, keyboard tabs, and reserved error feedback.
- [Stage 4 overworld matrix](evidence/stage-04-packaged-webview/overworld/report.json): all three font settings, explicit Pokédex Entry/More, Party overview/detail, Ability/Nature, Party Analysis, scrolled Specimens/dialog, Setup, and recovery.
- [Stage 4 Battle matrix](evidence/stage-04-packaged-webview/battle/report.json): real sanitized `move-selected` Entry, selected-move Attack, Rarity, and truthful empty Moves.

## README evidence

The representative README tour was replaced with `17` fresh WebP captures from the finished debug QA APK on the owned Thor-profile emulator. The wording now distinguishes exact packaged-WebView evidence from physical-Thor or signed-production evidence, identifies the externally staged exact ROM and sanitized replay, and states that no ROM is bundled. The Accessibility image was regenerated after the final 85% Settings floor correction.

## Blockers and referrals

- Stage 4 blockers: None.
- Task #294 — clean-start QA catalog provisioning remains separate. This closure proves warm exact-catalog acceptance and does not silently claim clean-start provisioning.
- Task #237 — stable release remains separately gated. This work does not authorize or publish stable, an RC, a signed production artifact, a PR, or a merge.
- No parser corpus run was required because parser, catalog, build-wrapper, and corpus-execution code did not change.
- The external ROM remained test input only and was never copied into production assets or retained in repository evidence.
