# THUMB ability-mechanics experiment status

Status: **stopped and excluded from the v1.0.0 release**.

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

## Correct restart direction

Any future attempt should begin with official Emerald and FireRed/LeafGreen controls, establish family-independent battle-routine discovery from validated callers and battle-structure data flow, and prove common retail mechanics first. Modern Emerald should then serve only as an extension oracle for additional abilities—not as the discovery template.
