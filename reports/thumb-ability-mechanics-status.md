# THUMB ability-mechanics experiment status

Status: **stopped and excluded from the v1.0.0 release**. A separate official-ROM-first proof of concept now supersedes the failed discovery approach, but it is not integrated into production.

## What was proven

The isolated ARM7TDMI experiment built a strict Thumb/A32 decoder, bounded control-flow analysis, and deterministic ARM/Thumb conformance runner. Against the exact Modern Emerald 3.5 source oracle, it structurally selected the damage routine at ROM offset `0x18FBE8` and proved two mechanics:

- Ability 37: attacker Attack x2.
- Ability 74: attacker Attack x2.

The catalog projection truthfully reported `ABILITY_MECHANICS` as `PARTIAL`, covering 2 of 81 named abilities. Ten additional source-known Modern mechanics were explicitly withheld rather than inferred without sufficient proof.

## Release-gate result

The required first-50 corpus gate was deterministic across two fresh sessions, but it failed decisively:

| Outcome | ROMs |
| --- | ---: |
| Proven success | 0 |
| Not applicable (Gen I/II) | 4 |
| Unavailable | 46 |
| Ambiguous, budget-exhausted, error, or semantic mismatch | 0 |

Of the 46 applicable GBA ROMs, 38 lacked the exact Modern-style low-HP anchor used by candidate discovery. The first-33 parser/family comparison also detected an Arcoiris reference-error-set difference. The deterministic packet digest was `0445027e...cdc86`.

This demonstrates that the Modern-specific resolver does not generalize. None of the experimental THUMB commits are included in v1.0.0.

## Official-ROM-first replacement POC

GLM produced an isolated successor on branch `poc/glm-official-arm7`, commit `7be7ffc` (`feat(poc): ARM7TDMI ability-mechanics resolver (Huge Power Attack x2)`). It is based on the stopped ARM7TDMI branch but adds no catalog, Android, or release integration.

The POC starts from official Emerald, FireRed Rev 1, and LeafGreen Rev 1. It proves only the common Huge Power / Pure Power mechanic:

- Ability 37: attacker Attack x2.
- Ability 74: attacker Attack x2.

For each official control it binds the selected move-details table, decoded callers, `BattlePokemon` attack and ability fields, ability comparisons, and the doubling write. Concrete ARM7 execution varies ability and attack values, verifies unrelated abilities remain unchanged, and removes the result when the doubling instructions are mutated in memory. Modern Emerald is a fourth extension control, not the discovery template.

The implementation reports 38 successes among the first 50 corpus entries for this one mechanic pair. That result is promising but not yet a release artifact: the committed report contains the official controls, while the corpus probe only prints its rows and total. Before production integration, rerun it with exact ROM rehashing, persist all 50 outcomes, and verify two fresh sessions produce identical mechanics/proof digests.

Known unsupported compiler shape: PokéClassic and Clover use binary-search ability dispatch with the effect factored through a table and helper calls, rather than the inline doubling block. They remain unavailable until helper execution or summaries can prove the same effect.

Production integration also remains blocked on the existing `CapabilityAggregationStrategy` identity gate, and other mechanics such as Guts, Marvel Scale, Thick Fat, and the pinch abilities are explicitly withheld.

## Correct restart direction

Continue from `7be7ffc`, not from the Modern-anchor resolver. First preserve a reproducible first-50 packet for the Huge Power / Pure Power proof, then integrate that single mechanic behind truthful partial capability reporting. Add other mechanics and factored helper paths only as independently proven extensions.
