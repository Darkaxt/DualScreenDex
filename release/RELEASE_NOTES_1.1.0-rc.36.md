# DualDex 1.1.0-rc.36

RC36 completes the Gen III Local-map POI presentation introduced in RC33 and corrected in RC35.

## ROM-derived place names

- Conditional male/female sign scripts now resolve their actual ROM text instead of falling back to the generic town name.
- When live trainer identity is available, `{PLAYER}` is replaced with the trainer name; otherwise both truthful sign alternatives remain visible.
- The new conditional names survive SQLite catalog persistence and reload.

## Map presentation

- POI icons and labels disappear when the user zooms below the starting Local-map view.
- Colliding labels are decluttered while their POI markers remain selectable at supported zoom levels.
- The POI category control now uses a conventional funnel icon and the same dimensions, colors, border, focus treatment, and shadow as the other map controls.
- The current area name is no longer reused as a generic entrance label.

## Verification

- Exact Modern Emerald ROM control validates both Littleroot house sign branches.
- A 1240×1080 browser control validates the filter styling, label separation, and zoom-out removal behavior.
- Parser, API, SQLite, web, and Android map-focused regression gates are green.

## Delivery

- RC36 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
