# DualDex 1.1.0-rc.4

RC4 completes the fourth staged part of DualDex 1.1: presentation and navigation for the normalized read-only player state, plus convergence of the local world-map work.

## Trainer Card

- Adds a live Trainer Card with name, public ID, money, play time, Pokédex totals, card stars, and eight stable badge positions.
- Uses ROM-derived portraits and badges when independently available; missing assets fall back per element without hiding valid text or progress.
- Withholds the entire shortcut when no safe Trainer record exists.

## Party

- Adds six stable Party slots with nickname, species, level, HP, status, types, nature, ability, experience progress, stats, and four move/PP rows.
- Reuses existing Pokédex, move, and ability details, returning to the same selected Party slot afterward.
- Keeps the player's owned Party visible in Organic, Discovered, and Hidden knowledge modes. Opponent moves remain observation-gated.
- Suppresses unresolved IDs and unsupported fields instead of exposing raw numeric placeholders.

## Compact, capability-driven switching

- Trainer, Party, Map, and Settings share the existing Pokédex header; RC4 adds no extra toolbar or navigation row.
- Trainer, Party, and Map actions appear only when the current catalog/session publishes usable data.
- Back navigation returns to the exact prior screen, while Map continues into the existing Area Pokédex flow.

## Local world maps

- Includes the committed local-map work converged from master: normalized maps, persistent discovered-area fog, exact multi-area navigation, Pokémon area insets, and fail-closed optional map resolution.
- Preserves the compact map rail, semantic icons, one-pointer pan, midpoint-anchored two-pointer pinch, and fully black outer fog edges.

## Deliberate boundary of RC4

- Display selection and recovery remain Stage 5.
- Bag and battle-UI snapshots remain internal for later presentation work.
- Emulator memory access remains read-only. RC4 adds no input injection, memory writes, cheats, or game commands.

Device installation and validation remain manual.
