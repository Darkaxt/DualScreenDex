# DualDex 1.1.0-rc.38

RC38 corrects the remaining Local-map house-label problems found in RC37.

## Player house fallback

- When a supported live identity is available, `{PLAYER}` signs still use the decoded player name.
- When live identity is unavailable, the possessive placeholder is now presented naturally as `Your`, producing `Your House` instead of exposing `PLAYER` in the UI.
- This is a presentation fallback only; it does not claim that Modern Emerald's live player name has been decoded when that field is unavailable.

## Nearby map labels

- Nearby POI labels can be placed above or below their markers instead of silently dropping the second label.
- Exact duplicate coordinates remain decluttered.
- The existing semi-opaque background, blur, outline, and text shadow remain.
- The label font size is unchanged. Compact padding is restored, while the width limit now accommodates complete house names without ellipsis.

## Verification

- The API regression proves `Your House` when no live identity exists.
- The focused Local-map suite proves duplicate decluttering and alternate placement for neighboring labels.
- A 1024x768 real-browser control over the exact ROM-parsed 320x320 Modern Emerald Littleroot raster proves both house labels are attached to their actual sign tiles, visible, non-overlapping, and unclipped.
- The production web build and focused companion-core tests pass.

## Delivery

- RC38 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
