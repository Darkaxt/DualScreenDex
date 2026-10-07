# Pokescape native Dex-description batch

**Status: IN_PROGRESS. Authorization: already_authorized.** This is a bounded leaf of the [all-ROM parent](2026-10-07-all-rom-compatibility-completion-design.md), not a new ROM target or parent completion.

## Proven failure and contract

The retained build uses a distinct 36-byte Dex record: terminated category prefix before byte 14, height halfword at 14, weight halfword at 16 and one description pointer at 20. Existing 36-byte descriptions use height/weight at 12/14 and pointers at 16 or 16/20; none may be reinterpreted or relaxed.

Generated-only independent analysis verifies 906 physical rows (reserved row zero and 905 positive description rows). A compiled species lookup, immediately narrowed and passed to the two-selector dimensions accessor, maps all 1,235 physical active species onto those 905 rows, including form aliases. Two independently located positive palette consumers establish the exact adjacent 32-byte object boundary. Neither adjacent malformed bytes nor scalar popularity alone proves extent or ownership. The current public project source is structural, not exact-build authority.

Eight separately reviewed diagnostic reads in this batch have been consumed (08–15), with no production parser invocation. Two failed closed: 12 exceeded an overbroad incoming-call cap; 13 asserted an unobserved accessor entry. Their reservations and producers are immutable and were not retried. Successful 14 captured actual raw envelopes; 15 used the now-observed exact entry. No authority requirement was bypassed.

## Requirements

| ID | Required behavior and acceptance |
|---|---|
| DEX-01 | Add a distinct typed 36-byte single-pointer-at-20 contract, bounded category prefix and native dimensions. Existing Japanese 28-byte, ordinary 32-byte and legacy 36-byte one/two-page decoding remain unchanged. Preserve scalar/text thresholds, extent/cancellation guards and page provenance; malformed rows fail closed. |
| DEX-02 | Select only through complete compiled two-selector dimensions accessor and description-field witnesses, not a ROM title/hash/address, category word or reference rank. Discover roots across the image under deterministic work/root/candidate limits. Unknown/clobbered/branching address formation is not authority. |
| DEX-03 | Establish physical end using the positive adjacent-object palette/copy consumer contract. A terminated scalar run, padding or pointer occurrence alone cannot establish extent. Competing complete roots, replacement/partial contracts and exhausted budgets close only this optional module. |
| DEX-04 | Prove complete active native-species-to-description joins through the actual return-value wrapper and immediate narrowed accessor caller. Validate all active IDs, all referenced rows and alias ownership; conflicting maps reject. Publish a separate immutable description-row binding without changing existing canonical species/Dex data. |
| DEX-05 | Observe independent relocated/adversarial RED/GREEN for codec, selection and publication joins. Preserve old ABI controls and core/World/520 Local Maps. Required Windows builds use the installed shared host gate and bounded profile; no agents, corpus-wide run or devices. |
| DEX-06 | Commit exact source and advance parser revision, not SQL schema. Fresh separately reviewed one-target packaged CLI and independent native verification bind source/runtime/report, all published text/category/dimensions and joins, complete decoded references, SQLite equality and logical digests. Reverse only intended description/capability/revision changes to reproduce the accepted packed-move baseline digest. |
| DEX-07 | Publish only verified affected fields, preserving the 342 identities and 341 unrelated rows. Retain compact private proof; inspect/ticket/apply registered outputs and verify absence. Resume this same ROM's remaining obligations. No full-ROM/parent/live/release claim. |

## Design

Reuse `DescriptionTableLayout`'s pointer-field identity: `36/[20]` is distinct from `36/[16]` and `36/[16,20]`. A narrow compiled resolver reuses the typed codec, dimensions-accessor matcher and positive palette-boundary helper, then proves the full species-row map. Its immutable selected layout carries that description-only binding to catalog publication; canonical species numbering remains unchanged.

The fallback runs only after existing description validation fails and does not replace working exact/published/expansion routes. Failed optional proof retains the working core and maps. Diagnostics remain private; source, synthetic fixtures and sanitized evidence are publishable. The current original-read reservations are not reusable for acceptance or verification.
