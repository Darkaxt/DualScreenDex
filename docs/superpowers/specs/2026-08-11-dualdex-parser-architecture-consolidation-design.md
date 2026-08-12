# DualDex Parser Architecture Consolidation Design

| Field | Decision |
| --- | --- |
| Status | Approved direction for implementation |
| Corpus boundary | Freeze after reviewed ROM index 33 until the redesign gate passes |
| Primary scope | Static ROM analysis, capability evidence, and catalog materialization |
| Adjacent scope | Complete the already-started save-detected level-up ruleset path |
| Deferred | Map reconstruction and general Thumb CFG/data-flow interpretation |

## 1. Purpose

The parser has accumulated broad ROM support through evidence-backed fixes, but several central files now combine too many responsibilities. `DatasetResolvers`, `FamilyParsers`, `TableValidators`, and `PokemonDatasetValidators` each mix discovery, candidate ranking, structural validation, decoding, orchestration, and review policy. Catalog materializers also contain discovery logic for some datasets. This makes a local fix capable of changing unrelated ROM behavior and encourages format-specific fallthroughs.

The redesign establishes stable internal boundaries before corpus validation continues beyond index 33. It is behavior-preserving except for fixes already established by RED-first tests, including bounded reference indexing, cross-format learnset competition, typed alternate rulesets, and save-detected Modern Emerald level-up selection.

The goal is not fewer files by itself. The goal is one auditable path from ROM evidence to a resolved dataset and one shared decoder interpretation from validation through materialization.

## 2. Architectural invariants

1. A ROM analysis creates one immutable analysis session. Expensive indexes are built lazily at most once per session and reused by every resolver.
2. Discovery, validation, selection, and materialization are separate stages with explicit typed inputs and outputs.
3. A materializer never scans for candidate tables, ranks candidates, or silently changes record formats.
4. A resolver never publishes decoded catalog content. It publishes layout, format, provenance, coverage, and review evidence.
5. Validation and materialization use the same dataset codec. A record cannot validate under one ABI and materialize under another.
6. Every extent calculation uses checked `Long` arithmetic before conversion to an indexed `Int`.
7. Candidate and scan budgets are explicit deterministic work limits. Budget exhaustion produces reviewable evidence; it never truncates a ranking and never relies on cancellation timeouts.
8. Offsets, ROM order, and proximity do not resolve substantive ties. They may order diagnostics only.
9. Multiple tables are retained as alternatives only when direct compiled consumer or selector evidence binds them as alternatives.
10. Semantic-domain projection may exclude independently proven structural slots. An active malformed row remains a truthful gap.
11. Parser family and format generation are separate concepts. Families compose format codecs and discovery strategies instead of scattering `generation == N` decisions.
12. Exact ROM profiles may supply authoritative roots only for an exact identity match. A derived ROM treats the same roots as inherited hypotheses.
13. Unsupported or ambiguous evidence fails closed without clearing independently validated capabilities.
14. No ROM digest, filename, corpus index, or one-off offset selects a derived-ROM implementation path.

## 3. Analysis session and evidence index

`RomAnalysisSession` is created once by `ParserOrchestrator` and passed to every family probe. It owns:

- immutable `RomImage` and `RomHeader`;
- exact-profile identity, if any;
- immutable analysis limits;
- one lazy bounded `GbaReferenceIndex` for GBA ROMs;
- bounded caches for explicitly supported compiled-evidence queries;
- diagnostics for budget exhaustion or unavailable evidence.

The reference index records bounded literal-load evidence by ROM target. Counts are used as corroboration, and a bounded set of instruction sites is retained when a direct consumer must be proven. Building the index scans the ROM once. Dataset resolvers may query it but may not rebuild private target maps.

Narrow compiled-pattern extractors, such as the Modern Emerald SaveBlock1 level-up selector, receive the session and already-validated roots. They must state and prove a bounded contract. They are not general Thumb interpreters. An extractor that cannot uniquely prove its inputs returns no selector.

## 4. Typed resolution model

The shared resolution kernel defines:

```text
DatasetKind
CandidateSource
DatasetCandidate<TLayout>
CandidateStrength
DatasetResolution<TLayout>
ResolutionLimits
```

`CandidateSource` distinguishes exact profile, published header, direct compiled consumer, compiled reference, inherited family layout, and explicitly permitted structural anchor. It never contains a ROM-specific name.

Every `DatasetCandidate` carries:

- dataset kind and exact layout/record format;
- source provenance;
- compiled-reference count and bounded sites where relevant;
- raw structural coverage;
- semantic active-domain coverage where available;
- format-specific quality evidence;
- ambiguity, recovery, anomaly, and manual-review provenance;
- a stable candidate identity used only for deduplication and diagnostics.

`DatasetResolution` is a sealed outcome:

```text
Resolved
Partial
Ambiguous
Unavailable
BudgetExceeded
```

Each outcome converts to the existing public capability/report model through one adapter. No resolver constructs ad hoc `CapabilityEvidence` or interprets report policy independently.

## 5. Candidate eligibility and ranking

Selection occurs only after eligibility. A candidate is eligible when its bounds, format decoder, cross-references, required evidence, and dataset-specific minimum structural shape pass.

Authority is ordered as follows:

1. exact-identity published/profile evidence;
2. direct compiled consumer or selector evidence;
3. published-header evidence corroborated by compiled references;
4. compiled-referenced structural evidence;
5. inherited family evidence;
6. unreferenced structural discovery, only for a dataset whose specification explicitly permits it.

Within one authority tier, comparison is lexicographic:

1. semantic active-domain completeness;
2. raw structural coverage;
3. independent compiled-reference strength;
4. dataset-specific structural quality.

Offset, proximity to an inherited root, and enumeration order are excluded from strength. If independent candidates remain equal, the result is `Ambiguous`. Deterministic offset ordering is used only when serializing the ambiguity report.

Reference count is table authority, not row liveness. Row liveness comes from an independently resolved semantic domain, canonical mapping, published active-order table, or another dataset-specific proof. This prevents one corrupted unused slot from rejecting a good table while ensuring a reachable corrupted row remains partial.

## 6. Dataset units

Each dataset is represented by a focused resolver and codec. The final package organization is responsibility-based:

```text
parser/analysis/
  RomAnalysisSession
  ResolutionLimits
  GbaReferenceIndex
  CompiledEvidenceQuery

parser/resolution/
  DatasetResolution
  DatasetCandidate
  CandidateSelector
  CapabilityEvidenceAdapter

parser/dataset/core/
  SpeciesNameResolver
  BaseStatsResolver
  MoveResolver

parser/dataset/descriptions/
  DescriptionResolver
  DescriptionCodec

parser/dataset/evolutions/
  EvolutionResolver
  EvolutionCodec

parser/dataset/learnsets/
  LearnsetResolver
  LearnsetCodec
  LevelUpSelectorExtractor

parser/dataset/abilities/
  AbilityResolver
  AbilityDescriptionResolver

parser/dataset/types/
  TypeChartResolver
  TypeChartCodec

parser/dataset/media/
  SpriteResolver
  SpriteCodec

parser/dataset/encounters/
  EncounterResolver
  EncounterCodec

parser/dataset/acquisition/
  MoveAcquisitionResolver
  MoveAcquisitionCodec
```

Generation-specific codecs may live beside a dataset when they are private implementation details. Shared platform primitives remain in focused `io`, `text`, and compression packages.

The existing large facade objects may remain temporarily during migration, but the final gate requires them to contain no discovery, ranking, or decoding logic. Tests and production callers migrate to the focused units before the facades are deleted or reduced to thin compatibility adapters.

## 7. Family composition and analysis phases

`EngineFamilyDefinition` replaces most conditionals currently embedded in `FamilyParsers`. It declares:

- header/identity scoring rules;
- compatible exact and inherited profiles;
- text codec and platform format family;
- applicable datasets;
- dataset strategy composition;
- capability applicability, independent of whether a dataset resolves.

`FamilyProbeCoordinator` executes phases with explicit dependencies:

1. identity and exact/published roots;
2. core names, counts, stats, moves, and type IDs;
3. semantic species domain;
4. dependent datasets such as descriptions, relationships, abilities, media, encounters, and acquisition;
5. capability aggregation and immutable resolved layout.

A phase cannot read a later phase. Recovery of a base-stat layout occurs before consumers such as type-chart and ability resolution. Family scoring remains separate from dataset availability.

`ParserOrchestrator` creates the session, runs platform-compatible family definitions, selects ancestry, applies semantic projection through the shared evidence adapter, and returns the public parse result. It does not implement dataset-specific policy.

## 8. Catalog materialization

Catalog materializers consume only `ResolvedRomLayout` and the corresponding dataset codec. They may decode, normalize, join, and persist records. They may not:

- search for alternative roots;
- build reference maps;
- infer a different record format;
- suppress ambiguity;
- turn a missing active record into a fabricated sibling value unless an explicit resolved inference policy permits it.

Where validation needs row-level state, the codec returns `Decoded`, `StructuralEmpty`, or `Malformed` with bounded evidence. The validator aggregates those states; the materializer decodes the selected layout through the same codec implementation.

Catalog schema changes remain additive when possible and bump `parserSchemaVersion` when persisted interpretation changes. Legacy save/catalog JSON receives explicit migration/default tests.

## 9. Save-detected level-up ruleset

The already-started Modern Emerald work is completed as part of this gate because it exercises the new evidence flow:

1. the ROM resolver validates multiple typed level-up tables;
2. a bounded compiled extractor proves a SaveBlock1 byte/mask branch between those validated roots;
3. the catalog stores a selector descriptor on each affected level-up ruleset;
4. the checksum-valid reconstructed SaveBlock1 evaluates the descriptors;
5. Android `AUTO` selects the uniquely detected level-up table;
6. multiple tables without valid save evidence leave `AUTO` unresolved;
7. a manual choice is an explicit recovery/debug override.

The selector is scoped to `LEVEL_UP`. It does not claim Egg or TM selection until those datasets gain separately resolved alternatives and selector bindings.

## 10. Failure and review policy

Failures are dataset-local. A failed description resolver cannot clear valid stats. A family selection score cannot turn a missing capability into success.

The shared evidence adapter preserves:

- ambiguity;
- budget exhaustion;
- active incomplete rows;
- explicit validator recovery/anomaly review;
- raw versus semantic coverage;
- candidate provenance;
- stable, deterministic reasons.

Informational structural trimming does not force manual review when an authoritative semantic domain is complete. Explicit recovery or anomaly evidence remains reviewable even when semantic coverage is complete.

## 11. Migration strategy

The refactor proceeds vertically by dataset and remains buildable after each step:

1. Characterize current approved behavior and establish architectural dependency tests.
2. Introduce the analysis session, limits, typed resolution model, and evidence adapter.
3. Migrate descriptions, evolutions, and learnsets, including the save selector.
4. Migrate core names/stats/moves, abilities, and type charts.
5. Migrate sprites, encounters, and move acquisition.
6. Replace `FamilyParsers` internals with family definitions and the phased coordinator.
7. Remove discovery and format inference from materializers.
8. Delete or reduce the old monoliths after all callers and tests migrate.
9. Complete persistence migration tests and schema audit.
10. Run the complete architecture, module, live-control, and corpus 1–33 gates.

No corpus index above 33 is parsed during migration. If a refactor changes a stable observation, the differential gate requires an explicit explanation or correction before the baseline advances.

## 12. Test strategy

### Unit contracts

- candidate authority and tie behavior across every source tier;
- reference count corroboration without offset tie-breaking;
- checked arithmetic and every configured budget boundary;
- one decoder interpretation shared by validation and materialization;
- per-row structural-empty, active-missing, malformed, and recovered states;
- family applicability independent from generation;
- exact-profile authority limited to exact identity;
- selector proof, unrelated-global decoy rejection, save evaluation, and unresolved Auto.

### Architecture contracts

- one GBA reference index build per analysis session;
- dataset materializers do not import discovery or candidate-selection packages;
- resolvers do not import catalog persistence/UI modules;
- no whole-ROM target-map construction outside the bounded analysis index;
- no substantive candidate comparator contains offset, distance, or enumeration order;
- every alternative ruleset has a typed format and direct alternative-set evidence;
- legacy monolith facades contain no implementation logic at the final gate.

### Regression controls

- all JVM, Android unit, web, release-policy, lint, and build gates;
- official Gen I, II, and III controls;
- representative derived controls for each supported ROM family and ABI;
- live Clover, Modern Emerald, and Unbound catalog materialization;
- checksum-valid Modern Emerald SaveRAM selecting the expected level-up table;
- SQLite reopen/integrity and old-JSON migration;
- deterministic compatibility observations and corpus rebaseline through index 33.

## 13. Completion gate

The redesign is complete only when:

1. focused dataset units own discovery, validation, format decoding, and ranking through the shared contracts;
2. family orchestration is dependency-phased and no longer a monolithic implementation;
3. materializers contain no discovery or implicit ABI selection;
4. one bounded reference index serves the full GBA analysis;
5. ambiguity, partial coverage, recovery, and budget exhaustion remain explicit;
6. the Modern Emerald live save selects its compiled-proven level-up table while unsupported selector scope remains honest;
7. all automated module and architecture tests pass;
8. required live controls and persistence checks pass;
9. corpus indices 1–33 pass the differential review gate with every delta acknowledged or corrected;
10. the compatibility baseline remains frozen at 33 until all preceding conditions hold.

Only after this gate may validation continue with index 34.

