# DualDex 1.1.0-rc.84

RC84 is the signed release candidate for the completed Thor lower-display usability remediation. It preserves the RC78 hotfix 5 RetroArch activation correction while incorporating the exact packaged-WebView fixes validated across the full `538×445` Thor matrix. The RC number advances to 84 so its Android version code remains monotonic after the immutable RC78 hotfix sequence.

## Thor lower-display usability

- Rework compact Browse, Pokédex details, Battle, Party, Trainer, Local Map, Area Guide, Settings, setup/recovery, Specimens, and linked detail routes for the Thor viewport.
- Keep important actions reachable with bounded route-local scrolling, larger touch targets, keyboard-safe focus behavior, and visible text floors across 85%, 100%, and 135% Android font scales.
- Make discovered-mode Pokédex counts truthful: All represents the full parsed catalog, while Seen, Caught, and Team enable only when matching projected entries exist.
- Add clustered Local-map point selection and a contained Area Guide flow without stealing controller focus from the game.
- Organize Settings into focused categories, retain accessible confirmation dialogs, and keep recovery actions explicit.
- Extend the authoritative in-game clock across normal content and drill-down routes while leaving setup and recovery surfaces uncluttered.
- Preserve truthful Battle Entry, Attack, Rarity, and unavailable Moves states from sanitized raw-memory pipeline evidence.

## Reliability and isolation

- Bound companion requests and contain loopback peer disconnects without crashing the Android host.
- Keep optional Area Guide projection failures local to that module.
- Retain the RetroArch-free simulator, raw-memory controls, and clean-start large-heap provisioning exclusively in the debug source set.
- Verify that the release application remains `DualDexApplication`, does not request `largeHeap`, contains no QA-only controls, and bundles no ROM.

## Measured validation

- Exact packaged Thor matrix: 40 preserved-view captures covering all remediation stages and both overworld and Battle states.
- Thor WebView runner: 14/14 scenarios passed.
- Companion unit gate: 35 files and 309/309 tests passed.
- Public Chromium acceptance: 52/52 Playwright tests passed.
- Pull request CI, CodeQL, public Chromium acceptance, and packaged Android managed-device acceptance passed.
- Clean debug QA uninstall/install acceptance completed all 11 exact Modern Emerald catalog phases at the stock 192 MiB AVD growth limit with one uninterrupted process; this validates the QA harness and does not alter release heap policy.

## Delivery

- This candidate uses Android version code `1010084`.
- The candidate is built and signed only through the protected GitHub Actions environment; production signing material is never exposed to the repository or local workspace.
- DualDex remains read-only and sends no game commands or emulator-memory writes.
