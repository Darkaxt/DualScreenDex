# Stage 4 Gen III area-domain boundary checkpoint

Date: 2026-09-11

Status: **parser boundary corrected; final matrix capture must be regenerated**

## Finding

The first current-source 44-control G0 capture selected every control with zero parser errors, but reconciliation against the independent capability-applicability audit found six FireRed/LeafGreen controls publishing `AREA_NAMES` as `AVAILABLE` (`425/425`). The fixed applicability policy requires that domain to be `NOT_APPLICABLE` for FireRed/LeafGreen. The capture therefore was not eligible for matrix acceptance.

The root cause was a shared map-location result in `CatalogMaterializer`: compiled Gen III base-area labels were used both to replace synthetic encounter labels and to populate `CatalogRuntimeMetadata.areaNamesByBaseId`. Those are different semantic domains. FireRed/LeafGreen needs the former for ROM-native encounter labels, but has no distinct runtime `AREA_NAMES` domain.

## Correction

`CatalogMaterializer` now resolves compiled Gen III base-area labels once and keeps their uses separate:

- all supported non-expansion Gen III families may use the labels to replace synthetic encounter labels;
- only Ruby/Sapphire and Emerald publish the labels into the runtime area-name domain;
- FireRed/LeafGreen publishes no runtime area identities or localized `AREA_NAMES` records.

This preserves `ENCOUNTER_AREA_NAMES` authority without fabricating a FireRed/LeafGreen runtime capability. Existing Local-map authority remains available to validate persisted encounter labels independently of method/time interface copy. Parser revision **80** invalidates revision-79 caches containing the conflated domain; storage schema remains **2** and codec version remains **1**.

## Verification boundary

The focused FireRed/LeafGreen regression was observed RED before the correction because the parser exposed the runtime area domain. The family-boundary tests pass after the correction. The complete parser/cache checkpoint then passed **2,167 tests** with zero failures/errors (**2052 parser-core, 115 catalog-store; 206 environment-inapplicable tests skipped**). The tests require:

- FireRed/LeafGreen still executes compiled area-label resolution but finishes `AREA_NAMES` as `NOT_APPLICABLE` with no runtime area identities;
- Ruby/Sapphire and Emerald retain their runtime area identities and localized names as `AVAILABLE`.

The failed initial G0 outputs remain diagnostic evidence only. Because parser code changed, the parser CLI, both G0 partitions, caches, receipts, and all downstream source-bound evidence must be regenerated from the committed correction. This checkpoint accepts no official control and does not weaken the 506 accepted / 154 excluded capability policy.
