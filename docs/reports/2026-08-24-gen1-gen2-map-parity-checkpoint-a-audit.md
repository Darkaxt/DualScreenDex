# Gen I/II Map Parity Checkpoint A Audit

Specification: `docs/superpowers/specs/2026-08-24-gen1-gen2-local-map-parity-design.md`

| Stage | Requirement | Evidence | Classification | Target / acceptance |
|---|---|---|---|---|
| Baseline | Reconcile with `fork/master` before every commit | Plan commit `d6b3722` reconciled at master `5c316d8`; implementation gate repeats before every commit | PASS | Re-run and record refs at every commit |
| Shared solver | Gen III output remains unchanged | Pending | BLOCKER | Existing and added Gen III tests pass exactly |
| Gen I scenes | Compiled connections produce bounded scenes | Pending | BLOCKER | Red/Blue/Yellow strict controls pass |
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

## Classification rules

A stage is marked `PASS` only from an exact command, bounded real-ROM result, or retained artifact. A Checkpoint A requirement cannot be deferred to unblock release. Any permitted deferral must include a stable task ID, exact missing behavior, affected structural family, safe fail-closed behavior, target stage, and concrete acceptance condition.
