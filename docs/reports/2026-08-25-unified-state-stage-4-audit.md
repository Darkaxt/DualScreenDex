# Unified Game State Stage 4 Audit

Date: 2026-08-25

Specification: `docs/superpowers/specs/2026-08-25-unified-game-state-single-authority-design.md`

Plan: `docs/superpowers/plans/2026-08-25-unified-game-state-single-authority.md`

## Result

Stage 4 satisfies the production overworld single-authority contract. Area, position, clock, readiness, visited-area history, and proximity discovery are projected from one `ResolvedGameSnapshot` and published atomically. Atlas consumers receive one coherent state: they cannot observe a new area with the prior coordinate or with discovery knowledge from a different sample.

## Specification trace

| Requirement | Evidence | Result |
|---|---|---|
| One overworld authority | `ProductionCompanionRuntime.applyResolvedOverworldState` is reached only from the `TransientGameStateSource` subscription and consumes area, position, clock, and readiness from the same matching `ResolvedGameSnapshot`. | Met |
| Atomic location and knowledge | The runtime derives visited areas and POI proximity before dispatch. `ResolvedOverworldStateChanged` carries the complete in-memory ledger with the area, position, clock, and readiness in one reducer publication. | Met |
| No invented coordinate | An available area with an unavailable position publishes `currentMapPosition = null`; proximity mapping is not invoked and an origin POI at `(0, 0)` stays undiscovered. | Met |
| Recovery is coordinated | The API no longer reads `KnowledgeLedger.currentAreaBaseId` as an offline feature fallback. A recovered save area is visible only after `UnifiedGameStateDecoder` resolves it into the same location field used by live RAM. | Met |
| Atlas consumers | Map mode, player marker, camera following/recenter/gliding, POIs, fog visibility, header clock, and dynamic lighting read only `State.currentAreaBaseId`, `currentMapPosition`, `revealedAreaBaseIds`, `localMapPois`, and `gameTime` from the canonical projection. | Met |
| User-owned viewport controls | Pan, zoom, follow state, smoothing, POI filters, and zoom thresholds remain UI/settings state; they are not decoded from transient memory. | Met |
| Compatibility paths removed | `LiveAreaChanged`, `LiveMapPositionChanged`, `LiveGameClockChanged`, and `LiveGameStateChanged` no longer exist in production or tests. Catalog loading clears resolved session state through the catalog lifecycle reducer. | Met |
| Save persistence boundary | Overworld samples change only the in-memory ledger. No sample writes a persistence ledger; checkpoint freezing/writing remains restricted to a validated same-playthrough `SaveObservationKind.CHANGED`. | Met |

## Verification

- The new offline-ledger fallback test failed before the API fallback was removed and passes afterward.
- The new catalog-transition reset test failed before the resolved overworld fields were cleared and passes afterward.
- The atomic runtime publication test failed while area/position and visited/POI knowledge were dispatched separately and passes after the single publication change.
- `UnifiedGameStateDecoderTest`: passed.
- `CompanionGatewayTest` and `ApiViewBuilderTest`: passed.
- Full `:companion-core:test`: passed.
- `ProductionCompanionRuntimeTest`: passed.
- `MapPage.test.tsx`: 24 passed.
- `GameClockIndicator.test.tsx`: 5 passed.
- Production web build: passed.
- `git diff --check`: passed.

## Deferred to assigned later stage

- Stage 5 will remove the temporary resolved-section action boundary itself after Gen I/II fixtures and remaining test harnesses submit and observe unified snapshots directly.

## Blockers

None.
