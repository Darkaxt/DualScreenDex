# Trainer License Unlock Design

## Goal

Unlock the Trainer Card as a small gameplay milestone when the active ROM-save playthrough first exposes a valid party Pokemon. The feature must not remain hidden merely because one Trainer Card field, SaveRAM, or a transient live-memory section is unavailable.

## Selected design

Add a one-way `trainerCardUnlocked` flag to the playthrough knowledge ledger. A validated party record from either live memory or the matching save sets the flag. Empty or unavailable party samples never clear it. The flag follows the existing ROM-and-save checkpoint lifecycle and defaults to locked for older checkpoints.

The API exposes the flag independently from Trainer Card data. Normal page headers use only this flag to decide whether the Trainer Card button exists. The full Trainer Card snapshot is no longer the navigation gate.

After unlock, the Trainer Card may be built from either the complete trainer snapshot or the live trainer identity. Fields that have not resolved use an ordinary dash placeholder. No parser state, capability label, failure reason, or other diagnostic text appears on the normal page.

## Alternatives rejected

- Checking whether the party is currently non-empty is simpler, but it can relock the feature during a partial poll or an unusual empty-party transition and therefore does not behave like a license.
- Checking caught or seen Pokédex data can inherit stale ROM-wide knowledge and does not prove that this playthrough has received its first Pokemon.
- Continuing to require the complete Trainer Card snapshot conflates data completeness with gameplay progression and reproduces the reported ambiguity.

## Data flow

1. The live-party or matching-save mapper validates party records against the active catalog.
2. At least one validated occupied party record sets `trainerCardUnlocked=true` while preserving any previous true value.
3. The save-synchronized knowledge checkpoint serializes the flag with a backward-compatible schema revision.
4. The state API publishes the flag and a partial-or-complete Trainer Card view.
5. The Pokedex header reveals the Trainer Card action from the license flag alone.
6. Catalog or playthrough replacement restores the flag only from that matching playthrough; transient polling cannot clear it.

## UI contract

- Locked: no Trainer Card shortcut is shown.
- First validated party Pokemon: the shortcut appears immediately.
- Unlocked: the shortcut remains available for that playthrough.
- Complete trainer data: all existing card fields render normally.
- Partial trainer data: known identity and artwork render; unresolved facts display `—`.
- No unlock toast, debug subtitle, parser explanation, or capability label is added.

## Verification

- Live and save party mappers unlock on the first valid party record and never relock on an empty sample.
- Invalid species records cannot grant the license.
- Ledger schema round-trips the flag and reads older schemas as locked.
- The API exposes partial trainer identity after unlock without inventing numeric values.
- The Pokedex header is hidden before the milestone and visible afterward even without a complete Trainer Card snapshot.
- Existing full Trainer Card rendering and navigation remain covered.
