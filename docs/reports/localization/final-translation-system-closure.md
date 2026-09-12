# Full Translation System Closure Audit

**Decision:** `IMPLEMENTATION_COMPLETE — FINAL_ACCEPTANCE_PENDING`

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
| 6 — Interface translation | Device-global `AUTO/en/fr/de/it/es`, typed web dictionaries, Android resources, production chrome/accessibility, pseudo-locale, compact browser matrix | `stage-06-closure.md` | `HOST_COMPLETE — PACKAGED_AVD_PENDING` |

All implementation checkpoints through `3cba2c42` are present on `fork/feat/full-translation-system`. The final documentation checkpoint is committed and pushed separately after smart-sync.

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
| `LNG-INV-009` no untracked gaps | Pending final acceptance | `LNG-B004`, `LNG-B005`, and `LNG-D001`–`LNG-D004` are explicit in the ledger with owners, targets, fail-closed dispositions, and acceptance conditions. |

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

### Final source-bound corpus

The one consolidated post-Stage-6 corpus binds executable source `5397f6e3b131cf0e15e16fff4f13b4e09761e46d`, generator `8de5fada53efdd248b02f36cc9a6e86c40cfaf0da1d9e8e90795704b80bb3017`, report schema 16, receipt schema 1, and 333 current eligible inputs.

Outcomes are **276 selected**, **2 ambiguous**, and **55 no-family-match**, with **261 resolved** and **15 unknown** selected language manifests. Parser-result and catalog/reference errors are zero. Of 276 selected rows, 274 completed persistence in the final corpus with equal before/after logical digests. Two otherwise-selected rows failed persistence closed with `OutOfMemoryError: Java heap space` under the six-GiB/eight-worker process.

Those two warning-adjacent rows were then verified individually and serially with `--jobs 1`, the same six-GiB ceiling, the same parser CLI generator, and the same exact source commit. Each bounded one-input report selected the same family, produced one successful SQLite cache, bound its receipt to its exact report, recorded zero parser/catalog/persistence/reference errors, and had equal non-empty before/after logical digests. This did not repeat the 333-input corpus. Combined evidence now accounts for successful persistence/reopen of all **276 selected rows**. Hashes and detailed classification are in `stage-06-closure.md`; `LNG-B005` is closed.

## Open blocker and deferral audit

| ID | Type | Owner / target | Current fail-closed disposition | Acceptance |
|---|---|---|---|---|
| `LNG-B004` | `STOP-CORE` acceptance gate | Stage 6 packaged-AVD acceptance | No packaged WebView/native runtime acceptance is claimed. | The packaged localization instrumentation passes under the authorized exact emulator boundary and restores `AUTO`. |
| `LNG-B005` | `STOP-CORE` evidence gap | Final-system acceptance | Both exact heap-exhausted rows failed closed in the full run; each subsequently passed a source-bound, jobs-one persistence/reopen verification. | Closed: combined evidence accounts for all 276 selected rows with matching logical digests without repeating the full corpus. |
| `LNG-D001` | `POST-SYSTEM` | First post-official corpus stage | No universal fan-translation/hack-text claim. | Generic source-backed manifests/codecs pass sanitized corpus evidence without identity hacks. |
| `LNG-D002` | `POST-SYSTEM` | Post-Stage-6 interface expansion | Japanese/Korean ROM content remains native; unsupported interface locales follow `AUTO`/English fallback. | Complete typed/native packs pass font, line-break, accessibility, and compact-layout review. |
| `LNG-D003` | `POST-SYSTEM` | First supported RTL locale | The initial production locale set is explicitly LTR. | Direction-aware layout/navigation/icons and bidirectional compact tests pass. |
| `LNG-D004` | `POST-SYSTEM` | Separately commissioned documentation/release milestone | Non-production material remains English. | Locale set, owner, review process, and publication path are defined and delivered. |

No implementation work is silently deferred. `LNG-B005` is closed by the bounded recovery evidence. `LNG-B004` alone prevents a truthful full-system `COMPLETE` decision; it does not reopen the delivered Stage 1–5 architecture or Stage 6 host implementation.

## Privacy, publication, and operational boundary

The complete branch-delta audit found no committed ROM, SaveRAM, SQLite, APK, archive, keystore, signing material, raw private memory, or corpus artifact. Public reports contain only aggregate counts, hashes, typed decisions, and source provenance. Raw controls, reports, cache databases, paths, and private evidence remain outside Git. Production signing remains GitHub Actions protected-environment work and was not invoked.

No emulator, physical device, ADB operation, installation, production signing, tag, release, or pull request is part of this audit. The next packaged-AVD command crosses the explicit live Android pause boundary and has not been run.

## Final decision

`IMPLEMENTATION_COMPLETE — FINAL_ACCEPTANCE_PENDING` — The six-stage translation architecture, Stage 6 host implementation, and final corpus persistence evidence are delivered. Full-system completion is not claimed until the packaged-AVD gate receives its separately authorized acceptance outcome.
