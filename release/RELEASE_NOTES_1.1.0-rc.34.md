# DualDex 1.1.0-rc.34

RC34 corrects Local-map sign, entrance, and label presentation introduced with RC33.

## Local-map names

- Gen III sign headlines are decoded from their ROM event scripts.
- A sign beside a unique entrance represents that destination as one POI instead of overlapping separate sign and door markers.
- Gender-dependent or otherwise dynamic sign scripts use the decoded destination map name after proximity discovery.
- Approaching a sign or entrance identifies its name in Organic mode; hidden ground items remain unidentified silhouettes.

## Map presentation

- The current area name is shown only in the header, not repeated as a cyan map overlay.
- Unidentified POIs retain their marker but no longer print generic `Place` or `Unknown` labels across the map.
- Parser schema 30 rebuilds existing catalogs once so previously cached duplicate POIs cannot survive the update.

## Delivery

- RC34 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
