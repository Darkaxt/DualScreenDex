# Stage 4 map-text isolation checkpoint

Status: **verified bounded map-text isolation; not Stage 4 closure**.

## Scope and authority

This bounded `LNG-B002` / Task 379 checkpoint applies `LNG-INV-006` to Gen II world-map bindings and the related local-map name caller.

- Compiled consumers, bank-local pointers, coordinate bounds, region classification, and dynamic-special rules retain structural authority. Readable text cannot select between conflicting numeric chains.
- Malformed or ambiguous localized names no longer erase independently validated coordinates or bindings. Unavailable labels remain null; no alternate-language borrowing is introduced.
- Numerically equivalent candidate chains publish a label only when the contributing chains agree. The first candidate does not acquire text authority merely from iteration order.
- Whole-chain dialect uncertainty conservatively suppresses its names. Numeric results do not require a successful dialect choice.
- Cancellation propagates through structural scans, binding/name loops, exact-codec tokens, and the relevant local-map caller. Broad catches rethrow cancellation rather than returning an empty or unavailable result.

No language-authority, codec-identity/version, parser-schema, catalog-schema, or runtime-language-selection change is included.

## Root cause and regression evidence

Previously, binding construction rejected a numeric chain if name-encoding selection failed, landmark decoding conflated malformed text with invalid structural data, numeric deduplication retained the first conflicting label, and broad world/local catches swallowed cancellation.

Generated fixtures exercise complete world graphics/planes/palettes/header/landmark/classifier chains and a complete local-map tileset/roof/palette chain. Healthy fixtures passed before implementation. Negative controls preserve structural rejection; text mutations and conflicting/equivalent candidate chains exercise capability isolation. Japanese/Korean exact-codec cases and actual entrypoint cancellation are included.

The pre-fix gate ran `Gen2WorldMapResolverTest` and `Gen2LocalMapCancellationTest` with `--rerun-tasks`: **18 cases, 12 failures, 5 passes, 1 external-control skip**. The post-fix focused gate added `Gen2LandmarkNameCodecTest`: **43 cases, 42 passes, no failures/errors, 1 external-control skip**. The skipped control requires `DUALDEX_POKEGOLD_ROM`; it was not supplied.

A bounded independent read-only review of the diff and call paths found no concrete high-confidence issue. That review did not execute tests and does not substitute for the coordinator's fresh combined gate.

## Independent combined verification

The coordinator ran the following fresh combined gate after the map files were stable:

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test \
  --tests 'com.enrpau.dualscreendex.parser.text.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.validate.*Test' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityLegacyParityTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2LandmarkNameCodecTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2WorldMapResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2LocalMapCancellationTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2LocalMapPoiResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.Gen2CompiledNamePairResolverTest' \
  --tests 'com.enrpau.dualscreendex.parser.parse.FamilyParsersAbilityResolutionTest' \
  --tests 'com.enrpau.dualscreendex.parser.family.FamilyProbeCoordinatorTest' \
  --rerun-tasks --console=plain
```

Result: **BUILD SUCCESSFUL; 335 cases across 23 classes: 333 passed, 0 failures, 0 errors, 2 skipped**. Compilation and tests executed freshly. Skips require `DUALDEX_CLOUD_WHITE_2_ROM` and `DUALDEX_POKEGOLD_ROM`; neither was supplied. Synthetic world/local entrypoints are exercised, but this run does not claim external official-control acceptance. Existing compiler/Gradle warnings remain. `git diff --check` passed.

An in-progress native-type test fixture helper was present during successful test compilation but is excluded from this checkpoint. No native-type regression execution or support claim is included.

## Mandatory remaining work

This checkpoint does not close `LNG-B002`: official native end-to-end names/descriptions/locations and independent numeric survival still require acceptance. `LNG-B001` native language corroboration and unmarked Japanese Gen I structural trials remain mandatory. `LNG-D005` native compiled type semantics remain in progress. `LNG-B003` requires the complete sanitized 43-cell official matrix, persistence/reopen/API acceptance, one final current-corpus run after Stage 4 executable changes are final, and published Stage 4 closure.

No required official cell is deferred. Stage 4 remains open and Stage 5 stays blocked. No ROM payload, private memory, corpus run, release, signing, emulator, physical-device, ADB, or cleanup action is included.
