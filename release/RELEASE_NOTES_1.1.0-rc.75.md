# DualDex 1.1.0-rc.75

RC75 expands the offline Portable Challenge engine with ROM-derived roles, save-scoped progress, and Organic disclosure. It does not connect to RetroAchievements at runtime or copy their trigger expressions.

## Portable challenge expansion

- Add six role-bound templates beside the six existing Tier 1 templates.
- Resolve badge, regional Pokédex, and area-collectible objectives from the active parsed catalog rather than ROM names, hashes, fixed offsets, or presumed ancestry.
- Generate stable per-area collectible objectives only for structurally proven flagged-item groups.
- Keep unsupported Gym Leader and game-specific mechanic objectives absent instead of substituting stock rules.
- Persist progress, pause, miss, reset, and first-completion evidence under the exact ROM SHA plus save identity.

## Organic presentation

- Show completed ranks and only the next unfinished rank in each challenge chain.
- Hide untouched objectives for other areas while preserving current, started, and completed objectives.
- Display bounded per-objective progress and one knowledge-safe overall completion percentage.
- Keep the full applicable inventory reachable in Discovered mode without revealing undiscovered entities through the Organic denominator.

## Measured compatibility

- Reference descriptions classified: 883/1,003 (88.04%); all 883 classified descriptions are expressible through the independent semantic vocabulary.
- Combined template applicability: 110/168 control/template slots (65.48%).
- Fully observable among applicable slots: 104/110 (94.55%).
- Validated among fully observable slots: 104/104 (100.00%).
- Fourteen exact controls were evaluated: the eleven official English Generation I–III ROMs plus Modern Emerald, Pokemon Unbound, and Pokemon Odyssey.
- The structural binders produce 1,093 deterministic concrete definitions across those controls with zero report errors.

## Validation and delivery

- Challenge engine, catalog binding, persistence, runtime, Organic-disclosure, browser, compatibility, and performance gates passed before packaging.
- Complete JVM, Android unit, lint, unsigned release assembly, and production web gates passed.
- This candidate uses Android version code `1010075`; RC74 is intentionally absent because RC73 hotfix 1 already consumed qualifier/version code 74.
- DualDex remains read-only. No user console, emulator, ROM, save, or browser session was used to create the candidate.
