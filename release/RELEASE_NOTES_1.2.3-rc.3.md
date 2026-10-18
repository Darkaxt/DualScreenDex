# DualDex 1.2.3-rc.3

Release candidate for the full-corpus regression and RetroArch setup batch. Stable promotion remains subject to exact signed-candidate acceptance; this is not complete ROM compatibility.

## Changes

- Restore Unbound's seven lost encounter-backed maps. Its expanded graph retains 444 of 458 maps; fourteen lower-priority maps remain explicitly budget-limited under unchanged asset/pixel caps.
- Repair compiled ability consumers/static memberships, acquisition ownership and persisted ruleset references using source-backed, fail-closed evidence. Rejected historical tutor/machine heuristics are not restored as support.
- Distinguish selected config readback from RetroArch's active config and live endpoint. Require valid command status and restart verification, and explain persistence/upstream listener limitations plus manual-catalog fallback in all five interface languages.
- Fix a cancellation race that could deliver an interrupted guide-load callback before the session authority changed. The transition owner now retains completion ownership until invalidation, without calling back under runtime/task monitors.
- Fix the height-comparison artwork reset race and update affected dependency-security pins.
- Rebuild older catalogs with parser revision139, retaining SQLite schema2. First load after upgrading may regenerate a catalog cache.

## Qualification

- Fresh source-bound execution of all342 distinct current-library inputs:301 selected/two ambiguous/39 unmatched, zero parser/catalog/persistence errors.
- Independent audit of301 catalogs/5,403 sections/10,296,409 references. All corpus regression candidates are reconciled; no new corpus production regression remains open.
- Prior guarded host/security/lint, web/browser and actual final generated-catalog app binding checks passed; optional original-only host controls remain explicitly skipped.
- Exact frozen-source API35 hosted packaged Android acceptance:11 passed/zero failures/errors/skips; four screenshots inspected. Language authority is286 resolved/15 explicitly unknown catalogs. This proof retains its original source, not the new signed candidate's identity.
- Deterministic cancellation RED reproduces the ownership race in0.104 seconds; all95 focused runtime/session controls pass after repair, including stale-publication rejection, single completion and lock-order safeguards. The new candidate still requires the unchanged full protected host/package/signing gates.

RC1 was intentionally canceled before signing to repair stale promotion bookkeeping. RC2's hosted Android job succeeded but its host cancellation assertion failed, so signing was skipped. Both original tags/runs remain preserved. RC3 repairs that actual app race; parser/catalog/build/corpus-execution source and generated outputs are unchanged, with no extra corpus run or replacement tag.

RetroArch Android1.22.2's upstream missing listener is not repaired by file editing, and no installed RetroArch APK is claimed tested here. Family selection is not full optional-module support. Physical Thor has not been used for this qualification. Remaining Unbound improvements are assigned to the separate requested follow-up release; unfinished Pokescape work is not automatically resumed.
