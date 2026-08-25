# DualDex 1.1.0-rc.66

RC66 prevents a transient false Gen III Pokédex layout from briefly showing dozens of owned and seen Pokémon when the first Party member is acquired.

## Live Pokédex stability

- The Gen III live decoder now preserves the candidate Pokédex flag offset selected from SaveBlock2.
- A plausible first acquisition is shown immediately.
- A suspicious first layout is withheld until the same offset and exact caught/seen sets appear in two consecutive samples.
- A plausible correction replaces a withheld candidate immediately, so the captured `0 -> 48 -> 1` input publishes only `0 -> 1`.
- Once confirmed, the Pokédex flag offset is pinned for the ROM session; a conflicting or temporarily unavailable candidate retains the last accepted live value.
- Ordinary discoveries remain immediate, including `1 / 1 -> 1 / 3` when a double battle reveals two opponents.
- Starting or ending a different ROM session resets the confirmation; a temporary live suspension does not.

## Scope

- Gen I/II decoding is unchanged.
- Save recovery, knowledge checkpoints, database persistence, battle knowledge, and UI contracts are unchanged.
- The retained offset is internal and is not shown in the companion UI or public API.

## Validation and delivery

- Six focused stability scenarios pass, including the captured starter sequence and the double-battle case.
- Unified decoder and Gen III live memory codec regressions pass.
- The complete companion web suite passes with 192 tests across 26 files, and the production web bundle builds successfully.
- Release-policy tests, secure dependency verification, Android debug lint, release lint-vital, and RC66 release assembly pass with version code `1010066`.
- Physical reproduction on Modern Emerald remains user validation; the APK is not installed or launched during publication.
