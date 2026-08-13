# Gen II hack landmark-join evidence

This checkpoint follows the frozen 50-ROM survey in
`2026-08-13-map-first50-gen12-raw.json`. It changes no family selection and uses
no ROM identity, title, hash, or absolute offset in production.

## Earliest structural comparison

| Control | Header consumer | Landmark consumer | Region consumer | Earliest result |
|---|---|---|---|---|
| Gold / Silver source builds | 9-byte grouped headers | 4-byte coordinate/name entries | complete two-threshold `GetCurrentRegion` | resolved |
| Crystal source build | 9-byte grouped headers | 4-byte coordinate/name entries | complete two-threshold `GetCurrentRegion` | resolved |
| Bronze 1.23 | Gold/Silver shape | Gold/Silver shape | complete Gold/Silver two-threshold shape | name control semantics reject later in the join |
| Bronze2 1.05 | Crystal shape | Crystal shape | malformed far two-threshold helper, but one complete `IsInJohto` authority | resolved by this checkpoint |
| Dark Energy 5.01 | Gold/Silver consumer candidates | Gold/Silver landmark candidate | complete two-threshold candidate | deferred until Bronze name semantics are closed |

The source oracle for the accepted fallback is pret `pokegold`/`pokecrystal`
`IsInJohto`: current group/map call, `LANDMARK_FAST_SHIP` Johto branch,
`LANDMARK_SPECIAL` backup group/map retry through the same call, one Kanto
threshold, and common Johto/Kanto returns. The richer Victory Road-aware
two-threshold authority remains preferred whenever it is complete.

## Focused real control

Bronze2 ROM SHA-256:
`87758fbc06a9abc73577bbc16d184bc3fb6f35d5abf22d776156629b5e5ae811`.
The complete one-threshold authority is unique. All 101 encounter-bound static
map IDs join through the retained header/landmark tables: 68 base areas across
34 primary-region landmarks and 33 base areas across 26 secondary-region
landmarks.

Normalized exact controls:

- primary raster ARGB SHA-256: `6e36d20b35f904a06fec5e11750c8938b9163f2d05ccdc848bd44b16e883497c`
- secondary raster ARGB SHA-256: `17a94384a359aaa5c9179249800442388dd1042fe9956a83f1fad319c7e275f1`
- location fingerprint SHA-256: `9646f17b9ca2a9559f3f2c6bf50ebac374171f8d8683ce75039f91c67329bf8a`

Real-binary counterfactuals prove fail-closed behavior: making the threshold
equal to the ship landmark or changing the backup path's map-location call
target leaves no complete classifier and returns typed `landmark-join`
unavailable. Gold, Silver, and Crystal retain their existing exact raster and
location hashes.
