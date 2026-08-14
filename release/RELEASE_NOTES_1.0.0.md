# DualDex 1.0.0

DualDex is a passive ROM, SaveRAM, and validated live-WRAM Pokédex companion for mainline-family Pokémon games from Game Boy through Game Boy Advance.

## RC29: broader ROM-native Pokédex entries

- Resolves Celia's compiled 386-row Gen III Pokédex-entry table structurally, publishing 382 ROM-native descriptions across 384 navigable Pokédex species. The two unsupported active rows remain unavailable rather than receiving external or fabricated text.
- Raises Celia's measured compatibility from **90.40% to 95.14%** and the exact-first-50 average from **95.53% to 95.63%**. First-50 Pokédex-description coverage changes from **35 available / 11 partial / 4 not found** to **35 available / 12 partial / 3 not found**.
- Preserves the exact base gate at **50/50 selected**, zero routing or first-33 reference deltas, zero cross-reference errors, and 50/50 SQLite write/reopen/quick-check success.
- Leaves THUMB ability mechanics unchanged and deferred. Existing evolution coverage remains **50/50 available**.

## RC28: broader move data and clearer map state

- Resolves Celia's widened 16-byte move-detail table structurally, publishing all 1,188 validated move rows without ROM-name, hash, or fixed-offset selection. First-50 `MOVE_DETAILS` availability increases from **47/50 to 48/50**, and Celia's measured compatibility rises from **85.64% to 90.40%**.
- Replaces the generic loading message with the module currently being materialized: ROM identity, core catalog, sprites and entries, evolutions and areas, extended data, or saved catalog.
- Makes map fog follow the global knowledge mode. Discovered mode shows the map clearly; Organic and Hidden retain discovery fog. The redundant local fog button is removed.
- Silences the non-actionable `map location has no encounter area` warning. Locations without encounter-backed Area Pokédex data simply keep that action disabled.
- Includes the latest source-backed tiled 8bpp Gen III map resolver work already integrated on the release branch.

## RC27: complete evolution catalogs and consistent Organic identities

- Completes evolution parsing on **50/50** exact first-corpus ROMs. All rows expose available per-species evolution fields with zero malformed rows, deterministic semantic edge maps, and exact SQLite reopen parity; `NOT_FOUND` and `NOT_APPLICABLE` are not counted as successes.
- Applies one Organic identity rule to Pokédex rows, the detail avatar, and evolution targets: unknown species use a black silhouette and stay non-interactive, seen species use grayscale artwork and remain navigable, and captured species use full-color artwork.
- Corrects DarkFire's compiled ten-slot evolution table, restoring 33 real edges that the former eight-slot interpretation omitted while preserving Classic's independent ten-slot source layout.
- Includes the latest fail-closed Gen III map resolver refinements for branch-owned and alternate affine loaders without weakening the RC25 map evidence gates.

## RC25: ROM-derived maps, semantic ability mechanics, and broader compatibility

- Restores the Map experience on top of normalized ROM-derived raster and location evidence. Complete maps support one-pointer pan, two-pointer pinch zoom with midpoint preservation, visible zoom controls, recenter, fog, location overlays, and direct Area Pokédex navigation without adding a second Area toolbar.
- Passes the exact first-50 world-map release gate at **26/50** ROMs and 81 complete regions. Every available result has intrinsic raster, geometry, and encounter-bound location evidence; all 24 unresolved ROMs keep the normal Pokédex and Area behavior with an empty, non-crashing map capability.
- Adds source-controlled map reconstruction for official Red/Blue/Yellow, Gold/Silver/Crystal, Emerald and FireRed/LeafGreen engine formats, plus structurally compatible derivatives. ROM names, hashes, symbols, and fixed offsets remain test evidence rather than production selectors.
- Replaces the retail byte-signature ability materializer with bounded ARM7TDMI semantic proof. The frozen first-50 survey proves exact attacker Attack ×2 mechanics for abilities 37 and 74 on **38/46 applicable GBA ROMs**, with no extra inferred mechanics; four GB/GBC rows are correctly not applicable and unsupported or ambiguous GBA paths publish nothing.
- Opens Rarity only once, at the start of a structurally proven wild battle with a usable assessment. Trainer, link, tutorial, special, ambiguous, and incomplete battle evidence never steals the active tab.
- Preserves the exact base compatibility gate at **50/50** selected, reference-clean, persisted and reopened catalogs. The broader 332-identity audit is published separately so first-50 release coverage is not presented as global compatibility.
- Keeps the live Gen III party and battle-window improvements from RC23/RC24. SaveRAM remains the fallback for ownership, boxes, Pokédex flags, and unsupported live layouts; every newer live path remains read-only and fail closed.

## RC24: immediate ROM-derived Gen III battle window

- Resolves the Gen III live battle-mon array structurally from compiled ROM references.
- Reads the bounded battle window immediately after the proven live battle flag activates, removing the serialized full-EWRAM scan from supported ROMs.
- Retains the existing fail-closed full-memory discovery path when the ROM evidence is absent or ambiguous.
- Keeps the RC23 live party reader, stable RC19 interface, Organic Area silhouettes and All Files permission contract unchanged.

## RC23 live party

- Reads the active Gen III party from a structurally resolved `gPlayerPartyCount`/`gPlayerParty` EWRAM pair, so a newly received starter appears before the first in-game save.
- Reuses the checksum-validating Gen III Pokémon codec and publishes a party only when every occupied record decodes completely.
- Keeps live party membership authoritative while connected, including across stale periodic SaveRAM refreshes; SaveRAM remains the fallback and continues to supply boxes, Pokédex flags, and saved location.
- Polls only one count byte plus the six-record party window and performs no emulator-memory writes.
- Missing or ambiguous ROM evidence fails closed to the existing SaveRAM behavior.

## RC22 rollback

- Restores the complete RC19 runtime, parser, API, and navigation baseline. The experimental Map First page, embedded maps, map assets, routes, icons, and shortcuts from RC20/RC21 are not included.
- Keeps the Organic Area encounter roster: Pokémon already seen or caught in the parsed local encounter table appear first with their normal identity; unseen possible encounters follow as disabled black silhouettes with masked numbers and names.
- Prevents caught starters, gifts, or trades from appearing in an Area unless that species is actually present in the ROM's parsed encounter table for the resolved area.
- Keeps unsupported or unresolved areas fail-closed: the Area filter remains unavailable rather than inventing data or exposing a broken map surface.

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
