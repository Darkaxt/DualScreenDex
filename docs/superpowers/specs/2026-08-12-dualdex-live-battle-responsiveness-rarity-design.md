# DualDex Live Battle Responsiveness and Rarity Design

Date: 2026-08-12

## Objective

Improve the live companion experience without introducing ROM-specific battle addresses or speculative data. This change covers four user-visible problems:

1. Initial battle detection takes about five seconds.
2. Opening the Pokédex from Combat is immediately overridden by the next battle sample.
3. Combat needs a five-star rarity indicator between the Pokémon name and Pokédex shortcut.
4. The rarity message must combine the agreed area-relative level word with the agreed IV/DV quality word.

The implementation must preserve the current parser, catalog, compatibility-document, and GAFT registry contracts. It changes live companion behavior and presentation only.

## Decisions

### Configurable adaptive polling

Add a device-global `battlePollingIntervalMs` setting with an integer range of 1 through 20 milliseconds and a default of 5 milliseconds. It controls only full battle-layout discovery. It is device-global because it describes the Android device and RetroArch network-command capacity, not a ROM rule.

The Settings screen exposes the value as a labeled 1–20 ms control, explains that lower values increase UDP and CPU activity, and applies changes without restarting DualDex or RetroArch. Invalid persisted or API values are clamped to the supported range.

The battle coordinator uses a single-threaded, self-rescheduling heartbeat so the next delay can reflect the current read mode:

- `DISCOVERY`: use `battlePollingIntervalMs`.
- `CACHED`: use the existing 20 ms cadence.
- ineligible or closed session: do not perform memory reads.

Only one UDP request may be outstanding. The existing 1,024-byte maximum read chunk remains unchanged. There is no UDP pipelining and no cancellation timeout.

A Gen III discovery reads 256 KiB in 256 chunks. Theoretical transfer cadence is therefore about 1.28 seconds at the default 5 ms and about 0.26 seconds at 1 ms, before Android scheduling and UDP overhead. These are targets, not guaranteed wall-clock claims.

### Battle-layout lifetime

A validated layout remains cached across a validated transition to non-battle state and across a normal battle outcome. Subsequent battle entries use the bounded cached window and the existing 20 ms cadence.

The cache is cleared only when:

- the active ROM identity, generation, or supported core session changes;
- the transport/session is reset;
- a nonzero battle state fails structural validation against the cached layout; or
- the cached offsets no longer fit the returned memory region.

A structurally validated zero/non-battle flag retains the layout. This prevents a full-memory rediscovery for every new battle while still failing closed on genuine layout drift.

### Edge-triggered automatic navigation

Battle lifecycle and battle sample refresh become distinct actions:

- `BattleStarted`: emitted only for an inactive-to-active transition. If `autoOpenTarget` is enabled, it opens Combat once and records the prior screen.
- `BattleUpdated`: replaces the active battle data but never changes the current screen.
- `BattleEnded`: clears battle state. It returns to the recorded prior screen only if the user is still viewing Combat; if the user already navigated elsewhere, that explicit navigation is preserved.

Therefore, selecting the Pokédex shortcut during an active battle remains in the Pokédex while later memory samples continue updating the background battle state. A later distinct battle may auto-open Combat again.

### Structured rarity model

Replace the opaque two-word `rarity` string with structured opponent rarity data:

- `relativeTier`: `WEAK`, `ORDINARY`, `COMPETENT`, `STRONG`, `MAJOR`, or unavailable;
- `innateTier`: `FODDER`, `STANDARD`, `TRAINED`, `VETERAN`, `ELITE`, `ACE`, or unavailable;
- `baseStars`: the IV/DV-derived whole-star value;
- `areaAdjustment`: `-0.5`, `0.0`, `+0.5`, or unavailable;
- `stars`: the final value clamped to 0 through 5.

The innate tier remains based on the complete IV or DV vector. Gen III uses the integer average of six IVs. Gen I/II DVs continue to be normalized onto the 0–31 scale before averaging.

| Average normalized IV | Innate tier | Base stars |
| --- | --- | --- |
| 0–9 | FODDER | 0 |
| 10–17 | STANDARD | 1 |
| 18–23 | TRAINED | 2 |
| 24–27 | VETERAN | 3 |
| 28–29 | ELITE | 4 |
| 30–31 | ACE | 5 |

### Exact encounter-table level baseline

Relative level is no longer compared with the player's party. It is compared with the weighted expected level of the exact current-area encounter table capable of producing the observed opponent.

The calculation uses only a current area derived from a checksum-valid, ROM-matched SaveRAM snapshot and catalog encounter tables belonging to that area. A table is a candidate only if it contains a slot for the opponent species whose inclusive level range contains the observed opponent level.

For each candidate table:

1. Validate that every participating slot has a positive weight and a coherent inclusive minimum/maximum level.
2. Compute each slot midpoint as `(minimumLevel + maximumLevel) / 2.0`.
3. Compute the table reference as the weight-normalized mean of all valid slot midpoints.
4. Round the reference to the nearest game level.
5. Classify `observedLevel - referenceLevel` using the agreed bands.

| Relative level | Tier | Star adjustment |
| --- | --- | --- |
| -3 or lower | WEAK | -0.5 |
| -2 through +1 | ORDINARY | 0.0 |
| +2 through +3 | COMPETENT | +0.5 |
| +4 through +5 | STRONG | +0.5 |
| +6 or higher | MAJOR | +0.5 |

If multiple candidate tables exist, DualDex uses the relative tier only when every candidate produces the same tier. If location, weights, ranges, or candidate agreement are unavailable, it does not substitute an unweighted average or another area. The area adjustment remains unavailable and the final stars equal the innate base.

Trainer battles or unsupported encounter layouts naturally receive no area adjustment unless the opponent is unambiguously supported by the validated current-area encounter evidence.

### Rarity presentation

The Combat identity row becomes:

`Pokémon name` → `five-star indicator` → `Pokédex shortcut`

The indicator always renders five star positions and supports half-star fill. Its accessible label states the final numeric rating and its innate and area components. Examples:

- `3.5 of 5 stars; VETERAN innate quality; COMPETENT for this encounter table`
- `2 of 5 stars; TRAINED innate quality; area comparison unavailable`

The Rarity tab title displays both tiers together, such as `WEAK STANDARD`. The old layout that displayed only `STANDARD` as the title and placed `WEAK` underneath is removed. When area comparison is unavailable, the title is `UNKNOWN STANDARD`; when innate data is unavailable, the card reports `RARITY UNAVAILABLE` and does not invent stars.

The explanatory copy states that the first word describes level relative to the current encounter table and the second word describes normalized IV/DV quality. Exact IVs, DVs, EVs, and encounter probabilities remain hidden.

### AYN Thor controller focus

Controller-focus automation is not shipped. Live firmware classifies the vendor setting as secure, so DualDex exposes no focus preference, provider, status, or permission request. Docked and Overlay display selection remain independent of controller focus.

## Component Boundaries

### `BattleMemoryCoordinator`

Owns adaptive heartbeat scheduling, one-request-at-a-time memory reads, and validated layout retention. It accepts a live polling-interval provider rather than reading Android preferences directly.

### Rarity evaluator

A companion-core unit owns innate tiering, candidate encounter-table selection, weighted reference-level calculation, relative tiering, and final star adjustment. It consumes immutable catalog and state inputs and returns a structured rarity result. The web UI performs no rarity arithmetic.

### Companion reducer/runtime

The production runtime decides whether a sample begins or updates a battle. The reducer owns the screen-transition contract. Repeated samples cannot steal navigation.

## Failure Handling

- A failed or ambiguous memory read retains the last active sample only under the existing bounded tracker policy; it does not fabricate a new battle.
- An invalid polling setting is clamped, and a persisted missing value migrates to 5 ms.
- Missing or stale area evidence produces no relative tier or half-star adjustment.
- Missing IV/DV evidence produces no innate tier or star rating.
- Manual navigation always wins over background battle refreshes.

## Test Strategy

Implementation follows test-driven development.

### Companion-core tests

- All IV and normalized DV tier boundaries.
- Base-star mapping for six innate tiers.
- Weighted encounter midpoint and reference calculation.
- Every relative-level boundary and half-star adjustment.
- Clamp behavior at 0 and 5 stars.
- Multiple matching tables that agree and disagree.
- Missing weights, invalid ranges, stale/missing area, trainer/nonmatching opponents, and missing innate data.
- Initial `BattleStarted`, repeated `BattleUpdated`, manual Pokédex navigation, `BattleEnded`, and next distinct battle.

### Android/app tests

- Discovery uses the configured 1–20 ms value and cached reads use 20 ms.
- Live setting changes affect the next discovery heartbeat without restart.
- No overlapping UDP requests.
- Validated non-battle retains the cached layout; ROM/session change or structural mismatch clears it.
- The packaged manifest has no controller-focus privileged permission or provider.
- Legacy persisted controller-focus fields are ignored and scrubbed during migration.

### Web tests

- Five positions render between the name and Pokédex shortcut.
- Whole and half-star fills and accessible labels match structured rarity data.
- The title renders `RELATIVE INNATE`, not the previous split presentation.
- Unavailable area and unavailable innate states are truthful.
- Polling control persists and sends clamped device-global values.

### Verification gates

- Focused tests for changed modules.
- Full companion-core, app unit, companion-server, and companion-web suites.
- Signed release APK build and local signature/hash verification.
- Public GitHub prerelease publication, never a draft.
- Install the exact published APK on the authorized AYN Thor using ADB.

## Release and Device Boundary

Publish the next version as a public prerelease with the existing stable release assets and no draft state. Verify release metadata and downloaded artifact identity without opening a draft release.

Device authorization is limited to installing the exact published APK. The permitted ADB action is the package install/update itself. Do not launch the app, query device state, grant permissions, change settings, send input, capture screens, inspect logs, or perform automated validation. The user will perform all device interaction and validation manually after installation.

## Success Criteria

- Default initial Gen III discovery has a theoretical cadence near 1.28 seconds rather than 5.12 seconds; 1 ms is user-selectable.
- Subsequent battles reuse a validated cached layout and are detectable on the bounded cached cadence.
- The Pokédex shortcut remains selected throughout the same battle despite continued samples.
- Stars are IV/DV-based with at most a half-star area-level adjustment.
- The rarity title combines the two agreed tiers.
- Area comparison uses only weighted evidence from an exact current-area candidate table and fails closed on ambiguity.
- No controller-focus setting, provider, status, or privileged permission is shipped.
- The public prerelease APK is installed, and no other device interaction occurs.
