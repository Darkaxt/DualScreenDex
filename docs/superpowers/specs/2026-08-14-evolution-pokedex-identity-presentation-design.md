# Evolution and Pokédex identity presentation design

## Scope

Use one knowledge-state presentation rule for species identities shown in evolution rows and Pokédex browse rows. Preserve the existing Organic, Discovered, Hidden, Area, search, sorting, and navigation policies. This change does not expose additional species, alter the knowledge ledger, or change parser/catalog data.

## Presentation contract

Every rendered species identity derives one of three states from the active knowledge policy and the species ledger:

| State | Sprite | Name | Navigation |
| --- | --- | --- | --- |
| Unknown | Black silhouette | One `?` per non-space character | Disabled |
| Seen | Grayscale sprite | Real ROM-derived name | Opens the species Pokédex entry |
| Captured | Full-color sprite | Real ROM-derived name | Opens the species Pokédex entry |

Outside Organic mode, validated ROM identities are presented as captured/full knowledge for this visual rule, matching the existing evolution behavior. Missing ROM sprite data remains the existing unavailable-sprite placeholder; it is not replaced with bundled art.

Pokédex membership remains unchanged. In particular, Organic All continues to exclude completely unseen species, while Organic Area may show parsed-but-unseen encounter rows using the unknown presentation.

## Architecture

Add one small shared identity-presentation helper in the companion web layer. It accepts the knowledge mode plus optional species state and returns the three-state value. The shared sprite component accepts that value and applies the corresponding accessible label and CSS class. Evolution rows and Pokédex browse rows both consume the helper; neither reimplements the policy.

The existing evolution-row click behavior remains authoritative: seen and captured targets open the correct Pokédex entry and reset the detail tab to Entry; unknown targets are not interactive. Pokédex rows retain their existing `OPEN_SPECIES` action when the shared state is seen or captured and remain disabled when the state is unknown.

## Failure behavior

- Missing knowledge state in Organic mode is unknown.
- A caught species is captured even if a malformed external state reports `seen=false`.
- Missing sprite capability renders the existing unavailable placeholder without leaking identity through alt text.
- Missing target species data leaves the evolution row non-interactive and does not invent a target.

## Verification

Focused component tests must assert all three states in both consumers, including exact CSS classes, masked names, accessibility labels, and click/no-click behavior. Existing Organic Area membership, Organic All exclusion, Discovered visibility, evolution navigation, dark-theme, and small-screen behavior remain green. The production web build and relevant Android loopback/runtime tests then verify that the catalog/API data reaches the APK unchanged.
