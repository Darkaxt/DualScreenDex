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
| Dark Energy 5.01 | Gold/Silver code shape, but required encounter IDs exceed its valid group table | Gold/Silver landmark candidate | complete two-threshold candidate | fail closed at header extent/join |

The source oracle for the accepted fallback is pret `pokegold`/`pokecrystal`
`IsInJohto`: current group/map call, `LANDMARK_FAST_SHIP` Johto branch,
`LANDMARK_SPECIAL` backup group/map retry through the same call, one Kanto
threshold, and common Johto/Kanto returns. The richer Victory Road-aware
two-threshold authority remains preferred whenever it is complete.

## Bronze name-control boundary

The Gold/Silver `TownMap_ConvertLineBreakCharacters` source requires an `@`
terminator for the copied buffer, converts only `<WBR>`/`<BSP>` to Town Map line
feeds, and then calls the unchanged `PlaceString` engine. In that engine,
`<DONE>` is a display terminator, but `<NULL>` replaces the source with a
runtime debug/error string involving mutable object state. It is not a stable
name terminator.

Bronze's first rejected encounter-bound record contains readable glyphs followed
by `<NULL>`, then `<DONE>` controls, and eventually `@`. The normalized decoder
therefore does not prefix-truncate the record: it accepts `<DONE>` only when the
copied `@` terminator is present, decodes the complete static English glyph and
Town Map control set, and fails closed on `<NULL>` or other dynamic controls.
Bronze remains typed `landmark-join` unavailable for this proven binary reason.

Dark Energy retains the Gold/Silver ROM0 pointer consumer and the caller proves
the same map-data bank. Its materialized encounter projection, however, includes
required group/map IDs beyond the structurally valid group pointer extent. The
first such required group entry is not a switchable-ROM pointer, and numerous
later required entries fail the same invariant. No fallback table extent or
partial encounter binding is inferred; the candidate header authority is
rejected before landmark decoding.

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
