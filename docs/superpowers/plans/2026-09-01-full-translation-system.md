# DualDex Full Translation System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the full ROM-content and interface translation system defined in `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`, with interface translation delivered only after parser, catalog, runtime, and API authority are complete.

**Architecture:** Use one immutable parser-produced language manifest, one shared numeric/media catalog, and language-keyed localized overlays. Runtime may select an already-persisted overlay only through validated read-only RAM evidence; the interface locale is independent and changes only presentation.

**Tech Stack:** Kotlin/JVM, Android/Kotlin, SQLite catalog persistence, Preact/TypeScript, Vitest, Playwright, Gradle/JUnit, PowerShell corpus tooling.

---

## 1. Delivery model

Use one cumulative implementation branch, `feat/full-translation-system`, created from the merged specification/plan baseline. The six stages are strictly ordered:

1. Language authority foundation.
2. Multi-projection catalog foundation.
3. Official Western-language parsing.
4. Official Japanese/Korean parsing.
5. Multilingual runtime selection and semantic API.
6. Interface translation.

Do not begin Stage 6 early. Do not add a temporary German-only UI, manual ROM-language setting, reparsing switch, exact-ROM production selector, or second catalog per language.

No stage signs, tags, promotes, or publishes an RC without separate authorization. Parser/catalog stages do not need a physical device. Stage 6 uses browser and packaged-AVD acceptance at the exact Thor viewport; physical Thor work still requires explicit thread-scoped ownership.

## 2. Stage discipline

### Before each stage

- [ ] Fetch `fork/master`, reconcile without reset or force-push, and record the exact baseline.
- [ ] Confirm the worktree contains no unexplained changes.
- [ ] Re-read the complete specification, then the clauses assigned below.
- [ ] Run the listed focused baseline tests before changing production code.
- [ ] Inventory external controls required by that stage without copying ROMs into the repository.

### During each stage

- [ ] Add a failing focused test before each behavior change.
- [ ] Keep optional language capabilities fail-closed and bounded.
- [ ] Commit small green checkpoints; smart-sync against `fork/master` immediately before every commit.
- [ ] Push every checkpoint to `fork/feat/full-translation-system`.
- [ ] Rerun the source-bound corpus only when parser, catalog, build-wrapper, or corpus-execution code changed; never rerun it for Stage 6 UI-only changes.

### Close every stage

Create or update:

```text
docs/reports/localization/stage-XX-closure.md
docs/reports/localization/localization-ledger.md
```

The closure report must map every assigned spec clause to implementation and fresh evidence, record the exact source commit and synchronized `fork/master`, list commands/results, and end in `COMPLETE` or `BLOCKED`.

Every open ledger row must contain the Section 16 fields from the specification. Use `LNG-B###` for blockers and `LNG-D###` for deferrals. An omitted feature, missing control, failed test, unsupported official cell, or unresolved authority ambiguity cannot disappear into prose.

Stage closure rules:

- [ ] No open `STOP-SAFETY` or `STOP-CORE` item.
- [ ] No current-stage requirement deferred merely to close the stage.
- [ ] Every optional rejection demonstrates its fail-closed result.
- [ ] Every deferral names its owner, target, and measurable acceptance condition.
- [ ] Implementation, tests, evidence, closure report, and ledger are committed and pushed together.
- [ ] The dependent stage starts only after the prior closure report is public.

## 3. Corpus command contract

For any stage requiring a source-bound corpus run, use the existing guarded wrapper with environment-supplied private roots:

```powershell
if (-not $env:DUALDEX_CORPUS_ROOT) { throw 'DUALDEX_CORPUS_ROOT is required' }
if (-not $env:DUALDEX_APK_VERSION_CODE) { throw 'DUALDEX_APK_VERSION_CODE is required' }
$workRoot = Join-Path $env:TEMP 'dualdex-localization-corpus'
pwsh -NoProfile -File tools/corpus/Invoke-DualDexCorpusValidation.ps1 `
  -SourceRoot $env:DUALDEX_CORPUS_ROOT `
  -WorkRoot $workRoot `
  -ApkVersionCode ([int]$env:DUALDEX_APK_VERSION_CODE) `
  -Reset
```

Retain private raw receipts only in the guarded work root. Commit only aggregate, sanitized, source-bound evidence. After closure, remove disposable corpus caches/reports that are not required as retained evidence; never remove source ROMs.

---

## Stage 1 — Language authority foundation

**Specification:** Sections 2–7, 14.1, 14.5, and Stage 1 in Section 15; especially `LNG-INV-001`–`LNG-INV-007`, `LNG-HDR-001`–`LNG-HDR-003`, and `LNG-DISC-001`–`LNG-DISC-005`.

**Outcome:** English behavior is unchanged, but language, evidence, header identity, and variable-width text decoding become first-class parser inputs carried to materialization.

### Primary files

Create:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/language/LanguageModels.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/language/LanguageRegistry.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/language/LanguageModelsTest.kt
```

Modify:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/detect/RomHeader.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/PokemonTextCodec.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinator.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/IdentityRootsStrategy.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/CoreDatasetsStrategy.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RelationshipMaterializers.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/detect/RomHeaderTest.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/PokemonTextCodecTest.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinatorTest.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModelsTest.kt
```

### Work checklist

- [ ] Add red tests for normalized extensible `LanguageTag`, duplicate projection rejection, default-projection membership, explicit `UNKNOWN`/`AMBIGUOUS`, and bounded evidence confidence.
- [ ] Add red header tests retaining raw GB/GBC title/identifier bytes and complete GBA game code while preserving current sanitized display fields.
- [ ] Replace the single-byte decoder contract with token consumption that reports bytes, terminator, glyph/control/invalid counts, and accepts cancellation plus a maximum byte count.
- [ ] Port `gbEnglish` and `gbaEnglish` through the new contract with golden parity vectors, including terminators, controls, truncation, and invalid bytes.
- [ ] Insert one language-resolution handoff in family probing and carry it through `ResolvedRomLayout` and `ParsedCatalog`; do not recompute the codec from generation in materializers.
- [ ] Remove final-materializer calls that directly select `gbEnglish`/`gbaEnglish`; receive the resolved projection/codec instead.
- [ ] Prove unknown/ambiguous language disables affected text only and preserves structurally valid numeric capabilities.
- [ ] Run the source-bound eligible corpus once after the English refactor and compare all pre-existing English text and capabilities.
- [ ] Write `stage-01-closure.md`; create the ledger with seeded `LNG-D001`–`LNG-D004` plus any discovered items.

### Verification gate

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test --stacktrace
```

Then run the Section 3 corpus command. Stage 1 is blocked by any unexplained English output drift, generation-based codec reconstruction, unbounded token decode, or language failure that aborts numeric parsing.

---

## Stage 2 — Multi-projection catalog foundation

**Specification:** `LNG-INV-003`–`LNG-INV-006`, Sections 10, 11.1, 14.3, and Stage 2 in Section 15.

**Outcome:** A catalog persists one shared data graph plus every localized overlay and reopens it atomically. The default overlay reaches bootstrap without reparsing or duplicating shared assets.

### Primary files

Modify:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt
catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt
catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogMigration.kt
catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt
catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogWriter.kt
catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogCache.kt
companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt
app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt
app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt
catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt
app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt
```

Create:

```text
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogLanguageOverlayTest.kt
catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogLanguagePersistenceTest.kt
```

### Work checklist

- [ ] Add red model tests for one immutable manifest, one default projection, stable overlay versions, language-specific capability state, and no cross-language field fallback.
- [ ] Move localized names/descriptions/labels from shared records into `CatalogLanguageOverlay` while retaining stable numeric entity IDs and language-neutral values once.
- [ ] Add an artificial two-language catalog fixture and prove both overlays reference the same shared stats, media, maps, encounters, and runtime layouts.
- [ ] Extend the catalog schema, section codec, writer, reader, and digest checks for manifest and overlays; bump catalog/parser schema according to cache policy.
- [ ] Enforce aggregate/per-projection encoded and inflated limits before allocation and transaction commit.
- [ ] Prove migration rebuilds only catalog tables and does not alter SaveRAM recovery snapshots or device settings.
- [ ] Publish manifest/default language/default overlay in bootstrap while preserving exact ROM SHA as the sole cache identity.
- [ ] Add a parser-invocation spy proving reading/publishing either persisted overlay performs zero parser calls.
- [ ] Run the source-bound eligible corpus once with persistence/reopen enabled and compare shared capability results.
- [ ] Write and push `stage-02-closure.md` plus the updated ledger.

### Verification gate

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest --stacktrace
```

Then run the Section 3 corpus command. Stage 2 is blocked by duplicated shared payloads, non-atomic overlay persistence, stale-schema cache hits, unbounded overlays, cross-language fallback, or parser activity during overlay publication.

---

## Stage 3 — Official Western-language parsing

**Specification:** `LNG-INV-001`, `LNG-INV-006`, `LNG-INV-007`, Sections 5–8, 14.1, 14.2, 14.5, and Stage 3 in Section 15.

**Outcome:** English, French, German, Italian, and Spanish official Gen I–III releases resolve structurally, decode their own text tables, materialize localized overlays, and pass all 35 applicable matrix cells.

### Primary files

Create:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/WesternPokemonTextCodecs.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/language/OfficialLanguageResolver.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/WesternPokemonTextCodecsTest.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/language/OfficialWesternLanguageControlsTest.kt
docs/reports/localization/official-western-matrix.json
```

Modify:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/EngineFamilyDefinition.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/IdentityRootsStrategy.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/CoreDatasetsStrategy.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/DatasetResolvers.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/TableValidators.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RelationshipMaterializers.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializer.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/AbilityDescriptionMaterializer.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/descriptions/DescriptionCodec.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/AbilityNameCodec.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LandmarkNameCodec.kt
```

### Work checklist

- [ ] Inventory exact external controls for all 35 Western cells and record only SHA-256, family, language, expected counts, and sanitized evidence in the public matrix.
- [ ] Add red golden-vector tests for Gen I, II, and III Western codec differences, accented glyphs, controls, terminators, and invalid/truncated bytes.
- [ ] Resolve official language candidates from complete header/game-code evidence, then require structural table and codec corroboration before `RESOLVED`.
- [ ] Replace English-only family title/game-code routing with region-aware candidate routing that remains non-authoritative for hacks.
- [ ] Replace or locale-scope `POUND`/`KARATE CHOP`/`DOUBLESLAP`, `SEED`, English ability lists, and English word-shape checks; structural consumers and table geometry remain primary.
- [ ] Carry the chosen Western codec through descriptions, abilities, move text, type/nature/item/location text, records, and relationships without direct English fallback.
- [ ] Add one exact-control test row per family-language cell, including materialization and cache reopen.
- [ ] Prove missing localized optional tables fail closed in that projection without borrowing English or disabling shared numeric data.
- [ ] Run the source-bound eligible corpus once and classify every changed language/capability row.
- [ ] Generate `official-western-matrix.json`; write and push `stage-03-closure.md` plus the updated ledger.

### Verification gate

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test :catalog-store:test --stacktrace
```

Then run the Section 3 corpus command. Stage 3 cannot close with a missing/failed official Western cell, an English literal as sole production authority, mixed-language fields, or unexplained corpus regression.

---

## Stage 4 — Official Japanese and Korean parsing

**Specification:** `LNG-INV-001`, `LNG-INV-006`, `LNG-INV-007`, Sections 6–8, 14.1, 14.2, 14.5, and Stage 4 in Section 15.

**Outcome:** Japanese official Gen I–III cells and Korean Gold/Silver decode through script-aware codecs and validators, completing all 43 official matrix cells.

### Primary files

Create:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/JapanesePokemonTextCodecs.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/KoreanGen2PokemonTextCodec.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/LanguageTextPlausibility.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/JapanesePokemonTextCodecsTest.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/KoreanGen2PokemonTextCodecTest.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/language/OfficialJapaneseKoreanControlsTest.kt
docs/reports/localization/official-language-matrix.json
```

Modify:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/language/LanguageRegistry.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/language/OfficialLanguageResolver.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/TableValidators.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializer.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/AbilityDescriptionMaterializer.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/descriptions/DescriptionCodec.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LandmarkNameCodec.kt
docs/reports/localization/official-western-matrix.json
```

### Work checklist

- [ ] Inventory exact external controls for the seven Japanese family cells plus separate Korean Gold and Korean Silver controls.
- [ ] Add red Japanese golden vectors for each generation’s glyph/control/terminator behavior and malformed sequences.
- [ ] Add red Korean Gen II vectors proving multibyte consumption, controls, truncation, invalid lead/trail handling, and strict maximum-byte enforcement.
- [ ] Implement script-aware plausibility based on valid/content/control ratios and table geometry; remove Latin word-shape authority from Japanese/Korean paths.
- [ ] Resolve and materialize Japanese/Korean names, descriptions, and applicable location text without transliteration or browser translation.
- [ ] Prove malformed non-Latin text disables only its localized capability and cannot crash, overread, or contaminate another projection.
- [ ] Run every Japanese exact control and both Korean controls through parse, materialization, persistence, close, reopen, and API overlay construction.
- [ ] Run the source-bound eligible corpus once and classify every changed row.
- [ ] Publish the complete 43-cell `official-language-matrix.json`; write and push `stage-04-closure.md` plus the updated ledger.

### Verification gate

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test :catalog-store:test :companion-core:test --stacktrace
```

Then run the Section 3 corpus command. Stage 4 cannot close with any missing official cell, a single-byte-only Korean path, a Latin-only validator gating non-Latin text, or unresolved cross-projection contamination.

---

## Stage 5 — Multilingual runtime selection and semantic API

**Specification:** Sections 3.3–3.4, 9, 11, 14.3–14.4, and Stage 5 in Section 15; especially the `LIVE_RAM`/`ROM_DEFAULT` authority order and no-manual-override rule.

**Outcome:** Multilingual ROMs persist all structurally proven projections, use ROM default offline, switch to a live projection only through fenced read-only RAM evidence, and expose translation-ready semantic API messages.

### Primary files

Create:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/language/RuntimeLanguageSelectionResolver.kt
battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/ContentLanguageMemoryReader.kt
companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/PresentationMessage.kt
parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/language/RuntimeLanguageSelectionResolverTest.kt
battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/ContentLanguageMemoryReaderTest.kt
companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/PresentationMessageTest.kt
app/src/test/java/com/darkaxt/dualdex/web/ContentLanguageRuntimeTest.kt
```

Modify:

```text
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt
parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinator.kt
battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModels.kt
app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt
app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt
app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt
app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt
companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt
companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt
companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexRuntime.kt
companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt
companion-web/src/models.ts
companion-web/src/gateway.ts
app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt
```

### Work checklist

- [ ] Add compiled synthetic fixtures proving all projection tables are discovered, grouped, decoded, and persisted instead of stopping at default.
- [ ] Add red resolver tests requiring a compiled consumer, bounded address/read width, value mapping, default behavior, and table-selection evidence before a runtime layout is published.
- [ ] Integrate the language read into the existing one-request-at-a-time live-memory window flow; single-language/no-layout catalogs add no poll.
- [ ] Add red runtime tests for valid live switch, invalid value, unavailable RAM, disconnect, ROM change, session epoch change, delayed response, and retry after transient failure.
- [ ] Publish `activeRomLanguage`, `ROM_DEFAULT`/`LIVE_RAM`, ROM SHA, session epoch, state version, and projection version atomically; stale work must be discarded.
- [ ] Add an active-overlay loopback endpoint that accepts no user-authoritative language override and binds every response to ROM SHA, catalog version, language tag, and projection version.
- [ ] Add browser gateway tests discarding stale/mismatched overlays and proving live switches cause zero parser calls.
- [ ] Inventory every backend-generated string consumed as UI prose; replace it with a closed semantic code plus typed arguments or add a blocking ledger row.
- [ ] Keep raw exceptions/diagnostics outside production presentation messages and sanitized at API boundaries.
- [ ] Run focused multilingual fixtures, affected parser/catalog tests, and one source-bound eligible corpus run because parser/catalog contracts changed.
- [ ] Write and push `stage-05-closure.md` plus the updated ledger.

### Verification gate

```bash
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :parser-core:test :catalog-store:test :battle-memory:test :companion-core:test :companion-server:test :app:testDebugUnitTest --stacktrace
cd companion-web && npm test -- --run && npm run build
```

Then run the Section 3 corpus command. Stage 5 is blocked by a fixed RAM address, user/manual content-language override, polling on single-language ROMs, stale cross-session publication, last-observed offline authority, reparse on switch, or uncoded UI-consumed backend prose.

---

## Stage 6 — Interface translation last

**Specification:** `LNG-INV-004`, `LNG-INV-008`, Sections 3.5, 12, 13, 14.1, 14.3, and Stage 6 in Section 15.

**Outcome:** English, French, German, Italian, and Spanish application chrome is complete through a typed lightweight Preact module plus matching Android resources. The setting is device-global and parser-independent.

### Primary files

Create:

```text
companion-web/src/i18n/types.ts
companion-web/src/i18n/index.ts
companion-web/src/i18n/format.ts
companion-web/src/i18n/messages.en.ts
companion-web/src/i18n/messages.fr.ts
companion-web/src/i18n/messages.de.ts
companion-web/src/i18n/messages.it.ts
companion-web/src/i18n/messages.es.ts
companion-web/src/i18n/pseudo.ts
companion-web/src/i18n/i18n.test.ts
companion-web/e2e/localization.spec.ts
app/src/main/res/values-fr/strings.xml
app/src/main/res/values-de/strings.xml
app/src/main/res/values-it/strings.xml
app/src/main/res/values-es/strings.xml
app/src/test/java/com/darkaxt/dualdex/settings/InterfaceLanguageSettingsTest.kt
app/src/androidTest/java/com/darkaxt/dualdex/LocalizationPackagedAcceptanceInstrumentedTest.kt
```

Modify:

```text
companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt
companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt
app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt
app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt
app/src/main/res/values/strings.xml
companion-web/index.html
companion-web/src/models.ts
companion-web/src/gateway.ts
companion-web/src/App.tsx
companion-web/src/components.tsx
companion-web/src/gameplayCopy.ts
companion-web/src/pages/SetupPage.tsx
companion-web/src/pages/SettingsPage.tsx
companion-web/src/pages/PokedexBrowse.tsx
companion-web/src/pages/PokedexDetail.tsx
companion-web/src/pages/BattlePage.tsx
companion-web/src/pages/MapPage.tsx
companion-web/src/pages/AreaGuideDrawer.tsx
companion-web/src/pages/PartyPage.tsx
companion-web/src/pages/PartyAnalysisPage.tsx
companion-web/src/styles.css
companion-web/e2e/ui-space-regressions.spec.ts
app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt
```

Audit every other production `.tsx`, native Toast, dialog, accessibility label, and recovery string found by the Stage 6 source scan; either translate it or add a blocking ledger row with its exact file/line.

### Work checklist

- [ ] Add red compile-time dictionary tests requiring identical message keys and function argument signatures in `en/fr/de/it/es`.
- [ ] Implement `AUTO/en/fr/de/it/es`, language-only fallback from regional system locales, English fallback for unbundled locales, reactive locale state, and synchronized `document.documentElement.lang`.
- [ ] Add a parser-invocation spy proving interface-language changes only persist device-global settings and rerender state.
- [ ] Replace visible literals, ARIA/live-region text, alt text, dialogs, status/error/empty/loading copy, and `gameplayCopy` with typed messages.
- [ ] Replace translated visible strings used as control/test identity with stable IDs and independent translated labels.
- [ ] Render semantic API codes through locale-owned message functions; do not concatenate translated sentence fragments.
- [ ] Format UI dates/numbers/plurals with interface locale while normalizing ROM-name searches with active ROM content language.
- [ ] Add complete Android resources for native startup/recovery/Toast surfaces and test explicit/system fallback.
- [ ] Add an expansion pseudo-locale with delimiters and longer copy; it is test-only and cannot persist as a production setting.
- [ ] Run route behavior/accessibility tests in at least German and one other non-English locale.
- [ ] Add a packaged instrumentation regression that loads the bundled WebView, changes the interface locale, and verifies translated web/native recovery surfaces without a parser or network dependency.
- [ ] Run all five locales plus pseudo-locale at exact `538×445` CSS viewport and 85%, 100%, and 135% text scale; assert containment, reachable controls, focus restoration, readable text, scroll ownership, and virtual-row geometry.
- [ ] Confirm `LNG-D002`, `LNG-D003`, and `LNG-D004` remain explicit with their acceptance conditions rather than being represented as silently missing UI.
- [ ] Do not run the parser corpus because this stage changes UI/settings only; if unrelated parser/catalog code enters the branch, classify that scope change and run the corpus before closure.
- [ ] Write and push `stage-06-closure.md` plus the final ledger.

### Verification gate

```bash
cd companion-web && npm test -- --run && npm run build && npm run test:e2e
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :companion-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
JAVA_HOME='C:/Program Files/Zulu/zulu-21' ./gradlew :app:qaApi35DebugAndroidTest --stacktrace
```

Run the packaged-AVD localization acceptance for the bundled WebView at the same exact viewport. Stage 6 is blocked by an untranslated production string, dictionary mismatch, translated text used as behavior identity, parser/cache activity on interface changes, invalid locale fallback, inaccessible copy, or any failing compact-layout cell.

---

## 4. Final system audit

After Stage 6, create:

```text
docs/reports/localization/final-translation-system-closure.md
```

- [ ] Recheck all invariants and every requirement in Sections 1–19 of the specification.
- [ ] Verify the complete 43-cell official parser matrix and the five-locale interface matrix are source-bound.
- [ ] Verify live multilingual selection has `LIVE_RAM`/`ROM_DEFAULT` authority only and never reparses.
- [ ] Verify interface settings are device-global and content-language-independent.
- [ ] Verify all six stage reports and the ledger are internally consistent and every closure commit exists on the pushed branch.
- [ ] Verify no ROM, SaveRAM, private path, raw memory, corpus receipt, signing material, or disposable build artifact entered Git.
- [ ] Run `git diff --check`, inspect the complete branch diff, smart-sync with `fork/master`, rerun tests affected by reconciliation, commit, and push the final closure.

A release, PR merge, tag, production signing run, or physical Thor deployment remains a separate outward-facing step and is not implied by completing this implementation plan.
