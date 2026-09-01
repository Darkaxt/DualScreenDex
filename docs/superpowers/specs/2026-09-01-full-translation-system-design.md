# DualDex Full Translation System Design

**Date:** 2026-09-01  
**Status:** Approved design for staged implementation  
**Scope:** ROM-language discovery and decoding, multi-language catalog persistence, live language selection, semantic API localization, and interface translation  

## 1. Goal

DualDex must parse localized Pokémon ROM content without treating English as the implicit format. It must discover and persist every structurally valid language projection embedded in a ROM, select the active projection without reparsing, and translate the application interface independently.

The architecture is generic. Official Generation I–III releases are the first ratified set, not a permanent whitelist. Later corpus work must be able to add fan translations and structurally compatible language variants without changing the language model.

The interface is deliberately last. Parser, catalog, runtime, and API language authority must be complete before translated UI chrome is introduced.

## 2. Non-negotiable invariants

### LNG-INV-001 — ROM authority

ROM content language and localized tables come from the exact compiled ROM. Production selection must not depend on filenames, user labels, per-ROM fixed profiles, or production SHA-256 routing. Exact hashes may identify external validation fixtures and reports only.

### LNG-INV-002 — Read-only operation

Language detection and live selection are read-only. DualDex never modifies ROM bytes, SaveRAM, emulator memory, or game commands.

### LNG-INV-003 — Parse once, select many

Every validated language projection is decoded and persisted during catalog creation. Changing the active in-game language or the interface language must not invoke the parser.

### LNG-INV-004 — Separate authorities

The following are independent:

- available ROM content languages;
- active ROM content language;
- interface language.

No interface preference may alter parser arguments, catalog cache identity, or ROM text decoding.

### LNG-INV-005 — Shared data stays singular

Numeric, structural, media, map, runtime, and capability data shared across language projections is stored once. Only genuinely localized fields are stored per language.

### LNG-INV-006 — Fail closed by capability

Unknown or ambiguous language evidence disables only affected text capabilities. Structurally valid numeric features remain available. DualDex must not silently decode with English, borrow text from another projection, or mix languages within a field.

### LNG-INV-007 — Official first, extensible afterward

All applicable official Gen I–III language-family cells must be ratified before official-language parsing closes. Fan translations are added later through the same descriptors, codecs, evidence, and corpus process.

### LNG-INV-008 — UI last

The production interface translation stage cannot begin until parser-to-API language contracts and structured presentation messages are closed.

### LNG-INV-009 — No untracked gaps

Every implementation stage ends with a published blocker and deferral audit. Every unresolved item has a durable ID, spec clause, evidence, classification, target stage, owner, and exact acceptance condition. An untracked omission is a blocker.

## 3. Terminology and authority model

### 3.1 Language tag

`LanguageTag` is an extensible normalized identifier compatible with BCP 47 language subtags. The first registered values are:

- `en` — English
- `fr` — French
- `de` — German
- `it` — Italian
- `es` — Spanish
- `ja` — Japanese
- `ko` — Korean

The implementation must not encode the complete language universe as a closed official-only enum. A registry validates known descriptors while allowing later, versioned additions.

### 3.2 Available ROM languages

`availableRomLanguages` is the set of projections whose table roots, codec, record geometry, termination, and content plausibility are structurally validated for the exact ROM.

A normal official ROM usually publishes one projection. A multilingual ROM may publish several.

### 3.3 ROM default language

`romDefaultLanguage` is the projection selected by compiled ROM default behavior when no current live language value is available. It is parser-derived and persisted.

A manifest with multiple projections but no structurally defensible default is `AMBIGUOUS` and cannot advertise complete multilingual support.

### 3.4 Active ROM language

`activeRomLanguage` is selected by this authority order:

1. `LIVE_RAM` — a valid current-session bounded RAM value mapped to a persisted projection;
2. `ROM_DEFAULT` — the parser-proven default projection.

A stale, invalid, out-of-range, cross-ROM, or cross-session RAM value has no authority. DualDex immediately uses `ROM_DEFAULT` when validated RAM authority is unavailable. It does not persist a last-observed content language as offline authority. There is no routine manual ROM-content-language override; unsupported or ambiguous authority remains visible and fail-closed rather than user-forced.

### 3.5 Interface language

`interfaceLanguage` is device-global and has the values `AUTO` or an explicitly bundled interface locale.

`AUTO` resolves from the Android/system locale. If the system locale is not bundled, it falls back to English. It does not follow the ROM language implicitly.

Changing `interfaceLanguage` rerenders web and native application chrome only. It never changes `activeRomLanguage` or rebuilds a catalog.

## 4. Language-resolution model

The parser carries one immutable language result through analysis, materialization, persistence, and publication.

A conceptual model is:

```kotlin
@JvmInline
value class LanguageTag(val value: String)

enum class LanguageResolutionStatus {
    RESOLVED,
    AMBIGUOUS,
    UNKNOWN,
}

enum class LanguageEvidenceKind {
    COMPILED_CONSUMER,
    TABLE_RELATIONSHIP,
    HEADER_REGION_HINT,
    CODEC_PLAUSIBILITY,
    TERMINATOR_GEOMETRY,
    RETAIL_VALIDATION_CONTROL,
}

data class LanguageEvidence(
    val kind: LanguageEvidenceKind,
    val summary: String,
    val confidence: Int,
)

data class RomLanguageProjection(
    val language: LanguageTag,
    val codecId: String,
    val codecVersion: Int,
    val localizedTables: LocalizedTableLayout,
    val evidence: List<LanguageEvidence>,
    val status: LanguageResolutionStatus,
)

data class RomLanguageManifest(
    val defaultLanguage: LanguageTag?,
    val projections: List<RomLanguageProjection>,
    val runtimeSelection: RuntimeLanguageSelectionLayout?,
    val status: LanguageResolutionStatus,
    val diagnostics: List<String>,
)
```

Names may change during implementation, but these invariants may not:

- a projection binds language, codec identity/version, table layout, and evidence;
- the default must reference a persisted resolved projection;
- duplicate language tags are rejected;
- ambiguous candidates remain explicit;
- the same result reaches final materialization rather than being recomputed from generation.

## 5. Header and identity evidence

### LNG-HDR-001 — Preserve raw header fields

The parser must preserve enough raw header evidence to distinguish regional releases without treating sanitized display titles as the only identity:

- GB/GBC raw title bytes;
- CGB flag;
- CGB manufacturer/game identifier fields where present;
- GBA title;
- complete four-character GBA game code;
- revision and platform fields already used by family detection.

### LNG-HDR-002 — Header evidence is a hint

Regional game-code characters and recognized official header tuples seed language candidates. They do not independently prove a hack’s text language and cannot override contradictory compiled/table evidence.

### LNG-HDR-003 — Existing exact profiles do not expand

The translation system must not add production language behavior through new exact-ROM hashes. Official hashes belong in external validation controls and machine-readable evidence.

## 6. Codec architecture

### 6.1 Required contract

The current `Byte -> Char?` decoder is insufficient for Japanese control sequences and Korean Gen II multibyte text. The replacement codec contract must:

- consume one or more bytes per token;
- distinguish glyphs, whitespace, terminators, formatting controls, substitutions, and invalid sequences;
- report consumed bytes and valid/content units;
- bound reads by caller-provided start and maximum length;
- stop safely on terminator, invalid bounds, or cancellation;
- normalize presentation whitespace without losing evidence metrics;
- expose a stable codec ID and version.

Conceptually:

```kotlin
data class DecodedRomText(
    val text: String,
    val consumedBytes: Int,
    val terminated: Boolean,
    val validUnits: Int,
    val contentUnits: Int,
    val controlUnits: Int,
    val invalidUnits: Int,
)

interface PokemonTextCodec {
    val id: String
    val version: Int
    val language: LanguageTag
    val applicableGenerations: Set<Int>

    fun decode(
        rom: RomImage,
        offset: Int,
        maximumBytes: Int,
        cancellation: ParseCancellation,
    ): DecodedRomText
}
```

### 6.2 First codec families

The first ratified codec registry covers:

- Gen I Western: English, French, German, Italian, Spanish;
- Gen II Western: English, French, German, Italian, Spanish;
- Gen III Western: English, French, German, Italian, Spanish;
- Gen I–III Japanese where official releases exist;
- Gen II Korean Gold/Silver multibyte text.

A language may require generation-specific codecs. Shared implementations are allowed only when byte semantics are proven identical.

### 6.3 Codec plausibility

Plausibility must be language-aware. English lowercase/word-shape heuristics cannot validate Japanese or Korean. Each codec supplies or references bounded validation rules suitable for its script and generation.

Plausibility is corroborating evidence. It cannot make an unreferenced byte region authoritative without structural table evidence.

## 7. Structural language and table discovery

### LNG-DISC-001 — Structural evidence first

Localized table discovery prioritizes:

- compiled consumers and pointer loads;
- pointer-array relationships;
- shared record counts and generation-specific geometry;
- terminator distribution;
- neighboring numeric table relationships;
- bounded codec plausibility;
- consistent cross-table language grouping.

### LNG-DISC-002 — Literal anchors are optional

English literals such as `POUND`, `KARATE CHOP`, `DOUBLESLAP`, `SEED`, or complete English ability-name lists may be used only as locale-scoped corroboration. Missing localized equivalents cannot block a structurally proven table.

Where a literal is the only current discovery route, implementation must either:

1. replace it with structural resolution;
2. define equivalent versioned evidence for every ratified locale; or
3. mark that capability unavailable with a tracked item.

### LNG-DISC-003 — Discover all projections

When compiled structure exposes multiple localized table families, parsing continues until every bounded candidate is resolved, rejected, or recorded as ambiguous. It must not stop after finding the ROM default language.

### LNG-DISC-004 — Bounded work

Candidate count, table bytes, pointer walks, token consumption, and total localized payload are bounded. Cancellation applies throughout. Optional localization failure cannot crash or exhaust the core parser.

### LNG-DISC-005 — Projection consistency

A projection may combine a table only when its language evidence and structural family agree. A German species-name table cannot be combined with an English description table merely because both decode successfully.

Missing per-language fields remain unavailable. DualDex does not silently fall back to another ROM language.

## 8. Official ratification matrix

The first parser ratification set contains 43 language-family cells:

| Engine family | ja | en | fr | de | it | es | ko |
|---|---:|---:|---:|---:|---:|---:|---:|
| Red/Blue lineage, including Japanese Green/Blue | Yes | Yes | Yes | Yes | Yes | Yes | N/A |
| Yellow | Yes | Yes | Yes | Yes | Yes | Yes | N/A |
| Gold/Silver | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Crystal | Yes | Yes | Yes | Yes | Yes | Yes | N/A |
| Ruby/Sapphire | Yes | Yes | Yes | Yes | Yes | Yes | N/A |
| Emerald | Yes | Yes | Yes | Yes | Yes | Yes | N/A |
| FireRed/LeafGreen | Yes | Yes | Yes | Yes | Yes | Yes | N/A |

Korean Gold and Korean Silver require separate exact controls even though they share one engine-family cell. Korean Crystal, Gen I, and original Gen III are not fabricated as applicable releases.

Each applicable cell requires:

- an externally supplied exact ROM identified by SHA-256;
- raw header evidence;
- expected language manifest;
- codec golden vectors;
- successful structural family selection;
- validated species and move names;
- applicable descriptions and location text;
- cache write/reopen parity;
- zero cross-language projection contamination;
- fail-closed evidence for unavailable optional fields.

ROMs and extracted private content are never committed or attached to releases. Public evidence may publish hashes, counts, statuses, and sanitized diagnostics.

## 9. Multi-language runtime selection

### 9.1 Runtime layout

For a multilingual ROM, the parser may publish `RuntimeLanguageSelectionLayout` only when compiled ROM structure proves:

- address space and bounded address calculation;
- read width;
- mask/shift or bounded decoding rule;
- mapping from live values to persisted language tags;
- default behavior;
- evidence tying the consumer to localized table selection.

No filename, ROM hash, hardcoded RAM address, or user content-language preference may select the runtime layout.

### 9.2 Polling

Language polling reads only the proven byte/word window and follows existing one-request-at-a-time, epoch, ROM-SHA, and session-identity fences.

Polling is enabled only when the catalog contains multiple resolved projections and a valid runtime selection layout. Single-language ROMs perform no language RAM polling.

### 9.3 Outcomes and liveness

Every poll has one of these outcomes:

- `LIVE(language)` — valid current-session value mapped to a projection;
- `DEFAULT` — RAM unavailable, unsupported, invalid, or out of range;
- `DEFERRED` — a transient read is outstanding, with the existing next poll/session event as retry;
- `TERMINAL_UNSUPPORTED` — no proven runtime layout; default remains authoritative.

A language change is published atomically with active ROM SHA, session epoch, state version, and projection version. Stale work is discarded.

## 10. Catalog model and persistence

### 10.1 Shared catalog plus overlays

`ParsedCatalog` gains:

- `languageManifest`;
- shared entities with language-neutral IDs and values;
- `localizedTextByLanguage` overlays;
- immutable default-projection metadata and overlay versions needed for API publication.

A conceptual overlay contains localized fields keyed by stable entity IDs:

```kotlin
data class CatalogLanguageOverlay(
    val language: LanguageTag,
    val speciesNames: Map<Int, String>,
    val speciesDescriptions: Map<Int, String>,
    val moveNames: Map<Int, String>,
    val moveDescriptions: Map<Int, String>,
    val abilityNames: Map<Int, String>,
    val abilityDescriptions: Map<Int, String>,
    val typeNames: Map<Int, String>,
    val natureNames: Map<Int, String>,
    val itemNames: Map<Int, String>,
    val areaNames: Map<Int, String>,
    val landmarkNames: Map<Int, String>,
)
```

The implementation may group fields differently, but it must not duplicate shared stats, sprites, encounters, maps, runtime layouts, or shared structural capability evidence per language. Language-specific text capability evidence belongs to its overlay or projection metadata.

### 10.2 Language-specific capability state

Text capabilities can differ by projection. Diagnostics and API views must distinguish:

- shared structural capability state;
- per-language localized capability state;
- active projection availability.

A partially recovered language is not reported as fully complete because another projection is complete.

### 10.3 Schema migration

The language-aware catalog changes the durable contract and requires:

- a catalog SQL schema version change;
- a parser schema revision according to existing cache policy;
- metadata columns for manifest/default/codec provenance or an equivalently atomic representation;
- transactionally persisted overlays;
- exact reopen parity tests;
- bounded aggregate and per-projection encoded/inflated sizes.

Migration drops and rebuilds only catalog tables under existing policy. Save snapshots and unrelated persisted settings survive.

The cache filename/key remains the exact ROM SHA-256. Neither active ROM language nor interface language becomes part of cache identity.

## 11. API and runtime publication

### 11.1 Bootstrap contract

Bootstrap publishes:

- language manifest summary;
- ROM default language;
- active ROM language;
- authority source (`ROM_DEFAULT` or `LIVE_RAM`);
- active projection version;
- shared catalog view;
- active localized overlay.

### 11.2 Projection refresh

When RAM changes the active language:

1. runtime validates the language against the persisted manifest;
2. runtime atomically updates active language and projection version;
3. state publication notifies the client;
4. the client requests the already-persisted active overlay from a loopback endpoint;
5. the response binds ROM SHA, catalog version, language tag, and projection version;
6. the client discards stale or mismatched responses.

The browser cannot make a content language authoritative by requesting a different tag.

### 11.3 Structured semantic messages

Server-generated user-facing prose must become stable codes plus typed arguments wherever the final UI needs translation. Examples include:

- evolution conditions;
- status/stat labels;
- setup and guide-load failures;
- save and connection status;
- capability explanations intended for users.

Technical diagnostics remain technical and may remain English where they are not production user copy. Raw exceptions are never translated or displayed as normal UI prose.

## 12. Interface translation system

This section is implemented only in the final stage.

### 12.1 Initial interface locales

The first complete interface set is:

- English (`en`)
- French (`fr`)
- German (`de`)
- Italian (`it`)
- Spanish (`es`)

Japanese and Korean interface packs are tracked follow-ups. Japanese and Korean ROM parsing remains mandatory in the official parser ratification stages.

### 12.2 Settings behavior

`interfaceLanguage` is added to `CompanionSettings` and persisted as device-global:

- `AUTO` follows a bundled system locale, else English;
- explicit locale selection persists across restart and ROM changes;
- per-ROM settings cannot override it;
- a settings update changes state only and cannot start, cancel, or publish parser work.

The web and native recovery surfaces resolve the same selected locale. Before persisted settings are available, native startup uses the system-locale fallback.

### 12.3 Typed translation module

The Preact layer uses a small project-owned typed module, not a heavy dependency. It provides:

- a `SupportedInterfaceLocale` registry;
- compile-time-complete message dictionaries;
- parameterized message functions;
- locale-owned plural and word order;
- `Intl.NumberFormat` and `Intl.DateTimeFormat` helpers;
- locale-aware normalization helpers with separate interface-locale and ROM-content-locale inputs;
- stable control IDs separate from translated labels;
- reactive locale state;
- synchronized `document.documentElement.lang`.

Static and parameterized messages use the same typed contract. UI code must not construct sentences from translated fragments.

### 12.4 UI translation scope

The final stage includes all production:

- navigation and headers;
- loading, empty, error, and recovery states;
- dialogs, tabs, buttons, selectors, descriptions, and settings;
- accessibility names, alt text, status text, and live regions;
- gameplay fallback copy;
- map and Area Guide chrome;
- Party, Pokédex, Trainer, Battle, and analysis chrome;
- native Android recovery copy and Toasts;
- dates, numbers, counts, and plurals.

ROM-native names and descriptions remain direct active-overlay values and are never translated in the browser.

### 12.5 UI exclusions

Unless separately scoped, the system does not translate:

- engineering documentation;
- compatibility reports;
- release notes;
- GitHub workflows and CI logs;
- signing documentation;
- store listings;
- licenses;
- raw technical diagnostics.

## 13. Accessibility, formatting, and layout

### LNG-UI-001 — Stable behavior identifiers

Tests, routing, focus restoration, and actions must not use translated visible text as identity. Controls use stable IDs and translated labels.

### LNG-UI-002 — Formatting

Dates, UI numbers, plural categories, UI-label case folding, and interface-option search use the resolved interface locale. Searches over ROM-native names use `activeRomLanguage` so interface and content language remain independent. Game-specific units remain faithful to the intended game presentation while their surrounding UI labels are translated.

### LNG-UI-003 — Compact acceptance

English, French, German, Italian, Spanish, and an expansion pseudo-locale must pass the exact Thor `538×445` CSS viewport at 85%, 100%, and 135% text scale.

Every production route must retain:

- horizontal containment;
- reachable controls;
- correct focus restoration;
- readable text;
- an intentional scroll owner;
- synchronized virtual-row geometry where fixed heights remain necessary.

The pseudo-locale expands strings and adds visible delimiters without altering stable control IDs.

### LNG-UI-004 — Directionality boundary

The first UI ratification set is left-to-right. RTL layout is a named follow-up, not silently implied by generic language tags.

## 14. Validation strategy

### 14.1 Unit fixtures

Checked-in tests may contain synthetic byte vectors and minimal generated table fixtures. They cover:

- every codec family;
- terminators and controls;
- invalid/truncated multibyte sequences;
- plausibility metrics;
- projection grouping and contamination rejection;
- runtime value mapping;
- typed translation completeness and interpolation.

### 14.2 External official controls

Official ROM validation uses externally supplied exact files. Controls verify the matrix in Section 8 without committing ROM bytes, saves, trainer data, or private memory.

### 14.3 Cache and API

Tests prove:

- write/reopen equality for all projections;
- size-bound failures are typed and isolated;
- active/default selection survives cache reopen;
- UI settings do not change cache identity;
- active projection responses reject stale ROM SHA/catalog/projection versions;
- no parser invocation occurs on interface or live content-language changes.

### 14.4 Runtime controls

Synthetic compiled fixtures and source-backed multilingual corpus controls prove:

- structural language-field resolution;
- bounded read width;
- default behavior offline;
- live switching without reparsing;
- invalid-value fallback;
- session/identity fencing;
- reachable retry events after transient read failures.

### 14.5 Regression corpus

Parser stages compare against the current eligible corpus. Expected gains/language changes are explained; unrelated capability loss, parser errors, crashes, or privacy regressions are blockers.

## 15. Staged delivery contract

### Stage 1 — Language authority foundation

Deliver:

- extensible language/evidence types;
- complete header preservation;
- variable-width codec interface;
- English codecs adapted to the new contract;
- immutable language result carried through analysis and materialization;
- English output parity.

Acceptance:

- existing English controls and eligible corpus remain output-compatible;
- no generation-based codec recomputation remains in final materializers;
- unknown language is explicit and non-crashing;
- Stage 1 audit and ledger are committed and pushed.

### Stage 2 — Multi-projection catalog foundation

Deliver:

- shared catalog plus localized overlays;
- language manifest persistence;
- schema/cache migration;
- per-language capability evidence;
- default active overlay in bootstrap;
- bounded overlay sizes and reopen parity.

Acceptance:

- an artificial multilingual fixture persists at least two projections;
- shared data is not duplicated;
- reading and publishing either persisted overlay requires no parser call;
- stale cache and oversized projection behavior fail closed;
- Stage 2 audit and ledger are committed and pushed.

### Stage 3 — Official Western-language parsing

Deliver:

- English, French, German, Italian, and Spanish codecs for applicable generations;
- structural replacement or locale-scoping of English-only discovery paths;
- all 35 Western language-family cells ratified;
- sanitized compatibility evidence.

Acceptance:

- every applicable Western cell passes exact external control, materialization, and cache reopen;
- no required cell is deferred;
- eligible corpus regressions are classified and resolved or block closure;
- Stage 3 audit and ledger are committed and pushed.

### Stage 4 — Official Japanese and Korean parsing

Deliver:

- Japanese codecs and validation across applicable Gen I–III families;
- Korean Gen II multibyte codec and Gold/Silver controls;
- script-aware description/name validation;
- complete 43-cell official matrix report.

Acceptance:

- every remaining official cell passes;
- Korean Gold and Silver pass as separate controls;
- Latin-script assumptions no longer gate non-Latin capabilities;
- no official parser cell is deferred;
- Stage 4 audit and ledger are committed and pushed.

### Stage 5 — Multilingual runtime selection and semantic API

Deliver:

- discovery/persistence of every language table in multilingual ROMs;
- compiled runtime language-selection layout;
- bounded RAM polling and ROM-default fallback;
- atomic projection version updates;
- lazy active-overlay API refresh;
- structured user-facing semantic message codes needed by the UI.

Acceptance:

- a source-backed or generated compiled multilingual control switches projections from RAM without reparsing;
- disconnect, invalid values, and stale sessions select ROM default;
- client/server stale-response fences pass;
- every UI-consumed backend prose path is coded or explicitly ledgered;
- Stage 5 audit and ledger are committed and pushed.

### Stage 6 — Interface translation

Deliver:

- device-global `AUTO/en/fr/de/it/es` setting;
- typed Preact dictionaries and formatters;
- matching Android resources;
- complete production chrome/accessibility/error translation;
- expansion pseudo-locale;
- stable behavior/focus IDs;
- exact compact-layout acceptance.

Acceptance:

- all five locales are complete at compile time;
- settings persist without parser/cache activity;
- every production route passes behavior and accessibility checks in a non-English locale;
- exact Thor viewport/text-scale matrix passes all five locales plus pseudo-locale;
- Japanese/Korean UI, RTL, and non-production documents remain named follow-ups;
- Stage 6 audit and ledger are committed and pushed.

## 16. Mandatory blocker and deferral audit

Every stage report contains this table:

| Field | Requirement |
|---|---|
| ID | Stable `LNG-B###` blocker or `LNG-D###` deferral ID |
| Spec clause | Exact requirement ID/section |
| Stage found | Stage number |
| Classification | `STOP-SAFETY`, `STOP-CORE`, `CONVERGENCE`, `TUNING`, or `POST-SYSTEM` |
| Observed | Concrete wrong/missing outcome |
| Expected | Required outcome |
| Evidence | Test/report/log/source reference |
| Temporary disposition | Fail-closed behavior while open |
| Owner | Named implementation stage or management thread |
| Target | Exact stage/milestone |
| Acceptance | Concrete evidence that closes the item |
| Status | Open, fixed, deferred, or closed |
| Closure commit | Commit proving closure when applicable |

Closure rules:

1. `STOP-SAFETY` and `STOP-CORE` prevent stage completion.
2. A required Stage 3 or Stage 4 official language-family cell cannot be deferred.
3. A deferral without owner, target, and acceptance is a blocker.
4. Rejected or unsupported optional capabilities must show their fail-closed behavior.
5. The audit compares implementation against this specification, not merely against the stage plan.
6. Smart-sync against `fork/master` immediately before every commit.
7. Implementation, tests, evidence, stage audit, and ledger changes are committed and pushed together.
8. The next stage does not begin until the prior stage audit is public and truthful.

## 17. Seeded tracked deferrals

These are known scope boundaries, not forgotten work:

### LNG-D001 — Expanded fan-translation corpus

- **Target:** first post-official localization corpus stage.
- **Acceptance:** selected source-backed or structurally characterized fan translations publish language manifests through the same registry/codecs with no production identity hacks.
- **Current disposition:** architecture supports registration; no universal fan-translation compatibility claim.

### LNG-D002 — Japanese and Korean interface packs

- **Target:** post-Stage 6 interface-locale expansion.
- **Acceptance:** complete typed dictionaries, native resources, font/line-break review, accessibility checks, and the full compact-layout matrix.
- **Current disposition:** Japanese/Korean ROM content remains supported; interface falls back according to `AUTO`/English rules.

### LNG-D003 — RTL interface layout

- **Target:** first supported RTL interface locale.
- **Acceptance:** logical CSS properties, direction-aware icons/navigation, bidirectional text tests, and compact-layout acceptance.
- **Current disposition:** first interface set is explicitly LTR.

### LNG-D004 — Non-production document translation

- **Target:** separately commissioned documentation/release localization stage.
- **Acceptance:** named document/store locale set, owner, review process, and publication path.
- **Current disposition:** engineering and release material remains English.

## 18. Expected code impact

The implementation plan must map exact edits, but the expected boundaries are:

- parser header/model/orchestrator/family phases;
- text codecs and language-aware validators;
- all catalog text materializers that currently reconstruct English codecs;
- `ParsedCatalog`, section codec, SQL metadata/schema/migration, reader, and writer;
- runtime metadata and bounded live-memory polling;
- API models, loopback endpoints, and state versions;
- `CompanionSettings` and device-global settings persistence;
- Preact models, app shell, shared components, every production page, and typed i18n module;
- Android string resources and native recovery/Toast surfaces;
- official external-control tests, corpus evidence, runtime fixtures, web unit tests, and compact Playwright acceptance;
- stage reports and the localization blocker/deferral ledger.

## 19. Completion definition

The full translation system is complete when:

1. all 43 official parser language-family cells are ratified;
2. multilingual ROMs can persist all proven projections and follow validated live RAM selection without reparsing;
3. single-language and offline multilingual ROMs use a proven default projection;
4. interface selection is parser-independent and device-global;
5. English, French, German, Italian, and Spanish production interfaces are complete;
6. every stage audit has zero open safety/core blockers;
7. every remaining limitation has a tracked deferral with a target and acceptance condition;
8. all committed implementation/evidence is smart-synced, pushed, and reproducible without distributing ROM or private memory data.
