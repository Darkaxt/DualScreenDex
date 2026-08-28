# RetroAchievement semantic recovery runtime audit

Date: 2026-08-28

Specification: `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`

Plan: `docs/superpowers/plans/2026-08-28-retroachievement-semantic-recovery.md`

## Outcome

The 120 descriptions rejected by the original regex vocabulary are now classified and expressible. The authenticated 1,003-record corpus therefore has **1,003/1,003 semantic classifications (100.00%)**, **1,003/1,003 expressible descriptions (100.00%)**, and **0/1,003 unclassified descriptions (0.00%)**.

This recovery adds **0/120 exact runtime equivalents (0.00%)**. That number is deliberate: semantic classification does not prove that the current unified snapshot can observe the full completion, reset, miss, and temporal boundary of an objective. No APK behavior, player-facing challenge, version, tag, or release changes in this commit.

RetroAchievements trigger bytecode or trigger expressions imported: **0**.

## Recovery inventory

| Recovery path | Classified | Percentage of recovered 120 | Exact runtime equivalents added | Not found |
| --- | ---: | ---: | ---: | ---: |
| Persistent source fact | 56 | 46.67% | 0 | 56 |
| Normalized live rule | 13 | 10.83% | 0 | 13 |
| Game-specific adapter | 43 | 35.83% | 0 | 43 |
| Sequence-sensitive | 8 | 6.67% | 0 | 8 |
| **Total** | **120** | **100.00%** | **0** | **120** |

`NOT_FOUND` means the exact runtime fact or adapter is absent. It is not `NOT_APPLICABLE`: the objective has an intelligible semantic meaning and may become applicable after source-backed implementation.

## Evidence boundary

The committed override registry stores exactly six fields per recovered reference:

```text
source game ID
achievement ID
source description SHA-256
semantic family
portability tier
recovery path
```

It stores no title, description, trigger, memory expression, address, offset, ROM filename, ROM SHA, executable predicate, or APK asset. Overrides fail closed on duplicate identity, orphaned identity, stale description fingerprint, unknown semantic family, unknown recovery path, extra field, or portability-tier mismatch.

## Unified snapshot audit

The resolved snapshot already provides useful partial evidence:

- trainer identity, money, play time, badges, and Trainer Card stars;
- seen/caught species;
- Party and storage individuals, including stable identity, species, level, egg state, gender, friendship, held item, status, and moves when decoded;
- current area and map position;
- game clock;
- decoded bag pockets and decoded event-flag set where the active generation exposes them;
- battle activity, encounter kind, battlers, selected/executed moves, HP, status, types, ability, held item, and battle outcome where supported.

Those fields are insufficient for an exact recovered objective for these reasons:

| Recovery path | Missing proof |
| --- | --- |
| Persistent source fact | The snapshot exposes raw decoded event-flag IDs but the parsed catalog does not map the relevant flags to normalized story, item, choice, champion, facility, or NPC-service roles. Consuming a number directly would recreate the forbidden address/byte-expression coupling. |
| Normalized live rule | Individual and battle samples expose parts of the condition, but the semantic projector does not retain all required transitions or battle-epoch counters. Examples include evolution cancellation, one-battle Pay Day proceeds, no-damage turn counts, hatch provenance, natural thaw without item use, and exact move-effect occurrence. |
| Game-specific adapter | No current control publishes a proven mechanic adapter. Yellow partner reactions, Trainer Tower modes, size records, special shops, Pokéblock results, and scripted services therefore remain absent. |
| Sequence-sensitive | Current polling does not prove every forbidden collision, warp, wall touch, trainer defeat, Flash use, note timing, or continuous ledge interval. A final state cannot safely reconstruct the required path. |

## Source availability

All eleven official reference games have a corresponding local source foundation:

| Reference games | Local source foundation |
| --- | --- |
| Red and Blue | `pokered` |
| Yellow | `pokeyellow` |
| Gold and Silver | `pokegold` |
| Crystal | `pokecrystal` |
| Ruby and Sapphire | `pokeruby` |
| Emerald | `pokeemerald` |
| FireRed and LeafGreen | `pokefirered` |

These sources can support future normalized catalog roles and typed adapters. They do not justify reading source symbol offsets directly at runtime. A future implementation must resolve a structural catalog role, project it through `ResolvedGameSnapshot`, and disappear when that role cannot be proven for a modified ROM.

## Specification cross-check

| Requirement | Result | Evidence |
| --- | --- | --- |
| Section 4.3 fail-closed classification | Satisfied | All 120 overrides are exact-ID and description-fingerprint bound; 1,003/1,003 records classify. |
| Section 5.1 single current-state authority | Satisfied | No runtime reader, SaveRAM parser, raw-event consumer, or alternate state pipe was added. |
| Section 5.3 typed events | Deferred | The 13 normalized live rules need additional stable typed transitions before an exact challenge can exist. |
| Section 5.5 applicability | Satisfied | All 120 exact runtime equivalents remain absent because their full inputs are not proven. |
| Section 7.3 Organic policy | Satisfied | No new challenge title, target, entity, or completion enters ordinary UI. |
| Section 11.1 bounded predicate vocabulary | Satisfied | All 120 semantics fit the closed predicate/adapter model; no expression interpreter was added. |
| Section 11.2 portability tiers | Satisfied | Recovered rows are 69 Tier 2 and 51 Tier 3; Tier 4 is 0. |
| Section 11.3 dynamic instantiation | Deferred | Normalized roles/adapters must be implemented and tested separately before runtime definitions bind. |
| Section 11.5 non-goals | Satisfied | Runtime network calls, accounts, trigger bytecode, memory writes, and official unlock claims remain absent. |
| Sections 15 and 17 numeric evidence | Satisfied | Classification and recovery-path counts are generated, schema-validated, tested, and reported separately from runtime support. |

## Tracked next work

The next safe implementation order remains:

1. normalized source-backed persistent milestone roles;
2. generic live transition facts and battle-epoch counters;
3. exact game-mechanic adapters;
4. sequence-sensitive objectives only where the sampling contract proves every required and forbidden event.

Each addition requires its own real-ROM compatibility evidence and remains absent outside controls where the complete role resolves. No release is warranted by this research-only recovery commit.

## Verification

- Classification regeneration: `Classified 1003/1003; 0 remain unclassified.`
- RetroAchievements, compatibility-report, and release-policy Node suites: **72/72 passed**.
- Sanitized override records: **120/120** have exactly the six permitted fields; recovery-path counts are 56/13/43/8.
- JSON artifacts parsed successfully: **5/5** selected changed documents.
- Forbidden trigger/address markers in the override registry: **0**.
- `git diff --check`: passed.
- Browser regression execution was attempted, but this isolated worktree intentionally has no installed `companion-web/node_modules`; it stopped before test discovery. No browser, UI, Android, or production Kotlin source changed, so no browser build, Gradle build, APK, or RC was manufactured for this documentation/developer-tool commit.
