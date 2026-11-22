# DualDex 1.2.5 — Gen II relationships and GBA hack selection

One full 342-identity corpus run at parser revision 144: **310 hacks selected (was 301), 0 ambiguous (was 2), mean compatibility 79.91% → 81.51%**, 18 ROMs improved, **no regressions**, 0 read/parse, persistence or cross-reference errors, all originals unchanged.

- **Gen II evolutions and learnsets** for Ambrosia, Anniversary Crystal, Crystal Kaizo, Mystic Crystal, Gold/Silver 97 Reforged, Polished Crystal and Crystal Inheritance (+8 to +11 points each), from compiled-consumer bounds rather than inherited counts. Crystal Inheritance shows 242 of 254 evolutions; 12 HOLDING-method evolutions stay hidden instead of being shown as trades.
- **GBA hacks now recognized:** Moon Galaxy and Sun Sky (FireRed); Scorched Silver, Parallel Emerald and Team Rocket Edition (Emerald, 0x1B8 published header); Ultra Violet (99.50%) and Lugias Ocean (FireRed, tie resolved); Pisces and Sovereign of the Skies (pokeemerald-expansion species extents). Scale x Fang rises from 4% to 12%.
- **Library and storage:** a larger ROM-library scan quota, lazy theme-asset selection (no longer holds every map raster), faster parsing, and an Emerald in-game clock fix (from PR #35, thanks to ItsRetroPup).
- **Maintenance:** RetroArch setup cancellation can no longer hang; theme render failures are logged; companion `vite` and the build-classpath `guava` (33.7.2) are patched.
- Parser cache revision 144 rebuilds revision 140 catalogs on first open; SQLite schema 2 is unchanged.

Qualification: the [B4 release corpus report](https://github.com/Darkaxt/DualScreenDex/blob/master/docs/reports/corpus/2026-11-22-b4-release-corpus.md), the regenerated [ROM compatibility matrix](https://github.com/Darkaxt/DualScreenDex/blob/master/docs/rom-compatibility-matrix.md), and [hosted packaged acceptance](https://github.com/Darkaxt/DualScreenDex/actions/runs/45022647102) (11/11 passed). Selected hacks still have unsupported optional domains; selection does not mean complete ROM or gameplay compatibility. Production signing and publication remain governed by the protected GitHub release workflow. No physical-device acceptance is claimed.
