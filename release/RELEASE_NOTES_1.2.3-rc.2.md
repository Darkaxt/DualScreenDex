# DualDex 1.2.3-rc.2

Release candidate for the full-corpus regression and RetroArch setup batch. Stable promotion remains subject to exact signed-candidate acceptance; this is not complete ROM compatibility.

## Changes

- Restore Unbound's seven lost encounter-backed maps. Its expanded graph retains 444 of 458 maps; fourteen lower-priority maps remain explicitly budget-limited under unchanged asset/pixel caps.
- Repair compiled ability consumers/static memberships, acquisition ownership and persisted ruleset references using source-backed, fail-closed evidence. Rejected historical tutor/machine heuristics are not restored as support.
- Distinguish selected config readback from RetroArch's active config and live endpoint. Require valid command status and restart verification, and explain persistence/upstream listener limitations plus manual-catalog fallback in all five interface languages.
- Fix the height-comparison artwork reset race and update affected dependency-security pins.
- Rebuild older catalogs with parser revision139, retaining SQLite schema2. First load after upgrading may regenerate a catalog cache.

## Qualification

- Fresh source-bound execution of all342 distinct current-library inputs:301 selected/two ambiguous/39 unmatched, zero parser/catalog/persistence errors.
- Independent audit of301 catalogs/5,403 sections/10,296,409 references. All regression candidates are reconciled; no new production regression remains open.
- Guarded host/security/lint, web/browser and actual final generated-catalog app binding checks pass; optional original-only host controls remain explicitly skipped.
- Exact frozen-source API35 hosted packaged Android acceptance:11 passed/zero failures/errors/skips; four screenshots inspected. Language authority is286 resolved/15 explicitly unknown catalogs.

RC1 was intentionally canceled during pre-build verification after a second stale eight-test-only assertion was found in promotion tooling. RC2 aligns both evidence paths with the known eight/eleven-case profiles, retaining every source/hash/zero-gap and signed-promotion requirement. No app/parser/catalog production code changes relative to RC1, no corpus rerun, no tag replacement and no claim that canceled RC1 was signed or accepted.

RetroArch Android1.22.2's upstream missing listener is not repaired by file editing, and no installed RetroArch APK is claimed tested here. Family selection is not full optional-module support. Physical Thor has not been used for this qualification. Remaining Unbound improvements are assigned to the separate requested follow-up release; unfinished Pokescape work is not automatically resumed.
