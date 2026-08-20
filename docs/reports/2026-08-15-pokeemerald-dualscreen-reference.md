# pokeemerald-dualscreen reference inventory

Primary reference: [Goldoire/pokeemerald-dualscreen](https://github.com/Goldoire/pokeemerald-dualscreen)
at commit `d27ecd5cd48028f6a2a601e022f8d2032051b126` (2026-08-14).

This project is a closer product reference for DualScreenDex than Kanto Gear: it targets the AYN
Thor and other dual-screen Android devices, keeps the game on the top screen, and provides a live
touch UI on the bottom screen. It is therefore the primary reference for future companion features;
Kanto Gear remains a secondary visual/interaction reference.

## Feature inventory

| Feature | Reference implementation | DualScreenDex reuse opportunity |
|---|---|---|
| Party grid | Six live icons with HP and status | Extend the existing Team presentation with the compact six-slot status layout |
| Party detail | Stats, nature, ability, moves, PP and experience | Populate from existing catalog plus read-only SaveRAM/live-party data |
| Battle touch UI | Four-action and four-move grids, cursor synchronization, cancel, double-battle battler ownership | Reuse the interaction contract only after a safe emulator input seam exists |
| Region map | Runtime-decoded Pokénav raster, live position, location name and full location table | Keep DualScreenDex's normalized cross-ROM map pipeline; reuse the compact live-position presentation |
| Bag | Five pockets and live quantities | High-value new read-only feature using family-specific SaveRAM pocket layouts |
| Trainer card | Badges, money, playtime, Pokédex counts, trainer ID and player portrait | High-value new read-only summary using existing SaveRAM ownership and catalog assets |
| Settings | Background, widescreen, touch overlay, battle-menu placement, fast-forward and volume | Use as the reference list for game-session controls that can be proven safe through RetroArch |
| Secondary display | Android `Presentation`, non-focusable bottom window | Confirms DualScreenDex's established no-focus-stealing display contract |

## Architecture boundary

`pokeemerald-dualscreen` compiles the game and its bridge into one native process. Its C bridge can
read game globals directly and exports a per-frame JSON snapshot plus runtime-decoded graphics over
JNI. DualScreenDex is an external companion for many ROM families, so it must not copy assumptions
that require direct access to Emerald globals. The reusable parts are:

- the JSON/state shape for party, battle, bag, map and trainer-card data;
- the bottom-screen information hierarchy and touch contracts;
- the non-focusable secondary-display behavior;
- the game's own font/icon/map presentation patterns when equivalent data is independently decoded.

The non-transferable parts are fixed Emerald globals, direct native calls into game functions, and
the ROM-specific asset-hole packaging flow.

## Recommended order

1. Party detail parity, because the catalog and live/save party readers already provide most fields.
2. Trainer card, because the required counters and badge flags are compact read-only SaveRAM data.
3. Bag pockets and quantities, gated by validated per-family SaveRAM layouts.
4. Touch battle input and fast-forward only after a dedicated, reversible RetroArch control contract.

The repository's MIT license applies only to its original port modifications and explicitly excludes
upstream Pokémon code/assets. Reuse must preserve that boundary and should favor interaction/state
contracts over copying upstream game content.

## 2026-08-19 upstream delta

The upstream `main` branch moved from the reviewed `d27ecd5` snapshot to `8ec6cba` and released its
0.5.0 presentation. The delta was reviewed as source only; DualScreenDex did not build or execute the
fork and did not copy its Emerald globals or game assets.

| Upstream addition | DualScreenDex decision |
|---|---|
| 2x3 rich party cards plus ROM-derived status, held-item, item and type artwork | Worth carrying forward as a presentation/asset-normalization candidate. RC15 already exposes the normalized six-slot party, HP, status, held item, ability, moves and experience, so this is visual parity rather than missing state. |
| Double-battle menu owner, active party indexes and partner/opponent records | Worth a later typed battle-state extension. It must be derived through the ROM-specific runtime descriptor and remain read-only. |
| Cursor continuity and retaining the last battle menu while the game transitions | Worth applying as event/state continuity if flicker is observed. Do not copy the fork's frame-delay implementation or add a cancellation timeout. |
| Move effectiveness and foe weakness hints | Do not copy as an unconditional feed. Organic mode must keep opponent facts hidden until observed; any future hint must use DualScreenDex's existing knowledge and visibility policies. |
| Touch-driven Bag/Party battle takeover, virtual buttons and item submission | Out of scope. These write game input/state and violate the passive companion boundary. |
| Widescreen renderer, native PPU fixes and asset-hole packaging | Not applicable to the external multi-ROM companion architecture. Bounds and decompression lessons may inform isolated parser hardening, but the implementation is not portable. |

No new upstream item blocks the 1.1 continuity release. The highest-value follow-up is the passive
double-battle ownership contract, followed by richer normalized party artwork.
