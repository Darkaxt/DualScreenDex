# Unbound and Odyssey static catalog completion

Date: 2026-08-20

This stage completes the applicable static-description domain for the exact Pokémon Unbound v2.1.1.1 and Pokémon Odyssey v4.1.1 controls. It does not claim complete ROM support: ability mechanics, world maps, and local maps remain separate applicable work, so each parser is now at 20/23 complete capabilities.

## Numeric result

| ROM | Before | After | Applicable static result | Parser capabilities |
| --- | ---: | ---: | --- | ---: |
| Pokémon Unbound v2.1.1.1 | 868/922 move descriptions | 922/922 move descriptions | `MOVE_DESCRIPTIONS` available; every positive move ID 1..922 has ROM-derived text or an explicit ROM `-` placeholder | 20/23 |
| Pokémon Odyssey v4.1.1 | 409 descriptions plus 2 battle records incorrectly treated as missing Pokédex rows | 409/409 applicable Pokédex descriptions plus 2/2 preserved battle-only records | `POKEDEX_DESCRIPTIONS` available; Abyss Eye and Tentacle keep battle name/type/stats/sprite data while Pokédex-only fields are not applicable | 20/23 |

Neither result changes a denominator to manufacture coverage. Odyssey still contains 411 battle species. The complete compiled description domain contains Pokédex entries 0..409, while the creator-authored v4.1.1 workbook identifies the two overflow records as boss-fight entities rather than ordinary Pokédex entries.

## Binary authority

- Unbound's selected move-data layout contains 923 records including ID 0. A unique complete 922-entry description-pointer table is independently referenced by three compiled consumers. The adjacent false candidate is rejected. IDs 769..819 point to explicit `-` text in the ROM; IDs 820 and 821 contain ordinary descriptions and are retained.
- Odyssey's selected description layout contains 410 records including Dex 0 and is independently referenced by eight compiled consumers. The following full record is erased. A separate complete compiled species-to-Dex map is referenced twice and covers ordinary Dex IDs 1..409 exactly; the two consecutive battle-only internal records map to overflow Dex values 410 and 411.
- Production selection uses the shared analysis session, typed tables, compiled consumers, complete domains, and fail-closed uniqueness. ROM names, SHA-256 values, source symbols, and absolute per-ROM addresses are confined to exact tests/evidence and do not select production behavior.

## Persistence and integrity

Each exact ROM is parsed twice from independent byte loads. The repaired ID-to-field semantics match between parses, then survive the production incremental `CatalogStore` write/reopen path unchanged.

- 14/14 catalog sections persisted for each ROM.
- `PRAGMA quick_check` returned `ok` for each SQLite catalog.
- `PRAGMA foreign_key_check` returned zero rows.
- Decoded catalog references close for species types, abilities, evolutions, learnsets, acquisitions, and encounters.

## Verification

Focused exact parser and persistence controls:

```powershell
.\gradlew.bat :parser-core:test --tests '*UnboundOdysseyStaticCompletionLiveRomTest' --tests '*MoveDescriptionMaterializerTest' --tests '*RecordMaterializersTest' --no-daemon --console=plain
.\gradlew.bat :catalog-store:test --tests '*Unbound completed move descriptions*' --tests '*Odyssey completed Pokedex descriptions*' --no-daemon --console=plain
```

The focused parser run passed 42 tests. The broader description/materialization run passed 103 tests with zero failures or errors (six unrelated opt-in controls skipped). The exact two-ROM persistence run completed successfully in 2m55s.

The complete affected-module gate passed in 6m13s:

- `parser-core`: 1,106 tests, zero failures or errors; 157 unrelated environment-gated controls skipped.
- `catalog-store`: 17 tests, zero failures or errors; one unrelated environment-gated control skipped.

## Remaining applicable gaps

For both exact controls, the remaining parser gaps are ability mechanics, world maps, and local maps. Save/runtime completion is a later stage: Unbound remains 0/14 on the supplied expanded saves; Odyssey remains 12/14 with Trainer and Bag unresolved. These gaps remain applicable and are not counted as success.
