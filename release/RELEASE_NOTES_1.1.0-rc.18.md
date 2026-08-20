# DualDex 1.1.0-rc.18

RC18 completes the source-backed Pokémon Unbound and Pokémon Odyssey compatibility work and adds
a deterministic ROM-derived GAME theme. It retains RC17's Trainer Card, richer Party view,
Atlas/local maps, game clock, archive loading, display recovery, double-battle ownership, and
privacy behavior.

## Unbound and Odyssey completion

- Pokémon Unbound v2.1.1.1 and Pokémon Odyssey v4.1.1 now expose all **23/23 parser
  capabilities** in the exact reference controls.
- Unbound resolves one world region and 294 reachable local maps backed by 258 unique assets.
- Odyssey resolves four world regions and 168 reachable local maps backed by 147 unique assets.
- Unbound publishes a complete 254-ability signed AI-rating domain. Odyssey publishes complete
  behavior evidence for its 129-ability domain without fabricating numeric ratings.
- Odyssey's populated reference save now resolves all **14/14 runtime domains**, including
  Trainer Card and five Bag pockets.
- Unbound's expanded-save ABI, logical-section checksums, parasite tails, and expanded Bag layout
  are resolved and persisted. Its available reference SaveRAM is erased, so live values remain
  truthfully withheld until a populated save is available.

## ROM-derived GAME theme

- GAME colors are deterministically derived from normalized Trainer, world-map, local-map, and
  species artwork rather than ROM names, hashes, families, symbols, or fixed addresses.
- Every catalog persists one complete readable theme. Missing or insufficient visual evidence
  produces a neutral fallback without aborting Pokédex, maps, evolutions, abilities, Trainer, or
  Party parsing.
- The theme is projected through SQLite and the companion API and applied consistently across
  Pokédex, detail, Trainer, Party, Battle, loading, Settings, local-map, and Atlas screens.
- Dark, Light, and high-contrast modes remain fixed user-selected alternatives. Map pixels, fog,
  navigation, and information-reveal rules are unchanged.

## Verification and delivery

- Exact Red, Crystal, Emerald, Unbound, and Odyssey theme controls parsed deterministically and
  survived SQLite/API round-trips.
- Exact Unbound and Odyssey parser, map, ability, and save/runtime controls passed alongside their
  official family controls.
- The affected Kotlin gate passed with 1,123 parser tests, 18 catalog-store tests, and 50
  companion-core tests; the web gate passed 123 Vitest tests and a production build.
- A real 1024x768 Helium browser gate covered every companion screen, fixed theme alternatives,
  high contrast, navigation, fog, and unchanged map rasters.
- The production APK is built and signed only by the protected GitHub workflow. It is not installed
  or launched by this release task; device acceptance remains with the user.
