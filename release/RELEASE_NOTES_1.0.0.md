# DualDex 1.0.0

DualDex is a passive, ROM- and SaveRAM-backed Pokédex companion for mainline-family Pokémon games from Game Boy through Game Boy Advance.

## Included in v1

- Direct and ZIP-based ROM parsing with SHA-256-keyed SQLite catalogs.
- Gen I–III Pokédex data, ROM sprites, moves, abilities where applicable, evolutions, encounters, type charts, type colors, and ball artwork.
- SaveRAM-backed Seen, Caught, Team, Area, preferred-owned-individual, qualitative IV/DV tier, and capture-ball state where applicable.
- Structural Gen III Pokédex-layout discovery for derivatives that expand `SaveBlock2`, preventing shifted seen/caught flags without requiring a per-ROM profile.
- Discovered, Organic, and Hidden information policies.
- Passive RetroArch session detection plus manual ROM fallback.
- Parsed Area-filter day/night markers for encounter tracking.
- Passive Generation I and III battle context with automatic single targets, inferred Generation III double targets, highlighted player-move metadata, qualitative recruitment rarity, and frequency-ranked opponent moves.
- Organic matchup discovery driven by player PP consumption, plus per-ROM persistence of observed opponent moves and discovered matchups.
- Docked and bounded, user-resizable 4:3 overlay presentation.
- An optional, session-only, read-only memory issue reporter that is isolated from the production Pokédex and battle state.

## Capability boundary

Live battle context is enabled only when the active parsed catalog and read-only core memory agree on a supported structure. Generation I Red/Blue/Yellow and Generation III layouts are implemented; Generation II live layouts remain unavailable. Any missing or ambiguous memory capability disables only the affected battle feature—the ROM/SaveRAM Pokédex remains usable. The issue reporter only gathers evidence and never modifies ROMs, saves, emulator memory, or Pokédex state.

The attached compatibility report identifies the tested official games and structurally selected derivatives without distributing ROM content.
