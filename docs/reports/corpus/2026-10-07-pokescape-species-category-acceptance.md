# Pokescape native species-category acceptance

**Accepted boundary:** one retained Pokescape v1.0.4 build, host/static localized category persistence. **Parent and full-ROM status: IN_PROGRESS.** SPECIES_CATEGORIES is **AVAILABLE 1,235/1,235**; the public core score correctly remains **87.50%**.

- Production source: `6853d5d6a0d464d21613cd6f1b8f1156054dad34`.
- Parser revision **92**, SQL schema **2**, emitted overlay format **2**; exact committed CLI stamp and all **13 runtime hashes** pinned.
- [Machine-readable acceptance](2026-10-07-pokescape-species-category-acceptance.json); [specification](../../superpowers/specs/2026-10-07-pokescape-species-category-publication-design.md); [execution ledger](../../superpowers/plans/2026-10-07-pokescape-species-category-publication-plan.md).

## Authority and publication

The selected description codec already decoded categories, but media materialization and persistence dropped the field. Publication now revalidates the bounded native category cell, requires agreement with the selected decoded row and uses the same compiled species/form→description-row join as accepted prose/dimensions. No new table root/count/profile, public-source category, inferred label or positional alias is introduced. Prose validators and accepted description resolution are unchanged.

Strict selected ordinary/expanded Gen III inline ABIs and the existing Japanese six-byte zero-terminated helper are covered by synthetic controls. Unproved routes remain unavailable. Categories are stripped from core records and published only through an immutable selected-language overlay; neither shared nor other-locale text is a fallback. Missing categories do not demote prose or dimensions. Required storage fields/maps, actual applicable keys/coverage, whole digest and prior-parser cache rejection are explicit.

## Independent verification

| Evidence | Result |
|---|---|
| Bounded physical category cells | **906** |
| Distinct positive physical rows reached | **905** |
| Published categories / compiled alias joins / native species-name joins | **1,235 each** |
| Retained compiled windows rechecked | **9** |
| Existing structural relationship references | **84,452** |
| Localized references, including 1,235 category keys | **6,546** |
| Combined reference checks | **90,998**, zero errors |
| Whole SQLite write/reopen and independent canonical digest | Equal; integrity check passed |
| Reverse only category payload/format and parser 92→91 | Exact accepted type-name baseline |
| Complete other sections unchanged | **16 of 18** |

The expanded reference total includes independently checked localized domains; it is not an increase in the old structural relationship count.

Accepted catalog digest: `6672b02c8230915a80ade72b8265b20c2073c3dd05d40e0c25485ad02839f44f`.

Exact reverse baseline: `84d79c67cce35228090afea1b2c130bfbf689a26ec09133d3b95d6279f7ff12a`.

Affected parser **346 cases / 27 classes**, full catalog-store **122 / six skipped**, full parser-cli **77 / one skipped**, companion API **46**: **591 total, 584 executed, seven skipped, zero failures/errors**. Seventeen new synthetic cases cover strict cells, relocated native aliases, invalid/blank/truncated/out-of-domain tokens, cancellation/budgets, immutable coverage/reference bounds, independent prose, locale-only API/CLI publication, storage tampering, SQLite/digest and parser-91 cache rejection. Three initially incorrect type-chart package filters were corrected and verified in a separate fresh run.

Observed functional REDs: materializer six cases/two assertion failures, compiled-alias publication one assertion failure, valid storage roundtrip failure, species API one assertion failure. Compiler/setup/stale-fixture errors are retained separately and are not functional RED evidence; the initial storage tamper-test setup NPE is not independent RED proof.

Every Gradle invocation acquired the installed shared Windows gate: two workers/no parallel, observed Gradle 3 GB/requested Kotlin 3 GB/project default, one configured 512 MiB test fork per task, no memory failure. Configuration-time markers are not a total-host/native memory guarantee.

## Admissions, preservation and remaining work

New admissions **26 and 27** were separately scoped and MAIN-reviewed, then consumed once: **two original reads, one production parser execution, zero failed consumed admissions**. The independent admission completed generated-only source/runtime/report/reference/SQLite/reverse-baseline preflight before reading the original. Historical admissions remain immutable; no original copy retained or published.

Only the target's new counter, localized capability and source/digest provenance change in the 342-row matrix. **341 unrelated rows, all identities/aliases and historical receipts remain unchanged.** Core score capabilities and Markdown table rows are unchanged. Both maps and all accepted move/Dex/type fields are preserved by exact whole-catalog baseline equality.

Acceptance publication, compact proof retention and registered exact-output cleanup are in progress; this is not final leaf closure yet. Abilities/descriptions/mechanics and required item/Local/encounter/POI text remain same-ROM obligations. No other ROM, full-corpus rerun, live rendering, Android/emulator/ADB, APK/signing or release claim.
