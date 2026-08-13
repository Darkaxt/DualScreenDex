# DualDex 1.0.0

DualDex is a passive, ROM- and SaveRAM-backed Pokédex companion for mainline-family Pokémon games from Game Boy through Game Boy Advance.

## RC20 map preview

World maps are release-gated to the exact validated Modern Emerald 3.5 ROM in RC20. Other ROMs continue to use the existing Pokédex and Area experience: map capability remains unavailable, no Map control or assets are published, and catalog parsing continues normally.

- Adds a full-screen, ROM-derived Map First view with zoom, pan, recenter, region selection where applicable, and a compact route into the Area Pokédex.
- Adds Organic fog of war driven by validated visited-area knowledge. Unknown map cells, labels, and hit targets remain concealed, with fully black outer boundaries.
- Adds symmetric Map-to-Pokédex and Pokédex-to-Map navigation while preserving the selected or current area context.
- Adds a Pokémon Area map showing the locations where that species has actually been observed.
- Changes the Organic Area Pokédex roster to use parsed encounter slots: unseen encounter species appear as non-interactive black silhouettes with masked names and Pokédex numbers, while gifts and starters absent from the encounter table do not leak into the area.
- Materializes the validated Modern Emerald 3.5 ROM map raster and geometry. Missing, ambiguous, unsupported, or non-allowlisted map data fails closed without affecting the catalog or Pokédex.
- Excludes the experimental ARM7TDMI/THUMB ability-mechanics work. Its Modern-only proof did not pass the required first-50 generalization gate and is documented separately rather than shipped.

## RC19 update

- Decodes the absolute Generation III main-state address, live combat byte, and one-bit lifecycle mask from matched ROM Thumb set/clear operations. Runtime polling now reads that parser-proven byte directly instead of rediscovering a callback-shaped RAM structure or assuming a fixed field offset.
- Keeps Generation III double battles available with manual target selection when no independently decoded live cursor exists; RC19 does not publish the former source-relative cursor offset as runtime authority.
- Invalidates RC18 catalog caches so the instruction-decoded runtime layout is materialized before live battle polling.
- Replaces the permanently zero loading percentage with an honest indeterminate Loading indicator.
- Removes the redundant Seen filter in Organic mode. The Area filter now contains only species actually observed in that resolved area; a Pokémon caught elsewhere no longer appears there automatically.

## RC18 update

- Resolves the Generation III main runtime layout from ROM structure and uses its live battle flag as the primary combat lifecycle signal, eliminating stale callback-driven Combat state without hardcoded game addresses.
- Polls only the bounded live lifecycle, location, and optional target bytes outside combat; a confirmed live transition triggers the existing full battle-layout discovery.
- Preserves all supported opponents in double battles. Automatic targeting uses the source-proven live hover cursor only when the ROM validates that optional field; otherwise the UI falls back to manual target selection.
- Changes the Pokédex Area filter to show species actually observed while the player was in that live-RAM area, while captured species remain visible everywhere. Parsed encounter tables continue to supply area-relative levels, rarity, and encounter-time details.
- Invalidates older catalog caches so the new parser-derived runtime layout is materialized safely on first use.

## RC17 update

- Prevents stale Generation III battle records from opening Combat when DualDex starts while the game is already in the overworld.
- Qualifies battle data against live main-loop callback and battle-memory transitions, while preserving recovery when DualDex starts during an existing battle and immediate exit when the game returns to the overworld.

## RC16 update

- Uses live RetroArch RAM as the current-area authority while connected. Disk SaveRAM remains available as an offline last-known location, but can no longer override or fill a missing live location.
- Publishes Generation I and II live map identity before combat and during combat from their selected engine WRAM layouts. Cached Generation II polling now reads bounded battle and location regions instead of the full 8 KiB WRAM bank.
- Fixes delayed or stuck Generation III battle exit by validating the live main-loop callbacks from IWRAM, even when battle records remain populated after returning to the overworld.
- Retries an unanswered read-only RetroArch memory request without an arbitrary timeout, and applies the configured 1–20 ms polling interval during both discovery and cached reads.
- Removes the opponent-based encounter-table guess. Area-relative rarity is applied only when live RAM or an offline matched save supplies an actual area; otherwise only the innate rating is shown.
- Replaces technical rarity explanations with short, organic assessments and keeps missing Pokédex-entry copy generic.
- Publishes the reviewed player-facing matrix under the explicit name `dualdex-rom-hacks-compatibility`, linked prominently from the README and attached beside Parser Compatibility.

## RC14 update

- Corrects Gen III encounter parsing by treating struct padding as opaque and accepting the full non-hidden `u8` encounter-rate domain. Modern Emerald now selects its referenced 272-header overworld table, and Blazed Glazed selects its referenced 195-header overworld table instead of seven-header facility data.
- Adds encounter-root and runtime-area diagnostics used to distinguish unresolved live locations from parser failures.
- Exposes encounter-root and runtime area evidence in capability/API diagnostics, and replaces mode-specific missing-entry text with a generic Pokédex fallback.

## RC13 update

- Removes the unsupported AYN Thor controller-focus setting, its obsolete persisted value, the Shizuku/Sui integration, and the privileged Android settings permission. DualDex no longer offers or requests this feature.

## RC12 update

- Makes live-battle discovery polling device-configurable from 1–20 ms (5 ms by default), while retaining the one-request-at-a-time RetroArch network protocol and using a lower-frequency cached battle window after discovery.
- Keeps a validated battle-memory layout across normal battle exits so the next encounter can be detected without repeating a full memory scan.
- Prevents repeated battle samples from forcing the companion back to Combat after the user opens the Pokédex or another manual view.
- Replaces the single rarity word with a two-part area-relative and innate title, plus a five-star display with half-star level adjustments. The relative tier uses the weighted expected level of the exact current-area encounter table capable of producing the observed Pokémon.

## Earlier RC updates

- Adds catalog-validated Generation II single-battle detection from Gold/Silver/Crystal WRAM, including selected attacks, automatic targets, DV rarity, effectiveness, opponent move-frequency learning, and automatic battle exit.
- Hardens zero-profile ROM parsing for relocated and expanded Gen III tables, move-acquisition variants, sprites, descriptions, and ROM-native record widths.
- Publishes a clearly named ROM Hacks Compatibility report for the reviewed first 50 ROMs, alongside the lower-level Parser Compatibility evidence. Indices 1-33 and the three worst later offenders were rerun with the final parser; the other 14 later observations retain the exact base run after reviewed non-impact proof, matching the explicitly bounded review strategy.

## Included in v1

- Direct and ZIP-based ROM parsing with SHA-256-keyed SQLite catalogs.
- Gen I–III Pokédex data, ROM sprites, moves, abilities where applicable, evolutions, encounters, type charts, type colors, and ball artwork.
- SaveRAM-backed Seen, Caught, Team, preferred-owned-individual, qualitative IV/DV tier, and capture-ball state where applicable; current Area uses live RAM while RetroArch is connected.
- Structural Gen III Pokédex-layout discovery for derivatives that expand `SaveBlock2`, preventing shifted seen/caught flags without requiring a per-ROM profile.
- Discovered, Organic, and Hidden information policies.
- Passive RetroArch session detection plus manual ROM fallback.
- Parsed Area-filter day/night markers for encounter tracking.
- Passive Generation I–III battle context with automatic single targets, inferred Generation III double targets, highlighted player-move metadata, qualitative recruitment rarity, and frequency-ranked opponent moves.
- Organic matchup discovery driven by player PP consumption, plus per-ROM persistence of observed opponent moves and discovered matchups.
- Docked and bounded, user-resizable 4:3 overlay presentation.
- An optional, session-only, read-only memory issue reporter that is isolated from the production Pokédex and battle state.

## Capability boundary

Live battle context is enabled only when the active parsed catalog and read-only core memory agree on a supported structure. Generation I Red/Blue/Yellow, Generation II Gold/Silver/Crystal single battles, and Generation III layouts are implemented. Generation III attempts automatic double-target resolution; Generation II multiple opponents are not applicable. Any missing or ambiguous memory capability disables only the affected battle feature—the ROM/SaveRAM Pokédex remains usable. The issue reporter only gathers evidence and never modifies ROMs, saves, emulator memory, or Pokédex state.

The attached ROM Hacks Compatibility and Parser Compatibility documents identify the tested official games and structurally selected derivatives without distributing ROM content, saves, trainer data, or private paths.
