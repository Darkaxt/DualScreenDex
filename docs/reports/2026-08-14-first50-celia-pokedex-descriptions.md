# First-50 Celia Pokédex-description closure — 2026-08-14

## Outcome

Celia's Stupid Romhack now resolves its compiled Gen III Pokédex-entry table.

- First-50 numeric compatibility average: **95.53% → 95.63%**.
- Celia numeric compatibility: **90.40% → 95.14%** (**19/21 → 20/21** applicable features).
- First-50 `POKEDEX_DESCRIPTIONS`: **35 available / 11 partial / 4 not found → 35 available / 12 partial / 3 not found**.
- Celia publishes **382 decoded descriptions across 384 navigable Pokédex species**. The two unsupported active rows remain unavailable rather than receiving external or fabricated text.
- Evolutions remain **50/50 available**. THUMB ability mechanics are unchanged and intentionally deferred.

## Structural authority

Production selection uses ROM-derived structure only:

- the source-defined published Gen III header supplies a bounded Pokédex count only when its fixed species-name, move-name, and sprite pointer roles are valid;
- the published count must be within the independently decoded species-name domain;
- compiled ROM references nominate description-table candidates;
- the typed codec validates all 386 records under the selected 36-byte ABI and description pointer at `+16`;
- the typed candidate must agree with the existing structural description validator;
- the independently compiled species-to-Dex mapping defines the navigable semantic domain;
- partial rows and out-of-domain internal slots stay unavailable.

No ROM name, SHA-256, source symbol, or absolute table offset participates in production selection. Exact identity and addresses exist only in the live regression.

The comparative source oracle is Celia's Stupid Repository commit `8b31f2472810f75571d122159d164467e149d4a8`. Its `PokedexEntry` layout and `NATIONAL_DEX_COUNT` agree with the independently decoded ROM table. The exact live ROM SHA-256 is `81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148`.

## Fail-closed behavior

- Retail and previously supported hack layouts retain their existing typed selection path.
- A published count without the required fixed header pointer roles is ignored.
- A count outside the independently decoded species-name domain is ignored.
- Typed discovery is used only when the existing structural description path fails.
- Typed and legacy structural selection must agree on root, count, and record width.
- The two non-decodable active rows remain missing; a partial table cannot erase otherwise valid species identities.

## Verification

- Implementation commit: `b5809db` (`Resolve compiled Pokedex descriptions`).
- Full non-THUMB parser regression: **996 tests, 0 failures, 0 errors**; 106 opt-in controls skipped.
- Focused live description, resolver, published-header, and one-pass architecture gate: **BUILD SUCCESSFUL**.
- Exact first-50: **50/50 SELECTED**, 0 ambiguous, 0 no-family, 0 errors.
- Exact identity/order: **50/50**; routing deltas: **0/50**; first-33 routing/reference deltas: **0/33**.
- Reference errors: **0/50**.
- Persistence: **50/50** catalogs written and reopened; each contains 12 sections.
- SQLite validation: **50/50** `quick_check=ok`; **0** foreign-key findings.
- Celia SQLite: **1,527,808 bytes**, 12 sections, reopened successfully.
- Raw JSON SHA-256: `ac10886a7bc3afb2f48c49202eb9bfb3011db0bb045cc14f43ca0377a7813d02`.
- Raw Markdown SHA-256: `639a99c9679be39eb906d045cad3894b0e95d399892387974a8f214ee85e9592`.

Only one exact-50 corpus pass was run after the focused implementation and controls.
