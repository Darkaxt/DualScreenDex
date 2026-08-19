# Unbound and Odyssey Complete Compatibility Design

## Goal

Bring the exact Pokémon Unbound v2.1.1.1 and Pokémon Odyssey v4.1.1 controls to complete, truthful DualScreenDex support. Completion means 23/23 parser capabilities and 14/14 save/runtime capabilities for each ROM, with deterministic catalogs, zero dangling references, successful SQLite reopen, and the same data exposed through the runtime/API/UI paths.

`NOT_FOUND` is an unresolved applicable capability and never counts as `NOT_APPLICABLE` or as success. Percentages are calculated from decoded records over the exact expected record domain, not from labels.

## Exact controls and baseline

### Pokémon Unbound v2.1.1.1

- Exact ROM SHA-256: `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`.
- Parser baseline: 86.70%, calculated as `(19 complete capabilities + 868/922 move descriptions) / 23`.
- Complete static domains: 1,266 species/names/types/stats/sprites; 1,266 displayed Pokédex descriptions backed by 906 source description records; 1,294 evolution rows and 732 edges; 20 types and 128 matchups; 922 moves/details; 1,266 learnsets with 21,862 entries; 3,954 egg, 45,381 machine, and 19,524 tutor links; 254 ability names/descriptions; 339 encounter areas; 20 type presentations; 12 balls.
- Static hole: 868/922 move descriptions, with 54 unresolved records.
- Parser holes: 0 ability mechanics, 0 world-map regions, and 0 local-map rasters.
- Save/runtime baseline: 0/14. Three exact 131,072-byte saves are readable but currently rejected because the stock Gen III sector/checksum layout does not yield a complete valid slot.
- Persistence baseline: 14 SQLite catalog sections, successful reopen, zero reference errors.

### Pokémon Odyssey v4.1.1

- Exact ROM SHA-256: `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0`.
- Parser baseline: 86.94%, calculated as `(19 complete capabilities + 409/411 Pokédex descriptions) / 23`.
- Complete static domains: 411 species/names/types/stats/sprites; 412 evolution rows and 205 edges; 18 types and 116 matchups; 477 moves/details/descriptions; 411 learnsets with 5,609 entries; 973 egg, 8,707 machine, and 2,127 tutor links; 129 ability names/descriptions; 24 encounter areas; 18 type presentations; 12 balls.
- Static hole: 409/411 Pokédex descriptions, with 2 unresolved species.
- Parser holes: 0 ability mechanics, 0 world-map regions, and 0 local-map rasters. Local-map structural discovery currently admits a false extent of 105,010,432 pixels and correctly stops at the 100,000,000-pixel safety boundary; the fix must prove the real map extent rather than raise that boundary.
- Save/runtime baseline: 12/14. The exact revision-5 save resolves 14 sections, 41 seen, 40 caught, one current area, six party members, 26 stored Pokémon, and 32 Pokémon identity/stat/IV/ball records. Trainer and Bag remain unresolved because no typed Odyssey Trainer/Bag ABI is selected.
- Persistence baseline: 14 SQLite catalog sections, successful reopen, zero reference errors.

## Source authority and limitations

Sources are semantic oracles. Production code must recover the corresponding structures from the supplied ROM/save and must not select behavior from a ROM filename, SHA, title, source symbol, absolute address, fixed per-ROM offset, or byte signature.

- `Dynamic-Pokemon-Expansion-Unbound`, branch `Unbound`, commit `fe058e0e3ac23cf968cf950de43332135bc1549d`, provides the expanded species, evolution, Pokédex, sprite, move, and ability data model used by the Unbound ecosystem.
- `Complete-Fire-Red-Upgrade`, commit `b637a27898b14e25dd24d0f69a3e302f0069deb8`, provides the related battle-engine and expanded save/runtime semantics. It is not proof that every linked address or optional feature is identical in the shipped Unbound ROM.
- `Pokemon-Odyssey-Docs-App`, commit `31b1effbda21e23c706e6713cd8fec5cd989c89f`, contains creator-authored Odyssey data workbooks and generated data. These are authoritative for documented static content, not for GBA binary layout, maps, saves, or ARM/Thumb control flow.
- `bernardoennes/pokemon-odyssey`, commit `8c5911e4`, is an RPG Maker XP / Pokémon Essentials project rather than the GBA v4.1.1 source. It must not be used as a binary-layout oracle for this work.

The exact ROMs and saves are the final binary authority. Test-only SHA checks bind evidence to those controls; they do not enter production selection.

## Architecture

### Static catalog completion

Static recovery remains inside the existing parser session and catalog materializer. A source-shaped candidate is eligible only when a compiled consumer or independently selected typed parent table nominates it, its decoded domain agrees with the already selected move/species identity domain, and every accepted row validates under the relevant text/pointer ABI.

Unbound move-description recovery must account for all 922 ordinary move IDs and distinguish shared/fallback descriptions from missing pointers without inventing text. Odyssey Pokédex recovery must determine why two active species lack ordinary description rows and materialize the source-defined fallback/shared representation only when the ROM itself proves it.

Partial or contradictory evidence remains partial or unavailable. A dense-looking raw table cannot nominate itself.

### World and local maps

Map work terminates in the existing normalized raster and semantic-location contracts. Discovery must prove the compiled loader, asset roles, dimensions, crop, palette, semantic regions, and encounter bindings. It may extend existing family-neutral loader/dataflow resolvers, but may not add a stock fallback raster or per-ROM identity rule.

For Unbound, the target is the relocated map-group authority plus its world-map loader/assets. For Odyssey, the target is the true map-group/map raster extent. The existing pixel ceiling remains a safety invariant; an oversized candidate is rejected until its real dimensions are structurally derived.

Success requires non-empty exact rasters, normalized dimensions, encounter-backed locations, deterministic region identities, SQLite persistence/reopen, and byte-identical API-served PNGs.

### Save and runtime

Save parsing remains typed and revisioned. Unbound requires a source-and-binary-proven expanded sector directory, checksum algorithm, slot selection rule, and field layout. All three real saves must resolve deterministically and agree where their contents agree. Odyssey requires typed Trainer and Bag structures recovered from its exact save/ROM consumers so the existing 12 resolved domains become 14/14.

Malformed, incomplete, or checksum-invalid slots fail closed. No brute-force offset scan may publish a field merely because its bytes look plausible.

### Ability mechanics

Mechanics are last because they require ARM/Thumb role and predicate proof. The selected ability and move tables establish the ID domains, but an ID reference alone is not a mechanic. Each published mechanic requires decoded control flow, attacker/defender ownership, typed field provenance, predicates, effect arithmetic, and writeback/output proof. Conditional predicates survive catalog, SQLite, API, and UI projection. Unsupported or contradictory mechanics remain absent individually without invalidating proven mechanics.

## Staged delivery

1. Complete the two static description domains and freeze exact 922/922 and 411/411 controls.
2. Resolve world/local maps through compiled loader and map-group authority, including full catalog-to-API vertical tests.
3. Resolve Unbound expanded saves and Odyssey Trainer/Bag for 14/14 runtime domains each.
4. Decode and project ability mechanics with exact role/predicate/effect evidence.
5. Run two fresh complete parses of each ROM/save, SQLite integrity/reopen checks, runtime/API/UI verification, numeric compatibility reports, and release integration.

Each stage is a separate reviewed commit. Later stages build on prior committed evidence; they do not redefine the denominator or count safe failure as success.

## Completion gates

- Unbound parser: 23/23, including 922/922 move descriptions and non-empty world/local map catalogs.
- Odyssey parser: 23/23, including 411/411 Pokédex descriptions and structurally bounded local maps.
- Unbound save/runtime: 14/14 on all three exact saves.
- Odyssey save/runtime: 14/14 on the exact revision-5 save.
- Ability mechanics: every published row has typed semantic proof; unsupported rows remain explicitly unresolved and therefore block a 23/23 claim until completed.
- Two fresh parses per control produce identical semantic hashes.
- All selected catalogs persist and reopen with `PRAGMA quick_check = ok`, no foreign-key violations, and zero catalog reference errors.
- Runtime/API/UI expose exactly the persisted data without hidden source sidecars.
- Production contains no ROM-name, SHA, title, symbol, fixed-address, or per-ROM offset selector.

## Release boundary

No partial stage is called 100% and no release is published merely because a catalog builds. Release integration follows only after both exact controls satisfy every completion gate and the affected module verification is green.
