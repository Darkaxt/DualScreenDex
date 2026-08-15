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
