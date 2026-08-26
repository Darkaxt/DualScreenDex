# DualDex 1.1.0-rc.69

RC69 adds a knowledge-safe Area Guide to Atlas and Local maps while preserving map tracking, zoom, fog, filters, and Organic discovery.

## Area Guide

- Open one `Area Guide` drawer from the existing map utility rail without leaving or rebuilding the map.
- Review connected areas, known wild encounters, time windows, level ranges, encounter rates, known places/services, people, and items when the active ROM can prove them.
- Follow the live player area while map tracking is active, or keep a manually selected map point until Recenter resumes tracking.
- Select only knowledge-visible POIs that are already eligible at the current map zoom; the drawer never forces a hidden marker to render.
- Back and Escape close the drawer before leaving the map and preserve the existing viewport, tracking state, fog, POI filters, and mounted Local rasters.
- Signs use their first meaningful decoded line; unresolved player tokens become `Your`, and generic `Place` labels are rejected.

## Organic discovery and performance

- Organic and Discovered modes reuse the same discovery projection and ROM-save-scoped POI category preferences already used by Local maps.
- Fogged Local rasters and undiscovered POIs remain unmounted; hidden items remain absent until the player enters their tile or one of its eight neighbors.
- Long encounter and POI sections are windowed, and the drawer adds no memory reader, ROM parse, SaveRAM path, poller, raster buffer, or persistent render loop.
- Existing Debug performance logs now include Area Guide projection count, CPU nanoseconds, render time, and retained list items. No diagnostic text is added to ordinary UI.
- Objectives remain absent until Stage 3 supplies applicable, knowledge-safe progress and challenge facts.

## ROM-derived compatibility evidence

- The exact report covers all 11 official English Gen I–III ROMs plus Modern Emerald, Pokémon Unbound, and Pokémon Odyssey.
- All 14 catalogs selected, persisted, and reopened with zero report errors.
- Encounter species, availability windows, rates, Local-map records, and the five shared filters measured 100.00%; encounter levels measured 99.94%.
- Area names measured 3,973/4,979 (79.80%), named exit targets 7,848/10,513 (74.65%), maps containing parsed POIs 4,737/4,978 (95.16%), and POIs with resolved content 5,881/25,003 (23.52%).
- Missing facts are explicitly `NOT_FOUND` in the report and are withheld rather than receiving stock-ROM names, exits, services, or objectives.

## Validation and delivery

- The full parser-core, parser-cli, catalog-store, companion-core, Android unit, and official-ROM real-control suites pass.
- All 209 companion browser tests pass across 28 files, and the production web bundle builds successfully.
- RC69 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010069`.
- DualDex remains read-only. No device or emulator was used during implementation or publication.
