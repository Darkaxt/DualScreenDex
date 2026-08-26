# RC67 Height Comparison validation

**Result:** READY FOR PROTECTED SIGNING

## User-visible contract

| Requirement | Evidence | Result |
| --- | --- | --- |
| Trainer represents 1.7 m | Height chart keeps the 1.7 m physical reference and its accessible description reports the same value | PASS |
| Taller subject uses 80% of the graph | Browser geometry reports a visible trainer height of 80% of the ruler | PASS |
| Transparent padding does not shrink artwork | Alpha-bounds unit control trims a padded RGBA fixture to its exact visible rectangle | PASS |
| Artwork is not stretched | Browser geometry preserves the fixture's 1:4 visible aspect ratio | PASS |
| Figures remain grounded | Browser geometry reports zero distance between the rendered figure and ruler baseline | PASS |
| Taller Pokémon remain supported | Existing scale controls retain `max(1.7 m, Pokémon height) / 0.8` | PASS |

## Root cause

The physical-height percentage was applied to a wrapper while `object-fit: contain` scaled the entire square PNG inside that wrapper. Transparent margins and the wrapper's width constraint reduced the visible trainer even though the wrapper itself measured 80%. The earlier browser assertion measured that invisible wrapper, so it could not detect the presentation failure.

RC67 derives the opaque pixel bounds once when each height-comparison image loads, draws only that rectangle into a pixel-preserving canvas, and applies the physical-height percentage to that visible rectangle. The original image remains the fallback if the browser cannot inspect its pixels.

## Verification

| Gate | Command | Result |
| --- | --- | --- |
| Focused measurement unit tests | `npm test -- --run src/pages/PokedexDetail.test.ts` | 7/7 PASS |
| Pokédex detail navigation regressions | `npm test -- --run src/pages/PokedexDetailNavigation.test.tsx` | 9/9 PASS |
| Visible browser geometry | `npx playwright test e2e/height-comparison.spec.ts` | 1/1 PASS |
| Complete companion suite | `npm test` | 193/193 PASS across 26 files |
| Production web bundle | `npm run build` | PASS |
| Patch integrity | `git diff --check` | PASS |

Physical AYN Thor validation remains user-owned and is not claimed by these automated checks.
