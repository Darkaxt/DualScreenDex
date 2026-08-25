# Unified Game-State Single-Authority Corrective Plan

**Goal:** Complete the incomplete unified-state migration so every transient feature reads one canonical `ResolvedGameSnapshot`, with recovery selection and identifier translation owned by `UnifiedGameStateDecoder`.

**Specification:** `docs/superpowers/specs/2026-08-25-unified-game-state-single-authority-design.md`

**Execution rule:** This plan proceeds without approval pauses. Each stage begins with a failing contract test, ends with a specification audit and commit, and may advance only when every missing requirement is either fixed or recorded as a blocker. Deferral is allowed only for a requirement explicitly assigned to a later stage below.

## Stage 1 — Pokédex and Trainer single authority

1. Add a vertical regression reproducing the real failure: recovery publishes 52 caught species, then a live snapshot publishes two seen and one caught.
2. Require Trainer Card and Pokédex to report the exact same `2 / 1` values and exact canonical species IDs.
3. Rename the resolved Pokédex boundary from raw Dex/flag numbers to canonical species IDs.
4. Move flag-to-species translation into `UnifiedGameStateDecoder` using `SaveParseContext`.
5. Replace current resolved Pokédex fields in companion state; never union recovery-origin entries into live values.
6. Preserve separately proven current-session Organic battle observations.
7. Prove available empty live sets retract stale recovery entries and unavailable live fields still use matching recovery.
8. Write `docs/reports/2026-08-25-unified-state-stage-1-audit.md` and commit.

Stage exit: no Trainer Card/Pokédex disagreement and no raw Pokédex translation or recovery merge in the runtime.

## Stage 2 — Party, ownership, Trainer, and passive recovery

1. Make the companion transient projection contain Trainer, Party, bag, and event flags from the same snapshot.
2. Replace Party/Team/owned/license state atomically; a valid empty Party clears all three current-session projections.
3. Separate save-synchronized recovery content from Organic knowledge and user preferences.
4. Reduce checkpoint coordination to validation, persistence, and typed submission to the decoder.
5. Remove runtime `applyRecoveryState`, `savedPlayerState` authority, Party mapper authority, and direct SaveRAM-to-ledger merging.
6. Verify Trainer Card, Party page/details, Pokédex Team tab, rarity stars, Trainer license, badges, POIs, bag, and event progression against one snapshot.
7. Write the Stage 2 audit and commit.

Stage exit: SaveRAM cannot mutate a normal page except through a recovery-selected snapshot field.

## Stage 3 — Battle lifecycle, knowledge, and encounter identity

1. Route `BattleTrackingUpdate` into `UnifiedGameStateDecoder`; remove the coordinator-to-runtime publisher.
2. Publish battle observations and matchup discoveries as immutable translated battle knowledge in the battle section.
3. Project battle screen and Organic knowledge from the same update.
4. Preserve opponent-move privacy, command ownership, target selection, battle outcome, and IV-first rarity behavior.
5. Derive the normal page title as `WILD ENCOUNTER`, `TRAINER BATTLE`, or `ENCOUNTER`.
6. Verify overworld to wild to trainer to outcome transitions, including double battles and first-sample tab focus.
7. Write the Stage 3 audit and commit.

Stage exit: no production Battle callback can bypass `TransientGameStateSource`.

## Stage 4 — Atlas, clock, readiness, and POIs

1. Project area, coordinates, clock, readiness, visited-area observations, and proximity events from one overworld snapshot.
2. Ensure Atlas tracking, recenter/gliding inputs, POI proximity, fog history, header clock, and day/night state use those exact fields.
3. Preserve user-owned map pan/zoom/filter settings outside the decoder.
4. Prove an unavailable coordinate never invents `(0, 0)` and an area transition never mixes samples.
5. Remove any remaining area, position, clock, or readiness compatibility action.
6. Write the Stage 4 audit and commit.

Stage exit: no production overworld callback or feature-specific fallback remains.

## Stage 5 — Gen I/II parity and legacy deletion

1. Inventory every remaining transient action, selector, callback, saved-state field, and ledger mutation.
2. Migrate supported Gen I/II battle, location, position, and Gen II phase through the same snapshot projection.
3. Keep unsupported fields explicitly unavailable and independently recoverable.
4. Delete legacy live/resolved section actions and test-only seams after their fixtures submit snapshots instead.
5. Add a structural test that fails when a forbidden production route returns.
6. Write the Stage 5 audit and commit.

Stage exit: exactly one production transient interface and no competing authority or merge policy.

## Stage 6 — Complete verification and RC

1. Run all affected module tests, full Gradle unit/lint gates, all web tests, production web build, and whitespace validation.
2. Run exact read-only controls for official Gen I–III, Modern Emerald, Unbound, and Odyssey; report percentages per live field and recovery behavior without generic compatibility labels.
3. Verify cache behavior, session switches, disconnect/reconnect, privacy, Organic discovery, fog, map tracking, navigation, rarity focus, and normal-UI copy.
4. Re-run read-window, raw-byte retention, allocation, and sustained-heap controls.
5. Produce the final plan-to-spec trace. Fix every blocker; list only genuinely future scope as deferred.
6. Determine the immediate unused RC number, align version name/code/tag/APK/title, prepare release notes, publish through the protected workflow, and independently verify the signed artifact.

Stage exit: public signed RC and exact evidence for every specification requirement. No device installation or UI control is part of this plan unless separately requested.

## Current execution state

- Stage 1 is in progress.
- Stages 2–6 are pending.
- No requirement is currently deferred.
- The reproduced 52-versus-1 Pokédex contradiction is a Stage 1 blocker and is the first implementation target.
