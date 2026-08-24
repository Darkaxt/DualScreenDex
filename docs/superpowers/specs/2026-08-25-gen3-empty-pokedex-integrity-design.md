# Gen III Empty Pokédex Integrity Design

## Objective

Prevent a valid Gen III save with an empty decoded party from inventing Pokédex discoveries, and ensure every live Pokédex flag number is translated to the catalog's internal species ID before it enters Organic knowledge state.

## Observed failure

On Modern Emerald v3.5, the live save contains no party members and no caught Pokémon. The RC59 runtime nevertheless reports 22 seen Dex numbers and renders 20 gray species. Replaying `Gen3PokedexCodec` against the real SaveBlock2 shows that the candidate scan selects `0x38` because zero-filled bytes resemble a header and a non-empty unrelated region receives an evidence bonus. The correct user-visible result before a party anchor exists is no inferred discoveries.

The live player bridge has a second independent defect: `ResolvedPlayerStateChanged.seenDexNumbers` and `caughtDexNumbers` are added directly to `KnowledgeLedger.seenSpecies` and `caughtSpecies`, even though those ledger collections are keyed by internal species ID. Hacks whose internal IDs differ from National Dex numbers therefore reveal the wrong entries. The official Emerald control also proves that the catalog's displayed regional number cannot be used as the save flag index: Bulbasaur is displayed as Hoenn 203 but is stored in National-Dex flag 1.

## Design

### Empty-party resolution

Live `Gen3PokedexCodec` decoding will treat an empty, successfully decoded party as a fail-closed pre-party state. It will return empty seen and caught sets without running the heuristic offset scan. The snapshot's `ownedOffset` will be nullable and remain `null` because no layout was proven. Existing non-empty-party resolution remains unchanged, including expanded aligned layouts supported by party-owned Dex anchors. Checksum-validated persisted SaveRAM uses an explicit persisted-evidence mode so a damaged party record does not discard otherwise valid recovery data.

This behavior applies only when the party decoder positively supplies an empty list. An unavailable live party remains unavailable rather than being collapsed to an empty list.

### Catalog translation

`SaveKnowledgeMapper` will expose one shared Pokédex-flag translation. Both SaveRAM recovery and live player updates will use it. Published expansion and unified layouts keep their ROM-extracted National-Dex mapping. Retail Gen III catalogs whose displayed numbering is regional use the engine's structural National-Dex mapping (IDs 1-251 map directly and IDs 277-411 map to 252-386); the reserved form gap is not promoted as independent Pokédex entries. Unknown overflow records fail closed. The live action contract will carry `seenSpeciesIds` and `caughtSpeciesIds`, making the ledger key space explicit.

Trainer Card seen/caught totals remain counts of distinct decoded Dex numbers. Organic species visibility uses translated internal species IDs.

### Existing state

The affected console has no save-synchronized checkpoint for the current save. The false entries are regenerated in the running process. Loading the corrected release starts from the save-scoped baseline and will not recreate them. No ROM, save, catalog database, or legacy ledger deletion is part of this fix.

## Verification

- A codec regression reproduces the `0x38` decoy and proves an empty party returns no discoveries and no asserted offset.
- Existing expanded-layout tests prove non-empty-party discovery remains intact.
- An official Emerald real-ROM control writes explicit flags, proves regional 203 maps to National flag 1, and verifies 15 seen/8 caught remain intact through the live runtime.
- A runtime regression uses a catalog whose internal species ID differs from its Dex number and proves only the translated species is revealed/caught.
- Focused module tests, the complete unit suite, release policy, secure dependency gate, lint, and release APK assembly must pass before publication.
- The protected GitHub workflow creates `v1.1.0-rc.61`, version code `1010061`, and the signed prerelease APK. Publication does not install or launch the APK.
