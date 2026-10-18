# DualDex 1.2.3

Full-corpus regression fixes and clearer RetroArch setup, with the confirmed Unbound map regression repaired. This release does not claim complete compatibility with every ROM.

## Changes

- Restore Unbound's seven lost encounter-backed maps. The corrected expanded graph retains 444 of 458 maps; fourteen lower-priority maps remain explicitly budget-limited under unchanged pixel/asset caps.
- Repair compiled ability consumers/static memberships, acquisition ownership and persisted ruleset references using source-backed, fail-closed evidence. Rejected historical tutor/machine heuristics are not restored as support.
- Distinguish selected config-file readback from RetroArch's loaded configuration and live endpoint. Require valid command status and restart verification; explain persistence, upstream listener limitations and manual-catalog fallback in all five interface languages.
- Fix a cancellation race that could deliver an interrupted guide-load callback before session authority changed, and the height-comparison artwork reset race.
- Update affected dependency-security pins. Parser cache revision139 rebuilds older catalogs while keeping SQLite schema2; the first load after upgrading may regenerate a cache.

## Qualification and limits

- Fresh source-bound execution of all342 distinct current-library inputs:301 selected, two ambiguous,39 unmatched; zero parser/catalog/persistence errors. Independent audit covers301 catalogs/5,403 sections/10,296,409 references. All reported corpus regression candidates are reconciled.
- Required host/security/lint, web/browser and generated-catalog app binding checks passed; optional original-only host controls remain explicitly skipped. Language authority is286 resolved/15 explicitly unknown catalogs.
- Protected RC4 build, hosted Android and signing passed. All68 downloaded candidate assets, complete checksums, APK identity, pinned signer and source provenance were independently verified. Candidate-source Android acceptance passed11 tests with zero failures/errors/skips, and four screenshots were inspected.
- The user explicitly waived physical/signed-candidate device validation for this release. No Thor or signed-APK execution pass is claimed. Stable source differs from the verified RC4 only in enumerated release tooling, authorization and documentation; protected signing and final downloadable-asset verification still apply.

RetroArch Android1.22.2's upstream missing listener is not repaired by editing a config file; no installed RetroArch APK is claimed tested here. Family selection is not full optional-module support, and Unbound's99.87% composite result is not100% support or gameplay validation. Remaining Unbound improvements are assigned to a separate requested follow-up release; unfinished Pokescape work is not automatically resumed.
