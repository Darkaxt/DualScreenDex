# DualDex 1.1.0-rc.17

RC17 begins Stage 8 with a richer, ROM-driven Party view while retaining RC16's correct
double-battle command ownership and the preceding Trainer, Atlas, clock, archive-loading, and
display-recovery features.

## Richer normalized party presentation

- Identified party members use their normalized catalog portrait when one is available.
- Unidentified members follow the same black-silhouette privacy rule as the Pokédex and evolution
  views. Missing artwork and genuinely empty slots remain visually distinct.
- Fainted, healthy, statused, and partially decoded members have separate presentation states.
- Status artwork and primary/secondary type artwork are derived from normalized ROM data. Custom
  types and statuses use dynamic labels instead of a retail-only allowlist.
- Held-item presence is shown without exposing an unsupported raw item identifier or fabricated
  item name. Unavailable data remains explicit and fail closed.
- Long names, single/dual types, missing optional fields, and unusual ROM content remain readable
  in both the two-column 4:3 layout and the narrow single-column layout.

## Verification and delivery

- All 122 web component tests pass, including the expanded Party behavior.
- Focused API, companion runtime, and Android loopback tests pass.
- A real 1024x768 Helium browser gate covers the full party, fainted/missing-art, privacy
  silhouette, and custom partial-data states.
- The production APK is built and signed only by the protected GitHub workflow. It is not installed
  or launched by this release task; device acceptance remains with the user.
