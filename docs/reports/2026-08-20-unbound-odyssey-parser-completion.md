# Unbound and Odyssey parser completion

Date: 2026-08-20

This report closes the ROM/catalog portion of the staged Pokémon Unbound v2.1.1.1 and Pokémon
Odyssey v4.1.1 compatibility plan. A capability counts only when the exact ROM publishes an
`AVAILABLE` result with complete applicable coverage. `NOT_FOUND`, `AMBIGUOUS`, and
`NOT_APPLICABLE` are not counted as success.

## Exact controls

| Control | SHA-256 | Source authority |
| --- | --- | --- |
| Pokémon Unbound v2.1.1.1 | `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` | Dynamic Pokémon Expansion Unbound `fe058e0`; Complete FireRed Upgrade `b637a27` |
| Pokémon Odyssey v4.1.1 | `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0` | Creator-authored Odyssey documentation snapshot `31b1eff`; exact ROM descriptions and compiled consumers |

The source trees define semantics and table roles. Production selection does not use ROM names,
titles, hashes, source symbols, linked addresses, or per-ROM byte signatures.

## Final parser result

| ROM | Static/catalog | World map | Local maps | Ability mechanics | Complete parser capabilities |
| --- | ---: | ---: | ---: | ---: | ---: |
| Unbound | complete | 1 region | 294 maps / 258 assets | 254/254 AI ratings | **23/23 (100%)** |
| Odyssey | complete | 4 regions | 168 maps / 147 assets | 129/129 behaviors | **23/23 (100%)** |

Both exact controls publish every `RomCapability` key as `AVAILABLE`. Two independent complete
parses of each ROM produce identical capability evidence and ability mechanics.

## Ability-mechanics completion

### Unbound

The resolver identifies a unique raw-ID-indexed signed ability-rating vector through the selected
254-ID ability domain and compiled Thumb consumers that perform signed indexed-byte reads. The
vector includes the source-defined zero sentinel and uses the signed `-10..10` scale. Selection
requires a complete active domain, multiple distinct values, both negative and maximum ratings,
multiple complete compiled consumers, and a unique surviving candidate.

Every published Unbound ability receives one typed `AI_RATING`. Frozen examples include Stench
`1`, Drizzle `9`, Speed Boost `9`, Huge Power `10`, and Truant `-2`.

### Odyssey

Odyssey's 129 abilities use sparse raw IDs rather than a dense `1..129` domain. No trustworthy
compiled numeric AI-rating table was found, so the parser does not fabricate one. Instead, every
ability receives typed `BEHAVIOR` evidence:

- 121 behaviors come directly from the exact ROM's decoded ability-description records;
- eight records whose ROM text is literally `No description` use the corresponding standard,
  source-documented behavior selected by normalized ability identity;
- the profile is admitted only when the complete selected ability domain has a description or a
  known semantic fallback. An unknown placeholder causes the profile to fail closed.

This is complete behavior coverage, not a claim that Odyssey exposes 129 numeric AI ratings.

## Persistence, API, and presentation

For each exact ROM, the completed catalog was written through `CatalogCache`, checked with
`PRAGMA quick_check` (`ok`) and `PRAGMA foreign_key_check` (zero rows), reopened, and compared with
the parsed ability catalog. The reopened catalog was loaded into `ProductionCompanionRuntime`;
every ability referenced by navigable species retained nonempty mechanics through the bootstrap
API. The same vertical also revalidated the exact world-map PNGs and representative local-map
assets.

The existing Ability Detail view renders the persisted mechanic kinds and values. No new schema or
ROM-specific UI path was added.

## Save/runtime boundary

Odyssey remains **14/14** on the populated exact revision-5 save, including Trainer Card and all
five Bag pockets on a locally discovered artifact that was not supplied by the user. Unbound's
CFRU-style expanded-save ABI is resolved and persisted, but the locally discovered Unbound
SaveRAM is 128 KiB of `FF`. It correctly remains **0/14 decoded runtime values**;
a populated Unbound save is required to verify those fourteen values end to end. The erased file is
not counted as a successful save.

## Verification

The ability slice is commit `f34aea1` and follows the earlier static (`466795b`), map (`3d28e7a`),
and save/runtime (`3793750`) stages.

Fresh gates completed successfully:

```text
parser-core full suite: 1,112 tests, 0 failures, 0 errors
ability real controls: official Ruby/Sapphire/Emerald/FireRed/LeafGreen,
  Modern Emerald, Classic, Clover, Unbound, Odyssey; 0 skipped
Unbound/Odyssey exact parser: two independent parses per ROM; 23/23 each
Unbound/Odyssey CatalogParser -> SQLite -> runtime/API: BUILD SUCCESSFUL
SQLite integrity: quick_check=ok; foreign_key_check=0 rows for both exact catalogs
```

The ROM-derived companion-theme plan remains a separate queued change and is not part of this
compatibility result.
