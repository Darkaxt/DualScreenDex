# Gen I/II Map Parity Checkpoint A Audit

Specification: `docs/superpowers/specs/2026-08-24-gen1-gen2-local-map-parity-design.md`

| Stage | Requirement | Evidence | Classification | Target / acceptance |
|---|---|---|---|---|
| Baseline | Reconcile with `fork/master` before every commit | Plan commit `d6b3722` reconciled at master `5c316d8`; implementation gate repeats before every commit | PASS | Re-run and record refs at every commit |
| Shared solver | Gen III output remains unchanged | Shared normalized builder plus complete `LocalMapSceneBuilderTest` and `Gen3MapSceneResolverTest` pass | PASS | Re-run with every generation adapter change |
| Gen I scenes | Compiled connections produce bounded scenes | 11-byte decoder, fail-closed integration, synthetic ABI suite, and Red/Blue/Yellow exact controls pass | PASS | Red/Blue/Yellow strict controls pass |
| Gen II scenes | Compiled connections preserve four palettes | Pending | BLOCKER | Gold/Silver/Crystal strict controls pass |
| Live player | Existing area and X/Y drive shared scene marker | Pending | BLOCKER | Android and API tests pass |
| Overworld marker | Structurally resolved frame or compact-dot fallback | Pending | BLOCKER | Official controls and fail-closed tests pass |
| Discovery / Atlas | RC53 hidden-image and fallback contract remains intact | Pending | BLOCKER | Web tests pass |
| Persistence | Existing catalogs rebuild once and round-trip | Pending | BLOCKER | Parser schema 35 cache tests pass |
| GB/GBC corpus | No accepted Local raster regresses | Pending | BLOCKER | Deterministic matrix reports zero parser errors/regressions |

## Baseline characterization

`Gen3MapSceneResolverTest.partitionsContradictoryBranchesDeterministically` now freezes the exact safe-greedy branch membership and `scene/0001` key before solver extraction. Validation passed with:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen3MapSceneResolverTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

## Shared scene solver

Gen III connection decoding remains generation-owned while normalized constraint canonicalization, deterministic placement, overlap exclusion, partitioning, and scene bounds now live in `LocalMapSceneBuilder`. Missing-map constraints are discarded before topology construction. Validation passed with:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*LocalMapSceneBuilderTest' --tests '*Gen3MapSceneResolverTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

The pre-extraction exact scene membership and key characterization remained unchanged.

## Gen I scene adapter

`Gen1MapSceneResolver` now decodes each set cardinal flag in the compiled north/south/west/east order and advances exactly 11 bytes per record. Target strip, WRAM, width, sentinel, and signed-alignment evidence is validated independently before conversion to shared grid constraints. A malformed record contributes only a bounded diagnostic; an unexpected adapter failure produces an empty scene list and retains every accepted Gen I raster.

The synthetic suite covers all four directions, reciprocal agreement, flag-record ordering, invalid WRAM evidence, out-of-range target strips, wrong connected widths, unknown targets, bank-truncated records, odd tile alignment, and continuation after a malformed record:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen1MapSceneResolverTest' --no-daemon --console=plain
BUILD SUCCESSFUL
```

The official Red, Blue, and Yellow controls were run with their SHA-locked environment inputs and `--rerun-tasks`. They retained 226, 226, and 227 maps respectively; all existing names, dimensions, and ARGB raster hashes remained exact. Every compiled connection passed structural validation. Pallet Town (`0x00`) and Route 1 (`0x0c`) share `scene/0000` with the frozen displacement `(0, -36)`, and global scene membership and overlap invariants pass:

```text
D:/Temp/dualdex-gen2-dynamic-lighting/gradlew -p D:/Temp/dualdex-gen2-dynamic-lighting :parser-core:test --tests '*Gen1LocalMapResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL
```

Post-stage comparison against the specification found no missing or partial Gen I scene requirement. There are no Gen I scene blockers or deferrals.

## Classification rules

A stage is marked `PASS` only from an exact command, bounded real-ROM result, or retained artifact. A Checkpoint A requirement cannot be deferred to unblock release. Any permitted deferral must include a stable task ID, exact missing behavior, affected structural family, safe fail-closed behavior, target stage, and concrete acceptance condition.
