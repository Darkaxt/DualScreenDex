# Pokescape native Dex-description execution ledger

**Status: IN_PROGRESS. Authorization: already_authorized.** [Leaf specification](../specs/2026-10-07-pokescape-native-dex-description-design.md); [parent ledger](2026-10-07-all-rom-compatibility-completion-plan.md). One ROM only; execution remains serial and continuous.

| Stage | Requirements | Concrete scope | Evidence / status |
|---|---|---|---|
| 1 — Native diagnosis | DEX-01–04 | Public structural header, fresh bounded native diagnostics, independent generated-only accessor/lookup/palette analysis. | Complete: 906 physical rows, 905 positive rows; all 1,235 active physical species joined; three accessor callers and two positive palette boundary consumers. Eight new diagnostic reads, zero new parsers; two failed-closed reservations explicitly retained, never reused. |
| 2 — Typed ABI RED/GREEN | DEX-01, DEX-05 | `DescriptionCodec.kt`: distinct `36/[20]` header offsets, old shapes unchanged. `DescriptionModels.kt`: immutable description-only native join map. New relocated/adversarial codec cases; existing description controls. | Pending: observe functional RED, then focused GREEN through installed host gate. |
| 3 — Compiled selection and joins | DEX-02–05 | Add narrow compiled resolver; reuse and narrowly extend `CompiledDescriptionIndexBinding.kt` and `CompiledDescriptionExtentBinding.kt`. Prove description field, accessor, positive boundary and complete wrapper-map domain; conflicts/budgets/cancellation fail closed. Bridge `SemanticDomainStrategy.kt` only after legacy failure and use native description binding in `CatalogParser.kt` without changing canonical species numbering. | Pending: relocated positive, missing/clobbered/wrong-width field, wrong root/selector/return, missing/wrong palette/copy, competing root/map, invalid join, truncation, work/root/candidate/extent limits and cancellation; focused semantic/publication isolation. |
| 4 — Exact source and independent acceptance | DEX-05–06 | Inline review, parser revision advance, smart-sync source commit/push; installed gated exact-source CLI packaging; separately reviewed immutable one-target parser and independent original verification admissions. | Pending: host GREEN, runtime/report/source pins, every published description/category/dimension and native join, full references, whole SQLite parity, independent digest and accepted baseline reverse-projection equality. No broad corpus/devices. |
| 5 — Publish, clean and resume | DEX-07 | Sanitized one-row evidence, unchanged 341 other rows and denominator, smart-sync commit/push, compact proof, inspect/ticket/apply exact registered outputs. | Pending: verified remote publication and actual absence; then resume Pokescape's five move-description gaps and remaining abilities/type/text obligations. Parent stays IN_PROGRESS. |

## Reconciliation

- DEX-01–04: original-bound native diagnosis is verified, production implementation remains pending. Scalar run alone is not extent authority; verified palette/copy consumers provide the independent boundary. Native form aliases have explicit compiled lookup ownership, not guessed names.
- DEX-05: new synthetic RED/GREEN and preservation gates are still required. Prior packed-move tests do not satisfy this new implementation gate.
- DEX-06: no new production parser or acceptance occurred. Admissions 08–15 are consumed and immutable; 12/13 failed closed. New acceptance/verification admissions must be separate.
- DEX-07: next output transaction was registered before creation. Its cleanup is open, not applied. The preceding packed-move cleanup is published and verified at `57b8bec1`; do not reopen it.

**Next concrete action:** implement synthetic codec RED, then the distinct ABI and compiled resolver under the specified guards. No unresolved user decision or device authority is needed for this host stage. No requirement is waived or deferred out of the parent goal.
