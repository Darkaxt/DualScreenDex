# Localization Stage 5 Closure

**Decision:** `COMPLETE`

**Stage branch:** `feat/full-translation-system`

**Synchronized baseline:** `2fef59df95f6deb0ae861aad42cff52eff739a86` (`fork/master`)

**Implementation source:** `e8002258575d54158ab61be5e69f3a667dd205aa`

**Parser / SQL / codec schemas:** `82 / 2 / 1`

**Specification:** Sections 3.3–3.4, 9, 11, 14.3–14.4, and Stage 5 in Section 15 of `docs/superpowers/specs/2026-09-01-full-translation-system-design.md`

## Delivered runtime language authority

Stage 5 publishes every structurally proven catalog projection and selects content language through one bounded authority chain:

1. `LIVE_RAM` may select a persisted projection only through a compiled, bounded, read-only runtime layout.
2. `ROM_DEFAULT` is immediately authoritative whenever live evidence is unavailable, invalid, stale, disconnected, or unsupported.

The runtime never accepts a manual ROM-content-language override, never retains a last-observed live language as offline authority, and never polls a single-language catalog. A content-language change selects an already-persisted overlay and performs zero parser calls.

Every live result is fenced by exact ROM SHA-256, session epoch, state version, active language, and projection version. ROM replacement, session replacement, delayed work, and mismatched projection versions discard stale authority rather than publishing it across sessions.

## Active-overlay publication

`GET /api/language-overlay` lazily publishes only the runtime-authoritative active projection. Requests cannot nominate a language. Responses bind ROM SHA, catalog version, language tag, and projection version; the browser rejects any mismatch and keys refreshes by the complete projection identity.

Catalog bootstrap, state, specimens, current-area text, Area Guide content, species, moves, abilities, types, natures, items, areas, landmarks, and encounter areas use the same validated active projection. Missing ROM-native labels use language-neutral structural identifiers such as `#25`; unresolved `{PLAYER}` text is preserved internally and omitted from normal Area Guide presentation when trainer-name authority is unavailable.

## Semantic presentation boundary

UI-consumed backend prose is closed before Stage 6:

- setup, guide-load, progress, operation, evolution, ability-mechanic, and related presentation paths use `PresentationMessageCode` plus validated typed arguments;
- stat-map keys are semantic identifiers (`HP`, `ATTACK`, `DEFENSE`, `SPEED`, `SPECIAL_ATTACK`, and `SPECIAL_DEFENSE`), with English abbreviations rendered only by the web presentation layer;
- statuses, methods, categories, windows, genders, rarity tiers, and similar fields remain closed enum/code values;
- ROM-native names and descriptions remain direct content values and are not interface copy;
- raw exceptions, compatibility messages, and diagnostics remain outside normal browser presentation. SaveRAM and RetroArch diagnostic prose is confined to the Advanced/debug surface; normal setup errors use local interface copy or semantic presentation messages.

No uncoded backend prose remains on a normal user-facing browser presentation path, so no Stage 5 blocker or localization deferral is required.

## Verification evidence

Focused tests passed before publication at `e8002258`:

- runtime language binding, live French selection, ROM/session/projection fencing, default fallback, retry, zero-reparse switching, specimen projection, current-area text, and Area Guide projection;
- active-overlay endpoint identity and browser stale/mismatch rejection;
- semantic presentation-message construction, validation, ability mechanics, entity fallbacks, stat identifiers, and unresolved player placeholders;
- four affected Vitest files: **27/27 passed**;
- companion-web production build: **passed**;
- `git diff --check`: **passed**.

A broad Stage 5 JVM attempt on 2026-09-12 first exposed an incomplete verification environment; after supplying the retained native-control root and disposable evidence root, the relevant modules completed as follows:

| Module | Cases | Passed | Skipped | Failures / errors |
|---|---:|---:|---:|---:|
| `parser-core` | 2,067 | 1,866 | 201 | 0 |
| `catalog-store` | 117 | 111 | 6 | 0 |
| `battle-memory` | 49 | 49 | 0 | 0 |
| `companion-core` | 132 | 131 | 1 | 0 |
| `app` | 668 | 631 | 34 | 3 / 0 |

The three app failures are legacy challenge/control-inventory assertions (`ChallengeHackControlsTest`, `ChallengeOfficialControlsTest`, and `WesternGen1SemanticCaptureTest`), outside the Stage 5 runtime-language and semantic-API changes. They are neither localization blockers nor localization deferrals. Per the 2026-09-12 verification decision, the remaining broad JVM/web gate and the single final parser corpus are consolidated after Stage 6 rather than bottlenecking UI implementation with redundant intermediate execution.

No Android device, emulator, ADB, APK, signing, release, or live-memory capture was used for Stage 5.

## Requirement mapping

| Requirement | Implementation / evidence |
|---|---|
| Persist every structurally proven projection | Parser schema 82 catalog manifest/overlay tests and SQLite reopen coverage |
| Compiled bounded runtime layout | `dc98f1a6`; resolver and memory-reader tests reject fixed, ambiguous, or out-of-range layouts |
| Bounded polling and default fallback | `5653921a`; one-request live-memory integration, no single-language poll, retry and disconnect tests |
| Atomic fenced publication | `c07bedd8`; ROM SHA/session epoch/state/projection identity tests |
| Lazy active-overlay refresh | `c07bedd8`; endpoint and browser stale-response tests |
| Structured semantic API | `9a0efe89`, `c81f688e`, `de4989e5`, `3e367b76`, `e8002258`; focused JVM/web tests |
| Zero reparse on switch | Runtime and gateway counters remain zero across active projection changes |
| No manual content-language override | No endpoint, setting, or request parameter can nominate ROM content language |

## Ledger and privacy audit

There is no open Stage 5 `STOP-SAFETY` or `STOP-CORE` item. Existing `LNG-D001`–`LNG-D004` retain their post-system scope and unchanged fail-closed dispositions. The consolidated final verification timing creates no omitted feature and no new deferral.

Public evidence contains only aggregate results and source provenance. It contains no ROM bytes, private source paths, decoded private strings, raw memory, credentials, signing material, proof fixtures, raw reports, or SQLite catalogs.

## Final decision

`COMPLETE` — Stage 5 runtime-language and semantic-API implementation is published with focused acceptance. Stage 6 may proceed. The consolidated broad verification gate and exactly one final source-bound corpus remain final-system verification work after Stage 6.