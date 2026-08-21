# DualDex 1.1.0-rc.28

RC28 adds ROM-derived Gen III Nature catalogs on top of RC27's seamless local-map release.

## Nature catalogs

- Modern Emerald, Pokémon Unbound, and Pokémon Odyssey each resolve all 25 Nature records from their own ROM data.
- Nature names, raised and lowered stats, 110% and 90% multipliers, and flavor affinities are decoded from compiled table consumers rather than an APK-owned canonical list.
- Parsed Nature records persist in the SQLite catalog and are projected through the companion API.
- Party members link to a Nature detail page that presents the ROM's actual stat and flavor effects.
- Missing or ambiguous Nature data fails closed; Gen I and Gen II remain explicitly outside the Nature mechanic.
- Official Emerald, official FireRed, and Pokémon Classic remain real-ROM regression controls.

## Delivery

- RC28 retains RC27's world/local-map behavior and all prior parser and UI features.
- RC28 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
