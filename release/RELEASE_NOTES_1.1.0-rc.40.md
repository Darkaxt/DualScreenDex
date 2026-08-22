# DualDex 1.1.0-rc.40

RC40 restores live-screen priority and makes Local-map recentering follow the player until the map is manually adjusted.

## Live-screen priority

- Atlas is now the lowest-priority presentation surface.
- An authoritative transition to Battle, Pokédex, Party, Trainer, Settings, or another live page dismisses an open Atlas instead of remaining hidden behind it.
- This fixes battles being decoded correctly while the previously opened Atlas continued to cover the Battle page.

## Persistent player follow

- Pressing Recenter on a Local map keeps the live player position centered as coordinates change.
- The current zoom is preserved while following.
- A manual pan or wheel-zoom breaks follow, leaving the viewport under the player's control until Recenter is pressed again.
- Connected Local-map scenes continue following across placement updates without restoring the zoomed-out overview.

## Verification

- Focused App and Local-map regressions: 36 passed.
- Complete companion web suite: 166 passed.
- Production TypeScript and Vite build: passed.
- Protected release metadata and workflow tests verify the RC40 gates before signing.

## Delivery

- RC40 is an in-place prerelease update of `com.darkaxt.dualdex`.
- Production signing and APK publication remain isolated to the protected GitHub release workflow.
