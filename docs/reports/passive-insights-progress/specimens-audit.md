# Stage 4 audit — Pokédex Specimens

Date: 2026-08-27  
Specification: `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`  
Numeric evidence: `docs/reports/passive-insights-progress/specimens-compatibility.json`

## Result

Stage 4 has **0 blockers** and **0 errors**. A caught Pokédex entry now enumerates every decoded Party and PC individual for its canonical species, opens the same individual-detail component as Party, and returns through the immediate navigation parent without adding a standalone PC or Bag page. `UnifiedGameStateDecoder` remains the only live/recovery merger. Live PC bytes extend the existing Gen III read plan, and exact SaveRAM fills storage only when live storage is unavailable.

The exact 14-control evidence reports:

| Measurement | Covered | Total | Percent |
| --- | ---: | ---: | ---: |
| Applicable specimen fields | 148 | 178 | 83.15% |
| Storage acquisition and integrity sources | 77 | 84 | 91.67% |
| Official Gen I applicable fields | 18 | 30 | 60.00% |
| Official Gen II applicable fields | 18 | 36 | 50.00% |
| Official Gen III applicable fields | 70 | 70 | 100.00% |
| Modern Emerald, Unbound, and Odyssey applicable fields | 42 | 42 | 100.00% |

Every control resolves identity, canonical species/form, level, IV/DV values, rarity, and Party/box location: **84/84 fields (100.00%)** across those six families. Generation-defined nature and ability fields resolve **16/16 (100.00%)**. Nickname, HP/status, experience, and moves resolve **8/14 each (57.14%)** because the current Gen I/II record projection does not expose those fields. Those are explicit `NOT_FOUND` results, not substituted values.

## Specification cross-check

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | --- | --- |
| Section 3: one transient-data interface and no ordinary-page diagnostics | `UnifiedGameStateDecoder` publishes one `ResolvedOwnedStorageState`; the API consumes the resolved snapshot and emits player-facing names, values, and locations only. | Unified decoder, API view, runtime, and browser tests reject unavailable/corrupt data without exposing addresses, parser stages, or provenance copy. | The compatibility artifact contains only repository-relative evidence references and sanitized ROM identities. | No feature-specific save reader, live mapper, or diagnostic UI was added | SATISFIED |
| Section 8.1: Pokédex entry and no standalone storage page | `PokedexDetail` exposes `VIEW SPECIMENS` only when the selected caught species has at least one decoded owned record; `SpecimensPage` is reached from `MORE`. | Browser tests prove the action is absent for a caught flag with zero records and present for decoded records. | The rule is independent of ROM family and consumes the same canonical species projection for all controls. | PC ownership is useful inside the existing Pokédex flow without another top-level page | SATISFIED |
| Section 8.2: unified owned model and validated fields | `ResolvedOwnedStorageState`, `ResolvedStorageBox`, and `ResolvedStorageSlot` retain source authority and exact location; `OwnedIndividualView` exposes only nullable validated record fields. | Model invariants, Gen III record checksum/encryption tests, Gen I/II save-reader tests, API projection tests, and detail rendering tests cover fields and invalid records. | 148/178 applicable fields are resolved independently across the exact 14 controls. | Missing record fields remain absent; no retail or catalog value is presented as individual state | SATISFIED |
| Section 8.3 and 13: live/recovery authority, identity, empty replacement, and deduplication | Live storage wins over recovery; validated empty live storage replaces recovery; unsupported live storage may use exact recovery; Party is removed from box projection by individual identity or validated-record digest. | Decoder tests cover every authority case. The movement test proves Party-to-PC-to-Party retains one card and one stable key when the record supplies identity. | Gen III layouts are derived from exact official and source-backed ROM controls; Gen I/II boxes use their validated exact SaveRAM readers. | Recovery cannot append to validated live state or invent duplicates | SATISFIED |
| Section 8.4: Organic knowledge boundary | Specimens are created only from owned Party/PC records already accepted into the active resolved snapshot. Species, moves, ability, nature, and destination links reuse established knowledge-safe detail components. | API tests prove unrelated species and caught-only flags create no cards; existing Organic evolution, encounter, opponent-move, and detail tests remain in the full browser suite. | The report credits no encounter, opponent, or future-evolution source as specimen evidence. | Owned details are visible; undiscovered catalog entities are not introduced | SATISFIED |
| Section 8.5: records, malformed data, and required controls | Gen I/II readers validate generation-specific structure/checksum rules. Gen III decrypts and checks every 80-byte boxed record; incomplete storage, invalid species, corrupt checksum, impossible bounds, and unresolved layouts fail closed. | Parser, save, battle-memory, live-decoder, and API tests cover empty/partial/corrupt/unsupported cases. | Red, Blue, Yellow, Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen, Modern Emerald, Unbound, and Odyssey are keyed by exact SHA-256. | 14/14 identities evaluated; report errors 0 | SATISFIED |
| Section 12: immediate Back, remembered list position, theme, and accessibility | Specimen list state is owned by the existing app route; individual detail is the shared Party component; move, nature, ability, and species links use the navigation stack. Cards and dialogs use real labelled buttons and established theme/layout classes. | Navigation tests cover the specimen route and child destinations; browser tests restore the recorded scroll position and assert accessible button/dialog names. | The same production 4:3 component contract applies to every ROM identity. | Detail does not skip its specimen parent and the list resumes at its prior position | SATISFIED |
| Section 14: bounded reads and replacement projections | `Gen3LiveMemoryReader` adds one bounded storage region to its existing read request and fingerprints that immutable section. It does not introduce another loop, whole-memory buffer, or append-only copy. | Layout constructors reject incomplete descriptors, dual addressing, overflow, and invalid shape; reader tests verify pointer/direct bounded windows and replacements. | Official/source-backed ROM controls expose 14 or 15 boxes of 30 records with the proven 80-byte ABI. | Polling frequency is unchanged and storage refresh stops at the validated footprint | SATISFIED |
| Sections 15 and 17: numeric coverage and fail-closed audit | `specimens-compatibility.mjs` validates the authoritative identity manifest, exact evidence tuple, complete field/source schema, 0/1/null evidence states, and safe evidence references. | Reporter tests reject missing, duplicate, mismatched, malformed, and incomplete controls. | 14 controls, 178 applicable fields, 84 source slots, and 0 report errors. | Numeric percentages preserve `NOT_FOUND` and `NOT_APPLICABLE` separately | SATISFIED |

## Per-field compatibility

| Field | Covered | Applicable | Percent | Not found | Not applicable |
| --- | ---: | ---: | ---: | ---: | ---: |
| Identity | 14 | 14 | 100.00% | 0 | 0 |
| Species / form | 14 | 14 | 100.00% | 0 | 0 |
| Level | 14 | 14 | 100.00% | 0 | 0 |
| Nickname | 8 | 14 | 57.14% | 6 | 0 |
| Gender | 8 | 11 | 72.73% | 3 | 3 |
| HP / status | 8 | 14 | 57.14% | 6 | 0 |
| Experience | 8 | 14 | 57.14% | 6 | 0 |
| Nature | 8 | 8 | 100.00% | 0 | 6 |
| Ability | 8 | 8 | 100.00% | 0 | 6 |
| Held item | 8 | 11 | 72.73% | 3 | 3 |
| Moves | 8 | 14 | 57.14% | 6 | 0 |
| IV / DV | 14 | 14 | 100.00% | 0 | 0 |
| Rarity | 14 | 14 | 100.00% | 0 | 0 |
| Storage location | 14 | 14 | 100.00% | 0 | 0 |

## Explicit non-coverage

- Official Gen I live PC storage: **0/3 controls (0.00%) — 3 `NOT_FOUND`**. Validated Gen I exact SaveRAM supplies Party and PC records; the feature adds no unproven WRAM bank switch or second poller.
- Official Gen II live PC storage: **0/3 controls (0.00%) — 3 `NOT_FOUND`**. Validated Gen II exact SaveRAM supplies Party and PC records under the same authority rule.
- Unbound exact SaveRAM PC recovery: **0/1 control (0.00%) — 1 `NOT_FOUND`** because the only supplied control is erased flash. Its source-backed live storage layout is resolved and fails closed if the live bytes do not validate.
- Gen I nickname, HP/status, experience, and moves: **0/12 applicable fields (0.00%) — 12 `NOT_FOUND`**. Gen I gender, nature, ability, and held item are **12/12 `NOT_APPLICABLE`**.
- Gen II nickname, gender, HP/status, experience, held item, and moves: **0/18 applicable fields (0.00%) — 18 `NOT_FOUND`**. Gen II nature and ability are **6/6 `NOT_APPLICABLE`**.

These states do not block the Stage 4 user contract: each decoded owned record still has identity, species/form, level, DV/IV rarity, and Party/box location, while unresolved optional record fields remain visibly absent. They are not deferred work and do not enable guessed data.

## Verification commands

```text
node --test tools/reports/specimens-compatibility.test.mjs
./gradlew :parser-core:test :parser-cli:test :catalog-store:test :save-core:test :battle-memory:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain
npm test -- --run
npm run build
```

Observed results: report transformer 2/2; affected JVM/Android suites 1,836 tests with 0 failures and 0 errors; browser suite 220/220 across 30 files; production TypeScript/Vite build successful.
