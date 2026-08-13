# ARM7 Semantic Ability Mechanics Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the proven licensed ARM7TDMI core onto RC24 and prove source-supported attack-stat ability mechanics from typed parser ABIs across pret Emerald, FRLG, Modern Emerald, and Pokémon Classic without integrating catalog, API, app, device, or release behavior.

**Architecture:** The parser supplies immutable `BattleMechanicsAbi` and `MoveMechanicsAbi` descriptions. A bounded ARM/Thumb semantic analyzer derives candidates from decoded call targets, CFG reachability, exact typed field loads, and use-def relationships; it normalizes inline ratios and Q4.12 helper flows into canonical mechanics and fails closed per mechanic. ROM names, hashes, symbols, absolute offsets, proximity windows, nearest-PUSH guesses, arithmetic byte signatures, and fixed retail layouts remain test diagnostics only.

**Tech Stack:** Kotlin/JVM, Gradle, JUnit 5, existing `parser-core` models, ARMv4T/Thumb typed IR adapted from MIT `gba-recomp`.

---

### Task 1: Source-family evidence matrix

**Files:**
- Create: `docs/reports/arm7-source-family-mechanics-matrix.md`

- [ ] Record the exact source commits, routine factoring, callers, battle-record fields and widths, move-record ABI, arithmetic form, and exact four target mechanics for pret Emerald, FRLG, Modern Emerald, and Classic.
- [ ] Explicitly separate code-shape variance from ABI/layout variance and record which values are source-test fixtures rather than production selectors.
- [ ] Commit as `docs: define ARM7 mechanics source controls`.

### Task 2: Port the licensed ARM7TDMI foundation

**Files:**
- Create/modify only the files introduced by donor commits `1a98b63`, `34b7705`, `2b5a70f`, `dcf6d30`, and `2702e28` under `parser-core/src/{main,test}` and `third_party/gba-recomp`.

- [ ] Review the complete donor diffs and confirm no mechanics resolver, fail-open memory, ROM-specific selector, dirty experiment, or catalog integration is included.
- [ ] Cherry-pick the five reviewed commits in dependency order.
- [ ] Run `./gradlew.bat :parser-core:test --tests '*UpstreamFixtureIdentityTest' --tests '*ThumbIrDecoderTest' --tests '*ThumbDecodeExhaustiveTest' --tests '*ArmDecoderTest' --tests '*Arm7MachineVectorTest' --tests '*Arm7ConformanceRomTest'` and require exit 0.

### Task 3: Typed semantic ABI contract

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/analysis/BattleMechanicsAbi.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/analysis/BattleMechanicsAbiTest.kt`

- [ ] Write RED tests requiring positive stride, contained typed fields, distinct attacker/defender role descriptions, an exact selected move-table root/stride/type/category fields, and support for both u8 retail and u16 Classic ability fields.
- [ ] Run the focused test and confirm compilation fails because `BattleMechanicsAbi` is absent.
- [ ] Implement immutable `ScalarField(offset, width)`, `BattleRecordAbi(stride, ability, attack, defense, specialAttack, specialDefense, hp, maxHp, status)`, `MoveMechanicsAbi(tableRoot, stride, power, type, category?)`, and `BattleMechanicsAbi(record, move, abilityDomain, roleContract)` with constructor invariants only.
- [ ] Run the focused test and require pass; commit as `feat: define typed battle mechanics ABI`.

### Task 4: Bounded mixed ARM/Thumb semantic CFG and use-def

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/analysis/arm7/Arm7SemanticFlow.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/analysis/arm7/Arm7SemanticFlowTest.kt`

- [ ] Write RED tests for decoded BL-target function roots, ARM/Thumb interworking, exact PC-relative literal values, typed load provenance, conditional reconvergence, complete returns, and typed budget/unsupported/invalid-memory outcomes.
- [ ] Confirm RED because `Arm7SemanticFlow` is absent.
- [ ] Implement a worklist CFG over typed instructions. Function eligibility begins only at decoded call targets and requires a complete reachable return graph; calls are summarized explicitly and unknown callees yield an unsupported dependency instead of neutral values.
- [ ] Implement register-origin propagation for parameters, constants, exact literal addresses, typed memory fields, arithmetic, comparisons, and calls. No raw opcode matcher may grant eligibility.
- [ ] Run the focused tests and existing ARM7 core tests; commit as `feat: add typed ARM7 semantic flow`.

### Task 5: Canonical per-mechanic proof model

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/analysis/SemanticAbilityMechanics.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/analysis/SemanticAbilityMechanicsTest.kt`

- [ ] Write RED tests for canonical `(abilityId, target, predicates, effect)` tuples, sorted decoded-instruction evidence, per-dependency mutation evidence, deterministic proof digests, and independent omission of one unsupported mechanic without discarding siblings.
- [ ] Implement `AbilityPredicate`, `MechanicEffect.Multiply`, `SemanticMechanic`, `MechanicProof`, and typed resolved/unavailable/ambiguous/budget/unsupported/invalid outcomes.
- [ ] Require exact equality and no-extra behavior; run focused tests and commit as `feat: model fail-closed semantic mechanics proofs`.

### Task 6: Real-source semantic extraction

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/analysis/Arm7SemanticMechanicsResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/abilities/analysis/SourceControlMechanicsLiveRomTest.kt`

- [ ] Write the live-ROM RED test first. Tests construct typed ABIs from the source matrix, parse the already-selected move/ability layouts, run two fresh sessions, and assert exact/no-extra target tuples. FireRed and LeafGreen are corroborating samples of one FRLG family.
- [ ] Run the live control test and confirm RED because the resolver is absent.
- [ ] Implement eligibility from decoded call roots plus use-def proof of typed battle fields and selected move-table access. Normalize semantic `x2`, `x3/2`, and Classic Q4.12 `MulModifier`/`ApplyModifier` flows without matching their byte encodings.
- [ ] Prove ability compare, required status/move-category predicate, transform, and writeback independently. Mutation of one dependency removes only that mechanic. Unsupported branches omit only dependent mechanics.
- [ ] Run the live controls until exact results are: retail Emerald and FRLG four source tuples; Modern the same four; Classic its source tuples with physical-move predicates for Huge/Pure/Hustle/Guts and status for Guts.
- [ ] Run all focused ARM7 and semantic tests; commit as `feat: prove ARM7 mechanics across source families`.

### Task 7: Isolated gate report

**Files:**
- Create: `docs/reports/arm7-semantic-mechanics-core-gate.md`

- [ ] Record source commit and ROM SHA only as test provenance, every proof stage per mechanic, failures by stage, deterministic run comparison, and the exact deferred first-50 command.
- [ ] Run `./gradlew.bat :parser-core:test` plus the four real controls and confirm exit 0 before any completion claim.
- [ ] Verify `git diff d2182c6 -- app parser-cli` is empty and no catalog/API/app/device/release path changed.
- [ ] Commit the report as `docs: record ARM7 semantic mechanics core gate`.

## Self-review

- The plan covers all four source controls, both variance axes, typed parser inputs, ARM/Thumb semantics, inline and Q4.12 arithmetic, per-mechanic fail-closed proof, and the no-integration boundary.
- No production selection step depends on ROM name, SHA, source symbol, absolute offset, proximity, nearest PUSH, or arithmetic signatures.
- The first-50 corpus is explicitly deferred until the real-source gate passes.
