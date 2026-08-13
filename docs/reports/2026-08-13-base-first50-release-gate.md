# DualDex RC25 exact first-50 base release gate

This independent release-gate artifact is derived from the final exact-first-50 run. It does not replace the formal reviewed compatibility report and contains no ROM bytes or private paths.

## Result

| Gate | Result |
| --- | ---: |
| Exact SHA-256 rehashes | 50 / 50 |
| Selected | 50 / 50 |
| Deterministic status/family versus preceding exact-50 gate | 50 / 50 |
| Reference-closed | 50 / 50 |
| Catalogs persisted and reopened | 50 / 50 |
| SQLite quick-check `ok` | 50 / 50 |
| SQLite foreign-key errors | 0 |

Families: 30 FireRed / LeafGreen, 14 Emerald, 3 Gold / Silver, 2 Ruby / Sapphire, and 1 Crystal.

Final report SHA-256: `C553D26C5D8A9B1D895716E3BD9D60FAF3481EB0951BB84F2272D27CAB45DD2F`. Evidence commit: `1e70a22dfe17a9430a4fe235d810056f1d7659c8`.

## Interpretation

- **Selected** means one family passed the unchanged structural selection gates; it is not a claim that every optional feature resolved.
- **Base-safe and reference-closed** additionally requires exact identity, deterministic termination, successful catalog persistence/reopen, clean SQLite checks, and zero published-reference errors.
- Optional capabilities remain independently truthful. `PARTIAL` and `NOT_FOUND` are not counted as success.
- `NO_FAMILY_MATCH` and `AMBIGUOUS` are safe rejections, not compatibility successes. Neither occurred in this cohort.

## 50-row evidence

| Row | ROM | SHA-256 | Status / family | Deterministic | References | Persistence | SQLite |
| ---: | --- | --- | --- | :---: | ---: | :---: | :---: |
| 1 | A Grand Day Out.gba | `2005275fc54ae63f3d1bc50c49980e87dcd9ecae5e4733d322bb2a2c99270916` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 2 | Advanced Adventure (2021).gba | `736af8f701690c59bf174593c7ea60aa1a531405eac1d69f459ae1e338a36829` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 3 | Adventure Red Chapter (Beta 15 + Expansion Fix C).gba | `75ca054238d41b38df5113ccb89af765561ce8963f78f7eb1befab6310306600` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 4 | Aesthetic Red (DS Font & Sprites) (Faithful Version) (v1.2).gba | `80e96000eb82963777d87a76baf05ecf50712961dcfc51dc5bd86493796f4270` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 5 | Aesthetic Red (DS Font & Sprites) (v1.2).gba | `7f01d5ffd8b2e597be313f4d8e5f425a3f0abf50689274564001cd9e51ef2b0c` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 6 | Aesthetic Red (GBC Font & Sprites) (Faithful Version) (v1.2).gba | `a88c1d13b0297070cc975b1c2421feca0639cf1c14a491da188282cf72875a54` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 7 | Aesthetic Red (GBC Font & Sprites) (v1.2).gba | `d3b3b5a8556d977618f1783f935d72b3b21c7847d217e4aad4eef754115dd282` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 8 | Aesthetic Red (Music & Graphics Only) (v1.2).gba | `0dfca1fd701b94440454c25ee352054ed331d4404e502f7d322d9f2776f99794` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 9 | All In (v1.0).gba | `baf1bad15fd25fa8103d53021991bdadb64c142f8108efd29c14cd01ba069905` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 10 | Altair (2019-06-13).gba | `333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 11 | Altered Emerald (v4.2c).gba | `8fe93d8245c96ea5aa49d61df2c74ee99a439b15cde7c0afa4f0b5a87aac34f0` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 12 | Amethyst (v1.3.0).gba | `3f987c21b2d62c02b3df43c9f94e5f877f8c71ebe82faad994b6667ddbd9089e` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 13 | Amnesia (Save Fix).gba | `08b51b82beef849e1956bfcf468823b8f45518e0a6dd907ecc993c7c44aa0d94` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 14 | Arcoiris.gba | `fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab` | SELECTED / RUBY_SAPPHIRE | yes | 0 | yes (11 sections) | ok / FK 0 |
| 15 | AshGray - Newerest Edition (v1.0).gba | `a08055484c8366768d3e98e2dbed0998641abd2899ffbfc8d7f132925875f7a1` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 16 | AshGray (v4.6).gba | `a2d141a4f080befb0c0b077a4434feba5583e9e1e5381492fdf65905e6028bad` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 17 | Battle Theater (V2.3.0).gba | `99c84950e2be2f887a84bdc32c741c92385bb4a54843d871a8876e9b47e1d59d` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 18 | Bill's Secret Garden DX (v2.0).gba | `2eb56e73fdba2b81c26596d19e80410fbd48de0586af5d342c25ec741eb59f57` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 19 | Blazed Glazed (v1.3).gba | `0b55d44bfd32a350202c0878754cfcacbbaee128de3b59297ee669b69269199f` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 20 | Blazing Emerald (v1.6).gba | `2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 21 | Bronze (Girl Patch) (v1.23).gbc | `9c6aa82ae48b1da1acc73e716c1c18aa90a02b05f33faa96289a2af6927940ea` | SELECTED / GOLD_SILVER | yes | 0 | yes (11 sections) | ok / FK 0 |
| 22 | Bronze (v1.23).gbc | `3cf45157784fe70ddf9f07639236022321bf62b70797c412457625b2704c3269` | SELECTED / GOLD_SILVER | yes | 0 | yes (11 sections) | ok / FK 0 |
| 23 | Bronze 2 (v1.05).gbc | `87758fbc06a9abc73577bbc16d184bc3fb6f35d5abf22d776156629b5e5ae811` | SELECTED / CRYSTAL | yes | 0 | yes (11 sections) | ok / FK 0 |
| 24 | CAWPS.gba | `88c2e3f60924a126b842f03817315c0525bc6dec71aa79bde57a7900c7e416d3` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 25 | Celia's Stupid Romhack (1.1.4).gba | `81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 26 | Chaos Black (Fixed) (v3.1).gba | `f21a917a5d43f1f4d952ff041ab13b42fba2c7563f0665e3525b1b39639a5979` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 27 | Chaos Black Recreated (2026-01-25).gba | `3358c57e22588a14cd2c3db82436482238d12f90e891a66547f91ffd6f41dda5` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 28 | Chronicles of Soala (v9.0).gba | `7c6f3945bdacc7e861f9f1279870b73b73233a83309fdd5006312da24e34c849` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 29 | Classic (v1.5.0b).gba | `01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 30 | Cloud White (v523d).gba | `f70922408ea71257a2893f06b51cc02aa890e573beb1b84043a100060de1d11d` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 31 | Cloud White 2 (v279).gba | `6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 32 | Cloud White 3 (v277).gba | `7ced98ef9232e3d09892c4e960e326eac8daf3c596f54d773661cc227d25b8e9` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 33 | Clover (v1.3.3).gba | `42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 34 | Crippling Medical Debt Edition (v1.1).gba | `79882b5e276f6c0386fe7c4d5cce122c56ff969d694ffc530b1a534ab57d25cb` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 35 | Crown (v1.9).gba | `28d7f55c96bde57269e3bbae8c5e8e177dcb92f16feaee65d556ab387ccaddd0` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 36 | Crystal Advance Redux (7-8-26).gba | `fbbcbf32afd427afa5de45799923c414c21b77917004477f214c9f5cd87537b6` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 37 | Dark Cry - The Legend of Giratina (v2.6.7).gba | `e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 38 | Dark Energy (v5.01).gbc | `6dba21527ea5d788f63ef6b64cab2be5e9c77f90db11d8ebf95eeea62fd2bc1c` | SELECTED / GOLD_SILVER | yes | 0 | yes (11 sections) | ok / FK 0 |
| 39 | Dark Rising - Order Destroyed.gba | `71b44f3b4be1b17428dd3fcb1c37002268c7b832dc49626b9d57bf56de10f387` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 40 | Dark Rising 2.gba | `81b97561b73d02a26ba52369d582ac5d8615078de2b202e0673f4e6512af120d` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 41 | Dark Rising Origins - Worlds Collide.gba | `c6440addb23d76f514d0ba4baf049a5c34a0d7c0938a5c6ee4fbfa3792f9daea` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 42 | Dark Rising.gba | `712697aba9a0f2401bc0fb8677caa69d9d21beee26c7d9920226e52f02f76a4e` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 43 | Dark Violet.gba | `6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 44 | Dark Violet (Fan-Patch).gba | `d171d29b691ced98178b4370826f0627f9c2ed6e0313d813f909ba147031c717` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 45 | Dark Worship.gba | `930663704d1a84b93815d276703114e88785de94fcb3230d832ef07dc399f1d8` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 46 | DarkFire (v2.1.3).gba | `8c564fcd1e419d81a56eaf6734ae9eb70d0f9849d08200c1807d31d674a48d69` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 47 | Delta Emerald (v1.1.5).gba | `7f4aa1aa68b1df783c3a44b38984640227a5eec22debffbf18db3713de2616bc` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
| 48 | Dragonstone (v1.63).gba | `2772296094b37c36ddf5735e58e54520bdde88a318c033e4817e40cc44676698` | SELECTED / RUBY_SAPPHIRE | yes | 0 | yes (11 sections) | ok / FK 0 |
| 49 | Dreams (v1.5.3).gba | `ad73b864873f17add4f931315d3162b792b19c65133c7a6819a85866b1afa403` | SELECTED / FIRERED_LEAFGREEN | yes | 0 | yes (11 sections) | ok / FK 0 |
| 50 | Dreamstone Mysteries.gba | `ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220` | SELECTED / EMERALD | yes | 0 | yes (11 sections) | ok / FK 0 |
