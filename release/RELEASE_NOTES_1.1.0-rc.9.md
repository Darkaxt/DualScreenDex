# DualDex 1.1.0-rc.9

RC9 corrects Atlas location identity and makes the local-map switch available for structurally decoded maps that do not have wild encounters.

## Atlas and local maps

- Complete, structurally valid Gen III `gMapGroups` catalogs are retained instead of reducing them to encounter-bearing maps. Sparse hacks keep the existing encounter-keyed fail-closed fallback.
- Modern Emerald v3.5 now reconstructs 557/557 local maps. Its live map `0x0009` resolves as Littleroot Town with an exact 320×320 ROM-derived raster.
- Gen III local rendering supports all 16 hardware-addressable BG palette banks. This covers tiles that use dynamically loaded upper palette slots without weakening tileset or raster validation.
- Atlas no longer substitutes the first revealed marker when the live location is unavailable, eliminating the appearance that the player occupies two places.
- The Local/Atlas shortcut appears whenever the current decoded map has a local asset. Area Pokédex remains disabled for locations without encounters.

## Presentation

- Atlas header actions now place Area Pokédex before Settings, matching the Pokédex view.
- The welcome screen now says `Choose a Pokémon game to begin.` and removes implementation-oriented storage commentary.

## Upgrade behavior

- Catalog schema revision advances from 14 to 15. RC8-derived catalogs rebuild once; settings, discovery knowledge, and SaveRAM snapshots remain intact.

## Real-ROM verification

- Official Ruby, Sapphire, Emerald, FireRed, LeafGreen, and Modern Emerald v3.5 retained exact Atlas and complete local-map catalogs: 394/394/518/425/425/557 maps.
- Modern Emerald v3.5 retained its exact Atlas raster and resolves live base `0x0009` as Littleroot Town with the frozen local-map raster hash.
- The 557-map catalog survived SQLite write/reopen and the Littleroot local PNG was served byte-for-byte by the production loopback API.
- Sparse Gen III map-group and Atlas resolver controls remained green.
