# October 3 Library: Full 342-Identity Release Gate

**Decision:** PASS — completed 2026-10-04.

## Exact source and input authority

- Parser/catalog source: `f7214d1f644aa97f3bdfacddd3de8dcefdede9f5`.
- Report schema: 16; logical catalog digest version: 1; SQL schema: 2; parser cache revision: 83.
- Generator classpath SHA-256: `443fbe6bc6804a12480e66e6c318a2dcd500c574663c84e210c09fcf03ba2b97`.
- Raw-report SHA-256: `c2c7d04dc5c5fb688ee268492d34d897b0fd734562e1881941cae900ac6c9de9`.
- Input multiset SHA-256: `733e36785fa9a5b71bc4121343a6b6d2cbd27e43e08b3fddc2e2870bac1a0561`.
- All 342 retained source files and normalized parser identities were rehashed. The scan root contained exactly those 342 distinct identities, with no extra supported input.
- Every raw-report identity and normalized size matched the retained snapshot. The execution receipt, embedded executable source, raw-report digest and installed generator JAR classpath agree.

## Terminal and persistence evidence

| Gate | Result |
|---|---:|
| Inputs / distinct identities | 342 / 342 |
| Selected / ambiguous / no family match | 300 / 2 / 40 |
| Resolved / unknown language manifests | 285 / 15 |
| Proven multilingual manifests | 0 |
| Materialized / persisted / exactly reopened catalogs | 300 / 300 / 300 |
| Parser / catalog / persistence / decoded-reference errors | 0 / 0 / 0 / 0 |
| Catalog logical digests preserved | 300 / 300 |
| Bounded persistence recovery runs needed | 0 |

The raw data-compatibility classifier reports 332 `PARTIAL` and 10 `UNRESOLVED` inputs. This classifier is separate from family routing and is not a claim that all optional modules are complete. All 300 selected catalogs remain usable with explicit optional-capability limits.

## Comparison against retained evidence

The compared 342-row baseline combined 263 historical, 31 retained delta, 30 focused Gen I and 18 focused Gen II records. The same denominator remains 300 selected, 2 ambiguous and 40 unmatched, with no selection loss, family change, weighted-coverage decrease, capability downgrade, significant confidence loss beyond the older two-decimal rounding, or available catalog-counter decrease.

Fresh evidence records 44 rows with capability gains: 66 `PARTIAL` → `AVAILABLE` transitions and 8 `NOT_FOUND` → `AVAILABLE` transitions. Weighted coverage improves in 36 rows. These are fresh observations of already-published parser changes, not a new compatibility implementation batch.

Shared capabilities and the four native-language text columns were compared separately. Missing language authority never borrows text. A seeded available-species-name downgrade was detected by the comparison helper, demonstrating that omitted localized text cannot silently bypass regression checking.

## Cache and release scope

Production revision 83 rejects valid revision-82 caches and requires a rebuild. The seeded RED/GREEN rejection/rebuild regression passed with 75 successful catalog-store tests and 6 unavailable real-ROM skips; SQL schema remains 2.

The current release summary explicitly retains the earlier eight-test localization package proof at its actual historical source `5397f6e3b131cf0e15e16fff4f13b4e09761e46d`. It does not relabel that proof as current. The new candidate must separately pass the required exact-source GitHub-hosted packaged Android gate and signing workflow.

This gate covers static compiled ROM parsing, reference integrity and SQLite persistence. It does not claim local emulator, physical-device, live gameplay or controller validation. Five cache-only runtime/API/web controls are additional release acceptance, not a second ROM corpus parse. Historical evidence and the frozen 333-input authority remain unchanged.
