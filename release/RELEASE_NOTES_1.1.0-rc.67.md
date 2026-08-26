# DualDex 1.1.0-rc.67

RC67 corrects the Pokédex Height Comparison so the visible trainer and Pokémon artwork—not each PNG's transparent canvas—represents the stated physical height.

## Height Comparison

- The trainer remains the approved 1.7 m reference.
- The taller of the trainer and Pokémon now occupies exactly 80% of the usable ruler height.
- Transparent margins are removed at render time without modifying or stretching ROM-derived artwork.
- Trainer and Pokémon aspect ratios remain intact and both figures stay grounded on the zero-metre baseline.
- The scale continues to expand for Pokémon taller than 1.7 m.
- If pixel inspection is unavailable, the original bounded image presentation remains as a safe fallback.

## Scope

- Pokédex data, trainer identity, ROM parsing, live memory, saves, battle behavior, maps, Organic discovery, and navigation are unchanged.
- No emulator memory is written and no game command is sent.
- No device or emulator is used during publication.

## Validation and delivery

- A focused alpha-bounds unit control proves transparent pixels are excluded from visible-size measurement.
- A headless browser control uses an image with 80% transparent padding and proves the visible trainer occupies 80% of the ruler, preserves its aspect ratio, and touches the baseline.
- The complete companion web suite passes with 193 tests across 26 files, and the production web bundle builds successfully.
- RC67 is an in-place prerelease update of `com.darkaxt.dualdex` with version code `1010067`.
