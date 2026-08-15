# DualDex 1.1.0-rc.10

RC10 makes Atlas navigation and loading clearer and introduces a validated live in-game clock path.

## Live game clock

- The upper toolbar displays a zero-padded `HH:MM` value when the parser and live-memory reader can validate the running game's clock layout.
- Modern Emerald v3.5 is source- and ROM-proven through compiled clock consumers; production does not select it by ROM name, SHA, symbols, or a fixed runtime address.
- Missing, ambiguous, unavailable, or invalid clock values stay hidden. DualDex never substitutes Android wall time or trainer play time.
- Official Gen I–III clock coverage will be audited after RC10. Local-map rendering remains daytime-only in this candidate; the live clock is the prerequisite for a later day/night presentation stage.

## Atlas and Pokédex navigation

- The Atlas Pokédex button is always a global Pokédex shortcut. It returns to the last-used Pokédex filter instead of requiring encounter data or opening an Area-filter side path.
- Revealed Atlas locations use compact cyan squares. The live local-player position remains a distinct circular marker.
- The live game clock is geometrically centered in the upper toolbar while the Pokédex title remains left-aligned.

## Loading presentation

- Idle guidance is hidden while module-aware loading progress is active, leaving the current module label and progress bar as the loading state.

## Upgrade behavior

- Catalog schema revision advances from 15 to 16. RC9-derived catalogs rebuild once; settings, discovery knowledge, and SaveRAM snapshots remain intact.

## Verification boundary

- Parser, live-memory, companion-state, API, web navigation, loading, marker, and production web-build checks are part of the RC10 gate.
- The signed APK is built and signed only by the protected GitHub release workflow. It is not installed or launched on a device by this release task; device acceptance remains with the user.
