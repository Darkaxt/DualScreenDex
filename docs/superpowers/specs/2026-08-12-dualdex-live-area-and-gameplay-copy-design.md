# DualDex Live Area and Gameplay Copy Design

Date: 2026-08-12

## Objective

Restore the missing area-relative rarity word by using the live Gen III map identity rather than treating the last checksum-valid SaveRAM location as current battle truth. Replace diagnostic and policy-oriented prose on gameplay screens with short, natural unavailable states. Resolve displayed area labels from the active ROM instead of exposing numeric map identifiers.

## Decisions

### Live area authority

For Gen III, the parser proves the ROM's SaveBlock1 pointer global from compiled consumers that load both location bytes at offsets 4 and 5. The catalog persists this immutable runtime address as ROM-scoped metadata. During battle polling, DualDex reads that pointer and the referenced location bytes alongside battle memory. A structurally valid live group/number is the primary rarity area. Checksum-valid SaveRAM remains a fallback only when no live area can be read.

The runtime never guesses an address, never uses a ROM-title exception, and never converts a stale SaveRAM location into live evidence.

### ROM-derived place names

The parser resolves Gen III map headers structurally from the selected encounter map domain, reads each map header's region-map section identifier, and resolves the corresponding ROM text through a validated region-name table. Names are published by group/number base ID. If either structure is ambiguous or invalid, the name is omitted; the UI does not display a hexadecimal fallback.

### Rarity behavior

The existing weighted encounter-table calculation remains authoritative. It receives the live area first and matched SaveRAM only as fallback. A supported comparison produces the relative word plus the innate word, for example `ORDINARY TRAINED`. If comparison evidence is unavailable, the title contains only the innate word and the explanation is `Area comparison not available.` No `UNKNOWN` label is rendered.

### Gameplay copy boundary

Battle, Pokédex, move, and ability pages show gameplay language only. They do not expose parser status, SaveRAM, RetroArch, ROM offsets, raw IDs, capability names, recovery/debug instructions, or internal failure enums. The explicit Diagnostics screen retains detailed technical evidence.

Approved unavailable copy includes:

- `DATA NOT AVAILABLE`
- `Catch this Pokémon to add its Pokédex entry.`
- `Pokédex data not available.`
- `NO MOVE SELECTED`
- `Select a move in battle to view its details.`
- `Area comparison not available.`
- `NO MOVES RECORDED`
- `Moves used in battle will appear here.`
- `Ability data not available.`
- `Move effect data not available.`
- `No additional data available.`

Unknown gameplay values render as an em dash rather than `UNKNOWN`.

## Data Flow

1. Parser proves the SaveBlock1 pointer global and ROM-derived map names.
2. `ParsedCatalog.runtimeMetadata` is stored in the catalog cache.
3. Android battle polling reads battle data plus the live SaveBlock1 location.
4. `BattleMemorySample.currentAreaBaseId` flows into `BattleState`.
5. The API chooses live area, then matched SaveRAM fallback, and publishes the resolved area name.
6. `RarityEvaluator` compares the opponent with the exact weighted encounter table for that area.
7. The web layer renders the result without interpreting internal failure reasons.

## Failure Handling

- Ambiguous parser evidence produces no runtime pointer or map label.
- A pointer outside GBA work RAM is rejected.
- A changed pointer is re-read; no stale cached target is trusted.
- An area absent from the encounter catalog produces no relative tier.
- Missing names produce no raw numeric label.
- Technical reasons remain available only in Diagnostics.

## Verification

- Synthetic parser tests for pointer proof, map-header/name resolution, ambiguity, and malformed pointers.
- Catalog-store round-trip and stale-schema invalidation tests.
- Battle coordinator tests for live location reads, cached polling, pointer changes, and fallback.
- Companion API tests for live-over-save precedence, resolved names, and two-word rarity.
- Web tests proving all approved copy and absence of gameplay `UNKNOWN`, raw area hex, SaveRAM, RetroArch, and debug prose.
- Exact Modern Emerald live-ROM regression.
- Full module tests, web production build, release APK, public prerelease, and install-only ADB handoff.
