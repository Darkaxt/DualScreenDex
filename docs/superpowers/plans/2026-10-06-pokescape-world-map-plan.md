# Pokescape World Map staged plan

**Status: IN_PROGRESS. Authorization: already_authorized.** Specification: [authoritative design](../specs/2026-10-06-pokescape-world-map-design.md). One target ROM per round; controls are regression-only. No new source worktree, agents or Workflows.

## Stages

| Stage | Objective and dependencies | Spec coverage | Intended changes | Required verification |
|---|---|---|---|---|
| 1 — Exact contract diagnosis | Pin source baseline and public oracle; review a new bounded MAIN target diagnostic admission. | PKW-01, PKW-02, PKW-03, PKW-05, PKW-08 | Read compiled resolver/compositor/semantic consumers and existing tests; identify the exact target failure. Register scratch before generation. No guessed production fix. | Immutable producer/source/input pins and consumed-once diagnostic receipt; bounded trace; minimal independent fixture reproducing the failed contract. |
| 2 — Deterministic RED boundaries | Turn the diagnosed contract into focused positive and adversarial tests. Depends on Stage 1's exact proof. | PKW-02, PKW-03, PKW-04, PKW-05 | Extend/create narrow compiled-loader/composition/join fixtures in the existing test packages; assert relocation, conflicts, malformed data, cancellation and isolation. | Fresh observed failing focused tests through the installed host gate; failures are attributable to the intended missing contract, not fixture/build defects. |
| 3 — Generic implementation and host GREEN | Implement only the proven contract. Depends on valid RED evidence. | PKW-02, PKW-03, PKW-04, PKW-05, PKW-06, PKW-08 | Small idiomatic resolver/codec/semantic changes, extracting a focused helper if necessary; parser schema revision only for changed production behavior; retain current optional fail-closed semantics. | Fresh affected parser/catalog and full CLI host tests, observed gate/profile, legacy-format regressions and inline adversarial review. Smart-sync exact source checkpoint for packaged-CLI stamping; explicitly not real-input completion. |
| 4 — One-target production acceptance | Evaluate the committed/stamped production CLI under a fresh MAIN admission; justify any separately admitted regression controls. Depends on Stage 3. | PKW-01, PKW-03, PKW-05, PKW-06, PKW-08 | Private independent raster/geometry/semantic oracle and complete catalog/reference/SQLite comparison; fix and retest any acceptance defect rather than weakening the spec. | Exact target identity and source/runtime/report bindings; complete references; unchanged working core/Local Map; correct World Map assets and joins; full SQLite write/reopen equality and logical digests. No library-wide run. |
| 5 — Final remediation, publication and cleanup | Close every required gap, reconcile the entire spec, publish sanitized acceptance and clean actual outputs. Depends on Stage 4. | PKW-01 through PKW-08 | Update only verified affected matrix evidence and named ledger items; retain compact private proof; smart-sync commit/push; inspect/ticket/apply exact disposable scratch/build outputs. | Zero blockers and zero required tracked deferrals; public commit/push verification; immutable private receipts; applied cleanup and absence checks; canonical source clean. |

## Cumulative reconciliation

| Requirement | Status | Evidence / pending action |
|---|---|---|
| PKW-01 | Stage 1 satisfied; Stage 4 pending | Fresh MAIN diagnostic and baseline reservations consumed separately. Baseline reaches asset composition but fails the region-entry join. A compiled guarded eight-byte name consumer proves the table's ordinal bound; one encounter section exceeds it. Exact public asset equality was **not** established and is not used as pixel authority. Relocated source-independent fixture reproduces rejection. |
| PKW-02 | Stage 3 host satisfied; final review pending | New bounded-consumer helper proves compare/BHI, literal root, row stride, name-field displacement and indirect read with register flow. Complete indexed sites and whole table extent required; conflicts, incomplete evidence and nomination exhaustion reject. Existing shell/text checks and anchor thresholds retained. |
| PKW-03 | Stage 4 pending | No original-bound raster/semantic acceptance claimed. Affine/tiled/text synthetic exact-pixel regressions pass; target asset/geometry/reference proof remains mandatory. |
| PKW-04 | Stage 3 host satisfied | Fresh malformed pointer/row/name, extent/32-nomination budget and cancellation cases pass. Optional catalog regression suite passes; no new catch or swallowed cancellation. |
| PKW-05 | Stage 4 pending | Fresh baseline matches historical working core counters and retains 520 Local Maps. Focused legacy synthetic regressions pass; final target before/after and complete-reference comparisons pending. No real control input needed for unchanged raster code yet. |
| PKW-06 | Stage 3 host satisfied; Stage 4 pending | Parser revision 87, SQL unchanged at 2. Host results: parser 63/63; storage 119 cases, zero failures/six opt-in skips; CLI 76 cases, zero failures/one opt-in skip. Exact committed-source packaging and target reopening still pending. |
| PKW-07 | Stage 5 pending | No gain published or claimed yet; target's unrelated missing datasets remain separate backlog. |
| PKW-08 | In progress | Two exact cleanup transactions register scratch/compiler/report roots; compact RED/diagnostic admissions retained privately. Each required Gradle call acquired the installed gate, two workers/no parallel, requested Gradle/Kotlin 3 GB, one 512 MB test fork; no memory failure. Kotlin strategy preserved. No agents/devices/other original inputs. Cleanup pending. |

**Blockers:** Stage 4 exact-source target raster/geometry/reference/persistence acceptance and Stage 5 publication/cleanup remain open. Stages 1–3 are reconciled at their host boundaries; this source checkpoint is not real-input feature completion.

**Required tracked deferrals:** None. Any later required gap must be assigned a named stage and verification method, then resolved before dependent/final closure.

**Separate compatibility backlog:** Existing `LIB26-MAPS` covers this target and other independently handled map targets. Pokescape's missing Dex text/move details, unresolved abilities and other optional capabilities are not implicitly included or closed by World Map recovery; record any newly diagnosed missing contract with a named target and acceptance condition before selecting its own round.

## Next action

Stage 2 observed four failures in eight valid regression tests before implementation. Stage 3's first expanded run exposed one **fixture** with a second compiled site omitted from its declared complete reference set; the fixture was corrected without changing production evidence requirements. Fresh 63-case parser GREEN and complete host storage/CLI suites followed. Inline review checked register preservation, signed branch math, pointer/extent bounds, candidate-local references, conflicts, cancellation and unchanged legacy fallback. No original pixel correspondence is inferred from the checked public source.

Smart-sync and publish the coherent source checkpoint, rebuild/stamp the packaged CLI at that exact commit, then issue a fresh one-target MAIN acceptance reservation. Independently validate compiled raster/semantic consumers and all decoded references/persistence before updating the matrix. Keep execution IN_PROGRESS until publication and actual cleanup close.
