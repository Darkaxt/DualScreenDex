# Official Gen I/II Completion Design

## Goal

Make the six canonical English Red, Blue, Yellow, Gold, Silver, and Crystal Rev 1 ROMs report a truthful 100% data-compatibility result, using the real ROMs under `H:\My Drive\Roms` and the matching pret source trees as the format oracle.

## Selected approach

Use family-level binary structures, not ROM filenames or new digest branches:

- Red and Blue: recover the detached Gen I Mew base-stat/front-sprite record that the game itself selects outside the ordinary 150-record `BaseStats` table. The resolver must require one structurally valid detached record for the missing Pokédex ID, valid same-bank sprite streams, and the compiled `GetMonHeader` load/copy contract. Yellow remains the contiguous-table control.
- Gen I Pokédex descriptions: keep validating all 190 internal pointer slots, but measure semantic coverage against the 151 positive Pokédex IDs selected by the independently decoded internal-to-Dex map.
- Gold, Silver, and Crystal: locate the unique bank-local table of one 16-bit pointer per move, decode all descriptions with the GB text codec, and reject absent, malformed, sparse, or ambiguous candidates. Gen I remains genuinely not applicable because it has no move-description table.

The catalog, capability evidence, and CLI percentage must agree. Optional failures remain fail-closed; no map, emulator, APK, or release code changes are part of this slice.

## Alternatives rejected

- Adding Red/Blue Mew and Gen II move-description offsets to exact SHA profiles is fast but does not help source-compatible builds and duplicates semantics already visible in ROM code.
- A complete LR35902 IR/dataflow engine would generalize further, but is disproportionate to these two small, source-proven table contracts.

## Acceptance

- Real-ROM tests assert Mew stats and sprite for Red/Blue, preserve Yellow, and assert 151/151 Gen I species/stat/sprite/description coverage.
- Real-ROM tests assert 251/251 decoded move descriptions for Gold/Silver/Crystal, including exact sample strings.
- All six parse as selected, have 100.00 compatibility, require no manual review, persist/reopen with clean SQLite integrity, and have no reference errors.
- Production changes contain no new ROM filename, SHA, title, or fixed-offset selector.
