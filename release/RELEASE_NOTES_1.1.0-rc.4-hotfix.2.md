# DualDex 1.1.0-rc.4-hotfix.2

This hotfix preserves every RC4 feature and fixes the remaining Android heap crash during a cold ROM catalog parse.

## Fixed

- THUMB ability analysis no longer materializes a complete post-dominator set for every decoded instruction.
- Control-flow joins now use a linear-memory immediate-dominator calculation over the reversed control-flow graph.
- Incomplete graphs and paths that cannot reach a decoded exit remain fail-closed, withholding only the unproven mechanic.
- Warm launches continue reopening the materialized SQLite catalog without repeating the ROM-wide cold parse.

## Root cause

The retained Android crash occurred on `dualdex-parser` at the 256 MiB heap limit inside `BattleRoleProvenance.immediatePostDominator`. A 4,096-instruction proof could allocate millions of boxed graph-set entries even after background ROM indexing had been bounded in hotfix 1.

No ROM identity rules, catalog tables, map reconstruction, WRAM behavior, or game-facing controls were changed.
