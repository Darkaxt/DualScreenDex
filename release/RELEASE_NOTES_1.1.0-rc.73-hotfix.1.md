# DualDex 1.1.0-rc.73-hotfix.1

RC73 adds a capability-gated damage forecast beside the currently selected move in Battle Attack. It computes only from the unified live battle snapshot and decoded catalog semantics; if a result cannot be bounded honestly, the existing move card remains available without a guessed value.

## Selected-move forecast

- Show the damage range, percentage of the target's current HP, hits to knock out, accuracy, effectiveness, and only the conditions that are currently active and safe to reveal.
- Follow the live command owner and selected target in double battles instead of assuming the left player battler.
- Replace the forecast atomically when the attacker, target, move, or relevant state changes, while reusing it across irrelevant polling samples.
- Keep Organic mode private: hidden opponent mechanics that could materially change damage suppress the result without naming the hidden fact.
- Add no memory reader, polling loop, browser timer, animation loop, or SaveRAM authority path.

## Formula safety

- Implement integer damage arithmetic for the official Gen I, II, and III families, including random range, critical behavior, STAB, type effectiveness, burn, weather bounds, immunity, multi-hit, fixed-damage, ability, and held-item paths when their semantics are proven.
- Admit ordinary live forecasts for the five official Gen III controls whose complete mechanics surface is decoded.
- Withhold retail assumptions from Modern Emerald, Pokemon Unbound, and Pokemon Odyssey; those controls fail closed until their altered formula semantics are proven.
- Preserve the existing Attack presentation when formula evidence, live battler fields, item effects, hidden modifiers, or another result-changing input is unresolved.

## Measured compatibility

- The exact report covers 14 controls: official English Red, Blue, Yellow, Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen, plus Modern Emerald, Pokemon Unbound, and Pokemon Odyssey.
- Formula arithmetic families: 11/14 controls (78.57%).
- Runtime formula evidence and effective ordinary-move forecasts: 5/14 controls (35.71%).
- Move core and type effectiveness: 14/14 controls (100.00%).
- Organic privacy decisions: 14/14 controls (100.00%).
- Exact, bounded, absent, `NOT_FOUND`, `NOT_APPLICABLE`, and `ERROR` outcomes remain separate in the attached compatibility report; no compatibility label substitutes for the numeric evidence.

## Validation and delivery

- Damage reporter and release-policy tests passed with zero failures.
- Affected JVM and Android unit suites: 1,836 tests with 0 failures and 0 errors.
- Companion browser suite: 225/225 across 30 files.
- Android-test sources, Debug lint, unsigned release assembly, and the production TypeScript/Vite build completed successfully.
- RC73 hotfix 1 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010074`.
- DualDex remains read-only. No user device was used during implementation or candidate creation.
