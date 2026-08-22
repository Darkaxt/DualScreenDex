# DualDex 1.1.0-rc.42

RC42 completes the current continuity and presentation pass: tracked Local maps move smoothly, cached catalogs reopen without repeating setup, ability details show player-facing mechanics instead of parser provenance, Pokédex height comparison uses the active trainer, and Party cards expose individual quality.

## Continuity and loading

- Live player coordinates update immediately while short map movements glide the camera; large discontinuities snap safely to the new position.
- Recenter resumes player tracking. Manual map pan or zoom still releases tracking until Recenter is selected again.
- Reopening the already active ROM no longer restarts setup, and reopening a persisted catalog no longer rewrites it.
- Fresh parsing uses red loading-message text; cache reopen uses yellow. The progress bar is unchanged.
- Catalog schema 32 forces one fresh rebuild so previously cached catalogs receive the new ability mechanics. Later compatible launches reopen from cache.

## Player-facing details

- Overgrow, Blaze, Torrent, and Swarm expose their source-backed activation condition and effect: at one-third HP or less, matching-type move power is multiplied by 1.5.
- Parser provenance such as compiled-source implementation notes is excluded from normal ability pages.
- Pokédex height comparison uses the current live trainer avatar when full save-backed Trainer Card state is unavailable.
- Party cards and the Party detail dialog show the existing zero-to-five-star IV/DV individual-quality rating.

## Verification

- Packaged web UI: 167/167 tests passed.
- Full affected parser, companion, catalog, Android unit-test, and lint gate: passed (56 tasks, zero failures).
- Parser suite: 1,192 tests completed with 181 explicitly skipped controls; the official five-ROM ability controls passed.
- Production web build: passed.
- Release metadata and protected-workflow policy: passed.

## Delivery

- RC42 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
- No APK was installed and no emulator or device was used during this implementation gate.
