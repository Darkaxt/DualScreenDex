# gba-pokemon-rom-to-wasm provenance

DualScreenDex uses ARM7TDMI semantics and CPU-test references from
[`agnt-gg/gba-pokemon-rom-to-wasm`](https://github.com/agnt-gg/gba-pokemon-rom-to-wasm),
pinned to commit `91b814c0ff63ded6fbf0c47d082dac2332d4a7f3` under the accompanying MIT
license.

Reference files at that commit:

- `src/cpu/arm_core.ts`
- `src/recompiler/arm_lifter.ts`
- `src/recompiler/thumb_lifter.ts`
- `tests/arm_core.test.ts`
- `tests/cpu_suite.test.ts`
- `tests/recompiler_diff.test.ts`
- `tests/thumb_diff.test.ts`

The following upstream-committed CPU conformance fixtures are redistributed as test
resources only:

- `build/arm.gba` — Git blob `dd0c023da55cc9a62afc90bc6917ceca689d1e29`, SHA-256
  `77ee88662552bdc885c1080c0172ff119d54db791bd73b21808cf1ff1fe5b40e`
- `build/thumb.gba` — Git blob `47e50db6920d5a996051d0902365e5cdf7b166b6`, SHA-256
  `b5cb2291df4ab314b31c598acd9bff2ccfa0b38efff29daadfe97422ce369b67`

No commercial ROM, BIOS, game asset, or save data is included. These small `.gba`
files are MIT-licensed CPU conformance programs committed by the upstream project;
they are not commercial game ROMs.
