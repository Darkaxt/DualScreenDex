# DualDex 1.1.0-rc.7

RC7 preserves every RC4 parser and runtime feature while polishing the startup and Atlas presentation.

## Loading progress

- Startup hides the ROM and RetroArch setup actions while bootstrap or catalog loading is active.
- The progress label follows the real active module: ROM identity, core catalog, sprites and entries, evolutions and areas, extended data, or saved catalog.
- Determinate progress uses the existing completed/total unit counts. Bootstrap remains honestly indeterminate until those counts exist.
- Recovery actions return when loading stops without a catalog.

## Atlas polish

- Removed the diagnostic-looking world-map and ROM-family title from the Atlas header.
- Selected location context stays on the left as `CURRENT` or `MAP POINT`.
- Settings and Area Pokédex use the same right-header action pattern as the Pokédex view.
- A fixed warm-brown map border travels with the raster so its edge remains visually stable while panning.
- The local-map shortcut remains the bottom-left `A`/`L` control and appears only when the live area has a parsed local-map asset.

No ROM identity, parser, catalog, SQLite, map reconstruction, fog, gesture, WRAM, or SaveRAM contract changed.
