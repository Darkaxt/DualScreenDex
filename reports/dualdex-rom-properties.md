# DualDex ROM compatibility and properties

Generated from parser report schema 11 and completed review for APK version 1000011.

Verification scope: Indices 1-33 and the three worst later offenders were rerun with the final parser; the other later ROMs retain the exact base-run observation after reviewed non-impact proof, as explicitly requested..

## Generation 2

### CRYSTAL

#### 23. Bronze 2 (v1.05).gbc

| Property | Value |
| --- | --- |
| SHA-256 | `87758fbc06a9abc73577bbc16d184bc3fb6f35d5abf22d776156629b5e5ae811` |
| Routing | SELECTED / CRYSTAL |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 251 / 251 / 0 / 18 |
| Rulesets | 1 |
| Encounters | 298 areas |
| Review | FULLY_COMPATIBLE |

### GOLD_SILVER

#### 21. Bronze (Girl Patch) (v1.23).gbc

| Property | Value |
| --- | --- |
| SHA-256 | `9c6aa82ae48b1da1acc73e716c1c18aa90a02b05f33faa96289a2af6927940ea` |
| Routing | SELECTED / GOLD_SILVER |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 251 / 251 / 0 / 18 |
| Rulesets | 1 |
| Encounters | 98 areas |
| Review | FULLY_COMPATIBLE |

#### 22. Bronze (v1.23).gbc

| Property | Value |
| --- | --- |
| SHA-256 | `3cf45157784fe70ddf9f07639236022321bf62b70797c412457625b2704c3269` |
| Routing | SELECTED / GOLD_SILVER |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 251 / 251 / 0 / 18 |
| Rulesets | 1 |
| Encounters | 98 areas |
| Review | FULLY_COMPATIBLE |

#### 38. Dark Energy (v5.01).gbc

| Property | Value |
| --- | --- |
| SHA-256 | `6dba21527ea5d788f63ef6b64cab2be5e9c77f90db11d8ebf95eeea62fd2bc1c` |
| Routing | SELECTED / GOLD_SILVER |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 251 / 251 / 0 / 18 |
| Rulesets | 1 |
| Encounters | 123 areas |
| Review | PARTIAL_ACCEPTED |

## Generation 3

### EMERALD

#### 9. All In (v1.0).gba

| Property | Value |
| --- | --- |
| SHA-256 | `baf1bad15fd25fa8103d53021991bdadb64c142f8108efd29c14cd01ba069905` |
| Routing | SELECTED / EMERALD |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 201 areas |
| Review | FULLY_COMPATIBLE |

#### 10. Altair (2019-06-13).gba

| Property | Value |
| --- | --- |
| SHA-256 | `333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380` |
| Routing | SELECTED / EMERALD |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 385 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 211 areas |
| Review | FULLY_COMPATIBLE |

#### 11. Altered Emerald (v4.2c).gba

| Property | Value |
| --- | --- |
| SHA-256 | `8fe93d8245c96ea5aa49d61df2c74ee99a439b15cde7c0afa4f0b5a87aac34f0` |
| Routing | SELECTED / EMERALD |
| Compatibility | 99.93% (PARTIAL) |
| Active species / moves / abilities / types | 426 / 761 / 234 / 19 |
| Rulesets | 1 |
| Encounters | 7 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPRITES: PARTIAL / PARTIAL_ACCEPTED
- MOVE_CATALOG: PARTIAL / PARTIAL_ACCEPTED
- LEARNSETS: PARTIAL / PARTIAL_ACCEPTED - excluded 48 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic learnset coverage 424/426; 2 named positive-Dex records lack a usable learnset
- ABILITIES: PARTIAL / PARTIAL_ACCEPTED - validated selected ability names through the typed codec; semantic coverage is 213/214; structural coverage is 234/255

#### 17. Battle Theater (V2.3.0).gba

| Property | Value |
| --- | --- |
| SHA-256 | `99c84950e2be2f887a84bdc32c741c92385bb4a54843d871a8876e9b47e1d59d` |
| Routing | SELECTED / EMERALD |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 1571 / 865 / 310 / 19 |
| Rulesets | 1 |
| Encounters | 201 areas |
| Review | FULLY_COMPATIBLE |

#### 19. Blazed Glazed (v1.3).gba

| Property | Value |
| --- | --- |
| SHA-256 | `0b55d44bfd32a350202c0878754cfcacbbaee128de3b59297ee669b69269199f` |
| Routing | SELECTED / EMERALD |
| Compatibility | 99.71% (PARTIAL) |
| Active species / moves / abilities / types | 411 / 354 / 85 / 18 |
| Rulesets | 1 |
| Encounters | 7 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; recovered 1 unique off-by-one description pointer(s) within referenced text boundaries; selected 411 navigable species through the complete compiled-referenced species-to-Dex map at 0x31DC82; excluded 1 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/411; 25 navigable species lack a decoded ROM description

#### 20. Blazing Emerald (v1.6).gba

| Property | Value |
| --- | --- |
| SHA-256 | `2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc` |
| Routing | SELECTED / EMERALD |
| Compatibility | 99.72% (PARTIAL) |
| Active species / moves / abilities / types | 410 / 354 / 125 / 19 |
| Rulesets | 1 |
| Encounters | 235 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 410 navigable species through the complete compiled-referenced species-to-Dex map at 0x31D94C; excluded 2 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/410; 24 navigable species lack a decoded ROM description

#### 24. CAWPS.gba

| Property | Value |
| --- | --- |
| SHA-256 | `88c2e3f60924a126b842f03817315c0525bc6dec71aa79bde57a7900c7e416d3` |
| Routing | SELECTED / EMERALD |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 201 areas |
| Review | FULLY_COMPATIBLE |

#### 28. Chronicles of Soala (v9.0).gba

| Property | Value |
| --- | --- |
| SHA-256 | `7c6f3945bdacc7e861f9f1279870b73b73233a83309fdd5006312da24e34c849` |
| Routing | SELECTED / EMERALD |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 219 areas |
| Review | FULLY_COMPATIBLE |

#### 29. Classic (v1.5.0b).gba

| Property | Value |
| --- | --- |
| SHA-256 | `01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c` |
| Routing | SELECTED / EMERALD |
| Compatibility | 95.24% (PARTIAL) |
| Active species / moves / abilities / types | 403 / 754 / 267 / 18 |
| Rulesets | 1 |
| Encounters | 184 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- ABILITY_MECHANICS: NOT_FOUND / PARTIAL_ACCEPTED - structured ability values were not resolved from compiled battle code

#### 33. Clover (v1.3.3).gba

| Property | Value |
| --- | --- |
| SHA-256 | `42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9` |
| Routing | SELECTED / EMERALD |
| Compatibility | 95.21% (PARTIAL) |
| Active species / moves / abilities / types | 387 / 719 / 253 / 19 |
| Rulesets | 1 |
| Encounters | 28 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 387 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 25 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/387; 1 navigable species lack a decoded ROM description
- ABILITY_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - decoded a structurally referenced partial ability-description table (252/253)
- ABILITY_MECHANICS: NOT_FOUND / PARTIAL_ACCEPTED - structured ability values were not resolved from compiled battle code

#### 47. Delta Emerald (v1.1.5).gba

| Property | Value |
| --- | --- |
| SHA-256 | `7f4aa1aa68b1df783c3a44b38984640227a5eec22debffbf18db3713de2616bc` |
| Routing | SELECTED / EMERALD |
| Compatibility | 95.2% (PARTIAL) |
| Active species / moves / abilities / types | 486 / 719 / 232 / 18 |
| Rulesets | 1 |
| Encounters | 201 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- MOVE_DETAILS: NOT_FOUND / PARTIAL_ACCEPTED - plausible move records 1029/1029 do not form a complete populated table; populated 673/1028
- ABILITIES: PARTIAL / PARTIAL_ACCEPTED - validated selected ability names through the typed codec; semantic coverage is 131/132; structural coverage is 232/255

### FIRERED_LEAFGREEN

#### 1. A Grand Day Out.gba

| Property | Value |
| --- | --- |
| SHA-256 | `2005275fc54ae63f3d1bc50c49980e87dcd9ecae5e4733d322bb2a2c99270916` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 220 areas |
| Review | FULLY_COMPATIBLE |

#### 2. Advanced Adventure (2021).gba

| Property | Value |
| --- | --- |
| SHA-256 | `736af8f701690c59bf174593c7ea60aa1a531405eac1d69f459ae1e338a36829` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 410 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 26 areas |
| Review | FULLY_COMPATIBLE |

#### 3. Adventure Red Chapter (Beta 15 + Expansion Fix C).gba

| Property | Value |
| --- | --- |
| SHA-256 | `75ca054238d41b38df5113ccb89af765561ce8963f78f7eb1befab6310306600` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 95.19% (PARTIAL) |
| Active species / moves / abilities / types | 1406 / 494 / 138 / 20 |
| Rulesets | 1 |
| Encounters | 0 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPECIES_CATALOG: PARTIAL / PARTIAL_ACCEPTED - selected 1406 navigable species through the complete compiled-referenced species-to-Dex map at 0x162020C; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 1402/1406; 4 named positive-Dex records lack valid base stats/types
- SPECIES_TYPES: PARTIAL / PARTIAL_ACCEPTED - selected 1406 navigable species through the complete compiled-referenced species-to-Dex map at 0x162020C; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 1402/1406; 4 named positive-Dex records lack valid base stats/types
- BASE_STATS: PARTIAL / PARTIAL_ACCEPTED - selected 1406 navigable species through the complete compiled-referenced species-to-Dex map at 0x162020C; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 1402/1406; 4 named positive-Dex records lack valid base stats/types
- LEARNSETS: PARTIAL / PARTIAL_ACCEPTED - recovered 7 short malformed tails before the Gen 3 learnset terminator; quarantined 1 malformed rows without a bounded terminator tail; selected 1406 navigable species through the complete compiled-referenced species-to-Dex map at 0x162020C; excluded 54 reserved or zero-Dex structural slots; semantic learnset coverage 1403/1406; 3 named positive-Dex records lack a usable learnset
- AREA_ENCOUNTERS: NOT_FOUND / PARTIAL_ACCEPTED - encounter tables were not located

#### 4. Aesthetic Red (DS Font & Sprites) (Faithful Version) (v1.2).gba

| Property | Value |
| --- | --- |
| SHA-256 | `80e96000eb82963777d87a76baf05ecf50712961dcfc51dc5bd86493796f4270` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 1240 / 792 / 254 / 19 |
| Rulesets | 1 |
| Encounters | 219 areas |
| Review | FULLY_COMPATIBLE |

#### 5. Aesthetic Red (DS Font & Sprites) (v1.2).gba

| Property | Value |
| --- | --- |
| SHA-256 | `7f01d5ffd8b2e597be313f4d8e5f425a3f0abf50689274564001cd9e51ef2b0c` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 1240 / 792 / 254 / 19 |
| Rulesets | 1 |
| Encounters | 219 areas |
| Review | FULLY_COMPATIBLE |

#### 6. Aesthetic Red (GBC Font & Sprites) (Faithful Version) (v1.2).gba

| Property | Value |
| --- | --- |
| SHA-256 | `a88c1d13b0297070cc975b1c2421feca0639cf1c14a491da188282cf72875a54` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 1240 / 792 / 254 / 19 |
| Rulesets | 1 |
| Encounters | 219 areas |
| Review | FULLY_COMPATIBLE |

#### 7. Aesthetic Red (GBC Font & Sprites) (v1.2).gba

| Property | Value |
| --- | --- |
| SHA-256 | `d3b3b5a8556d977618f1783f935d72b3b21c7847d217e4aad4eef754115dd282` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 1240 / 792 / 254 / 19 |
| Rulesets | 1 |
| Encounters | 219 areas |
| Review | FULLY_COMPATIBLE |

#### 8. Aesthetic Red (Music & Graphics Only) (v1.2).gba

| Property | Value |
| --- | --- |
| SHA-256 | `0dfca1fd701b94440454c25ee352054ed331d4404e502f7d322d9f2776f99794` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 219 areas |
| Review | FULLY_COMPATIBLE |

#### 12. Amethyst (v1.3.0).gba

| Property | Value |
| --- | --- |
| SHA-256 | `3f987c21b2d62c02b3df43c9f94e5f877f8c71ebe82faad994b6667ddbd9089e` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 95.24% (PARTIAL) |
| Active species / moves / abilities / types | 894 / 819 / 254 / 19 |
| Rulesets | 1 |
| Encounters | 0 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- AREA_ENCOUNTERS: AMBIGUOUS / PARTIAL_ACCEPTED - multiple structurally credible Gen 3 encounter roots remain ambiguous

#### 13. Amnesia (Save Fix).gba

| Property | Value |
| --- | --- |
| SHA-256 | `08b51b82beef849e1956bfcf468823b8f45518e0a6dd907ecc993c7c44aa0d94` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 221 areas |
| Review | FULLY_COMPATIBLE |

#### 15. AshGray - Newerest Edition (v1.0).gba

| Property | Value |
| --- | --- |
| SHA-256 | `a08055484c8366768d3e98e2dbed0998641abd2899ffbfc8d7f132925875f7a1` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 99.96% (PARTIAL) |
| Active species / moves / abilities / types | 389 / 354 / 77 / 17 |
| Rulesets | 1 |
| Encounters | 245 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 389 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 23 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/389; 3 navigable species lack a decoded ROM description

#### 16. AshGray (v4.6).gba

| Property | Value |
| --- | --- |
| SHA-256 | `a2d141a4f080befb0c0b077a4434feba5583e9e1e5381492fdf65905e6028bad` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 99.96% (PARTIAL) |
| Active species / moves / abilities / types | 389 / 354 / 77 / 17 |
| Rulesets | 1 |
| Encounters | 245 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 389 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 23 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/389; 3 navigable species lack a decoded ROM description

#### 18. Bill's Secret Garden DX (v2.0).gba

| Property | Value |
| --- | --- |
| SHA-256 | `2eb56e73fdba2b81c26596d19e80410fbd48de0586af5d342c25ec741eb59f57` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 99.94% (PARTIAL) |
| Active species / moves / abilities / types | 1509 / 1002 / 254 / 19 |
| Rulesets | 1 |
| Encounters | 224 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPECIES_CATALOG: PARTIAL / PARTIAL_ACCEPTED - excluded 31 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic species coverage 1506/1509; 3 named positive-Dex records lack valid base stats/types
- SPECIES_TYPES: PARTIAL / PARTIAL_ACCEPTED - excluded 31 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic species coverage 1506/1509; 3 named positive-Dex records lack valid base stats/types
- BASE_STATS: PARTIAL / PARTIAL_ACCEPTED - excluded 31 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic species coverage 1506/1509; 3 named positive-Dex records lack valid base stats/types
- SPRITES: PARTIAL / PARTIAL_ACCEPTED
- LEARNSETS: PARTIAL / PARTIAL_ACCEPTED - excluded 31 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic learnset coverage 1508/1509; 1 named positive-Dex records lack a usable learnset

#### 25. Celia's Stupid Romhack (1.1.4).gba

| Property | Value |
| --- | --- |
| SHA-256 | `81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 90.4% (PARTIAL) |
| Active species / moves / abilities / types | 385 / 1188 / 147 / 34 |
| Rulesets | 1 |
| Encounters | 307 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPRITES: PARTIAL / PARTIAL_ACCEPTED
- POKEDEX_DESCRIPTIONS: NOT_FOUND / PARTIAL_ACCEPTED - Gen 3 Pokédex description candidates do not cover enough of the species semantic domain
- MOVE_CATALOG: PARTIAL / PARTIAL_ACCEPTED
- MOVE_DETAILS: NOT_FOUND / PARTIAL_ACCEPTED - selected move-details typed resolution rejected: selected move-details layout failed its typed codec
- ABILITY_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - decoded a structurally referenced partial ability-description table (145/147)

#### 26. Chaos Black (Fixed) (v3.1).gba

| Property | Value |
| --- | --- |
| SHA-256 | `f21a917a5d43f1f4d952ff041ab13b42fba2c7563f0665e3525b1b39639a5979` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 219 areas |
| Review | FULLY_COMPATIBLE |

#### 27. Chaos Black Recreated (2026-01-25).gba

| Property | Value |
| --- | --- |
| SHA-256 | `3358c57e22588a14cd2c3db82436482238d12f90e891a66547f91ffd6f41dda5` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 222 areas |
| Review | FULLY_COMPATIBLE |

#### 30. Cloud White (v523d).gba

| Property | Value |
| --- | --- |
| SHA-256 | `f70922408ea71257a2893f06b51cc02aa890e573beb1b84043a100060de1d11d` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 100% (COMPLETE) |
| Active species / moves / abilities / types | 492 / 484 / 137 / 19 |
| Rulesets | 1 |
| Encounters | 26 areas |
| Review | PARTIAL_ACCEPTED |

#### 31. Cloud White 2 (v279).gba

| Property | Value |
| --- | --- |
| SHA-256 | `6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 98.72% (PARTIAL) |
| Active species / moves / abilities / types | 943 / 484 / 137 / 19 |
| Rulesets | 1 |
| Encounters | 611 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPECIES_CATALOG: PARTIAL / PARTIAL_ACCEPTED - selected 943 navigable species through the complete compiled-referenced species-to-Dex map at 0xCA3BE0; excluded 54 reserved or zero-Dex structural slots; plausible base stats 887/997 below 90.0%; retained published Gen 3 base-stat root 0xC85BC4 with 58 compiled references and 110 exact zero-filled rows; authoritative semantic species coverage 861/943; semantic species coverage 861/943; 82 named positive-Dex records lack valid base stats/types
- SPECIES_TYPES: PARTIAL / PARTIAL_ACCEPTED - plausible base stats 887/997 below 90.0%; retained published Gen 3 base-stat root 0xC85BC4 with 58 compiled references and 110 exact zero-filled rows; authoritative semantic species coverage 861/943; selected 943 navigable species through the complete compiled-referenced species-to-Dex map at 0xCA3BE0; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 861/943; 82 named positive-Dex records lack valid base stats/types
- BASE_STATS: PARTIAL / PARTIAL_ACCEPTED - plausible base stats 887/997 below 90.0%; retained published Gen 3 base-stat root 0xC85BC4 with 58 compiled references and 110 exact zero-filled rows; authoritative semantic species coverage 861/943; selected 943 navigable species through the complete compiled-referenced species-to-Dex map at 0xCA3BE0; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 861/943; 82 named positive-Dex records lack valid base stats/types
- ABILITIES: PARTIAL / PARTIAL_ACCEPTED - validated selected ability names through the typed codec; semantic coverage is 134/135; structural coverage is 137/155

#### 32. Cloud White 3 (v277).gba

| Property | Value |
| --- | --- |
| SHA-256 | `7ced98ef9232e3d09892c4e960e326eac8daf3c596f54d773661cc227d25b8e9` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 99.9% (PARTIAL) |
| Active species / moves / abilities / types | 943 / 484 / 137 / 19 |
| Rulesets | 1 |
| Encounters | 696 areas |
| Review | SOURCE_DATA_DAMAGED |

Gaps:

- SPECIES_CATALOG: PARTIAL / SOURCE_DATA_DAMAGED - selected 943 navigable species through the complete compiled-referenced species-to-Dex map at 0xCECBF4; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 940/943; 3 named positive-Dex records lack valid base stats/types
- SPECIES_TYPES: PARTIAL / SOURCE_DATA_DAMAGED - selected 943 navigable species through the complete compiled-referenced species-to-Dex map at 0xCECBF4; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 940/943; 3 named positive-Dex records lack valid base stats/types
- BASE_STATS: PARTIAL / SOURCE_DATA_DAMAGED - selected 943 navigable species through the complete compiled-referenced species-to-Dex map at 0xCECBF4; excluded 54 reserved or zero-Dex structural slots; semantic species coverage 940/943; 3 named positive-Dex records lack valid base stats/types
- POKEDEX_DESCRIPTIONS: PARTIAL / SOURCE_DATA_DAMAGED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 943 navigable species through the complete compiled-referenced species-to-Dex map at 0xCECBF4; excluded 54 reserved or zero-Dex structural slots; semantic Pokédex description coverage 939/943; 4 navigable species lack a decoded ROM description
- ABILITIES: PARTIAL / SOURCE_DATA_DAMAGED - validated selected ability names through the typed codec; semantic coverage is 134/135; structural coverage is 137/155

#### 35. Crown (v1.9).gba

| Property | Value |
| --- | --- |
| SHA-256 | `28d7f55c96bde57269e3bbae8c5e8e177dcb92f16feaee65d556ab387ccaddd0` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 85.71% (PARTIAL) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 0 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPRITES: NOT_FOUND / PARTIAL_ACCEPTED - decoded Gen 3 sprite streams 0/412 below 0.9
- AREA_ENCOUNTERS: AMBIGUOUS / PARTIAL_ACCEPTED - empty-first Classic24 candidate budget exceeded (256); encounter table selection is ambiguous
- BALL_CATALOG: NOT_FOUND / PARTIAL_ACCEPTED - compressed ball graphics tables were not located

#### 36. Crystal Advance Redux (7-8-26).gba

| Property | Value |
| --- | --- |
| SHA-256 | `fbbcbf32afd427afa5de45799923c414c21b77917004477f214c9f5cd87537b6` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 93.32% (PARTIAL) |
| Active species / moves / abilities / types | 699 / 510 / 206 / 19 |
| Rulesets | 1 |
| Encounters | 65 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPRITES: PARTIAL / PARTIAL_ACCEPTED
- EVOLUTIONS: PARTIAL / PARTIAL_ACCEPTED - partial Gen 3 evolution coverage 513/760; manual review recommended; evolution slot quality 1151/1520; 369 invalid slots skipped
- MOVE_CATALOG: PARTIAL / PARTIAL_ACCEPTED
- LEARNSETS: PARTIAL / PARTIAL_ACCEPTED - quarantined 2 malformed rows without a bounded terminator tail; 1 Gen 3 learnset pointers were null or out of bounds; selected 699 navigable species through the complete compiled-referenced species-to-Dex map at 0x158B670; excluded 61 reserved or zero-Dex structural slots; semantic learnset coverage 696/699; 3 named positive-Dex records lack a usable learnset
- MACHINE_MOVES: NOT_FOUND / PARTIAL_ACCEPTED - machine-move list and compatibility flags were not jointly resolved
- ABILITIES: PARTIAL / PARTIAL_ACCEPTED - validated selected ability names through the typed codec; semantic coverage is 148/149; structural coverage is 206/207

#### 37. Dark Cry - The Legend of Giratina (v2.6.7).gba

| Property | Value |
| --- | --- |
| SHA-256 | `e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 99.74% (PARTIAL) |
| Active species / moves / abilities / types | 408 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 99 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 408 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 4 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/408; 22 navigable species lack a decoded ROM description

#### 39. Dark Rising - Order Destroyed.gba

| Property | Value |
| --- | --- |
| SHA-256 | `71b44f3b4be1b17428dd3fcb1c37002268c7b832dc49626b9d57bf56de10f387` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 95.24% (PARTIAL) |
| Active species / moves / abilities / types | 386 / 475 / 127 / 19 |
| Rulesets | 0 |
| Encounters | 222 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- LEARNSETS: NOT_FOUND / PARTIAL_ACCEPTED - Gen 3 learnset pointer table not resolved by structural validation; selected 386 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 26 reserved or zero-Dex structural slots; semantic learnset coverage 0/386; 386 named positive-Dex records lack a usable learnset

#### 40. Dark Rising 2.gba

| Property | Value |
| --- | --- |
| SHA-256 | `81b97561b73d02a26ba52369d582ac5d8615078de2b202e0673f4e6512af120d` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 99.71% (PARTIAL) |
| Active species / moves / abilities / types | 411 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 228 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 411 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 1 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/411; 25 navigable species lack a decoded ROM description

#### 41. Dark Rising Origins - Worlds Collide.gba

| Property | Value |
| --- | --- |
| SHA-256 | `c6440addb23d76f514d0ba4baf049a5c34a0d7c0938a5c6ee4fbfa3792f9daea` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 94.95% (PARTIAL) |
| Active species / moves / abilities / types | 411 / 484 / 137 / 19 |
| Rulesets | 0 |
| Encounters | 225 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 411 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 1 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/411; 25 navigable species lack a decoded ROM description
- LEARNSETS: NOT_FOUND / PARTIAL_ACCEPTED - Gen 3 learnset pointer table not resolved by structural validation; selected 411 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 1 reserved or zero-Dex structural slots; semantic learnset coverage 0/411; 411 named positive-Dex records lack a usable learnset

#### 42. Dark Rising.gba

| Property | Value |
| --- | --- |
| SHA-256 | `712697aba9a0f2401bc0fb8677caa69d9d21beee26c7d9920226e52f02f76a4e` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 94.95% (PARTIAL) |
| Active species / moves / abilities / types | 411 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 0 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix; selected 411 navigable species through the complete compiled-referenced species-to-Dex map at 0x251CB8; excluded 1 reserved or zero-Dex structural slots; semantic Pokédex description coverage 386/411; 25 navigable species lack a decoded ROM description
- AREA_ENCOUNTERS: NOT_FOUND / PARTIAL_ACCEPTED - encounter tables were not located

#### 43. Dark Violet.gba

| Property | Value |
| --- | --- |
| SHA-256 | `6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 93.3% (PARTIAL) |
| Active species / moves / abilities / types | 366 / 468 / 117 / 18 |
| Rulesets | 0 |
| Encounters | 217 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- EVOLUTIONS: PARTIAL / PARTIAL_ACCEPTED - partial Gen 3 evolution coverage 244/412; manual review recommended; evolution slot quality 244/412; 168 invalid slots skipped
- LEARNSETS: NOT_FOUND / PARTIAL_ACCEPTED - Gen 3 learnset pointer table not resolved by structural validation; selected 366 navigable species through the complete compiled-referenced species-to-Dex map at 0x251FEE; excluded 46 reserved or zero-Dex structural slots; semantic learnset coverage 0/366; 366 named positive-Dex records lack a usable learnset

#### 44. Dark Violet (Fan-Patch).gba

| Property | Value |
| --- | --- |
| SHA-256 | `d171d29b691ced98178b4370826f0627f9c2ed6e0313d813f909ba147031c717` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 94.26% (PARTIAL) |
| Active species / moves / abilities / types | 366 / 468 / 117 / 18 |
| Rulesets | 0 |
| Encounters | 217 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- EVOLUTIONS: PARTIAL / PARTIAL_ACCEPTED - partial Gen 3 evolution coverage 327/412; manual review recommended; evolution slot quality 327/412; 85 invalid slots skipped
- LEARNSETS: NOT_FOUND / PARTIAL_ACCEPTED - Gen 3 learnset pointer table not resolved by structural validation; selected 366 navigable species through the complete compiled-referenced species-to-Dex map at 0x251FEE; excluded 46 reserved or zero-Dex structural slots; semantic learnset coverage 0/366; 366 named positive-Dex records lack a usable learnset

#### 45. Dark Worship.gba

| Property | Value |
| --- | --- |
| SHA-256 | `930663704d1a84b93815d276703114e88785de94fcb3230d832ef07dc399f1d8` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 90.41% (PARTIAL) |
| Active species / moves / abilities / types | 1236 / 344 / 254 / 19 |
| Rulesets | 0 |
| Encounters | 266 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- MOVE_CATALOG: PARTIAL / PARTIAL_ACCEPTED
- LEARNSETS: NOT_FOUND / PARTIAL_ACCEPTED - Gen 3 learnset pointer table not resolved by structural validation; selected 151 species from the strongly compiled-referenced regional Pokédex order at 0x19E8DAE; excluded 1148 installed expansion slots outside the ROM's active Pokédex domain; semantic learnset coverage 0/151; 151 named positive-Dex records lack a usable learnset
- TUTOR_MOVES: NOT_FOUND / PARTIAL_ACCEPTED - tutor move list and compatibility flags were not jointly resolved

#### 49. Dreams (v1.5.3).gba

| Property | Value |
| --- | --- |
| SHA-256 | `ad73b864873f17add4f931315d3162b792b19c65133c7a6819a85866b1afa403` |
| Routing | SELECTED / FIRERED_LEAFGREEN |
| Compatibility | 80.88% (PARTIAL) |
| Active species / moves / abilities / types | 1139 / 0 / 254 / 18 |
| Rulesets | 0 |
| Encounters | 33 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- SPECIES_CATALOG: PARTIAL / PARTIAL_ACCEPTED - excluded 27 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic species coverage 1136/1139; 3 named positive-Dex records lack valid base stats/types
- SPECIES_TYPES: PARTIAL / PARTIAL_ACCEPTED - excluded 27 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic species coverage 1136/1139; 3 named positive-Dex records lack valid base stats/types
- BASE_STATS: PARTIAL / PARTIAL_ACCEPTED - excluded 27 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic species coverage 1136/1139; 3 named positive-Dex records lack valid base stats/types
- POKEDEX_DESCRIPTIONS: PARTIAL / PARTIAL_ACCEPTED - trimmed adjacent non-description data after a structurally stronger Gen 3 Pokédex prefix
- MOVE_CATALOG: NOT_FOUND / PARTIAL_ACCEPTED - valid fixed names 6/355 below 0.85
- LEARNSETS: NOT_FOUND / PARTIAL_ACCEPTED - Gen 3 learnset pointer table not resolved by structural validation; excluded 27 reserved, unnamed, or zero-Dex structural slots from the Pokédex semantic domain; semantic learnset coverage 0/1139; 1139 named positive-Dex records lack a usable learnset
- EGG_MOVES: NOT_FOUND / PARTIAL_ACCEPTED - egg-move table was not resolved
- TUTOR_MOVES: NOT_FOUND / PARTIAL_ACCEPTED - tutor move list and compatibility flags were not jointly resolved

### RUBY_SAPPHIRE

#### 14. Arcoiris.gba

| Property | Value |
| --- | --- |
| SHA-256 | `fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab` |
| Routing | SELECTED / RUBY_SAPPHIRE |
| Compatibility | 99.88% (PARTIAL) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 179 areas |
| Review | SOURCE_DATA_DAMAGED |

Gaps:

- SPECIES_CATALOG: PARTIAL / SOURCE_DATA_DAMAGED - selected 386 navigable species through the complete compiled-referenced species-to-Dex map at 0x1FC1E0; excluded 26 reserved or zero-Dex structural slots; semantic species coverage 383/386; 3 named positive-Dex records lack valid base stats/types
- SPECIES_TYPES: PARTIAL / SOURCE_DATA_DAMAGED - selected 386 navigable species through the complete compiled-referenced species-to-Dex map at 0x1FC1E0; excluded 26 reserved or zero-Dex structural slots; semantic species coverage 383/386; 3 named positive-Dex records lack valid base stats/types
- BASE_STATS: PARTIAL / SOURCE_DATA_DAMAGED - selected 386 navigable species through the complete compiled-referenced species-to-Dex map at 0x1FC1E0; excluded 26 reserved or zero-Dex structural slots; semantic species coverage 383/386; 3 named positive-Dex records lack valid base stats/types

#### 48. Dragonstone (v1.63).gba

| Property | Value |
| --- | --- |
| SHA-256 | `2772296094b37c36ddf5735e58e54520bdde88a318c033e4817e40cc44676698` |
| Routing | SELECTED / RUBY_SAPPHIRE |
| Compatibility | 95% (PARTIAL) |
| Active species / moves / abilities / types | 386 / 354 / 77 / 18 |
| Rulesets | 1 |
| Encounters | 181 areas |
| Review | PARTIAL_ACCEPTED |

Gaps:

- MACHINE_MOVES: NOT_FOUND / PARTIAL_ACCEPTED - machine-move list and compatibility flags were not jointly resolved

## Generation Unresolved

### Unresolved

#### 34. Crippling Medical Debt Edition (v1.1).gba

| Property | Value |
| --- | --- |
| SHA-256 | `79882b5e276f6c0386fe7c4d5cce122c56ff969d694ffc530b1a534ab57d25cb` |
| Routing | NO_FAMILY_MATCH / Unresolved |
| Compatibility | 8.78% (PARTIAL) |
| Active species / moves / abilities / types |  /  /  /  |
| Rulesets | 0 |
| Encounters |  areas |
| Review | EXCLUDED_BY_SCOPE |

Gaps:

- SPECIES_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 329/412 below 0.85; no family-independent compatible evidence
- SPECIES_NAMES: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 329/412 below 0.85; no family-independent compatible evidence
- SPECIES_TYPES: PARTIAL / EXCLUDED_BY_SCOPE
- TYPE_CHART: NOT_FOUND / EXCLUDED_BY_SCOPE - type chart lacks a valid terminator or enough entries; no referenced root/end-bounded Q4.12 matrix validated; no family-independent compatible evidence
- BASE_STATS: PARTIAL / EXCLUDED_BY_SCOPE
- SPRITES: NOT_FOUND / EXCLUDED_BY_SCOPE - valid Gen 3 sprite pointers 16/412 below 0.9; decoded Gen 3 sprite streams 0/412 below 0.9; no family-independent compatible evidence
- POKEDEX_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - Gen 3 Pokédex description candidates do not cover enough of the species semantic domain; no family-independent compatible evidence
- EVOLUTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - Gen 3 evolution table prefilter shape budget exceeded (4096); automatic resolution requires review; no family-independent compatible evidence
- MOVE_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 180/355 below 0.85; no family-independent compatible evidence
- MOVE_DETAILS: NOT_FOUND / EXCLUDED_BY_SCOPE - ambiguous expanded move-data roots: 0x4e8d10/CFRU_MOVE_16, 0x4f0300/CFRU_MOVE_16; no family-independent compatible evidence
- MOVE_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated move_descriptions evidence; no family-independent compatible evidence
- LEARNSETS: NOT_FOUND / EXCLUDED_BY_SCOPE - Gen 3 learnset pointer table not resolved by structural validation; no family-independent compatible evidence
- EGG_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated egg_moves evidence; no family-independent compatible evidence
- MACHINE_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated machine_moves evidence; no family-independent compatible evidence
- TUTOR_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated tutor_moves evidence; no family-independent compatible evidence
- ABILITIES: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 0/78 below 0.85; no family-independent compatible evidence
- ABILITY_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ability_descriptions evidence; no family-independent compatible evidence
- ABILITY_MECHANICS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ability_mechanics evidence; no family-independent compatible evidence
- AREA_ENCOUNTERS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated area_encounters evidence; no family-independent compatible evidence
- TYPE_PRESENTATION: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated type_presentation evidence; no family-independent compatible evidence
- BALL_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ball_catalog evidence; no family-independent compatible evidence

#### 46. DarkFire (v2.1.3).gba

| Property | Value |
| --- | --- |
| SHA-256 | `8c564fcd1e419d81a56eaf6734ae9eb70d0f9849d08200c1807d31d674a48d69` |
| Routing | NO_FAMILY_MATCH / Unresolved |
| Compatibility | 21.18% (PARTIAL) |
| Active species / moves / abilities / types |  /  /  /  |
| Rulesets | 0 |
| Encounters |  areas |
| Review | EXCLUDED_BY_SCOPE |

Gaps:

- SPECIES_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 663/1090 below 0.85; plausible base stats 82/1090 below 89.5%; no family-independent compatible evidence
- SPECIES_NAMES: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 663/1090 below 0.85; no family-independent compatible evidence
- SPECIES_TYPES: NOT_FOUND / EXCLUDED_BY_SCOPE - plausible base stats 82/1090 below 89.5%; no family-independent compatible evidence
- TYPE_CHART: NOT_FOUND / EXCLUDED_BY_SCOPE - type chart lacks a valid terminator or enough entries; no referenced root/end-bounded Q4.12 matrix validated; no family-independent compatible evidence
- BASE_STATS: NOT_FOUND / EXCLUDED_BY_SCOPE - plausible base stats 82/1090 below 89.5%; no family-independent compatible evidence
- SPRITES: NOT_FOUND / EXCLUDED_BY_SCOPE - valid Gen 3 sprite pointers 663/1090 below 0.9; decoded Gen 3 sprite streams 663/1090 below 0.9; no family-independent compatible evidence
- POKEDEX_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - Gen 3 Pokédex description candidates do not cover enough of the species semantic domain; no family-independent compatible evidence
- EVOLUTIONS: PARTIAL / EXCLUDED_BY_SCOPE - partial Gen 3 evolution coverage 916/1090; manual review recommended; evolution slot quality 18791/21800; 3009 invalid slots skipped
- MOVE_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated move_descriptions evidence; no family-independent compatible evidence
- LEARNSETS: PARTIAL / EXCLUDED_BY_SCOPE - wide Gen 3 learnset coverage 663/1090 (60.83%); structural quality 663/663 (100.00%); 427 null; 0 invalid
- EGG_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated egg_moves evidence; no family-independent compatible evidence
- MACHINE_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated machine_moves evidence; no family-independent compatible evidence
- TUTOR_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated tutor_moves evidence; no family-independent compatible evidence
- ABILITY_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ability_descriptions evidence; no family-independent compatible evidence
- ABILITY_MECHANICS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ability_mechanics evidence; no family-independent compatible evidence
- AREA_ENCOUNTERS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated area_encounters evidence; no family-independent compatible evidence
- TYPE_PRESENTATION: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated type_presentation evidence; no family-independent compatible evidence
- BALL_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ball_catalog evidence; no family-independent compatible evidence

#### 50. Dreamstone Mysteries.gba

| Property | Value |
| --- | --- |
| SHA-256 | `ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220` |
| Routing | NO_FAMILY_MATCH / Unresolved |
| Compatibility | 13.36% (PARTIAL) |
| Active species / moves / abilities / types |  /  /  /  |
| Rulesets | 0 |
| Encounters |  areas |
| Review | EXCLUDED_BY_SCOPE |

Gaps:

- SPECIES_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 337/412 below 0.85; species base-stat table not resolved; no family-independent compatible evidence
- SPECIES_NAMES: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 337/412 below 0.85; no family-independent compatible evidence
- SPECIES_TYPES: NOT_FOUND / EXCLUDED_BY_SCOPE - species base-stat table not resolved; no family-independent compatible evidence
- BASE_STATS: NOT_FOUND / EXCLUDED_BY_SCOPE - species base-stat table not resolved; no family-independent compatible evidence
- SPRITES: NOT_FOUND / EXCLUDED_BY_SCOPE - valid Gen 3 sprite pointers 10/412 below 0.9; decoded Gen 3 sprite streams 0/412 below 0.9; no family-independent compatible evidence
- POKEDEX_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - Gen 3 Pokédex description candidates do not cover enough of the species semantic domain; no family-independent compatible evidence
- EVOLUTIONS: PARTIAL / EXCLUDED_BY_SCOPE - partial Gen 3 evolution coverage 332/412; manual review recommended; evolution slot quality 332/412; 80 invalid slots skipped
- MOVE_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - valid fixed names 282/355 below 0.85; no family-independent compatible evidence
- MOVE_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated move_descriptions evidence; no family-independent compatible evidence
- LEARNSETS: NOT_FOUND / EXCLUDED_BY_SCOPE - wide Gen 3 learnset coverage 316/412 (76.70%); structural quality 316/399 (79.20%); 13 null; 83 invalid; selected learnset layout failed typed validation; no family-independent compatible evidence
- EGG_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated egg_moves evidence; no family-independent compatible evidence
- MACHINE_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated machine_moves evidence; no family-independent compatible evidence
- TUTOR_MOVES: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated tutor_moves evidence; no family-independent compatible evidence
- ABILITIES: NOT_FOUND / EXCLUDED_BY_SCOPE - ability-name table not resolved; no family-independent compatible evidence
- ABILITY_DESCRIPTIONS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ability_descriptions evidence; no family-independent compatible evidence
- ABILITY_MECHANICS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ability_mechanics evidence; no family-independent compatible evidence
- AREA_ENCOUNTERS: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated area_encounters evidence; no family-independent compatible evidence
- TYPE_PRESENTATION: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated type_presentation evidence; no family-independent compatible evidence
- BALL_CATALOG: NOT_FOUND / EXCLUDED_BY_SCOPE - no validated ball_catalog evidence; no family-independent compatible evidence
