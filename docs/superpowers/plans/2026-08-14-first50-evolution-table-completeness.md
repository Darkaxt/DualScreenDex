# First-50 Evolution-Table Completeness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decode complete evolution tables for all 50 ROMs in the exact first cohort and for Modern Emerald 3.5.

**Architecture:** Preserve the existing evolution codec and catalog schema. Add a structural layout path that derives the table root and row ABI from compiled consumers, and allow that typed result to replace only an incomplete legacy result. Production selection remains independent of ROM identity.

**Tech Stack:** Kotlin, JUnit 4, Gradle, existing ARMv4T decoder/reference index, `EvolutionCodec`, `CatalogParser`, SQLite catalog store.

---

### Task 1: Freeze the real failure

**Files:**
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/evolutions/EvolutionLiveRomTest.kt`

- [ ] Add SHA-bound real controls for Crippling, Crystal Advance Redux, both Dark Violet builds, DarkFire, Dreamstone, and Modern Emerald.
- [ ] Parse each exact ROM through `CatalogParser` and assert that every typed row is non-malformed and that two fresh parses produce the same edge hash.
- [ ] Run the seven real tests and retain the expected failure showing six partial tables and Modern unresolved.

Run:

```powershell
.\gradlew.bat :parser-core:test --tests '*EvolutionLiveRomTest' --rerun-tasks --no-daemon --console=plain
```

Expected RED: the six partial controls report malformed rows and Modern has no resolved evolution layout.

### Task 2: Derive layouts from compiled reference roots and unified-record pointers

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/evolutions/EmbeddedEvolutionPointerResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/evolutions/EmbeddedEvolutionPointerResolverTest.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/DatasetResolvers.kt`

- [ ] Admit only compiled-reference roots that pass the typed evolution-table sample before consuming the candidate budget.
- [ ] Derive six- or eight-byte compiled records and slot counts from exact species cardinality and complete table validation; preserve ABI tail padding in the raw record.
- [ ] Resolve source-backed evolution-list pointers only inside an independently proven unified species root/stride and require a unique aligned pointer field plus terminated record ABI.
- [ ] Reject malformed rows, inactive/out-of-domain targets, contradictory layouts, and exhausted discovery as typed failures.
- [ ] Feed only unique structurally supported layouts to `EvolutionCodec`; do not select by ROM name, SHA, source symbol, or absolute offset.
- [ ] Run the focused resolver and real-ROM tests until all seven controls pass.

### Task 3: Admit complete typed recovery over partial legacy evidence

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/DependentDatasetsStrategy.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinatorTest.kt`

- [ ] Invoke the typed resolver for ordinary Gen III even when legacy discovery returned missing or partial evidence.
- [ ] Convert a complete typed result into exact `ValidationEvidence` using decoded row and slot counts.
- [ ] Preserve the existing result when it is complete and edge-identical.
- [ ] Keep ambiguous, malformed, or budget-exhausted typed results unresolved.
- [ ] Run family-probe and architecture tests.

### Task 4: Verify persistence and exact first-50 completion

**Files:**
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/evolutions/EvolutionLiveRomTest.kt`
- Create: `docs/reports/2026-08-14-first50-evolution-completeness.md`

- [ ] Freeze the seven real edge hashes after checking Modern against its source table.
- [ ] Parse the exact first-50 cohort twice from fresh sessions.
- [ ] Assert 50/50 complete rows, zero malformed slots, deterministic edge hashes, and identical SQLite reopen results.
- [ ] Compare the original 44 edge hashes and require zero changes.
- [ ] Run focused evolution tests, parser-core tests, catalog-store tests, and `git diff --check`.
- [ ] Commit the production fix and sanitized evidence.

Verification commands:

```powershell
.\gradlew.bat :parser-core:test --tests '*Evolution*' --tests '*FamilyProbe*' --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :parser-core:test :catalog-store:test --no-daemon --console=plain
git diff --check
```
