# Stage 4 official forecast-policy checkpoint

Status: **nine exact native controls pass the ROM-backed conditional type-policy boundary; not Stage 4 closure**.

## Scope and authority

`LNG-D005` requires decoded type semantics to reach forecast consumers without assumed numeric type order, display-name heuristics or generation-based semantic inference. The preceding native checkpoint established catalog/reopen/API type samples but did not invoke a forecast consumer.

`DamageForecastAssembler.conditionalWeatherPolicy` now contains the existing conditional weather decision, extracted without changing its behavior. Production `input()` consumes this helper. A decoded FIRE or WATER role supplies the existing bounded weather modifier; another proven role does not. Missing semantic authority produces the existing unresolved-interaction reason.

This is a ROM-only conditional policy. It does **not** prove that an engine implements weather, establish a damage formula, supply battle observations or validate numeric damage accuracy. No synthetic battle state is presented as official runtime evidence.

## Joined official acceptance

The existing nine separately pinned native controls now exercise:

1. Independent native NORMAL, FIRE and WATER label expectations locate actual parsed type records; semantic roles must be available and match those expectations.
2. Actual parsed move-to-type references reach the same policy used by production forecast assembly.
3. The checks repeat against the catalog reopened after actual SQLite write/close/reopen, with identical move-keyed decisions.
4. Separately labelled negative fault injections remove semantic authority while preserving move references and language overlays. The policy must return an unresolved outcome, not infer meaning from IDs or retained labels.
5. Existing catalog integrity, language isolation and cache-only API bootstrap assertions remain in place.

The positive official checks do not replace type records, semantic roles, move references or combat state. Per-control referenced-move counts are observed diagnostics: 92 for the two Gen I controls, 129 for the four Gen II controls and 169 for the three GBA controls.

Result markers distinguish:

- `officialRomSemanticForecast=PASS`, earned only after the required boundaries and overall joined case pass;
- `liveBattleForecast=NOT_RUN`;
- `scope=CONDITIONAL_TYPE_POLICY engineWeatherApplicability=NOT_TESTED`.

## Regression and independent verification

The implementation owner retained an assertion-only failing baseline: 12 cases, nine passed and three failed, with no errors/skips. The three added regressions cover role-versus-ID/name authority, absent semantic authority and propagation through the full synthetic assembler/calculator seam. Those constructed battles remain explicitly synthetic.

The coordinator inspected the production extraction and test diff, then independently ran:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.darkaxt.dualdex.web.DamageForecastAssemblerTest' \
  --tests 'com.darkaxt.dualdex.web.WorldMapCatalogApiRealControlTest.nativeOfficial*' \
  --rerun-tasks --no-parallel --console=plain
```

External SHA-pinned native inputs and a fresh private temporary-output directory were supplied through environment variables.

**Result: 21/21 passed, zero failures/errors/skips**: 12 assembler cases and nine native joined cases. Gradle completed in **5m44s**, with all 40 actionable tasks executed. Native JUnit time was **254.151 seconds**. The coordinator inspected both XML reports, verified all nine earned forecast markers and retained source snapshots, hashes, logs, XML and SQLite evidence outside public assets. Existing compiler/Gradle warnings remain.

## Remaining gates and discovered limitations

- The native ROM-backed conditional type-policy boundary is now verified. Complete current official-matrix ratification remains required before final `LNG-D005`/Stage 4 closure.
- The separate capability audit identified a **move-description false positive** in all three Japanese GBA controls: selected description roots are primary learnset roots, and packed learnset bytes are published as prose. The preceding nonblank move-description assertion did not catch this. Its apparent coverage is **not accepted prose evidence**. `LNG-B002` / Task 388 requires compiled move-description authority, a learnset-decoy regression and independent actual prose samples through reopen/API.
- Species-description slot authority, other incomplete localized capabilities and the combined evidence validator remain Tasks 385–387. No required content is waived by the passing sampled gate.
- Final current corpus execution waits until Stage 4 executable changes are final. The ledger audit and `stage-04-closure.md` remain pending; Stage 5 stays blocked.

No live battle, device/emulator/ADB, signing, release APK, full corpus, ROM publication or cleanup action is part of this checkpoint.
