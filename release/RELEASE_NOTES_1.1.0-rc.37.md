# DualDex 1.1.0-rc.37

RC37 corrects conditional Local-map sign labels and improves their readability.

## ROM-derived sign text

- Gen III live SaveBlock2 now supplies the current player name and gender independently of a separate save snapshot.
- Gender-conditioned signs select the branch that the game itself displays, including `{PLAYER}` substitution.
- Multi-line sign text remains limited to its first headline, and a deterministic first decoded headline replaces the old concatenated fallback when live identity is unavailable.
- In Littleroot, the two house signs therefore remain distinct instead of producing one combined label and suppressing its neighbor.

## Map and loading presentation

- POI labels use a stronger semi-opaque panel, subtle blur, outline, and text shadow over busy map graphics.
- The existing collision handling, category filters, and zoom thresholds remain unchanged.
- Loading progress has additional spacing below the DUALDEX title.

## Verification

- The exact Modern Emerald ROM validates both conditional Littleroot sign scripts and their first-line fallbacks.
- Save, live-memory, API, and Android runtime tests validate minimal live identity without fabricating unavailable Trainer Card fields.
- A 1240×1080 browser control validates two simultaneous non-overlapping labels, the clarified surface, and zoom-out removal.

## Delivery

- RC37 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
