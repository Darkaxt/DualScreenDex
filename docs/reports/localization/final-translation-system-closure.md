# Full Translation System Closure Audit

**Decision:** `COMPLETE`

**Branch:** `feat/full-translation-system`

**Synchronized baseline:** `2fef59df95f6deb0ae861aad42cff52eff739a86` (`fork/master`)

**Specification:** `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`

## Six-stage delivery

| Stage | Delivered boundary | Evidence | Decision |
|---|---|---|---|
| 1 — Language authority | Extensible language evidence, raw headers, exact variable-width codecs, immutable authority, English compatibility | `stage-01-closure.md` | `COMPLETE` |
| 2 — Multi-projection catalog | Singular shared data, bounded overlays, manifests, persistence/reopen, cache/API default projection | `stage-02-closure.md` | `COMPLETE` |
| 3 — Western official parsing | English/French/German/Italian/Spanish official family-language matrix | `stage-03-closure.md` | `COMPLETE` |
| 4 — Japanese/Korean official parsing | Complete 43-cell/44-control matrix, exact codecs, capability-level fail-closed authority | `stage-04-closure.md`; `official-language-matrix.json` | `COMPLETE` |
| 5 — Runtime language selection | Bounded `LIVE_RAM` then `ROM_DEFAULT`, fenced projection publication, zero reparse, semantic API messages | `stage-05-closure.md` | `COMPLETE` |
| 6 — Interface translation | Device-global `AUTO/en/fr/de/it/es`, typed web dictionaries, Android resources, production chrome/accessibility, pseudo-locale, compact browser matrix, packaged WebView/native acceptance | `stage-06-closure.md` | `COMPLETE` |

All implementation checkpoints through `5397f6e3` and corpus/recovery evidence through `8f66f78d` are present on `fork/feat/full-translation-system`. This final packaged-acceptance checkpoint follows a fresh smart-sync before publication.

## Invariant audit

| Invariant | Result | Evidence |
|---|---|---|
| `LNG-INV-001` ROM authority | Pass | Official matrix binds compiled-ROM structural/text evidence; no filename, path, hash, or fixed per-ROM production selector. |
| `LNG-INV-002` read-only operation | Pass | Parser/runtime controls read external inputs; no ROM or SaveRAM mutation path was introduced. |
| `LNG-INV-003` parse once, select many | Pass | Persisted projections switch through runtime authority with zero parser invocations. |
| `LNG-INV-004` separate authorities | Pass | ROM-content language is catalog/runtime authority; interface language is device-global presentation state. |
| `LNG-INV-005` singular shared data | Pass | Shared numeric/geometry/media data remains one catalog with localized overlays. |
| `LNG-INV-006` capability-level fail closed | Pass | Unsupported or ambiguous text is withheld without discarding independently validated non-text data. Every retained POI stays in the `POI_TEXT` denominator. |
| `LNG-INV-007` official first, extensible later | Pass | 43 official cells are ratified through generic registries/codecs; `LNG-D001` tracks later corpus expansion. |
| `LNG-INV-008` UI last | Pass | Interface translation followed parser, persistence, official matrix, runtime authority, and semantic API closure. |
| `LNG-INV-009` no untracked gaps | Pass | `LNG-B004` and `LNG-B005` are closed. `LNG-D001`–`LNG-D004` remain explicit post-system expansions with owners, targets, fail-closed dispositions, and acceptance conditions. |

## Authority and data-flow audit

- Official ROM content is decoded only by the structurally selected exact codec. Native names and descriptions remain active-overlay values and are never browser-translated.
- `LIVE_RAM` can select only a proven persisted projection. Missing, stale, disconnected, invalid, or unsupported live evidence immediately resolves to parser-proven `ROM_DEFAULT`; no last-observed offline authority survives.
- Interface changes persist device-global settings, update presentation state, and synchronize the document locale. They do not select ROM content, modify cache identity, start/cancel parsing, or publish parser work.
- UI-consumed backend outcomes use typed presentation codes and arguments. Route values, control IDs, enum values, protocol values, and focus identities remain stable under translation.
- G3 cache/API captures prove projection and persistence consistency only. They do not establish G4 linguistic or semantic truth.

## Acceptance summary

### Official parser matrix

The public Stage 4 matrix remains the official authority: **43/43 family-language cells**, **44/44 exact controls**, **308/308 mandatory checks**, and **660/660 capability dispositions** with zero matrix blockers. Japanese and Korean ROM-content parsing is complete even though their interface packs remain post-system work.

### Interface matrix

Each of English, French, German, Italian, and Spanish implements the same **713-message typed web contract**, **19-key main Android contract**, and **12-key debug Android contract**. The test-only pseudo-locale expands strings without becoming a persisted production value.

The six-locale × three-scale browser matrix at **538×445 CSS pixels** is accounted for by the initial run and exact failed-cell reruns. All affected cells pass containment, accessibility, control reachability, focus restoration, scroll ownership, readability, and virtual-row geometry. A fresh final Android debug build also succeeds.

### Consolidated host suites

`companion-core` completed 132 cases with zero failures/errors. The app suite completed 673 cases with 15 failures: three unrelated legacy control-inventory assertions and twelve missing-private-environment guards that stop before reading a control. These failures are reported accurately and are not localization blockers or deferrals. The genuine debug-resource lint gap found afterward was corrected; its focused parity test and full Android lint pass.

### Packaged Android acceptance

The packaged gate ran on the configured API 35 x86_64 Gradle Managed Device. The first aggregate run exposed an invalid localization-test assumption: a clean no-catalog launch cannot render the `POKÉDEX` control. The pre-existing packaged acceptance passed unchanged in isolation. The localization test was corrected to require `html.lang === 'es'` and the Spanish clean-start upload control, verify Spanish native recovery, then await completed `AUTO` restoration before releasing the scenario.

The focused localization class passed in 6.881 seconds. The final exact `:app:qaApi35DebugAndroidTest` run completed **8/8 tests**, zero failures/errors/skips, in 30.625 seconds of test time and ended `BUILD SUCCESSFUL` in 1 minute 10 seconds. The result XML SHA-256 is `cc6d9617ccfa58c0829f99c846a7c76697a3712cfc65d0749ebf5dc004abd88a`. The managed emulator stopped after the run; a read-only inventory showed only the separately owned physical Thor serial, which was not modified or used.

### Final source-bound corpus

The one consolidated post-Stage-6 corpus binds executable source `5397f6e3b131cf0e15e16fff4f13b4e09761e46d`, generator `8de5fada53efdd248b02f36cc9a6e86c40cfaf0da1d9e8e90795704b80bb3017`, report schema 16, receipt schema 1, and 333 current eligible inputs.

Outcomes are **276 selected**, **2 ambiguous**, and **55 no-family-match**, with **261 resolved** and **15 unknown** selected language manifests. Parser-result and catalog/reference errors are zero. Of 276 selected rows, 274 completed persistence in the final corpus with equal before/after logical digests. Two otherwise-selected rows failed persistence closed with `OutOfMemoryError: Java heap space` under the six-GiB/eight-worker process.

Those two warning-adjacent rows were then verified individually and serially with `--jobs 1`, the same six-GiB ceiling, the same parser CLI generator, and the same exact source commit. Each bounded one-input report selected the same family, produced one successful SQLite cache, bound its receipt to its exact report, recorded zero parser/catalog/persistence/reference errors, and had equal non-empty before/after logical digests. This did not repeat the 333-input corpus. Combined evidence now accounts for successful persistence/reopen of all **276 selected rows**. Hashes and detailed classification are in `stage-06-closure.md`; `LNG-B005` is closed.

## Blocker and deferral audit

| ID | Type | Owner / target | Current fail-closed disposition | Acceptance |
|---|---|---|---|---|
| `LNG-B004` | `STOP-CORE` acceptance gate | Stage 6 packaged-AVD acceptance | Closed after correcting the no-catalog Spanish sentinel and awaiting `AUTO` restoration; no timeout was increased. | Closed: focused localization and final 8/8 packaged instrumentation pass on the configured managed device. |
| `LNG-B005` | `STOP-CORE` evidence gap | Final-system acceptance | Both exact heap-exhausted rows failed closed in the full run; each subsequently passed a source-bound, jobs-one persistence/reopen verification. | Closed: combined evidence accounts for all 276 selected rows with matching logical digests without repeating the full corpus. |
| `LNG-D001` | `POST-SYSTEM` | First post-official corpus stage | No universal fan-translation/hack-text claim. | Generic source-backed manifests/codecs pass sanitized corpus evidence without identity hacks. |
| `LNG-D002` | `POST-SYSTEM` | Post-Stage-6 interface expansion | Japanese/Korean ROM content remains native; unsupported interface locales follow `AUTO`/English fallback. | Complete typed/native packs pass font, line-break, accessibility, and compact-layout review. |
| `LNG-D003` | `POST-SYSTEM` | First supported RTL locale | The initial production locale set is explicitly LTR. | Direction-aware layout/navigation/icons and bidirectional compact tests pass. |
| `LNG-D004` | `POST-SYSTEM` | Separately commissioned documentation/release milestone | Non-production material remains English. | Locale set, owner, review process, and publication path are defined and delivered. |

No implementation work is silently deferred. `LNG-B004` and `LNG-B005` are closed by packaged managed-device and bounded persistence evidence respectively. `LNG-D001`–`LNG-D004` remain explicit post-system expansion items and do not reopen the completed six-stage system.

## Privacy, publication, and operational boundary

The complete branch-delta audit found no committed ROM, SaveRAM, SQLite, APK, archive, keystore, signing material, raw private memory, or corpus artifact. Public reports contain only aggregate counts, hashes, typed decisions, and source provenance. Raw controls, reports, cache databases, paths, and private evidence remain outside Git. Production signing remains GitHub Actions protected-environment work and was not invoked.

The configured Gradle Managed Device was used only for the packaged acceptance gate and shut down afterward. The physical Thor was only visible in a read-only post-run inventory and was not used or modified. No production signing, tag, release, or pull request is part of this audit.

## Final decision

`COMPLETE` — The six-stage translation architecture, Stage 6 host and packaged implementation, final managed-device acceptance, and complete selected-row persistence evidence are delivered. All system blockers are closed; remaining ledger items are separately scoped post-system expansions.
