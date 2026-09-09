# Stage 4 — measured API projection observations

Date: 2026-09-09. Task 430 / `LNG-B003`; Stage 4 remains open.

The test-only cache capture now normalizes actual `BootstrapView` values into all fifteen localized field groups and records projection/type consistency measurements from the exact catalog returned by production restore. It retains the unchanged production response, DISCOVERED mode, parser-invocation measurement, private-copy cache protection, logical digest and run binding from the preceding [metadata checkpoint](stage-04-g3-metadata-binding-checkpoint.md).

## Observation contract

- Every capability is explicitly inspected. `fieldsChecked` is the number of completed inspections, followed by exact enum-coverage validation; it is not an assumed success count.
- Normalized fields contain only actual API values. Missing API text is not filled from the selected overlay. AREA_NAMES uses the Area Guide; item names combine balls and POI item IDs; POI text uses the category-specific display/item channel. World-location keys remain nested region/location pairs, avoiding slash collisions.
- `mixedFields` counts capability groups with conflicting API occurrences, uncovered selected text, or disagreement between the selected overlay, projection and API. `fallbackFields` counts groups with projected/API text lacking selected-overlay authority. `sharedTextFields` inspects all shared backing text even when the selected overlay wins. These counters count affected groups, not individual strings.
- Overlay coverage exceeding distinct observed text is explicitly incomplete. Unreferenced abilities, unexposed item/POI text and gender-dependent labels cannot become successful observations merely because the bootstrap omits them. No trainer gender is invented.
- `typesChecked` uses the actual restored type domain. Missing/non-AVAILABLE semantic roles count unresolved. Missing, duplicate, blank, different or extraneous API type rows count mismatches against the restored projection, not against themselves. A generated zero-type catalog measures zero; final matrix validation still requires a nonempty type domain.
- The shared Python validator/assembler requires both measured groups to equal the normalized required-check data and requires all fifteen normalized field dictionaries. Existing zero-contamination/unresolved/mismatch acceptance checks remain mandatory. The builder remains metadata-only and non-mutating.

These are **internal consistency observations**, not independent linguistic or semantic truth. All captures retain `acceptance=false`; current fabricated Python declarations are not official-control measurements or historical artifact upgrades.

## Verification history

The initial Kotlin behavioral RED ran fifteen methods: thirteen passed and two failed specifically because fields/measurements were absent. The next run compiled the observer and passed eighteen of nineteen methods; the remaining synthetic type fixture violated the model's expected-domain invariant. An attempted fixture correction called nonexistent `CatalogLanguageOverlay.copy()` and failed compilation. Both fixtures now use explicit constructors without weakening production invariants.

That compilation failure exposed stale XML reuse in the private runner. The next Gradle invocation succeeded, but its wrapper correctly rejected missing per-attempt XML: Android task configuration had replaced the early report destination. The runner now sets and locks the unique XML destination after task configuration. Historical logs/XML are retained; neither stale XML nor an empty inventory counts as a passing gate.

Python measurement-binding RED ran two methods with nine expected assertion failures and no errors/skips. It showed that changed/missing measurements and incomplete field inventories were previously admitted.

## Verified result

- Fresh Kotlin `green-05`: **20/20** exact methods, zero failures/errors/skips, 23.042 seconds. The unique per-attempt XML was produced and verified; all **813** Kotlin/build source pins remain unchanged.
- Full Python `green-measurements-full-01`: **115/115**, zero failures/errors/skips, 104.148 seconds; all **four** Python source pins remain unchanged.
- Main inspected the changed code, independently checked the XML method inventory and all 115 successful Python log entries, and rehashed both source inventories. An initial verification script assumed the Python pins used the Kotlin wrapper shape and raised `KeyError`; the corrected verifier consumed the actual flat Python inventory and passed without rerunning either gate.
- Both gates bind pre-commit HEAD `10b7a275530f5cb5f2e0f5a375005508c8ca33d3` plus the tested source hashes. Retained roots: `D:/Temp/dualdex-task430-capture-3fas4y9v` and `D:/Temp/dualdex-task430-python-lbsxw0px`.

| Artifact | SHA-256 |
| --- | --- |
| Kotlin actual XML | `bcbf2346ada97b43bfd2dd0c52dbb128b37b2ed650d54316bbb2926109ca1933` |
| Kotlin result | `a3f30a4fb528361e2cb7e2447668df704b7c8c49e3090750a1959015436d7d75` |
| Kotlin source pins | `e94f47700bf20f66ab3483ad3c986f245c898ba8771e242ea0b62fc879daf122` |
| Python log | `d9fc93664f4e9405f54b71e38897d0f372f5f959dc81e054015eb690a77e45b0` |
| Python result | `832bf706362c386c1a637f6ce48206d53bba59ef1c775a467e20f3b05fccd0ce` |
| Python source pins | `b649732fdf056510c255479f5a7dc03b084faf2657b97751dade2eced520ee91` |

## Remaining mandatory scope

No original ROM reads/stat/acquisition, corpus run, Android/device/emulator/ADB operation, APK, signing, release or PR is part of this checkpoint. No production parser/catalog/cache behavior or version changes. Historical captures remain immutable.

Task 430's final source-bound captures and independent oracle linkage remain open under Task 425 / `LNG-B003`. Tasks 419/420/428 and the remaining capability/applicability work are not silently waived. Zero final stable-source controls are accepted; the complete 43-cell/44-control matrix, one final eligible corpus after executable stabilization and published Stage 4 closure remain mandatory. Stages 5–6 remain sequentially blocked. Work is direct and serial, with no agents.
