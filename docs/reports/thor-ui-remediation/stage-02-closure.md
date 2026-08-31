# Thor UI Remediation Stage 2 Closure

**Verdict:** `COMPLETE`

**Specification:** `docs/superpowers/specs/2026-08-30-thor-ui-usability-remediation-design.md`

**Plan:** `docs/superpowers/plans/2026-08-30-thor-ui-usability-remediation.md`

## Synchronized checkpoint

- Synchronized `fork/master`: `4e6be87dac2f5c4496c4da47ac9ed516754d4652`
- Stage 1 closure: `8c6f05b0`
- Map targets and deterministic clustering: `bac24af4`
- Area Guide virtualization and exit projection: `361ae49d`
- Final browser corrections, packaged-runner precision, exact APK evidence, and validation: `cc41a1a7`
- Delivery branch: `feat/retroarch-free-ui-qa`
- Smart-sync immediately before each Stage 2 commit confirmed that `fork/master` remained an ancestor and the delivery branch matched its remote checkpoint. No reset, force-push, merge, or discarded work was required.

## Requirement closure

| Requirement | Evidence | Result |
| --- | --- | --- |
| `UI-INV-01` | Exact packaged geometry was `538×445` with a `538.103×445.312` visual viewport. The Map screen, fixed header, chooser, Area Guide, and measured semantic content remained contained. A packaged red baseline found the map header at `546/538px`; the compact header action minimum was corrected from `54px` to its authored `46px` track and recaptured at exactly the visual right edge. | PASS |
| `UI-INV-02` | The Stage 2 packaged scenario audited 26 actionable target instances. Authored `44px` targets measured no smaller than `43.997px`, within the existing `0.01px` device-rounding tolerance; the runner continues to reject `43.98px`. No audited pair overlapped. | PASS |
| `UI-INV-03` | `.area-guide-content` is the only Area Guide vertical scroll owner. Its `310px` client viewport retained a `1038px` scroll extent, while the long windowed list reported no child scroll owner. Three real upward swipes reached the final populated row. | PASS |
| `UI-INV-04` | The Stage 1 semantic pairs remain unchanged. Captured chooser and guide foreground/surface pairs measured `13.68:1` to `14.603:1`; map and guide headers measured `7.755:1`. No ROM token became semantic authority. | PASS |
| `UI-INV-05` | The packaged run re-attested `DISCOVERED` mode against the exact Modern Emerald authority. Cluster labels expose only counts, chooser labels use the existing knowledge-safe POI projection, and equal exit names use ordinals without revealing new topology. | PASS |
| `UI-INV-06` | Clustering and guide projections validate bounded input and fail closed. No optional failure path was changed to crash, invent data, or remove unrelated navigation. The independently discovered loopback peer-disconnect defect is tracked as Task #311 with a bounded acceptance target. | PASS |
| `UI-INV-07` | The full `34`-file web suite passed `284/284`. The exact-viewport browser gate also retained compact Pokédex, Trainer, and Battle space regressions while exercising Map and Area Guide. | PASS |
| `UI-INV-08` | Native POI IDs, map coordinates, destination IDs, navigation actions, discovery state, and ROM/session authority remain authoritative. Clustering and exit labels are presentation projections only. | PASS |
| `UI-MAP-01` | Local POIs, Atlas locations, habitat markers, map controls, map header actions, detail close, and chooser actions have authored `44×44` or larger wrappers while their marker artwork remains small. The exact Local capture measured five actionable targets representing all nine Oldale POIs. | PASS |
| `UI-MAP-02` | The pure connected-component projection clusters overlapping `44×44` hit regions deterministically, preserves source order, anchors keys to the first member, and splits naturally as zoom separates targets. Browser E2E selects member 9 from a synthetic nine-member cluster and then exposes all nine independent targets. Packaged evidence opens both live three-member Oldale clusters and audits all six member rows and both explicit close actions. | PASS |
| `UI-GUIDE-01` | The fixed drawer header remains outside one outer scroll owner. Outer-scroll virtualization preserves full spacer height while mounting a bounded row window. Browser E2E reaches Species 80 and the final objective, verifies the fixed header, closes/reopens, and observes `scrollTop=0`; exact APK swipes reach the final populated service row. | PASS |
| `UI-GUIDE-02` | Native exit projection retains exact destination multiplicity through `count`. Web projection groups repeated destination IDs and assigns deterministic ordinals only when distinct IDs share one visible name. Browser tests preserve destination IDs and navigation; packaged Oldale copy distinguishes `EXIT 1 OF 4` through `EXIT 4 OF 4` instead of presenting four identical actions. | PASS |
| `UI-VAL-01` | Focused red regressions preceded map clustering, Area Guide ownership/grouping, compact header containment, chooser close sizing, and evidence-rounding corrections. All focused and full green gates passed. | PASS |
| `UI-VAL-02` | The reusable runner retains strict package, transport, scenario, ROM/session, geometry, render-stability, privacy, evidence-budget, touch, overlap, active-state, and contrast checks. Its complete suite passed `13/13`. A dedicated regression permits only the `0.001px` representation difference introduced by three-decimal evidence serialization. | PASS |
| `UI-VAL-03` | Only companion web, companion core, packaged-runner, debug assembly, exact browser, and exact packaged gates ran. The parser corpus did not run. | PASS |
| Stage 2 portion of `UI-VAL-04` | Retained exact-APK captures cover Local Map, both live dense-POI choosers, Area Guide top, and the final populated Area Guide section. Stage 1 already closed Trainer, Settings, and Setup, completing Stage A's required APK surfaces. | PASS |

`UI-MODAL-01` does not apply: the cluster member chooser is a bounded nonmodal map popover.

## Red and green evidence

### Red baselines

- Pre-remediation map POI artwork supplied only `24×24` effective buttons; expanding wrappers without clustering would have produced overlapping actions. The dedicated nine-POI test initially formed one overlap component and established individual member recovery and zoom splitting before the green implementation.
- The compact Area Guide used a contained `280px` inner `overflow-y: auto` list inside a parent that still needed to reach later sections. Its exact Oldale projection also exposed repeated equal-name exits without useful differentiation.
- Exact packaged validation independently found three post-implementation defects before closure:
  - `.map-page-header` required `546px` inside the `538px` route because `46px` compact grid tracks contained `54px` action minimums;
  - the chooser close action rendered approximately `43.13px` high because a `44px` header border box reserved `1px` for its divider;
  - three-decimal evidence serialized the Settings right edge as `538.103px`, approximately `0.000034px` beyond the raw visual width, causing a false runner rejection.
- Each packaged defect received a focused failing regression before its minimal correction. The runner tolerance remains separate from the existing touch-size tolerance and covers only evidence serialization precision.

### Final gates

| Gate | Result |
| --- | --- |
| Full Vitest suite | `34/34` files, `284/284` tests passed |
| Exact Stage 2 Playwright gate | `7/7` tests passed at the exact `538×445` test viewport |
| Production companion build | TypeScript and Vite passed; `40` modules transformed |
| Packaged-WebView runner tests | `13/13` tests passed |
| Exact packaged Stage 2 scenario | Five captures accepted with real touch input and real vertical swipes |
| Full companion-core tests | `120` executed tests passed; the one opt-in external-ROM control was skipped because the ROM path was not passed to Gradle |
| Android debug assembly | `BUILD SUCCESSFUL`; no production signing or release publication performed |

The browser gate used an equivalent temporary Playwright configuration on port `4193` because the configured `4175` port was already occupied. The temporary configuration was removed after the run. Canonical map rasters remained external test inputs and were not committed or packaged as new assets.

## Exact packaged-APK evidence

Evidence root: `docs/reports/thor-ui-remediation/evidence/stage-02-packaged-webview/`

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
| [Local Map](evidence/stage-02-packaged-webview/local-map.png) | Five non-overlapping actions represent three singleton POIs and two three-member clusters; controls and both compact header actions remain contained. | 12 | `43.997px` | safe |
| [West chooser](evidence/stage-02-packaged-webview/west-map-point-chooser.png) | Cluster `cluster/local/000a/warp/2/3`; all three members and explicit close are independently actionable. | 4 | `43.997px` | safe |
| [East chooser](evidence/stage-02-packaged-webview/east-map-point-chooser.png) | Cluster `cluster/local/000a/warp/3/3`; selection recovered to the map, then all three members and explicit close were audited. | 4 | `43.997px` | safe |
| [Area Guide top](evidence/stage-02-packaged-webview/area-guide-top.png) | One `310px` outer owner over a `1038px` extent; three fully contained exit actions expose deterministic Oldale ordinals. | 4 | `43.997px` | safe |
| [Area Guide final section](evidence/stage-02-packaged-webview/area-guide-final-section.png) | Three real swipes reached `scrollTop=728.022` and the final `55.996px` service action while the header remained fixed. | 2 | `43.997px` | safe |

Every capture passed selector quotas, visual-stability checks, visual-viewport containment, touch-size and overlap checks, screenshot budgets, authority re-attestation, and privacy classification. Total screenshot evidence is `635,035` bytes. The report contains no ROM bytes, save bytes, memory bytes, credentials, personal details, private paths, or signing material.

## Blockers and referrals

- Stage 2 blockers: None.
- Task #311 — harden loopback client disconnects. Target: `AndroidLoopbackServer.handle`/streamed JSON response writing. Dependency: separate server hardening, not Stage 2 UI behavior. Acceptance: a bounded regression proves that a peer disconnect wrapped by Gson as an I/O-caused exception is contained as an ordinary disconnect, while serialization/programming errors still fail loudly. The discovered defect did not invalidate the successful packaged scenario and was not silently bundled into this checkpoint.
- Task #294 — harden clean QA catalog startup. This remains a separate provisioning concern and was not bundled.
- Plan Stage 3 remains responsible for the already-specified compact density, Settings navigation, Party/Specimen modal, and recovery-state work. This is planned scope, not a deferred Stage 2 defect.
- Stages 1 and 2 now form the complete high-priority audit Batch A.
- This checkpoint does not authorize an RC, signed artifact, stable promotion, PR, merge, or parser/compatibility work.
