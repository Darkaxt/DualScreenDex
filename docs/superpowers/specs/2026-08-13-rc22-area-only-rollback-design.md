# RC22 Area-Only Rollback Design

## Approved outcome

RC22 uses `v1.0.0-rc.19` as its complete production baseline. No world-map catalog, parser, asset, API, state, page, route, embedded view, icon, or shortcut from RC20/RC21 is included.

The sole retained post-RC19 behavior is the Organic Area encounter roster:

- The Area filter is sourced from the parsed encounter rows for `state.currentAreaIds`.
- Species whose identity is already known (`seen` or `caught`) appear first with their normal sprite, number, name, status, and encounter-window marker.
- Remaining parsed encounters follow in stable Pokédex order with a black sprite silhouette, `#???`, and one `?` per non-space name character.
- Unidentified rows are not actionable and a name/number search cannot reveal them.
- A caught starter, gift, or trade that is absent from the parsed area encounter table is never inserted into the Area roster.
- Discovered mode shows the complete parsed roster normally. Hidden mode retains RC19 caught-only behavior.

## Failure behavior

If no current area resolves, the Area filter remains unavailable exactly as in RC19. The rollback adds no map fallback and cannot expose a blank or malformed map surface.

## Release

Publish the result as signed prerelease `v1.0.0-rc.22`. Verify its public hash, signer, package, version, and install it on the Thor without installing any debug APK.
