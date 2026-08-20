# Gen I compiled-table coverage checkpoint

This report measures the generic Gen I evolution, learnset, and type-chart resolvers introduced by the commit containing this file. It contains aggregate coverage and public project names only; it contains no ROM bytes, decoded assets, saves, or private filesystem paths.

Baseline: `v1.1.0-rc.18`. Candidate: the commit containing this report.

## Method

The baseline and candidate use the same 95-ROM GB/GBC denominator from the published Gen I–III table report. Three GBA inputs present in the historical Gen I inventory are excluded. Each ROM has equal weight. An `AVAILABLE` table contributes 100%, a usable `PARTIAL` table contributes `coveredRecords / expectedRecords`, and a `NOT_FOUND` table contributes 0%.

Public source was used only to identify compiled consumer shapes. Production authority remains the raw ROM: the parser scans compiled instructions, resolves bank-local roots, validates the pointed data, requires one unique surviving layout, and fails closed on absence or ambiguity. Exact official profiles remain authoritative.

## Coverage

| Table | Baseline | Candidate | Gain | Candidate statuses |
|---|---:|---:|---:|---|
| Evolutions | 12.43% | 96.62% | +84.19 pp | 87 available, 5 partial, 3 not found |
| Learnsets | 0.00% | 89.47% | +89.47 pp | 85 available, 10 not found |
| Type chart | 10.53% | 95.79% | +85.26 pp | 91 available, 4 not found |

The machine-readable aggregate is in [`2026-08-20-gen1-compiled-table-coverage.json`](2026-08-20-gen1-compiled-table-coverage.json).

## Implementation invariants

- Relationship discovery recognizes the source-backed scaled-index and double-add pointer consumers, resolves the table in the consumer's physical bank, and accepts a root only when the combined evolution/learnset ABI validates uniquely.
- Physical root selection accepts the full one-byte move domain. Downstream semantic validation still uses the independently resolved move count, so an unsupported expanded move catalog disables learnsets without discarding valid evolutions.
- Gen I accepts every nonzero one-byte learnset level. Gen II retains its existing `1..100` bound.
- Gen I legacy type charts accept type IDs through 63 and standard multipliers only. Gen II and Gen III retain their narrower domains.
- A failed optional table remains `NOT_FOUND`; it does not reject the base catalog or crash catalog loading.
- Production recognition uses no filenames, ROM hashes, fixed ROM offsets, or hack-specific profiles.

## Validation controls

Synthetic tests cover both relationship consumer forms, preferred and fallback species counts, level 201, expanded Gen I type IDs, ambiguity rejection, and the unchanged Gen II level bound.

Raw-ROM live controls passed for:

- Pokémon Red, Blue, and Yellow.
- Intense Indigo Red, including learnset levels above 100.
- Beyond Red, including its expanded species and type domains; its unsupported learnset move domain remains isolated.
- Shin Red's refactored type consumer.
- Unova Red Vanilla + QoL's complete 151-row fallback relationship domain.

The full 98-input audit completed with 82 selected catalogs, 16 inputs without a mainline-family match, no ambiguity, and no parser errors. The report denominator remains the 95 GB/GBC inputs described above. Parser and catalog-persistence test suites passed after the parser schema advanced to revision 22.

## Unresolved ledger

| ID | Target | Acceptance condition |
|---|---|---|
| `G1-TBL-REL-01` | Complete the structurally resolved evolution domains in Brown, Nova, and both Red++ v3.0 variants. | Every row in each resolved species domain validates and materializes without lowering validator thresholds. |
| `G1-TBL-REL-02` | Resolve Grape's alternate relationship consumer and complete its 252-row domain. | A unique compiled consumer resolves the table and all 252 evolution rows validate from raw ROM bytes. |
| `G1-TBL-LEARN-01` | Resolve expanded move domains used by Beyond, Brown, Grape, Nova, and Red++. | Learnsets validate for every resolved species row and every move reference resolves through an independently validated move catalog. |
| `G1-TBL-TYPE-01` | Decode Brown's nonstandard type-effect multiplier ABI. | The compiled consumer and raw table uniquely establish the multiplier encoding and publish a complete type chart. |
| `G1-TBL-TCG-01` | Classify the three TCG-derived GB/GBC entries outside the mainline Gen I engine families. | Either a structurally validated dedicated family publishes their applicable tables or the corpus taxonomy excludes them from the mainline Gen I denominator. |
| `G1-CAT-MOVE-01` | Raise Gen I move-catalog coverage from the unchanged 50.24% baseline. | Source-backed expanded move domains resolve generically and retain valid move references across dependent datasets. |
| `G1-CAT-MACHINE-01` | Raise Gen I machine-move coverage from the unchanged 42.11% baseline. | Compiled machine compatibility consumers resolve uniquely and materialize valid species-to-machine links. |
| `G1-CAT-SPRITE-01` | Raise Gen I sprite coverage from the unchanged 44.18% baseline. | Shifted and expanded sprite tables resolve structurally and every published sprite decodes within validated bounds. |
| `G1-CAT-DEX-01` | Raise Gen I Pokédex-description coverage from the unchanged 41.73% baseline. | Description pointer consumers resolve structurally and all published text terminates and decodes within validated bounds. |
