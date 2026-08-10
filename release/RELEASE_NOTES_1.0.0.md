# DualDex 1.0.0

DualDex is a passive, ROM- and SaveRAM-backed Pokédex companion for mainline-family Pokémon games from Game Boy through Game Boy Advance.

## Included in v1

- Direct and ZIP-based ROM parsing with SHA-256-keyed SQLite catalogs.
- Gen I–III Pokédex data, ROM sprites, moves, abilities where applicable, evolutions, encounters, type charts, type colors, and ball artwork.
- SaveRAM-backed Seen, Caught, Team, Area, preferred-owned-individual, qualitative IV/DV tier, and capture-ball state where applicable.
- Discovered, Organic, and Hidden information policies.
- Passive RetroArch session detection plus manual ROM fallback.
- Docked and fixed 4:3 overlay presentation.
- An optional, session-only, read-only Memory Mapper Lab that is isolated from the production Pokédex.

## Deliberate boundary

Live battle targeting is not part of 1.0.0. The mapper only gathers evidence for that later feature; it does not modify ROMs, saves, emulator memory, or Pokédex state.

The attached compatibility report identifies the tested official games and structurally selected derivatives without distributing ROM content.

