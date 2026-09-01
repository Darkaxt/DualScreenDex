# DualDex 1.1.0-rc.86

RC86 is the final signed candidate for the Thor lower-display usability pass. It preserves RC85's consistent active-destination headers and completes the small presentation corrections identified during physical testing.

## Local Map and habitat truthfulness

- Show the normal ROM-derived player sprite without a pulse or outline by default.
- Offer a device-global, default-off **High-visibility map player** toggle under Accessibility for users who want the pulsing outline.
- Offer Pokémon Atlas shortcuts only when catalogued habitat evidence exists; Organic observations cannot invent a habitat outside that catalogue.
- Preserve Atlas recovery when a truthful habitat exists but cannot be placed on the normalized habitat map.

## Party and Pokédex readability

- Increase the compact Party experience-bar thickness and separate its bright blue fill from the neutral gray track.
- Increase compact Pokédex rows to 76 CSS pixels and keep virtualization geometry synchronized with the rendered cards.
- Keep type labels fully inside compact species cards at the exact Thor packaged-WebView viewport.
- Refresh the affected public Pokédex screenshot from the packaged Android WebView at `1241×1027`.

## Release governance

- Resolve draft candidate releases through the authenticated release list rather than the public-by-tag endpoint, which does not expose drafts.
- Download every draft asset by its immutable asset ID, then recheck the same release ID and complete asset set immediately before promotion.
- Preserve protected GitHub-only production signing, pinned-certificate verification, immutable checksums, provenance, and non-replacing publication.

## Validation and delivery

- Pull request CI, CodeQL, public Chromium acceptance, Gradle dependency submission, and packaged Android managed-device acceptance passed for the exact merged source.
- This candidate uses Android version code `1010086`.
- DualDex remains read-only, includes no ROM or private memory data, and sends no game commands or emulator-memory writes.
