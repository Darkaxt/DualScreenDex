# Generation I and II SaveRAM compatibility

This report covers the Western mainline-family SaveRAM layouts targeted by the current Red/Blue, Yellow, Gold/Silver, and Crystal ROM profiles. DualDex reads each 32 KiB source without modifying it. ROM bytes, save bytes, trainer data, private paths, and file hashes are not included.

## Result legend

- **AVD paired**: RetroArch/mGBA created the SaveRAM through normal game menus on the dedicated DualDex AVD, and the file was parsed with its exact ROM catalog.
- **Source fixture**: a minimal synthetic save exercises offsets and checksums derived from the named public disassembly. The fixture contains no third-party player data.
- **Available**: the reader validated and exposed the capability, including a valid empty collection.
- **N/A**: the original game format does not retain that value.
- **N/F**: the format supports the value, but the validated sample contains no usable evidence.

| Game family | Evidence | Save-copy competition | Seen / caught | Party / boxes | Area | DVs | Egg / form | Capture ball |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Pokemon Red Version / Pokemon Blue Version | Two independent AVD-paired saves | Main block plus current/canonical box recovery | Available | Available | Available | Available | N/A | N/A |
| Pokemon Yellow Version | Source fixture | Shared Gen I main and box-bank checksums | Available | Available | Available | Available | N/A | N/A |
| Pokemon Gold Version / Pokemon Silver Version | Source fixture | Primary block or reconstructed split backup | Available | Available | Available | Available | Available | N/A |
| Pokemon Crystal Version | Source fixture | Primary or contiguous backup block | Available | Available | Available | Available | Available | N/A |

The Red and Blue samples were deliberately captured at the earliest checksum-valid point, before receiving a party Pokémon. Both therefore report zero seen, zero caught, zero party members, zero stored members, and saved area `0:38`. This proved an important fresh-save edge case: unopened PC banks remain erased (`0xff`) and must be accepted as valid empty boxes only after the main save checksum has validated.

## Reader contract

- Gen I validates the complemented main checksum, bank-wide and per-box checksums, and structural species-list/record agreement. A structurally valid current-box copy can recover the active box when its canonical bank copy is damaged.
- Gold/Silver and Crystal compete as separate Gen II layouts. Each requires both check markers and its unsigned 16-bit checksum. Gold/Silver reconstructs its split backup regions in canonical order; Crystal validates its contiguous backup.
- Party and box records are decoded independently. A bad occupied record degrades only that collection's evidence instead of authorizing guessed values.
- Gen I/II DVs are normalized as `[HP, Attack, Defense, Speed, Special]`; HP is derived from the low bits of the four stored DVs. Special is counted once.
- Gen II recognizes the egg marker and derives Unown form identity from the four stored DVs.
- Gen I/II capture-ball identity is `NOT_APPLICABLE`. A caught marker uses the active ROM's generic Poké Ball artwork and never claims which ball captured the individual.
- A checksum-invalid or structurally incompatible 32 KiB file cannot replace the last checksum-valid snapshot.

## Public layout evidence

The implementation was checked against exact public source revisions and locally assembled RAM maps:

- [pret/pokered `ea49f472`](https://github.com/pret/pokered/blob/ea49f472bf4391e6f5241c007a8044a88dd6a8b0/ram/sram.asm) and its [checksum routines](https://github.com/pret/pokered/blob/ea49f472bf4391e6f5241c007a8044a88dd6a8b0/home/sram.asm)
- [pret/pokeyellow `0a085154`](https://github.com/pret/pokeyellow/blob/0a0851546ff65f65c4bb2af2b95e279e709a8653/ram/sram.asm) and its [checksum routines](https://github.com/pret/pokeyellow/blob/0a0851546ff65f65c4bb2af2b95e279e709a8653/home/sram.asm)
- [pret/pokegold `a0dad095`](https://github.com/pret/pokegold/blob/a0dad0957ac8a9ffa67e950ee3ab6715a212ded5/ram/sram.asm) and its [save routines](https://github.com/pret/pokegold/blob/a0dad0957ac8a9ffa67e950ee3ab6715a212ded5/home/sram.asm)
- [pret/pokecrystal `8e8f7e20`](https://github.com/pret/pokecrystal/blob/8e8f7e20052a596371a77022f0392c285e51bbf1/ram/sram.asm) and its [save routines](https://github.com/pret/pokecrystal/blob/8e8f7e20052a596371a77022f0392c285e51bbf1/home/sram.asm)

## Policy and UI evidence

- Organic lists only seen/caught species. An uncaught species exposes no static Stats/More data or ROM learnset; capture unlocks the complete static catalog.
- Hidden lists only captured species, while Discovered exposes the complete validated ROM index.
- Team and Area are independently capability-gated: either can remain available when the other join is unavailable.
- Ruleset changes switch resident catalog data without reopening the ROM, rereading SaveRAM, or writing a duplicate SQLite catalog.
- Real-browser captures exercised all three information policies with the Red catalog and a captured Organic encounter with the Gold catalog. The browser finished with zero console errors or warnings.

Only the named USA/Europe-compatible save shapes are asserted here. Other localizations must pass their own checksum and structural competition before DualDex reports them as compatible.
