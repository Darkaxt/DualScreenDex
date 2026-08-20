# DualDex 1.1.0-rc.13

RC13 simplifies the Atlas and turns validated in-game time into a compact day/night signal without inventing state for unsupported ROMs.

## Atlas controls

- The redundant top-left marker toggle is removed.
- Cyan Atlas nodes are now a permanent part of the standard view: Organic and Hidden show revealed locations, while Discovered shows every parsed location.
- The multi-region chooser and current-area Local/Atlas switch retain their existing capability-driven behavior.

## Source-aware clock

- The centered in-game clock is 20 percent larger in the Pokédex, detail, Trainer, Party, and Atlas headers.
- A compact orbit below the clock shows exactly one icon: sun from the source-validated day interval and moon from the source-validated night interval.
- The active icon travels left-to-right through its own interval. Sun and moon are never rendered together.
- Modern Emerald 3.5 proves day at `06:00–20:59` and night at `21:00–05:59` from its compiled clock predicate. The web UI receives normalized phase/progress and never uses Android wall time or ROM-family guesses.
- A valid clock without validated boundaries remains numeric-only; a missing clock remains hidden.

## Cache migration and verification

- Parser-cache schema 18 rebuilds RC12 catalogs so validated schedules are persisted with live-clock metadata.
- The exact Modern Emerald 3.5 ROM proves the source-derived clock address and `06:00`/`21:00` boundaries.
- Focused parser, SQLite, runtime, API, and web tests cover persistence, phase boundaries, single-icon rendering, and permanent marker behavior; the complete web suite and production build also pass.
- The signed APK is built and signed only by the protected GitHub release workflow. It is not installed or launched on a device by this release task; device acceptance remains with the user.
