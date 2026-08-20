# DualDex 1.1.0-rc.16

RC16 corrects passive double-battle ownership while retaining RC15's Trainer, Party, Atlas, clock,
archive-loading, and display-recovery features.

## Correct double-battle selection

- The runtime now consumes the parser-resolved Gen III battle UI owner group: active battler,
  action cursors, and per-battler move cursors are read together.
- In a double battle, the companion shows the move belonging to the active player battler instead
  of defaulting to the left player Pokémon.
- Target selection remains independent: a validated UI target cursor is used when available;
  otherwise only automatic single-opponent targeting remains and doubles fail target selection closed.
- Opponent or malformed ownership states fail closed instead of presenting another battler's move.

## Safe Organic knowledge

- A player's PP decrease is associated with the preceding confirmed owner/move/target command.
- Ambiguous double-battle samples do not create matchup-effectiveness knowledge.
- Existing per-ROM knowledge migrates once by clearing only old matchup-effectiveness entries that
  could have been paired incorrectly. Seen, caught, party, area, and move-frequency knowledge remain.
- Single-battle learning for Gen I, Gen II, and Gen III remains supported.

## Verification and delivery

- Focused decoder, layout, tracker, coordinator, runtime, and repository migration controls pass.
- Affected battle-memory, companion-core, Android unit, lint, and unsigned release-assembly gates pass.
- The production APK is built and signed only by the protected GitHub workflow. It is not installed
  or launched by this release task; device acceptance remains with the user.
