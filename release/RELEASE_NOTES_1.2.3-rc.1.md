# DualDex 1.2.3-rc.1

Release candidate for the full-corpus regression and RetroArch setup batch. Stable promotion remains subject to signed-candidate acceptance; this candidate is not a claim of complete ROM compatibility.

## Changes

- Restore Unbound's seven lost encounter-backed maps while retaining its expanded map graph: 444 of 458 maps are available; fourteen lower-priority maps remain explicitly budget-limited. Existing pixel/asset limits are unchanged.
- Repair compiled ability consumers and static ability memberships, acquisition ownership and persisted ruleset references with source-backed, fail-closed validation. Historical false-positive tutor/machine heuristics are not restored as support.
- Make RetroArch setup truthful about selected versus active config paths, persistence and restart verification. Only a valid live command status verifies the endpoint; config-file readback alone does not. All five interface languages explain upstream listener limitations and the manual-catalog fallback.
- Fix the height-comparison artwork reset race and update affected dependency-security pins.
- Rebuild old catalog caches using parser revision 139; SQLite schema remains 2. The first catalog load after upgrading may regenerate cached data.

## Qualification and limits

- Fresh source-bound evaluation of all 342 distinct current-library inputs: 301 selected, 2 ambiguous, 39 unmatched; zero parser/catalog/persistence errors. Family selection is not full optional-module support.
- Independent audit of 301 catalogs, 5,403 sections and 10,296,409 references; all regression candidates reconciled, with no new production regression left open.
- Guarded host/security/lint, production web/browser and actual final generated-catalog app binding checks pass. Original-only host cases remain explicitly skipped, not counted passing.
- Exact frozen-source hosted API35 packaged Android acceptance passes all 11 tests, zero failures/errors/skips; required screenshots inspected. Physical Thor was not used for this qualification.
- Current language manifests: 286 resolved, 15 explicitly unknown. No shared-text backfill or inferred multilingual support.

RetroArch Android 1.22.2's upstream missing-listener build issue is not repaired by changing a config file, and no installed RetroArch APK is claimed tested here. Unbound's remaining issues will be addressed in the separately requested follow-up release. Unfinished Pokescape work is not automatically resumed.

See [current release qualification](https://github.com/Darkaxt/DualScreenDex/blob/feat/pokescape-complete-catalog/docs/reports/corpus/2026-10-18-release-qualification.md) for evidence and bounded support claims.
