# Thor UI Remediation Stage 1 Closure

**Verdict:** `COMPLETE`

**Specification:** `docs/superpowers/specs/2026-08-30-thor-ui-usability-remediation-design.md`

**Plan:** `docs/superpowers/plans/2026-08-30-thor-ui-usability-remediation.md`

## Synchronized checkpoint

- Synchronized `fork/master`: `4e6be87dac2f5c4496c4da47ac9ed516754d4652`
- Specification and plan: `eaad87eed24740122a879ee2defdebdf33dfe069`
- Stage 1 implementation: `27bfb28b2e62c3d4729d3862517eb3bd9d87b222`
- Touch-floor hardening and final packaged evidence: `179499fd`
- Delivery branch: `feat/retroarch-free-ui-qa`
- Smart-sync immediately before the implementation/evidence commit confirmed that `fork/master` remained an ancestor. No reset, force-push, merge, or discarded work was required.

## Requirement closure

| Requirement | Evidence | Result |
| --- | --- | --- |
| `UI-INV-01` | Exact `538×445` Trainer browser E2E plus five packaged captures at the accepted Thor geometry; measured semantic elements remained within the visual viewport. | PASS |
| `UI-INV-02` | The packaged runner audited 23 actionable target instances for size, fractional-viewport containment, and pairwise overlap. The minimum packaged dimension was `43.997px`, within the runner's `0.01px` tolerance for an authored `44px`; `43.98px` remains rejected. Setup's lowest audited action ended at `444.932px` within the `445.312px` visual viewport. | PASS |
| `UI-INV-03` | Trainer, Settings, and Setup retain one bounded route content owner; static layout tests and compact E2E found no competing route scroll owner. | PASS |
| `UI-INV-04` | Semantic foreground/background/border pairs replace raw-ROM assumptions for migrated actions and surfaces. Captured normal-action contrast ranged from `6.306:1` to `8.473:1`, above `4.5:1`. | PASS |
| `UI-INV-05` | No knowledge or discovery projection changed. Packaged evidence attested `DISCOVERED` mode against the exact Modern Emerald authority. | PASS |
| `UI-INV-06` | Optional-route failure containment remained covered by the 24 selected loopback-server tests; no UI module gained an unbounded or invented fallback. | PASS |
| `UI-INV-07` | Pokédex and Battle compact E2E passed; Party, Party Analysis, and Specimens retained-view tests passed `15/15`; Trainer Card body was recaptured after the header change. | PASS |
| `UI-INV-08` | Theme derivation consumes existing catalog tokens without mutating authority. Runtime evidence attested the exact ROM/session. The forced release-artifact isolation test executed with zero skips and found no debug QA route, payload, or transport in release output. | PASS |
| `UI-NAV-01` | Card and Progress are simultaneously allocated in a `96px` switcher with distinct icons, accessible names, and separate `48px` actions. Real CDP touch input activated each destination and the runner verified its `aria-pressed` state. | PASS |
| `UI-THEME-01` | `themeContrast.ts` derives semantic primary, secondary, selected, surface, danger, status, header, and focus roles while preserving raw theme variables. Settings, Setup, shared controls, Capability actions, and selected controls use the semantic pairs. | PASS |
| `UI-THEME-02` | Pure theme tests cover Modern Emerald, near-black, near-white, saturated, low-separation gray, malformed inputs, and ROM-independent danger/status roles. Existing production fixtures and browser E2E cover GAME plus fixed Dark, Light, and High Contrast modes; rendered Settings and Setup contrast is checked directly. | PASS |
| `UI-VAL-01` | Product regressions were established before correction and all focused green gates passed. | PASS |
| `UI-VAL-02` | `tools/qa/thor-webview-cdp.mjs` is retained with strict package, transport, scenario, ROM/session, geometry, render stability, privacy, evidence-budget, touch, active-state, and contrast checks. Runner tests passed `13/13`. | PASS |
| `UI-VAL-03` | Only focused companion, runner, Android route/isolation, build, and exact packaged gates ran. The parser corpus did not run. | PASS |
| Stage 1 portion of `UI-VAL-04` | Retained captures cover Trainer Card, Trainer Progress, Settings top, Settings danger/debug actions, and RetroArch Setup through the packaged APK. Map and Area Guide captures remain assigned to Plan Stage 2. | PASS |

## Red and green evidence

### Red baselines

- Audit packaged baseline: the Trainer switcher required `588px` in a `538px` viewport, leaving Progress unreachable; affected ROM-derived action contrast measured `1.43:1` and `1.17:1`.
- The focused touch-floor regression:

  ```bash
  npm test -- src/layoutStyles.test.ts -t 'keeps Stage 1 Settings and Setup actions at the touch floor'
  ```

  failed `1` test against the pre-correction `38px` Settings/Setup actions. The exact packaged audit then independently reproduced sub-floor Progress, Settings, and Setup targets before the authored floors were raised.

### Final focused gates

| Gate | Result |
| --- | --- |
| Nine focused Vitest files | `9/9` files, `97/97` tests passed |
| Party/Party Analysis/Specimens preservation | `3/3` files, `15/15` tests passed |
| Theme and compact-layout Playwright gate | `5/5` tests passed at the exact `538×445` test viewport |
| Production companion build | TypeScript and Vite passed; `39` modules transformed |
| Packaged-WebView runner tests and scenario validation | `13/13` tests passed; scenario accepted |
| Android debug and unsigned release assembly | `BUILD SUCCESSFUL`; no signing or release publication performed |
| Forced selected Android tests with `DUALDEX_QA_REQUIRE_ARTIFACTS=1 --rerun-tasks` | `35/35` tests passed, `0` failed, `0` errored, `0` skipped |

The browser gate used an equivalent temporary Playwright configuration on port `4187` because the configured `4175` port was already occupied. The temporary configuration was removed after the run.

## Exact packaged-APK evidence

Evidence root: `docs/reports/thor-ui-remediation/evidence/stage-01-packaged-webview/`

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

| Capture | Audited targets | Minimum target dimension | Minimum contrast | Privacy |
| --- | ---: | ---: | ---: | --- |
| [Trainer Card](evidence/stage-01-packaged-webview/trainer-card.png) | 3 | `47.995px` | `6.306:1` | safe |
| [Trainer Progress](evidence/stage-01-packaged-webview/trainer-progress.png) | 6 | `43.997px` | `6.306:1` | safe |
| [Settings](evidence/stage-01-packaged-webview/settings.png) | 5 | `43.997px` | `7.755:1` | safe |
| [Settings danger/debug](evidence/stage-01-packaged-webview/settings-danger.png) | 5 | `43.997px` | `8.473:1` | safe |
| [RetroArch Setup](evidence/stage-01-packaged-webview/setup.png) | 4 | `43.997px` | `7.755:1` | safe |

Every capture passed selector quotas, visual-stability checks, measured containment, touch overlap checks, screenshot budgets, authority re-attestation, and privacy classification. The report contains no ROM bytes, save bytes, memory bytes, credentials, personal details, or private paths.

## Blockers and referrals

- Blockers: None.
- Tracked referrals: None.
- Plan Stage 2 remains responsible for the already-specified `UI-MAP-*` and `UI-GUIDE-*` work and their Local Map/Area Guide captures; this is planned scope, not a deferred Stage 1 defect.
- Task #294 clean-start QA catalog provisioning remains separate and was not bundled.
- This checkpoint does not authorize an RC, signed artifact, stable promotion, PR, merge, or parser/compatibility work.
