# Gen III Live Pokédex Stability Design

## Evidence

RC65 captured the Modern Emerald starter transition at the pre-UI boundary:

- sample 80: Party `0 -> 1`, caught `0 -> 48`, seen `0 -> 48`;
- sample 81: caught `48 -> 1`, seen `48 -> 1`;
- sample 84: caught remains `1`, seen becomes `2` after the next encounter.

Every value was sourced from `LIVE`; recovery application `4` remained `UNCHANGED`. The transient value therefore originates in the live Gen III Pokédex candidate resolver, which begins aligned SaveBlock2 layout selection only when validated Party evidence first becomes available.

## Contract

- **PS-01:** Normal plausible first acquisition (`0 -> 1`) and subsequent same-layout caught/seen changes publish on the first poll.
- **PS-01a:** A double battle may reveal two opponents in one sample; `1 / 1 -> 1 / 3` therefore publishes immediately.
- **PS-02:** A suspicious first layout candidate that adds more caught entries than the current Party can explain, or more than two seen entries beyond the established snapshot, is withheld from publication.
- **PS-03:** Two consecutive identical suspicious candidates confirm the layout and publish the value, preserving compatibility with legitimate large existing Pokédexes revealed after an empty-Party interval.
- **PS-04:** A later plausible candidate may replace a withheld suspicious candidate immediately; the observed `0 -> 48 -> 1` input therefore publishes `0 -> 1`, never `48`.
- **PS-05:** Once an owned-flag offset is confirmed, the session never changes to a different offset. A conflicting or temporarily unavailable candidate retains the last accepted live Pokédex state.
- **PS-06:** Session replacement/end resets all confirmation state; live suspension within the same ROM session does not.
- **PS-07:** Candidate offsets remain internal diagnostics and are not projected into the web API or normal UI.
- **PS-08:** Gen I/II, recovery precedence, database persistence, and battle knowledge are unchanged.

## Design

`Gen3PokedexCodec` already returns the selected `ownedOffset`, but `Gen3LiveMemoryCodecs` currently discards it. `LivePokedexState` will retain that optional internal offset. A focused `Gen3LivePokedexStabilizer`, owned by `UnifiedGameStateDecoder`, will compare each decoded candidate with the last accepted state and Party evidence before `LiveGameSnapshot` publication.

The stabilizer immediately accepts the first session snapshot and any plausible first acquisition. It holds only suspicious first-layout candidates and compares their offset plus caught/seen sets with the next poll. Once confirmed, the offset is pinned until the decoder starts or ends a different session. This is narrower than delaying every live update by two loops and preserves immediate ordinary behavior.

## Validation

Tests must reproduce the exact `0 -> 48 -> 1` sequence, two-loop confirmation of a legitimate large candidate, confirmed-offset conflict rejection, immediate `1 / 1 -> 1 / 2`, and reset behavior. Existing Gen III memory-codec and unified-state tests must remain green. The protected release workflow remains the final all-module, lint, signing, and publication gate.
