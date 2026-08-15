# Modern Emerald 3.5 complete compatibility

Date: 2026-08-15

The exact Modern Emerald 3.5 control has SHA-256
`21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895`.
Its source oracle is `resetes12/pokeemerald` Release 3.5 at commit
`01a4212b9718886b19ce6e45c332ba618cc92a26`.

## Sprite-domain correction

The ROM contains a physical 462-row front-sprite table. The sprite codec decodes 457
rows. Source and exact-ROM evidence agree that the five undecodable rows are the unused
structural slots 456, 457, 459, 460, and 461 (`SPECIES_UNUSED_SPACE5`,
`SPECIES_UNUSED_SPACE6`, `SPECIES_UNUSED_SPACE8`, `SPECIES_UNUSED_SPACE9`, and
`SPECIES_UNUSED_SPACE10`). They are not Pokédex species.

The parser had already selected a 428-species navigable domain through the complete
compiled-referenced species-to-Dex map, and all 428 records materialized ROM sprites.
Sprite completeness was nevertheless left on the raw 457/462 physical-table ratio
because semantic sprite coverage was applied only to published expansion-header layouts.
The correction applies the already-proven Gen III semantic domain to sprite coverage for
every selected Gen III layout. Production selection uses neither the ROM identity nor the
source labels above.

## Final result

The rebuilt parser reports:

- routing: `SELECTED / EMERALD`;
- data compatibility: `COMPLETE`;
- strict capability coverage: `22/22` (`100.00%`);
- record-weighted compatibility: `100.00%`;
- navigable species sprites: `428/428`;
- sprite capability: `AVAILABLE`, with zero incomplete semantic records;
- manual review required: `false`.

The generated JSON report had SHA-256
`5249BA8B26236536702962127474E46A950B859B0C649CC815B7FDAFAC1606B0`.

## Verification

- The real-ROM regression first failed on the previous `PARTIAL` sprite status, then
  passed after the semantic-domain correction.
- `SemanticCoverageTest`, `CatalogParserTest`, and the exact Modern sprite control passed.
- The exact catalog passed parser to schema-10 SQLite write/reopen, production runtime,
  and loopback `/api/bootstrap`; `SPRITES` remained `AVAILABLE` at every boundary.
