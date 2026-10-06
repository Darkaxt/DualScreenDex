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
| PKW-01 | Pending Stage 1 | Fresh MAIN diagnostic admission and exact contract trace not yet issued/executed. |
| PKW-02 | Pending Stages 1–3 | Production changes prohibited until compiled/data authority and observed RED tests establish the gap. |
| PKW-03 | Pending Stages 1–4 | Correct target raster and semantic joins need independent original-bound proof. |
| PKW-04 | Pending Stages 2–3 | New boundary coverage and actual cancellation/isolation verification required. |
| PKW-05 | Pending Stages 1–4 | Retained public row is historical baseline only; fresh working-dataset and regression comparisons required. |
| PKW-06 | Pending Stages 3–4 | Cache revision depends on actual production change; full equality and references are mandatory. |
| PKW-07 | Pending Stage 5 | No gain published or claimed yet; target's unrelated missing datasets remain separate backlog. |
| PKW-08 | In progress | Existing canonical checkout and verified public source reused; no new original reads/builds/devices yet. Scratch registration, required gate evidence, private proof and applied cleanup still pending. |

**Blockers:** Stage 1 original admission/proof and all dependent acceptance gates remain open. These are expected pending gates, not a request for another implementation go-ahead.

**Required tracked deferrals:** None. Any later required gap must be assigned a named stage and verification method, then resolved before dependent/final closure.

**Separate compatibility backlog:** Existing `LIB26-MAPS` covers this target and other independently handled map targets. Pokescape's missing Dex text/move details, unresolved abilities and other optional capabilities are not implicitly included or closed by World Map recovery; record any newly diagnosed missing contract with a named target and acceptance condition before selecting its own round.

## Next action

Complete Stage 1 read-only resolver/source/test analysis, register one bounded scratch root, and review a new exact MAIN diagnostic admission. Keep execution IN_PROGRESS until the specification's acceptance, publication and cleanup gates are genuinely met or an actual authority/resource blocker requires a safe hold.
