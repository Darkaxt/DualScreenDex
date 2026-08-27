# Stage 5 audit — Selected-move Damage Forecast

Date: 2026-08-27  
Specification: `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`  
Numeric evidence: `docs/reports/passive-insights-progress/damage-forecast-compatibility.json`

## Result

Stage 5 has **0 blockers** and **0 errors**. Damage Forecast is a capability-gated projection of the existing immutable battle snapshot. It does not read memory, SaveRAM, or the ROM independently, and it does not introduce a poller or browser render loop.

The required 14 controls produce these independently measured outcomes:

| Required input or calculation | Exact | Bounded | Absent | Not found | Not applicable | Error | Usable / applicable |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Formula arithmetic family | 11 | 0 | 0 | 3 | 0 | 0 | 78.57% |
| Runtime formula evidence | 5 | 0 | 0 | 9 | 0 | 0 | 35.71% |
| Live battler core | 11 | 0 | 0 | 3 | 0 | 0 | 78.57% |
| Move core fields | 14 | 0 | 0 | 0 | 0 | 0 | 100.00% |
| Type effectiveness | 14 | 0 | 0 | 0 | 0 | 0 | 100.00% |
| Same-type bonus | 5 | 0 | 0 | 9 | 0 | 0 | 35.71% |
| Live status | 11 | 0 | 0 | 3 | 0 | 0 | 78.57% |
| Critical arithmetic | 11 | 0 | 0 | 3 | 0 | 0 | 78.57% |
| Weather uncertainty | 0 | 5 | 0 | 9 | 0 | 0 | 35.71% |
| Multi-hit live semantics | 0 | 0 | 0 | 14 | 0 | 0 | 0.00% |
| Fixed-damage live semantics | 0 | 0 | 0 | 14 | 0 | 0 | 0.00% |
| Ability modifier | 5 | 0 | 0 | 3 | 6 | 0 | 62.50% |
| Held-item modifier | 0 | 0 | 0 | 11 | 3 | 0 | 0.00% |
| Organic privacy decision | 14 | 0 | 0 | 0 | 0 | 0 | 100.00% |
| Effective ordinary-move forecast | 5 | 0 | 9 | 0 | 0 | 0 | 35.71% |

Exact and bounded output modes are both available for the five official Gen III controls; the absent path is validated for all 14. Official Gen I/II formula arithmetic is golden-tested, but its runtime formula proof is not yet decoded into the catalog, so those nine non-admitted controls receive no stock forecast. Modern Emerald, Unbound, and Odyssey deliberately reject the retail formula surface.

## Specification cross-check

| Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification |
| --- | --- | --- | --- | --- | --- |
| Section 3: one transient authority | The existing `BattleMemorySample` is retained inside `ResolvedGameSnapshot`; `ProductionCompanionRuntime` assembles the forecast from that replacement snapshot only | Resolver retention tests and runtime/API tests | Official Gen I/II/III source layouts and real parsed catalogs | No feature-specific reader or recovery pipe | SATISFIED |
| Sections 10.1 and 10.2: selected-move placement and player result | Battle Attack keeps its selected move, move metadata, effectiveness, and adds HP, target-HP percentage, hits, accuracy, conditions, and bounded explanation | Exact, bounded, absent, and forbidden-copy browser tests | Five admitted official Gen III controls expose exact and bounded output modes | Forecast remains adjacent to the move | SATISFIED |
| Section 10.3: immutable calculation input | `DamageForecastInput` contains formula evidence, attacker, target, move, effectiveness, and proven/bounded modifiers; model validation rejects malformed inputs | Model and calculator tests | Red, Crystal, and Emerald consume actual parsed Tackle records | One typed input reaches the calculator | SATISFIED |
| Section 10.3: no name/hash/offset/ancestry runtime selection | Formula policy consumes the decoded complete mechanic surface and rejects incomplete or altered surfaces; identities appear only in the offline report | Mutation and policy control tests | Ruby, Sapphire, Emerald, FireRed, and LeafGreen admit; Modern Emerald, Unbound, and Odyssey reject | No ROM identity switch exists in runtime policy | SATISFIED |
| Section 10.4: exact, bounded, and absent | Calculator produces exact values only from proven inputs, finite enclosure for weather alternatives, and absence for unbounded effects, status moves, held items, hidden relevant abilities, or missing fields | Dedicated model/calculator/assembler/API/UI tests | Numeric report retains each outcome independently | No stock fallback or technical error card | SATISFIED |
| Section 10.5: Organic privacy | Candidate target abilities are considered without naming them; a possible damage-changing hidden ability withholds the result | Organic assembler and UI-copy assertions | Organic decision is 14/14 (100.00%) | Forecast cannot identify a hidden target ability | SATISFIED |
| Section 10.6: official arithmetic paths | Gen I level-doubling criticals, Gen II/III multiplier criticals, physical/special, STAB, effectiveness, random, status, weather, multi-hit, fixed damage, immunity, ability, and item modifiers are independent | Golden calculator suites | Real Red, Crystal, and Emerald move/type inputs establish the three formula families | Required calculation paths are covered | SATISFIED |
| Section 10.6: altered hacks fail closed | No formula is supplied without the admitted semantic surface; move-table similarity is insufficient | Real-control policy rejection tests | Modern Emerald, Unbound, and Odyssey ROMs plus available source roots | 3/3 altered controls reject retail results | SATISFIED |
| Section 10.6: double owner/target and stability | Assembler uses resolved command-owner battler and automatic/manual selected opponent; memoizer replaces by immutable input equality | Double-owner, manual-target, late-stable, and irrelevant-poll tests | Resolver layouts provide the authoritative owner/target sample | No stale intermediate forecast is retained | SATISFIED |
| Section 12: theme, readability, accessibility, ordinary-copy ban | Forecast uses the existing Battle scroll owner, four-column composition, ROM-derived tokens, labelled section, and player-facing copy only | 1024×768 layout, theme, production UI, and full browser build/tests | Same presentation contract is ROM-independent | No debug or provenance text escapes | SATISFIED |
| Section 14: no duplicate work or retained growth | One memoized input/result pair is retained; counters record calculations, CPU time, and retained input count in the existing Debug-only performance stream | Repeated identical samples preserve object identity and CPU/count values; source test rejects browser timers/fetch/animation loops | Existing minute heartbeat receives the counters | No new memory loop or sustained render loop | SATISFIED |
| Section 15.1: required corpus | Reporter requires exact control IDs, SHA-256, successful persisted/reopened catalogs, and zero source errors | Reporter rejects missing, duplicate, unexpected, or erroneous rows | 11 official controls plus Modern Emerald, Unbound, and Odyssey | 14/14 evaluated, 0 errors | SATISFIED |
| Sections 15.2 and 17: numeric evidence and stage audit | JSON separates six outcome states per mechanic and raw counts; this table records implementation, automation, and real evidence | Reporter tests and all affected suites | Committed report contains `errors: []` | No vague compatibility label is used | SATISFIED |

## Explicit non-coverage

- Runtime Gen I/II formula evidence: **0 / 6 (0.00%) — 6 NOT_FOUND**. Their arithmetic families are tested, but the ordinary UI withholds the forecast.
- Source-backed hack runtime formula evidence: **0 / 3 (0.00%) — 3 NOT_FOUND**. Retail Gen III semantics are rejected.
- Live multi-hit semantics: **0 / 14 (0.00%) — 14 NOT_FOUND**.
- Live fixed-damage semantics: **0 / 14 (0.00%) — 14 NOT_FOUND**.
- Held-item modifiers: **0 / 11 applicable (0.00%) — 11 NOT_FOUND; 3 Gen I NOT_APPLICABLE**.
- Gen III ability modifiers: **5 / 8 applicable controls (62.50%) — 3 NOT_FOUND; 6 Gen I/II NOT_APPLICABLE**.

These are field/mechanic evidence states, not deferred substitutes. When any unresolved fact can change the result outside a proven finite range, the forecast is absent and the existing move card remains usable.

## Verification commands

```text
node --test tools/reports/damage-forecast-compatibility.test.mjs
./gradlew :battle-memory:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain
npm test
npm run build
```

The final release verification results are recorded in `damage-forecast-release.md`.
