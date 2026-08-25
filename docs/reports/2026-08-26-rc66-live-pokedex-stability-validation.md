# RC66 Gen III Live Pokédex Stability Validation

## Outcome

The RC66 implementation satisfies the live Pokédex stability specification. The captured Modern Emerald input `0 / 0 -> 48 / 48 -> 1 / 1` now publishes `0 / 0 -> 1 / 1`; the false middle candidate is never exposed to the resolved snapshot or UI. Normal same-layout discoveries remain immediate, including the valid double-battle transition `1 / 1 -> 1 / 3`.

Physical reproduction on Modern Emerald remains user validation. Automated controls reproduce the exact decoded sequence at the pre-publication boundary without installing or operating an APK.

## Specification cross-check

| Requirement | Result | Evidence |
| --- | --- | --- |
| PS-01 | PASS | `withholds the transient starter layout and accepts the plausible correction immediately` proves `0 -> 1` publishes on the first plausible poll; `publishes ordinary same-layout discoveries on the first poll` proves later `1 -> 2` is immediate. |
| PS-01a | PASS | `publishes two newly seen opponents from a double battle immediately` proves `1 / 1 -> 1 / 3` is not delayed. |
| PS-02 | PASS | `Gen3LivePokedexStabilizer` compares new caught/seen members with the last accepted live sets and Party count; the 48-entry first candidate is withheld. |
| PS-03 | PASS | `publishes an identical suspicious candidate on its second poll` proves legitimate large states remain recoverable after two exact samples. |
| PS-04 | PASS | The captured `0 -> 48 -> 1` test returns caught/seen counts `0, 0, 1`; the plausible `0x28` correction replaces the pending `0x80` candidate immediately. |
| PS-05 | PASS | `rejects a conflicting offset after the live layout is confirmed` repeats the conflicting candidate and retains the accepted state; unavailable candidates follow the same retention branch. |
| PS-06 | PASS | `reset permits a different layout in a new session`; production calls `reset()` from `beginSession` and `endSession`, not `suspendLive`. |
| PS-07 | PASS | `ownedFlagOffset` is consumed only by the app-internal stabilizer and is not projected by `ResolvedPokedexState` or companion API models. |
| PS-08 | PASS | The production diff is confined to the Gen III player overview and unified Gen III live-sample path. Gen I/II adapters, recovery resolution, persistence, and battle knowledge are unchanged; affected decoder regressions pass. |

## Automated evidence

- `Gen3LivePokedexStabilizerTest`: 6/6 PASS.
- `UnifiedGameStateDecoderTest` and `Gen3LiveMemoryCodecsTest`: PASS.
- Companion web: 26 files, 192/192 tests PASS.
- Production companion web build: PASS.
- Release policy: 18/18 tests PASS.
- Secure build dependency verification: PASS.
- Android debug lint and release lint-vital: PASS.
- Unsigned RC66 release assembly: PASS.
- Unsigned identity: `com.darkaxt.dualdex`, version `1.1.0-rc.66`, code `1010066`.

## Deferred user validation

- Reproduce initial Pokémon acquisition once on the signed RC66 build and confirm that the Pokédex moves directly from empty to the acquired starter without a transient mass-owned frame.
- Confirm an ordinary encounter and a double battle update seen counts immediately. These physical checks are not claimed by automated validation.
