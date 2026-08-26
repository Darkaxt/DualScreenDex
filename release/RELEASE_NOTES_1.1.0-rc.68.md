# DualDex 1.1.0-rc.68

RC68 adds a ROM-derived Party Analysis page while preserving the established six-slot Party board and immediate Back-stack behaviour.

## Party Analysis

- Open `PARTY ANALYSIS` from the Party header without changing the 2×3 roster.
- Review team level/status summary and physical, special, status, or unresolved move distribution.
- Compare offensive coverage against the active ROM's parsed type chart.
- Review each member's weaknesses, resistances, immunities, repeated team weaknesses, and only proven incoming ability effects.
- See current evolution opportunities, nearby level-up moves, and factual physical/special move-role gaps.
- Open owned-member and species details from Analysis; Back returns one destination at a time and restores Party scroll state.
- Organic mode masks an undiscovered evolution target instead of revealing or linking it.

## ROM-derived mechanics and compatibility

- Source-backed incoming effects for Volt Absorb, Water Absorb, Flash Fire, Levitate, and Thick Fat resolve their type IDs from each ROM's parsed type table.
- The 14-control report covers all 11 official English Gen I–III ROMs plus Modern Emerald, Pokémon Unbound, and Pokémon Odyssey.
- All 14 catalogs selected, persisted, and reopened with zero report errors.
- Move details, type charts, and evolutions measured 100%; move categories measured 99.00% overall.
- Live Party fields measured 48/84 (57.14%): all eight Gen III controls are 6/6, while Gen I/II live Party decoding remains 0/6 and is stated explicitly.
- Proven typed defensive modifiers measured 30/849 abilities (3.53%); unproven hack mechanics are withheld rather than receiving retail defaults.

## Performance and scope

- Party Analysis reuses the existing immutable presentation cache and adds no memory reader, ROM copy, SaveRAM pipe, poller, or retained snapshot.
- Debug-only performance logs now include Party Analysis recomputation count and accumulated CPU nanoseconds.
- Maps, fog, POIs, battle behaviour, save synchronization, Trainer Card, and Pokédex knowledge remain unchanged.
- DualDex remains read-only: it does not write emulator memory or send game commands.
- No device or emulator was used during publication.

## Validation and delivery

- Full parser-core, catalog-store, companion-core, and Android unit suites pass.
- All 200 companion browser tests pass across 27 files, and the production web bundle builds successfully.
- The exact 14-ROM parse/persist/reopen report has zero errors after synchronization with current master.
- RC68 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010068`.
