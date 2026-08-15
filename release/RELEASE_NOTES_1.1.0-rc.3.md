# DualDex 1.1.0-rc.3

RC3 completes the third staged part of the DualDex 1.1 player-state foundation.

## One coherent read-only live snapshot

- Reads the ROM-resolved SaveBlock pointer globals first, then requests only the validated SaveBlock, Party, lifecycle, target, encounter-kind, and battle windows for that sample.
- Re-reads pointers for every sample, so a relocated SaveBlock is replaced rather than reused. An invalid SaveBlock2 pointer withholds Trainer and Bag while leaving independently valid location and Party data available.
- Publishes independent Trainer, location, Party, Bag, battle-presence, and battle-UI results. A validated party count of zero authoritatively clears the live party.
- Keeps opponent moves behind the existing observation gate; the new snapshot does not expose raw enemy move slots.

## Deterministic live-over-SaveRAM authority

- Valid live Trainer and Party state supersedes stale SaveRAM state.
- Missing or invalid live Trainer data retains a valid saved Trainer.
- A validated empty live party clears Team consistently in both normalized state and Pokédex filtering.
- Disconnect restores the SaveRAM fallback, while changing ROM identity drops all live state from the previous title.

## Deliberate boundary of RC3

- Trainer Card and detailed Party presentation pages are Stage 4. RC3 publishes their normalized internal state but does not add the screens yet.
- Bag and battle-UI results remain internal for later presentation work.
- Display selection/recovery is unchanged and remains a later 1.1 stage.
- All emulator memory access remains read-only. RC3 adds no input injection, memory writes, cheats, or game commands.

Device installation and validation remain manual.
