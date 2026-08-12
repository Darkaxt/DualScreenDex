# Organic Rarity Assessments Design

## Goal

Replace the technical rarity explanation with concise, player-facing recruitment advice based only on the final half-star rating.

## Behavior

- Keep the existing two-tier title and five-star display.
- Map the final rating into five bands: up to 1, up to 2, up to 3, up to 4, and up to 5 stars.
- Use one deterministic assessment per band so the message is stable and easy to learn.
- Do not expose area names, encounter-table mechanics, tier formulas, parser state, or memory state.
- If the final rating is unavailable, show only the clean rarity title and omit an explanatory paragraph.
- Do not change combat polling or rarity calculations.

## Verification

Cover every star band, half-star boundaries, technical-copy absence, and the unavailable fallback in focused UI tests, then run the complete web suite and production build.
