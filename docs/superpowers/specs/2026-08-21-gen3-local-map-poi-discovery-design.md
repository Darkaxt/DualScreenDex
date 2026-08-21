# Gen III Local-map POI discovery design

## Status

Approved design for implementation. Gen III is the first delivery target. Gen I and Gen II will use the same normalized contracts later, with generation-specific limitations documented rather than guessed around.

## Goal

Add useful, knowledge-aware points of interest to rendered Local maps without cluttering the route-scale view or leaking ROM knowledge in Organic mode.

At sufficiently close zoom, Local maps may show:

- building entrances and exact building identities;
- service categories such as Poké Mart, Pokémon Center, Gym, and specialist shop;
- visible field items;
- proximity-discovered hidden ground items;
- current shop inventory after the player has entered that shop.

The parser extracts facts from the ROM. SaveRAM and validated live state determine what the current save has discovered or collected. The companion never writes to game memory and never treats a guessed POI as real.

## Scope

### Included in the Gen III delivery

- A normalized POI catalog and per-save knowledge model.
- Gen III map-event, warp, event-script, item, sprite, flag, and shop decoding.
- Official Emerald and FireRed/LeafGreen source and real-ROM controls.
- Source-backed Modern Emerald, Pokémon Unbound, and Pokémon Odyssey structural controls where their checked-out source and exact ROM expose the required structures.
- Organic and Discovered projections.
- High-zoom Local-map icons, labels, details, and filters.
- Catalog persistence, save-scoped knowledge persistence, loopback API projection, and browser rendering.

### Deferred

- Gen I and Gen II POI adapters. They will reuse the same normalized model while using question-mark or black Poké Ball fallbacks when their ROMs lack equivalent overworld sprites or explicit metadata.
- Live game commands, route guidance, teleportation, or item pickup automation.
- Inferring semantic POIs from raster appearance alone.

## Approaches considered

### Selected: parsed POI catalog plus Organic knowledge ledger

The parser extracts ROM-owned POI definitions into a normalized catalog. The runtime combines them with validated live position, area transitions, and SaveRAM flags. A state-aware API projects only the information permitted by the selected knowledge mode.

This is portable across hacks because it follows typed data and script roles rather than matching screenshots, ROM names, filenames, hashes, or fixed absolute offsets.

### Rejected: raster and tile inference

Tiles can suggest a door, sign, or Poké Ball, but they cannot prove destination, item identity, collection flag, shop role, or inventory. Decorative graphics would create false POIs.

### Rejected: per-ROM source profiles

Source profiles would provide quick coverage for known projects but turn compatibility into a growing identity list. Source repositories are oracles for the binary contracts, not runtime selectors.

## Architecture

```text
Gen III map headers, events, scripts, item tables, and sprite tables
                              |
                              v
                 normalized LocalMapPoi catalog
                              |
             +----------------+----------------+
             |                                 |
             v                                 v
   validated live area/tile             checksum-valid SaveRAM
             |                            flags and inventory
             +----------------+----------------+
                              v
              ROM-and-save-scoped POI knowledge
                              |
                              v
       state-aware API projection and persisted filter choices
                              |
                              v
            high-zoom-only Local-map POI presentation
```

The static catalog contains complete parser evidence. The web API does not expose undiscovered Organic-only identities or hidden-item coordinates merely because they exist in the stored catalog.

## Normalized catalog model

Each `LocalMapPoi` has:

- a stable structural key derived from map identity, event role, tile coordinate, and source record identity;
- owning Local-map key and base-area ID;
- tile coordinates and elevation when meaningful;
- category: `PLACE`, `SERVICE`, `VISIBLE_ITEM`, `HIDDEN_ITEM`, or `UNCLASSIFIED`;
- optional subtype such as `MART`, `CENTER`, `GYM`, or `SPECIALIST_SHOP`;
- optional exterior display name and destination map identity;
- optional event script, object flag, hidden-item flag, item ID, and quantity;
- optional overworld sprite and resolved item-icon asset keys;
- optional shop definition with inventory entries and predicates;
- evidence describing which fields were resolved and which were withheld.

Catalog validation requires unique keys, in-map coordinates, valid referenced assets, and contained ROM pointers. Incomplete semantic evidence withholds only that POI or field; it does not invalidate the Local-map raster or the rest of the catalog.

## Gen III binary resolution

The Gen III Local-map resolver already proves map headers and layouts. POI resolution extends the same selected headers through their typed `MapEvents` pointer:

1. Decode contained event counts and pointers for object events, warps, coordinate events, and background events.
2. Resolve warp destinations through the same compiled map-group authority used by Local maps.
3. Decode visible item object scripts through bounded event-script control flow. An item is admitted only when the script proves item identity and collection flag roles.
4. Decode hidden-item background events through their typed item, quantity, and hidden-flag fields.
5. Classify services through decoded destination scripts and commands: Poké Mart commands and inventory lists, healing-service roles, Gym roles, and specialist-shop variants.
6. Resolve item names and icons from independently selected Gen III item and item-icon tables. Missing icons use the presentation fallback without discarding the item record.
7. Decode shop predicates only when their compared flag/variable and inventory branches are complete. Otherwise publish the invariant inventory intersection or withhold the conditional entries.

No production selector may use ROM filenames, project names, hashes, symbols, or source addresses. Exact ROMs and source builds belong in tests only.

## Organic discovery rules

Organic mode advances knowledge only from validated play evidence.

| POI | Initial state | Proximity state | Interaction state |
|---|---|---|---|
| Building entrance | Generic question mark at close zoom after its map is visited | Visible service category | Exact building name after entry |
| Mart, Center, Gym | Generic question mark | Mart, Center, Gym, or specialist category | Exact identity after entry |
| Visible field item | Black silhouette of its ROM sprite, otherwise black Poké Ball | Unchanged | Real item icon and name after collection |
| Hidden ground item | Absent | Black silhouette after the player enters its discovery neighborhood | Real item icon and name after collection |
| Unclassified event | Generic question mark only when structurally safe | Reveal fields supported by evidence | Never invent missing semantics |

The discovery neighborhood is the item tile plus all eight surrounding tiles. Passing through the neighborhood is sufficient; no companion click or game interaction is required.

Hidden-item proximity stores only the POI discovery key. It does not expose the item identity. Collection is confirmed by the corresponding save/live event flag. If that flag is unavailable, collection remains unknown rather than inferred from Bag contents.

Shop category becomes known near its entrance. Exact identity and current stock become known upon entering. Conditional stock is refreshed only after the player re-enters the shop after its predicates change.

## Discovered mode

Discovered mode bypasses Organic progression and projects every ROM-resolved POI, identity, hidden-item position, and statically resolvable shop entry immediately. Collection state still comes from actual save/live flags; it is never fabricated.

Changing knowledge modes does not erase Organic knowledge.

## Save-scoped persistence

POI knowledge and visibility preferences are keyed by:

```text
ROM SHA-256 + save identity
```

The persisted state contains:

- discovered POI keys;
- identified building/service keys;
- observed shop-inventory snapshots;
- collected state only where retained as corroboration of authoritative flags;
- POI filter selections;
- POI icon and label zoom thresholds.

Ledger integrity removes keys that do not exist in the currently loaded catalog and preserves valid discoveries across app restarts. A different ROM or save begins with an independent state. When no save identity is available, discoveries are session-only and are not silently attached to another save.

## POI filters

The Local-map utility rail gains one POI-filter button. It opens a compact panel with these independent categories:

- Places;
- Services;
- Available items;
- Collected items;
- Unknown POIs.

All categories are enabled by default, so the initial Local-map view shows every POI permitted by the active knowledge mode. The panel includes `Show all` and `Reset`. Filtering changes presentation only; it never changes discovery or collection state. Selections persist under the same ROM-and-save identity as POI knowledge.

## Zoom and interaction

POIs are a detail layer, not a route-scale overlay.

- Below the configured icon threshold, no POIs are mounted or rendered.
- At the configured icon threshold, icons and silhouettes are mounted and become interactive.
- At the independently configured label threshold, short labels become visible.
- Each threshold is stored as a normalized `0..100%` position between the active map's supported minimum and maximum zoom. It is not stored as one absolute scale that would behave differently across maps.
- The label threshold is constrained to be equal to or greater than the icon threshold.
- Defaults are `0%` for both icons and labels. The starting Local-map zoom is the supported minimum and therefore shows every knowledge-permitted POI and label immediately.
- Setting a threshold to `0%` enables that behavior at minimum zoom; `100%` restricts it to maximum zoom.
- POIs outside the visible viewport are not mounted.
- Tapping a POI opens a compact anchored card; it does not navigate away or change the game.

The existing player marker, sharp raster scaling, player-centered recentering, connected scenes, day/night palette, fog, and zoom preservation remain unchanged.

The Map POI panel exposes the current thresholds as bounded sliders alongside category filters. The same controls are available in Settings for deliberate configuration outside the map. Values persist with the POI filters under the exact ROM-and-save identity.

## Knowledge-safe API

Static POI definitions are stored in the catalog database, but normal bootstrap responses receive state-projected POIs:

- Organic omits undiscovered hidden items entirely.
- Organic strips unknown item identity, item icon, shop inventory, and exact building name until their discovery transitions occur.
- Discovered includes all resolved fields.
- Generic silhouettes and question marks do not require access to withheld assets.
- Normal pages contain no parser offsets, flags, script addresses, hashes, or evidence diagnostics. Those remain limited to Debug Settings or isolated reports.

## Failure behavior

- A malformed event table withholds POIs for that map but preserves its Local raster.
- A malformed script withholds the affected semantic fields or POI.
- An unresolved item icon falls back to a black Poké Ball or question mark.
- An unresolved save flag yields unknown collection state.
- An ambiguous shop predicate withholds conditional inventory.
- Missing POI support leaves the current map behavior unchanged.

No failure may reveal hidden information, abort an otherwise valid catalog, or substitute stock-ROM POIs.

## Verification

Verification is driven by real source and ROM data first:

1. Official Emerald and FireRed/LeafGreen exact controls freeze event counts, coordinates, categories, item identities, flags, sprites, warps, and shop inventory.
2. Modern Emerald proves the same normalized model across an expanded Emerald-derived layout.
3. Unbound and Odyssey prove source-backed structural compatibility where their exact ROMs expose the required roles; unsupported save-state predicates remain explicit without fabricated collection status.
4. Exact save/live controls prove proximity discovery, entry discovery, inventory refresh, and flag-backed collection.
5. CatalogStore round-trip and cache-schema tests preserve POIs and assets.
6. Runtime/API tests prove that Organic responses omit undiscovered identities and coordinates while Discovered responses include them.
7. Browser tests prove normalized min/max zoom thresholds, their ordering constraint, viewport culling, filters, silhouettes, labels, detail cards, fog coexistence, and per-save preference restoration.
8. Existing Local-map, world-map, knowledge-integrity, and presentation gates remain green.

Malformed or reordered fixtures are added only after the real controls establish the binary contract.

## Delivery stages

1. Gen III normalized POI, item, event-script, shop, and persistence contracts.
2. Gen III parser resolution with official and source-backed hack controls.
3. Save/live flag decoding and ROM-save-scoped Organic transitions.
4. Knowledge-safe API, high-zoom UI, POI cards, and persistent category filters.
5. Full Gen III vertical verification and signed prerelease.
6. Separate Gen I and Gen II adapter design based on their actual event, item, sprite, and save limitations.
