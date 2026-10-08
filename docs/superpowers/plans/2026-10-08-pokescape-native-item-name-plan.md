# Pokescape native item names — execution ledger

**Status: IN_PROGRESS. Authorization: already_authorized.** Source of truth: [ITN specification](../specs/2026-10-08-pokescape-native-item-name-design.md), under [ALL-01–08](../specs/2026-10-07-all-rom-compatibility-completion-design.md). Serial inline execution only. Current accepted parser93/SQL2, coverage95.83%, mechanics0/298; release pending. Scheduling this independent required same-ROM batch does not remove mechanics requirements.

| Stage | Requirements | Concrete next work / dependencies | Required evidence |
|---|---|---|---|
| 1 — Native diagnosis | ITN-01–02 | Reuse retained44 sanitizer and adjacent pointer getter. Separately review a bounded code-only metadata inventory of nominated root/field references, broad competing getter hints and pointer-getter callers; retain exact new instruction neighborhoods, not item payload. Check complete scalar/copy consumers and all literal/control-flow dependencies before any production change. | One-use reviewed receipt, measured complete inventories, native dataflow/extent/name boundary and copy contract; unknown consumers explicitly block. |
| 2 — Generic parser change | ITN-03–04 | Add relocated functional tests to `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3CompiledItemNameResolverTest.kt` (or a focused adjacent test file sharing its synthetic fixture). Observe RED, then extend `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3CompiledItemNameResolver.kt` for the proved complete leaf only. Preserve `ItemNameMaterializer.kt` exact-token behavior and original-route inventories. Advance the existing parser revision definition for changed behavior. | Fresh focused RED/GREEN, complete item/materializer/old ABI controls, required parser/store/CLI regression tests through shared gate; inline review and no mechanics promotion. |
| 3 — Exact-source acceptance | ITN-01, ITN-05–06 | After host GREEN and source publication, build the exact packaged CLI via the gate. Separately review one-target execution and independent native item payload verification. Compare all applicable names/joins/references, whole SQL/reopen and independent canonical digest; exact reverse projection to the retained ability-text baseline. | Pinned package/source/runtime, independent full requested-domain values and joins, unchanged other sections/maps, explicit remaining deficits. |
| 4 — Publication and cleanup | ITN-06 | Reconcile every ITN requirement, update only verified target fields, smart-sync/commit/push normally, retain compact proof and ticket/apply registered expendable outputs after consumers finish. Continue Pokescape mechanics and map/encounter/POI text; do not advance ROM or publish requested release prematurely. | Both fork refs verified, unrelated341 rows/stash/source/corpora retained, exact cleanup inspected/applied/absence verified, zero leaf blockers/deferrals. |

## Cumulative reconciliation

| Requirement | Status | Evidence / blocker |
|---|---|---|
| ITN-01 | Active | Historical retained44 is reusable; no new item-name admission or payload read yet. Existing registered mechanics scratch remains active and must not be deleted. |
| ITN-02 | BLOCKED | Literal-bound selector and adjacent getter retained; complete item consumer/root/hint/caller/copy inventory and name boundary not yet reconciled. |
| ITN-03 | BLOCKED by Stage1 | No production code changed. |
| ITN-04 | BLOCKED by Stage1 | No new host tests/build run. |
| ITN-05 | BLOCKED by Stage2 | No item names accepted, no production parser run. |
| ITN-06 | Active | This scheduling document is not compatibility or release acceptance; final verification/publication/cleanup remains required. |

**Concrete next action:** inspect retained44's adjacent getter and the existing nomination/index rules; create and generated-test a bounded metadata producer, independently review its MAIN scope, then consume once. No original item data, recursive descendants or mechanics acceptance is included. Parent/ROM/release remain IN_PROGRESS.
