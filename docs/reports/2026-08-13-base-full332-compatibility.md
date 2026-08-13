# DualDex full unique-ROM base audit

> This is the frozen full-corpus baseline at `b0b1adf`. It records the broad
> routing and persistence denominator before the later focused encounter,
> `NO_MOVE`, and ability-reference closure slices. Those corrections are
> verified on their affected real-ROM clusters and against the exact first 50;
> this 332-row matrix is not rewritten to imply a full rerun that did not occur.

- Manifest payload rows: 334
- Unique SHA-256 identities: 332
- Exact file rehashes: 332/332
- Status: {'AMBIGUOUS': 2, 'NO_FAMILY_MATCH': 100, 'SELECTED': 230}
- Families: {'CRYSTAL': 9, 'EMERALD': 53, 'FIRERED_LEAFGREEN': 88, 'GOLD_SILVER': 3, 'RED_BLUE': 59, 'RUBY_SAPPHIRE': 15, 'YELLOW': 3}
- Persisted/reopened selected catalogs: 230
- SQLite/cache audit errors: 0
- Reference error rows/errors: 9/2157
- Rejection clusters: {'BELOW_SELECTION_SCORE': 88, 'HARD_GATE_REJECTED': 4, 'INSUFFICIENT_INDEPENDENT_ANCHORS': 8, 'RUNNER_UP_MARGIN': 2}

## Duplicate manifest entries

- `75ca054238d41b38df5113ccb89af765561ce8963f78f7eb1befab6310306600`: row 3 Adventure Red Chapter (Beta 15 + Expansion Fix C).gba; row 147 Red Adventure (v15c).gba
- `037f5ba913953f2387175c5e0549347d162ef3b224d25660e8055acdac4564be`: row 264 Orange (Suloku Patch 2026.0.2 PSS).gbc; row 265 Orange (Suloku Patch 2026.0.2).gbc

## 332-row matrix

| Manifest row | ROM | SHA-256 | Status | Family | Persistence | References | Cluster |
| ---: | --- | --- | --- | --- | :---: | ---: | --- |
| 1 | A Grand Day Out.gba | `2005275fc54ae63f3d1bc50c49980e87dcd9ecae5e4733d322bb2a2c99270916` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 2 | Advanced Adventure (2021).gba | `736af8f701690c59bf174593c7ea60aa1a531405eac1d69f459ae1e338a36829` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 3 | Adventure Red Chapter (Beta 15 + Expansion Fix C).gba | `75ca054238d41b38df5113ccb89af765561ce8963f78f7eb1befab6310306600` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 4 | Aesthetic Red (DS Font & Sprites) (Faithful Version) (v1.2).gba | `80e96000eb82963777d87a76baf05ecf50712961dcfc51dc5bd86493796f4270` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 5 | Aesthetic Red (DS Font & Sprites) (v1.2).gba | `7f01d5ffd8b2e597be313f4d8e5f425a3f0abf50689274564001cd9e51ef2b0c` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 6 | Aesthetic Red (GBC Font & Sprites) (Faithful Version) (v1.2).gba | `a88c1d13b0297070cc975b1c2421feca0639cf1c14a491da188282cf72875a54` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 7 | Aesthetic Red (GBC Font & Sprites) (v1.2).gba | `d3b3b5a8556d977618f1783f935d72b3b21c7847d217e4aad4eef754115dd282` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 8 | Aesthetic Red (Music & Graphics Only) (v1.2).gba | `0dfca1fd701b94440454c25ee352054ed331d4404e502f7d322d9f2776f99794` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 9 | All In (v1.0).gba | `baf1bad15fd25fa8103d53021991bdadb64c142f8108efd29c14cd01ba069905` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 10 | Altair (2019-06-13).gba | `333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 11 | Altered Emerald (v4.2c).gba | `8fe93d8245c96ea5aa49d61df2c74ee99a439b15cde7c0afa4f0b5a87aac34f0` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 12 | Amethyst (v1.3.0).gba | `3f987c21b2d62c02b3df43c9f94e5f877f8c71ebe82faad994b6667ddbd9089e` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 13 | Amnesia (Save Fix).gba | `08b51b82beef849e1956bfcf468823b8f45518e0a6dd907ecc993c7c44aa0d94` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 14 | Arcoiris.gba | `fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 15 | AshGray - Newerest Edition (v1.0).gba | `a08055484c8366768d3e98e2dbed0998641abd2899ffbfc8d7f132925875f7a1` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 16 | AshGray (v4.6).gba | `a2d141a4f080befb0c0b077a4434feba5583e9e1e5381492fdf65905e6028bad` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 17 | Battle Theater (V2.3.0).gba | `99c84950e2be2f887a84bdc32c741c92385bb4a54843d871a8876e9b47e1d59d` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 18 | Bill's Secret Garden DX (v2.0).gba | `2eb56e73fdba2b81c26596d19e80410fbd48de0586af5d342c25ec741eb59f57` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 19 | Blazed Glazed (v1.3).gba | `0b55d44bfd32a350202c0878754cfcacbbaee128de3b59297ee669b69269199f` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 20 | Blazing Emerald (v1.6).gba | `2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 21 | Bronze (Girl Patch) (v1.23).gbc | `9c6aa82ae48b1da1acc73e716c1c18aa90a02b05f33faa96289a2af6927940ea` | SELECTED | GOLD_SILVER | yes | 0 | SELECTED |
| 22 | Bronze (v1.23).gbc | `3cf45157784fe70ddf9f07639236022321bf62b70797c412457625b2704c3269` | SELECTED | GOLD_SILVER | yes | 0 | SELECTED |
| 23 | Bronze 2 (v1.05).gbc | `87758fbc06a9abc73577bbc16d184bc3fb6f35d5abf22d776156629b5e5ae811` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 24 | CAWPS.gba | `88c2e3f60924a126b842f03817315c0525bc6dec71aa79bde57a7900c7e416d3` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 25 | Celia's Stupid Romhack (1.1.4).gba | `81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 26 | Chaos Black (Fixed) (v3.1).gba | `f21a917a5d43f1f4d952ff041ab13b42fba2c7563f0665e3525b1b39639a5979` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 27 | Chaos Black Recreated (2026-01-25).gba | `3358c57e22588a14cd2c3db82436482238d12f90e891a66547f91ffd6f41dda5` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 28 | Chronicles of Soala (v9.0).gba | `7c6f3945bdacc7e861f9f1279870b73b73233a83309fdd5006312da24e34c849` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 29 | Classic (v1.5.0b).gba | `01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 30 | Cloud White (v523d).gba | `f70922408ea71257a2893f06b51cc02aa890e573beb1b84043a100060de1d11d` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 31 | Cloud White 2 (v279).gba | `6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 32 | Cloud White 3 (v277).gba | `7ced98ef9232e3d09892c4e960e326eac8daf3c596f54d773661cc227d25b8e9` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 33 | Clover (v1.3.3).gba | `42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 34 | Crippling Medical Debt Edition (v1.1).gba | `79882b5e276f6c0386fe7c4d5cce122c56ff969d694ffc530b1a534ab57d25cb` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 35 | Crown (v1.9).gba | `28d7f55c96bde57269e3bbae8c5e8e177dcb92f16feaee65d556ab387ccaddd0` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 36 | Crystal Advance Redux (7-8-26).gba | `fbbcbf32afd427afa5de45799923c414c21b77917004477f214c9f5cd87537b6` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 37 | Dark Cry - The Legend of Giratina (v2.6.7).gba | `e61d4f66e2d4d39798bcd18f5abfb3db75282508fffd12401b9a1e9d0c1b08ed` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 38 | Dark Energy (v5.01).gbc | `6dba21527ea5d788f63ef6b64cab2be5e9c77f90db11d8ebf95eeea62fd2bc1c` | SELECTED | GOLD_SILVER | yes | 0 | SELECTED |
| 39 | Dark Rising - Order Destroyed.gba | `71b44f3b4be1b17428dd3fcb1c37002268c7b832dc49626b9d57bf56de10f387` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 40 | Dark Rising 2.gba | `81b97561b73d02a26ba52369d582ac5d8615078de2b202e0673f4e6512af120d` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 41 | Dark Rising Origins - Worlds Collide.gba | `c6440addb23d76f514d0ba4baf049a5c34a0d7c0938a5c6ee4fbfa3792f9daea` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 42 | Dark Rising.gba | `712697aba9a0f2401bc0fb8677caa69d9d21beee26c7d9920226e52f02f76a4e` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 43 | Dark Violet.gba | `6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 44 | Dark Violet (Fan-Patch).gba | `d171d29b691ced98178b4370826f0627f9c2ed6e0313d813f909ba147031c717` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 45 | Dark Worship.gba | `930663704d1a84b93815d276703114e88785de94fcb3230d832ef07dc399f1d8` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 46 | DarkFire (v2.1.3).gba | `8c564fcd1e419d81a56eaf6734ae9eb70d0f9849d08200c1807d31d674a48d69` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 47 | Delta Emerald (v1.1.5).gba | `7f4aa1aa68b1df783c3a44b38984640227a5eec22debffbf18db3713de2616bc` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 48 | Dragonstone (v1.63).gba | `2772296094b37c36ddf5735e58e54520bdde88a318c033e4817e40cc44676698` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 49 | Dreams (v1.5.3).gba | `ad73b864873f17add4f931315d3162b792b19c65133c7a6819a85866b1afa403` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 50 | Dreamstone Mysteries.gba | `ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 51 | Dreary (v1.4).gba | `be5ac0079d16fdc8dc3bf27564a3463815cf3c1b18e116a090c2b988b7e9bddc` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 52 | Elite Redux (2.65.3b).gba | `55b887450b936db1e12bfc3307b84e12c1cadbd117a57bf3769388ec76691c31` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 53 | Elysium (Part A) (v2.5.0).gba | `6254fb4edf3727d87c9837f4505ff97abb31eb7fd24820d63ea84b4c64291e4b` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 54 | Elysium (Part B) (v2.5.0).gba | `e907c970a70bd6349232843164feae9c338ca1322a94f62f9276ad6bdee57d5e` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 55 | Emerald Azure (0.5.4).gba | `8c28b77f562e73321d7cb60a05568bfeec3f0e821926e2541a48513340cdd52e` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 56 | Emerald Crest (v1.0.F).gba | `3ed3f02d6b2ed8a862551b5b169adcbe608077ab99009e1ca3702c2da2ab7e54` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 57 | Emerald Essence (v1).gba | `188d6d3ec5db2959f2140cb13797d5ac0f598c08467287f7106ee9649f6bee32` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 58 | Emerald Ex (1.0.2).gba | `431451885f665865860bc769b9343b3d682eb10d4978d06ca0b83c4542221fa3` | SELECTED | EMERALD | yes | 8 | SELECTED |
| 59 | Emerald Exceeded (v11.5).gba | `5c8faaed4b9341351ca6376b7bea9237ae47bdf8fdd3179fc59d4a93523e181b` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 60 | Emerald Extended Cut (Classic + Hotfix 1.6).gba | `7b133ea0a5b1c2438d963d220ae22fe759a998c153500f3ec67dbfb489845bb5` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 61 | Emerald Imperium (v1.3.1).gba | `79bfd6266f7980452c864a20ffe47783b8bb91ec9957174ed416b77d3facb3a0` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 62 | Emerald Isle (1.2.22).gba | `0306d84fa19f17e5b35b727e084237cb89646f54c03badc9ca160163df0a8df3` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 63 | Emerald Kaizo (21.06.26).gba | `e5e81ca35ab7b864e787a3c03e2b6d1de7edf5ef32c8de98cb533658a1d779df` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 64 | Emerald Legacy (04.06.26).gba | `9a33576fcce60dd68f8ea72c0322c944a9fe1beddd6a2a056cc342ac1f7cb8ac` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 65 | Emerald Mini (1.2.2).gba | `ba6e029c1ea0d4a6447e08071503ba6ad11e0a8442c603a488f27e008f59bf68` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 66 | Emerald Party Randomizer Plus (v1.0.8).gba | `39b7c62234f0c383f7707ef5e70aec0c2c09deedc899211d164e35c31d85f894` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 67 | Emerald Rogue (v2.1.2-EX).gba | `111b0008bcec519c59a02de57895a924fee5d3633c8b8fbb394a497153778ca3` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 68 | Emerald Rogue (Vanilla) (v2.1.2).gba | `bc41411bec0b89c37f8514bae6fe8b7472093fe6badcd503ed2c466929f1e93e` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 69 | Emerald Seaglass (v3.0).gba | `6098296dca53655bb51493658d74ba206abe6bacbbe60db8d4f497367926809f` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 70 | Fire Gold (v1.4.1).gba | `c65f698ffb844b99836a36dc0752e8b57ebd45be53be364cb3cfe52aa0cc74ce` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 71 | Fire of Sky (v1.0.3).gba | `062c82e76970d600a43626d589472e7c407d042bc0acaf428f5faeb504c7b494` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 72 | Fire Red Backwards Edition.gba | `b7e02b900240ab6b1fd3a34c8ac89c593583d928278199869ee065f1effc8ced` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 73 | Fire Red Extended (3.5.6).gba | `9db96505b71d197352dff533db6b7ae11d48c36188a998d51e948d633e96b173` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 74 | FireEmerald (v0.6.1).gba | `c4c8e277714850812e24f9249323d71059d62e4b7405938247db622ac605b00d` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 75 | FireRed & LeafGreen+ (v1.5.1).gba | `41b51f4f1d0a894dbb27572d6580821c6a192231f930d85c2805af664c13bbb6` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 76 | FireRed Essence (v1.5).gba | `e5ae53d5c8dd400eab44513283983bf3a7f15e9035d633976a0b9b8f7e38c2d2` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 77 | FireRed Extended (v3.5.6).gba | `a622dae8dec64b994d8916a47b17c3d41112d20519dba81c145ad25b1f785565` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 78 | FireRed Reignited Legacy.gba | `10d7e34128e977fbd42b6a9b57ceeb2968ed970b2ff3bf370a188bed14c88926` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 79 | FireRed Team Rocket Edition (v1.02).gba | `e6f232d0df265323c995e6025fe0ebcc532dfaf7634548a13ee9fb5673152442` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 80 | FireRed VR Missions (v1.0).gba | `c1825747150f4ec1ae324d88f360045aa8ea1f2edf0dd34a12b432efa2bd332f` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 81 | Flora Sky (Complement Dex).gba | `1da0b0577fd0063eafb1cfb1836e98beb08d4ee3677c606eb42fa215508eba51` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 82 | Flora Sky.gba | `aac242276e2cfedcc4cf8a07210d3eba7306c3d1e701fac107a2659dc0407c4e` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 83 | Fluvio (Demo) (v0.1.10).gba | `c4d67995e31779582cb2dfc526b63d0dbb997cd17328a92725d247e6a6884849` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 84 | Fuligin.gba | `7acaa6dc7bb4ef27a262b55e5d7c1cb00cc84c3bbdefba21e81e13a9b45bfd19` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 85 | Fused Dimensions (v2.3).gba | `d89e02430fbca8fa5792076ae18da18af8bd5e2bbd77f6e70c16327dde2a8ed2` | SELECTED | FIRERED_LEAFGREEN | yes | 172 | SELECTED |
| 86 | Gaia (v3.2).gba | `186a36cd389aecf10fb9ba112e7340fd81127fcb23d9564294df80b1e1473ab5` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 87 | Giga Red (v5.0.7).gba | `8612a4039ac90749e7d56d4d3e0254c4b900123170ba055f95d6219c9d90be96` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 88 | Giratina Strikes Back.gba | `0e885bf15e37ebf0b7f33ffced37c65ce7256a7cff091b358a878888c9b5bb93` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 89 | Giratina's Legend (Demo) (v1.0.2).gba | `a8885233bd938cae18bb40628025fc4f587ef6836f398509bbdc7e82f947ab6c` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 90 | Glazed (9.2.0).gba | `0ffe8bc9e983641df0cc8ccb7649a1d85f189aef8c387f6d6978a2180cdbb817` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 91 | Grand Dad Version.gba | `a51cf68b15789c28b093613689a25d024b981047b007aad286a3ae484da06634` | SELECTED | FIRERED_LEAFGREEN | yes | 510 | SELECTED |
| 92 | GS Chronicles (v2.7.6).gba | `83de12ab57692aa39d5eff5314d404aed06b466b038ace637a8b84c9c25f1569` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 93 | Harvestcraft (v2.0).gba | `07dcceb75eee8c911b1333479e432e4724e3b52a91eb3725606954308a536d16` | NO_FAMILY_MATCH | - | - | 0 | HARD_GATE_REJECTED |
| 94 | Heart & Soul (v1.2.1).gba | `7c4f90c8b68b64d4639a37306d5302a11cd4d7c44005114ec2faa2b28b210c3d` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 95 | Pokémon Hearth (v0.1.27).gba | `4dd65a9b6fd55cb880b7886cc0cfcc54c09720da3f94b4e09c246c0cb651595a` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 96 | Hoenn's Last Wish (v0.4.7).gba | `c122da779be661e85415d738f2e90f4d53dd1b476311858f7499836617d81e32` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 97 | Hyper Emerald - Lost Artifacts (v5.7).gba | `33a6c3d67f4a40d7cc03ad94ee5f66d7c8e647db4011040322b12e70fe1e921e` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 98 | Inclement Emerald (v1.1.3).gba | `9623d05908aa5ea086535c6830a69bb3fcfe9bfd1b017e0101352e520587571a` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 99 | Inclement Emerald [Custom UI Update 1.5] (v1.1.3).gba | `7db039bb6991cb73eb088137680a0aa11450476e175018de379335c835f7c9f0` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 100 | Inkwell (v1.04).gba | `2b47308e5ce7868b1f7fa5163187dc098e26aeb7bc69def7c432c8e4c8b8f234` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 101 | Kanlara Ultimate (2020.01.21).gba | `f7efa8eaa5699a3773dd8a1c2e5503353906a28a23a6afbdb41e66aeebc22e07` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 102 | Kanto Black (v2.08).gba | `0eb8deb5355207815cd81a0030d87ddf16b22b15f8b196defe21f6b5f9f34517` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 103 | Korosu (2017-07-23).gba | `9a6b843fe2d8d9d8ddf7808c8aa8b20c57d69ad3b054b743453105bb9a116433` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 104 | Lazarus (v2.0).gba | `d756b2783dede0bb2c534d1f07713bef733cf53500388e3912f14220a7e16e97` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 105 | LeafGreen Regrown Legacy.gba | `f8b5ed013b4ee6b1de54348edc4e7db4ec9b4a21ed01878d60777c3b67b8d0d1` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 106 | Legends Delta (v1.0.1).gba | `13192818fb1a6b90ccb0db4cf924d6d443c6b464421007a7e9035b998dca5c9b` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 107 | Let´s Go Pikachu (v6.0).gba | `e7360e78bc4399942f6d409d1816d3c0d377236844f380b600a7e145ae3ee289` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 108 | Light and Shadow (V2.0.4).gba | `18cbc369eb8fab908fcc42bdcd411a2bbe088dfb3b830aba6a8d8689fe11c59e` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 109 | Light Platinum (Old Version) (Fixed).gba | `10c7e9402fb56e1c66316cf4ea155ae5c2fa79846799474526e40b0c02031950` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 110 | Light Platinum.gba | `ac8cb698369fea63edff12c7c79ee14d59ac89bbd39a01880c21cbb0933e1ad3` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 111 | Light Platinum+ (Fixed).gba | `b81558a3e2bbc2fb77035744e7a1df294df544835cfbd168448a88641bc6c99f` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 112 | Lime (Demo) (v1.4).gba | `4fa3409d59f50a25a2aa9dd05c28f905d39063b47749d55c1028427a65989c6e` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 113 | Liquid Crystal (v3.3.00512).gba | `475b6279da87cff642c302fda1cc4423b300f2130035898cac35d4e8fa4bafca` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 114 | Lithium (1.0.18).gba | `acb50d55818e8b475fefad6c1fa003d7d7573a0ce5ff43eb7a7d75dd81736640` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 115 | Lugias Ocean.gba | `c632fed6025495cb2ec2879bff2474ce89853e76b054fa92e275f9b150f78d90` | AMBIGUOUS | - | - | 0 | RUNNER_UP_MARGIN |
| 116 | Maxie's Island (v1.4).gba | `0f4057f3ca536d05f315cf87e721be9d6b0e316e406db5ab338697a6efb63058` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 117 | Mega Power (v5.71).gba | `2a1fbc43db51181d2f18464611dc71072508abccb28ba09805ac37bb0e06e108` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 118 | Modern Emerald (v3.5).gba | `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 119 | Monster Hunter Emerald (v1.0).gba | `3f4bd521915c3ef77ff125388b7f0e0fd4cc539f638c23d1b9d07d02de812cdb` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 120 | Moon Galaxy.gba | `87766f522368b6208c840f0803641e41046c2b79c765892b2885a8fbbc682117` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 121 | Mystique (1.1.1).gba | `24d87eeed495a258e7b17688f8b7c7a4078910c3e457421929f64508173792fb` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 122 | Nameless (v5.45).gba | `483acdef9eacbd0f98ac1ad1bfe22e9f07240a98dac14f0772d6a50465dd777d` | SELECTED | FIRERED_LEAFGREEN | yes | 267 | SELECTED |
| 123 | National History Museum (v1.0.4).gba | `bd805632b57bfb120f6e326745201397cf7a8c5949673eefb7462ae6f882cf3f` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 124 | Noon (Completed).gba | `64712b2e16c51eedf37aed05975e72b7025f52bf7ea6db7d17d44605d013bc29` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 125 | Odyssey (v4.1.1).gba | `44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0` | SELECTED | FIRERED_LEAFGREEN | yes | 739 | SELECTED |
| 126 | Odyssey II - Heroes of Lemuria (Demo) (v0.1.3).gba | `4ec443aad4917fda94474d1632adfa01179e63788e514a9f05996198ddd6d7ba` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 127 | Omega Ruby Origins (v1.4.8.7).gba | `5335375e766245f10fc02d8ddd6b476b43e2afe033bbef4029c75a5aa4fd4388` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 128 | Orange Islands (Beta 5.7).gba | `fcc2821b9492271e0b7cdacb2427d8bf9489289089f5e7512e17f023e0bc7036` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 129 | Order & Chaos Remastered.gba | `1e1180b8dd30485064c0ec956fefa3c7c318378a66439e595eb4e59a5c314887` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 130 | Outlaw.gba | `736dbd4e6300f6c855aef1446569cf5957906bb19250332d1b05c109a11d8435` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 131 | Palimpsest (v1.2.0).gba | `be2864da219ba835bcf5989fdaf9a03fc4faa4568b8b1cb94324e7afe71c1cfa` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 132 | Parallel Emerald (v1.2).gba | `57d9ee54b152f63f74e2a3d8546d09b70c07b4854f4c9d75b000f198f2b1ba8f` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 133 | Peach (Demo) (v1.4).gba | `024a3b5c5bc01e0d80934de9e22c0f5d3927f1c59f49fd44a1baf450b7fe015b` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 134 | Phoenix Red (v1.2).gba | `b1a6af03ae7cff8de9b846188050d86b2a72bd35b19b7ec9876a124000acc268` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 135 | Pisces (v1.5.4).gba | `5a3bde580eb6670e40e33addc2ffaf7a45afaadee46f4cae3be9379fb49d45f7` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 136 | Pokescape (v1.0.4).gba | `2425846d68e322a86f362a9ee4fed8c8d9ca01a20fdfd8587d7f0559f001c47e` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 137 | Project Nova (v2.7.0).gba | `ce1712714701bda75074af96faeb7b72c518f874870ba20220315b9dc673f480` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 138 | Project Pi (v1.3.4).gba | `f927288b4c271bd9733ea9170e363fb05e9c5445efeabdc6f95eeb1e9c7d3b56` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 139 | Quartz Minus (v1.1).gba | `ed30b25d57b03aae88bd441bab1f95c7017763e6ae42338627ba0d2acc43e109` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 140 | Quetzal (Emerald Multiplayer) (v0.8.4).gba | `e9fc9cf506ad11db7642e7d65774e2bb0e5f174eb9aceda725cd961ab9aee070` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 141 | R.o.w.e (v2.1.9).gba | `1fb40dd2ee6760cebcef826d94df159e95fab40efbe0f221a94f7e25e0164afc` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 142 | Radical Red (v4.1).gba | `679d112cdfe699c2793d82c7e7999ac9dfca9e222ad5a85d4f8f1e457cd0283f` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 143 | Recharged Emerald (v2.2.8).gba | `15df05788ca9502fd4f8d92749157969d2a52dd0c07fa08bb6c0fc08291b61b9` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 144 | Recharged Yellow (v1.9.7).gba | `442c38ee4ac598795107eee730bdd4b0b59148c0faec3a4def21af8396703da9` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 145 | Recollection Quest (v1.2).gba | `3967691800fdf70b4573a61bcfa0e65ede56831a0a1b78f314ebf4e487c5a69c` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 146 | Recordkeepers (v1.2.1).gba | `b6b7cb019394a57dcd955f74c96a30a687a035fcc6f8c4249ba27fbff2e9aae9` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 148 | Regis’ Origin (v1.0).gba | `49c4e729bf5b39b56ea446bab3373b58fcb0d72e857e669c440ada0484bdad9a` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 149 | Resolute (v2.97).gba | `03657b979c7fed04361859958423f0604923f57bf90ae25191af965a79340713` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 150 | Retro Platinum (Demo) (v0.1.2).gba | `c179e5ba7f0cf418844f68af07ca532bcb1299e7882ec342a8f9e33b9a290927` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 151 | Rijon Adventures (dev-git).gba | `7187c03c69d4bf7d9574d57e3074afb0d32b924c6e1540b5d4bba73fb9e4f065` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 152 | Rijon Adventures.gba | `592dc0caa5f77a798569d92d95a1ff64ac0f58a30f6ae10d7c9060b7287ba2c5` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 153 | ROWE (v2.1.1).gba | `ee5072680bb1c0eff942442d7b7309f17d7ee5161a17379f9de2cf122d9c9c8b` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 154 | Ruby Destiny - Broken Timeline (v1).gba | `4481166c8d326e7fca3633e8bf7fcc50b8952d589f458fe6398d859983d59a6f` | SELECTED | RUBY_SAPPHIRE | yes | 131 | SELECTED |
| 155 | Ruby Destiny - Life of Guardians (v1).gba | `935200ade5f22026ebb336888d444ada758b696fa1ba0b9cce68342d60cded72` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 156 | Ruby Destiny - Reign of Legends (v4.1).gba | `0c13e7141e1f5589c006593299ace538c49e80c86739d7369dc05686e59aaaaa` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 157 | Ruby Destiny - Rescue Rangers (v1.84).gba | `34fa38f4ba5bc9dae2ab94a6bb5c20566d11853c2c165a9f8e9f5691ccd3e1cd` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 158 | Saffron (Demo) (v2.0).gba | `9d450338116b2e1acaae2441db13a3eff41beeccbbf08b17674f4beb2d6e41b7` | SELECTED | FIRERED_LEAFGREEN | yes | 126 | SELECTED |
| 159 | Saiph (v2020).gba | `b8dc42132873c74ba9655f2f73976e6a82247b4a933369211513211d93125dfd` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 160 | Saiph 2 (v1.4.0).gba | `ef746af34aecc33bd3b6c343a8aa16ff75c4c578396c688ce197158ed72492ca` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 161 | Saiph 2 [Lag Fix Removal] (v1.4.0).gba | `6575233c116b9ca67a1e88e4d67dfc6c4d7dbefbc15faf6b8ab7d37b74f94d97` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 162 | Saiph 2 [Time Based Removal] (v1.4.0).gba | `523578b5410acd2bd5287af7b7226ef51e2c0e50e00ee038dd6859cf980dbb8e` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 163 | Saiph 2 [Vigilante Mode] (v1.4.0).gba | `75b45a04f2fe516fbb5f12ae69cfe8c3b383895766f2a47b3d7a7d3791e4349b` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 164 | Scale x Fang (v1.0.2).gba | `8f58e67c994e95f9cabcb891759627a6c95f3d966f613c4ecb75c0c64604144d` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 165 | Scarlet & Violet.gba | `37b4c10670e639d36a2fb6e63490e546dcbc0ac5b40a68f8aca32313dcc31a77` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 166 | Scorched Silver (v1.3).gba | `3698392d27605dafdc85190c6271d68be1be8004f21f6c27ee81be05fbb1671f` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 167 | Septo Conquest (v3.1).gba | `6348ca6218cdfa814a9f71d0e7ceb99b9b881a78fdd42079a44cc3766be7d494` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 168 | Sienna.gba | `f959eecbfe8e5dcfcfc5d505d65e6cbb1dbc724fea0c4fe1364b9903c47b66e4` | SELECTED | FIRERED_LEAFGREEN | yes | 203 | SELECTED |
| 169 | Sirius (2019-06-13).gba | `bf0a73490c68a14198eb724b762f293d812bf98a0c8f2430680d4667eb819a04` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 170 | Sky Blue (2024-10-03).gba | `ec432c60a99b0f759a1b4c246d3f0acf14331a05fc990ee21a469882519edac1` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 171 | Sky Twilight (Fixed) (v3.0).gba | `f83f6666e7400d5f4699264ee65bddba7c61389741a5d7a6e73f0cb04b082bf3` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 172 | Snakewood.gba | `80097bc7c1ecba290a667df81ba79186b7c43f519700d0100b2c7ce028d9f740` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 173 | Sors (v1.3).gba | `293ad62d949cf8ce64a76a99197c642401b402294444c085b49b7d8fa3c6173c` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 174 | Sors 2 (Story Demo) (Hotfix 3).gba | `e84f2fef4c97c9c472e61764f94519651895fa32215a788320d47c05ef961a23` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 175 | Sors 2 (Tech Demo) (Evolution Fix).gba | `8ae236ad674c6deae14cd54a0f02534681fbc4853d158ab949c5a3f3d0f42971` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 176 | Soulgold (v1.0.2).gba | `94c09e3a2cabb1fee49e0de015e6ea66c7e8305fc7017b057ff28bad2ad83603` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 177 | Sovereign of the Skies (v2.1.2).gba | `ded48b60accfaa6d963a164a357545916674227e9ca747699820b76a8c4c1871` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 178 | Spades & Clubs (Demo) (v0.2.1).gba | `ede750faa835d589654a5b9315b144da5a50e275643aad3fcebff286ecf3236a` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 179 | Spirits of the Storm (v1.2.01).gba | `65a45dd98ff9d86e7207b8b561a36428996c5b1c6f3b6ab8547f237b213af751` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 180 | Sun Sky.gba | `f5bf7340a5eab05047273fdca6437fffbcd20ae3c67f1637df7535913510edcc` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 181 | Super Mariomon (v1.5.2-Anniversary).gba | `c24f0b73831ff1fc1ada91dad80befb0a05c182717fcd5c03125dbb6e5b70f97` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 182 | Sweet (v1.0).gba | `1b4f4f33805f27bbf4a325919503df77d70c08eb59f3bcfc09bc932324583f80` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 183 | Sweet 2th (v1.0).gba | `ed34b54539da42efc527ae9e95be0b7fa248aa05a72916f102f74e7aaed4d722` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 184 | Sword and Shield Ultimate Plus (Casual + Performance) (v1.2.1.2).gba | `afd5ed8d1d7a229e0d3359a50b3baadece9007bf0f3c66fca4a701b19b0b676e` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 185 | Sword and Shield Ultimate Plus (Casual Patch) (v1.2.1.2).gba | `fedbfaa74a67ad8cedabb05641bb857a6bd1c9db28d8f26a06469afda38ed1d3` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 186 | Sword and Shield Ultimate Plus (Performance Patch) (v1.2.1.2).gba | `3728b3a4beaf1dcb78335db670dea97126ed579f238eb2107096668792495749` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 187 | Sword and Shield Ultimate Plus (v1.2.1.2).gba | `f6d2e7092831b983318b685132a19567ff5e6428665255738c4e5a63371bcce3` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 188 | Team Rocket Edition (v2.1).gba | `560c746d5b9b123dae25879979a120a1985cbb032e329b336ddfac4e83b6a6b1` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 189 | The Nuzlomizer (3.0.1).gba | `0166385dc90937fad15c95bc464e69dd0f593e2e55f0c038d1ea5c23eb30367c` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 190 | The Pit (2.5.1) (Gen 3).gba | `156f0f5150d7bf2b6218ff9c6240fa3c815c8d11542e38500194663fdb518ac6` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 191 | The Pit (2.5.1) (Gen 5).gba | `66374062baebabb94b27e4e0d947234a05019e3bd82efb9fb750100ab9df2d17` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 192 | The Pit (2.5.1) (Gen 9).gba | `4f6e72a69998164164b65c3ae453dcc66dda979153ee911c8336db5cf262eeb1` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 193 | The Unown King (v1.1.0).gba | `2af6dddd7e092b61510baed358055621c981fb4cc24288bc4f7dde19c352189c` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 194 | Theta Emerald Renev (v2.3).gba | `0bc7d66369e3dfd7058a125e2646c533ae0e951e2866f073f709febbfa2dce21` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 195 | Too Many Types (v1.6).gba | `0c31f9415c7bca12f4ecfc215c3521f400f5f992104b130a8fba5c04bbfb232b` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 196 | Too Many Types 2 (v1.5.2).gba | `7bb7df60a2ed3fb6a4ee39021167458bf445d593ead97949599f1dbf5ce39122` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 197 | Topaz.gba | `528011e6a51ff41142035642d9750bc175494b64f614fd9958673aa6498c0f7b` | SELECTED | RUBY_SAPPHIRE | yes | 0 | SELECTED |
| 198 | Tourmaline (v1.1.1).gba | `d4f1851fe0813d98d72b1ffdfb554de1f86ac5da245e7b79c7fcfd594a54f319` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 199 | Transform (v1.4).gba | `dd4fb1252b7d3cdb637880e887b2c6119f93b2ea37b48d28ceec2b0569f90516` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 200 | TWO (v1.2).gba | `ffa751cfd54133041e4a7ccc0ade7f0930117c52ed30e4bef747a79f498be73e` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 201 | Ultimate Fusion.gba | `685d6288083c8c05715d304408b5a1ea7a294c4c9d83ae8c43c5a3267282c522` | SELECTED | EMERALD | yes | 0 | SELECTED |
| 202 | Ultra Shiny Gold Sigma (v1.5.0).gba | `62d1a99f5b64a45cd4f6364273743f9d8961e9c439d8201bfeedb27c02f32c64` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 203 | Ultra Violet (v1.22).gba | `467fe116ff6d7d5723b9f8fc60dd81c698c5d4e27b1b444168f3be292848d015` | AMBIGUOUS | - | - | 0 | RUNNER_UP_MARGIN |
| 204 | Unbound (v2.1.1.1).gba | `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 205 | Unknown (v1.0).gba | `cc59cff84c2ac38e070b89c154597f90ccc8277edace84134f2ecfa9bd17b77d` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 206 | Unova Emerald (2.0.3).gba | `87686967d56234c775ab91798588462e94d3170cbd578fb310f7e4dfe8aab677` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 207 | Valen (v1.9).gba | `bf5c6a59d2227c5fa02d5fd74c1b8c17120cc725bbafb4d95e7da726f2122afd` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 208 | Valiant (v4.3).gba | `c0ca353923d90176baeaf9f5b9ae9d80d75c3f83a329babf4cd3225bdf0067c5` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 209 | Vega.gba | `88e341912c8aef2978b5e45567de48ffd35f024e8bc64787dc8431c68d8ce1a2` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 210 | Vega [English Translation] (20200823).gba | `cbf11e64de453c0ec2bb0e73826e38f9df0ddcb6deb3eee857e490e1f8a98bb8` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 211 | Voyager (Battle Frontier Demo) (v1.1).gba | `1108eccf0fa9c843d8dff81aeaff9650d5c51979a479c4ab8217e8786dbed474` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 212 | Voyager (v0.3.6).gba | `c68f5d2932f605ac578a1e49000b522e19371e304d1ac8babf0d9d0fee315b14` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 213 | WaveBlue (v1.9.4).gba | `9c66085c61cfe41b6a8d39c160f09d53a4470dd56404989f2932a65f59b30d59` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 214 | Wish (Demo) (1.02).gba | `824914e66abca5ae126e78a7c3fc9568cf9925edc53647683057bbd898c27f93` | SELECTED | FIRERED_LEAFGREEN | yes | 0 | SELECTED |
| 215 | Ambrosia (v2.6.2).gbc | `ab13dacc1b79ac5d6d78c687086185609069a4c7d1cb199afa2a653f54304f44` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 216 | Anniversary Crystal (v1.2.3).gbc | `638dfbf61aa7a6e0bf1dcf75518dd69ed9e2f038f1dc09ab318ef4bbcdc29f5c` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 217 | Black & White 3 - Genesis (24.12.23).gbc | `b43cb68c9a625e67a99c642ec43ddc25df96ca4388d38a3b3a05d21a627043f9` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 218 | Blue Kaizo (19.06.26).gbc | `c20ed7899b5d967f8001b513a4317e7dd0e76e3d78b23627897f95e8700157bf` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 219 | Blue Kaizo (Christmas) (19.06.26).gbc | `650ab7eedabbb3e42ef5a0541f667a9632fe2bed4e9824fa01425c93c538ecd9` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 220 | Blue Kaizo (Yellow Colors) (19.06.26).gbc | `c2b2712802430b4db42e6abdb90eb65a252a22b696fa75901cf3d925c3614781` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 221 | Celebrations Blue.gbc | `aaa1cdd20f7e09c83cec19c2a7c9cead6e2ebdd24d30ba9a6ec6ee2c6f342094` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 222 | Celebrations Blue (CrysAudio Gen 2 UI).gbc | `87091333839d59121c81280632036f52777c5974610fff7a2a954d0757d5a3ab` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 223 | Celebrations Blue (CrysAudio Snowy Gen 2 UI).gbc | `98c1b10ff046fc18134948cd10cd891a07b31475fcb64372807e1387ced27aa2` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 224 | Celebrations Blue (CrysAudio Snowy).gbc | `cbc33bfe8817aef00ae41b07923ab4525e1591b4dff82aba87980ba01d28c744` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 225 | Celebrations Blue (CrysAudio).gbc | `b72c163d68d5dc47efb8a42f9d16226a385c6e9b44cfa346ed0008946c9b5161` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 226 | Celebrations Blue (Gen 2 UI).gbc | `d7f41f22a7690a252b7fffcbef52c450a9d013e429ab30917cb5665c23d1a707` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 227 | Celebrations Blue (Snowy Gen 2 UI).gbc | `07d13b5fbe89427419f9ea1355303b7f5d1f3cc20911b8855c5957bfcedaf4d7` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 228 | Celebrations Blue (Snowy).gbc | `7c42b68c64b67123d90ff07133087a8f1e23ac14f7fd9e155979d293499a200a` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 229 | Celebrations Green.gbc | `70e79c4a227f567fbcfc5c3c3f99a87541474af612698b9d45d770d85c63a01b` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 230 | Celebrations Green (CrysAudio Gen 2 UI).gbc | `e376ca33a37b07f7bcb438a8d3ca409dcf34be9c32d33c20bfbf007ba88a98d9` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 231 | Celebrations Green (CrysAudio Snowy Gen 2 UI).gbc | `e0a2e847a6c2720038c2fe435d70fd8b7291103b6bacccd5d665fdac9194c8e9` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 232 | Celebrations Green (CrysAudio Snowy).gbc | `8c5fd8efe1dc852992d84c298bb55a6118e69431cc7a65a37e834b90963225e5` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 233 | Celebrations Green (CrysAudio).gbc | `5388dfdbe4e8234a926a88cd31801a7b71afab3b2e4987a2d66bf103665d1916` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 234 | Celebrations Green (Gen 2 UI).gbc | `7b3ed722bfeb84b7b048c42b76621f251b56eb6bd66fb08f104f829fab95d2fd` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 235 | Celebrations Green (Snowy Gen 2 UI).gbc | `cc205eb97b3f21307e5d389794ff4c438bb10794465083a958f19e3cb36e5b1f` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 236 | Celebrations Green (Snowy).gbc | `dd1912d8454b5e663cb522a2ba96e1315b0e55b191e8e26c0d68c7ede0ef19de` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 237 | Celebrations Red.gbc | `f30569f7cbb79fd24ffd97b8a49e8e37bf12066d4042d58d1f6597faa199538c` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 238 | Celebrations Red (CrysAudio Gen 2 UI).gbc | `4bf0e81dab319e3be356c099240de6cf3feeab6b34b63cde519eac4124246c2f` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 239 | Celebrations Red (CrysAudio Snowy Gen 2 UI).gbc | `503795c297df28711bc7c407928176754647c97301db1224c133b5e784fa59ab` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 240 | Celebrations Red (CrysAudio Snowy).gbc | `5bc8011757d8130a5cfc9633080672420d9f922d0c38675feb0bbb78da66411d` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 241 | Celebrations Red (CrysAudio).gbc | `011fc16fc3abce2308ed2ee9617b30e0cbec45a1bd820de5642ca20e1caff99a` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 242 | Celebrations Red (Gen 2 UI).gbc | `3447c6eeb08dee5bbc9974a4e46c61cb275f5dd343c4aa433b31c5c20e61a8bc` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 243 | Celebrations Red (Snowy Gen 2 UI).gbc | `1ed97207d6dfce26dee10a9ed53846859808377198b0de444320d79f16638ce4` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 244 | Celebrations Red (Snowy).gbc | `d9e840ff23cefbae511b1b94d58cc7b5af293a6612fa29ebf22deee5e77caaf3` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 245 | Crystal Clear (Color Filter) (v2.6).gbc | `75257f71c5fbccd5313d6328f85bf10867aaa3bb203e704f503a149a59100e0a` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 246 | Crystal Clear (v2.6).gbc | `c4585682d26c0bae3eb424f064806739839ee8e537735a19a5e16703fa86e32a` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 247 | Crystal Inheritance (v1.0.5).gbc | `ae687d82f61cd7e176c7c27e4022e7457464bf4f66592a691ba69201a6d22c6e` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 248 | Crystal Kaizo (01.08.26).gbc | `a365d80e1a8a946f5fc584c1dc81f17883e259f6c4dcdd94f27f919ae4a33830` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 249 | Crystal Legacy (06.01.25).gbc | `18153207488a9e2b4837d677ec9f1240dc2674a29dd6a0319553b73cafccceaa` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 250 | Crystal Legacy Timeless (v1.1.3).gbc | `ffe7eb57beb0eab1ef5f8a07a26d56c38774cca102d5991cd7eaf4586e0f5cd1` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 251 | Digimon Crystal (v11.02.24).gbc | `a333699c83a7a9adaf62c942aa8215e9a0f3d9d598c62a96093fc671781113ae` | SELECTED | CRYSTAL | yes | 1 | SELECTED |
| 252 | Fools Gold (v1.3.2).gbc | `770c9b7dfbaf7094b4b660d98f61d425ea73efc2a5f614264c344b90c55957bb` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 253 | Gold 97 Reforged (v6.1f).gbc | `5e0c4688abd5ce2cb00d76902301791d5dfd196a99ff1e764268dffb196c50c3` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 254 | Intense Indigo (Blue) (28.07.26).gbc | `a5692ea0aa361a38bf3fc7611e2147f9837c868a9fe5f6a37fba4044795e4487` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 255 | Intense Indigo (Blue) (Gen 2 UI) (28.07.26).gbc | `fc323b88d64731d1534e044aaea8f685e502d29a871f89905f19eb75eb3b0ff9` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 256 | Intense Indigo (Red) (28.07.26).gbc | `b91a95b1110e5424454300ee1d7c649fd6efec8e7acf699e3162f44894cf4cf6` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 257 | Intense Indigo (Red) (Gen 2 UI) (28.07.26).gbc | `c9264aff02ba5a7af903693069055864bb2898639738a1d1fab43a03eb292b18` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 258 | Intense IndigoLite (Blue) (28.07.26).gbc | `ba3463346c094af5bfc121509164940b44fec6ccddd07373f4a0a8ea16d3a794` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 259 | Intense IndigoLite (Blue) (Gen 2 UI) (28.07.26).gbc | `c2370a7a626d5613be78e3729b241ccab4d6208f382121b683506ff8e4e6cefb` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 260 | Intense IndigoLite (Red) (28.07.26).gbc | `a318fadf69e04fd9fafb19ded60bc0f06efb63e3a87e38481f2ea6ba54475dc4` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 261 | Intense IndigoLite (Red) (Gen 2 UI) (28.07.26).gbc | `bc1e21711d9c40ace629d89f4388719e350eec5275b016dc192a40e9bab20989` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 262 | Kalos Crystal (v1.0).gbc | `7cd8957e47a04bf0542de5d6a65affb369704e85ce11e03022be491be7dc1050` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 263 | Mystic Crystal (v1.0).gbc | `8214da6c2ae6aeee20e28ef101604df7837fc94284d93f31c43cc8feaeef0012` | SELECTED | CRYSTAL | yes | 0 | SELECTED |
| 264 | Orange (Suloku Patch 2026.0.2 PSS).gbc | `037f5ba913953f2387175c5e0549347d162ef3b224d25660e8055acdac4564be` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 266 | Peridot Version (v2.3.0).gbc | `22dc749a396a353d6724aa96e28d4fef89d4b89e38e594852c8930c3c4a7f160` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 267 | Pinball Generations (v1.5).gbc | `e28c3a998483cb9b64a7cf1147f6a5a5dae1bfc4db741b4f09b3a66f0312c2eb` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 268 | Polished Crystal (29.07.26).gbc | `f26129ae2ed11e1f2dfa3d4cb2420192709e7061099363ae93ff543b2973f15f` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 269 | Polished Crystal (Faithful) (29.07.26).gbc | `82ceb011339820298fd47551a41b43d778dafafaaaa8907e488ec0c3f508c97c` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 270 | Prism (v0.95.0254).gbc | `dea0729edbc5923f1af1fbd9c79e56cd7ff255642bca12f7fb146d506196077d` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 271 | Red Kaizo (19.06.26).gbc | `b07b6b224363c3d9c110966572888bc611f876ba07be6e0799309beedfd39214` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 272 | Red Kaizo (Christmas) (19.06.26).gbc | `3dddd26bc73b99a6f9071fa4b10372e762888133260e6ebc73ad93827a36f783` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 273 | Red Kaizo (Yellow Colors) (19.06.26).gbc | `7c3c2585a9e3a602a9fe91f6c45908cd26cad23b105bf43040d3e0ebdb140178` | NO_FAMILY_MATCH | - | - | 0 | HARD_GATE_REJECTED |
| 274 | Silver 97 Reforged (v6.1f).gbc | `6d491ec85788e967aface80b61f91936bd84deb9239fef1d010f93962fe58828` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 275 | SourCrystal (30.07.26).gbc | `d698e6d48b324beaab43f99ea009d27fb52f4c8aae326a4afa805c996dfef698` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 276 | SourCrystal (GB Tower) (30.07.26).gbc | `5cca1c76899570625fa734ee6935c6d32022357dcaceb930f6f8ce683572380f` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 277 | SourCrystal (VC) (30.07.26).gbc | `4ff14ef1ccc550fca4ecd39ba161bcc06c9ddd27a83b01711376204744ce0896` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 278 | TCG - Generations (1.7.2b).gbc | `c171ee7d5aa5830fb130af6a837172eef79fc28928ac9af8d0498d6a0316021d` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 279 | TCG - Neo (Legacy) (v1.43).gbc | `d4cf20a84964f7ad58ac07089b23e37fd9d291116340965f1b170dcdb1cb7cd3` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 280 | TCG - Neo (v1.43).gbc | `4d860eae2555c1bbe54dde2b6cc5f3e6fac818d22379c2fc295320f0d65c4a3f` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 281 | Anniversary Red (v4.7.3c).gb | `90bb900570cf4b1c0835b2c51b66984a727d43ac4aa16d84b3a9fb89dd03f1c0` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 282 | Beyond Blue (v1.4.4).gb | `33eacb9917498505ac0dc669323d506cde7ee4c2e3a17a80ca2f9d8944ef217c` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 283 | Beyond Red (v1.4.4).gb | `3640ed0493287136cd9321cb3428f44113e87354cf90402665ba60e41c8fc61a` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 284 | Blue (Gen 2 & CrysAudio) (19.06.26).gb | `8646f33d689d46849b0909113a9c3992dafbed040498d6a1a4feb4dac9aaf472` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 285 | Blue (Gen 2) (19.06.26).gb | `379e4e9dee40e374eea5d9538e5f928537d8fe2c3baefa4daf7320335c90a7c9` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 286 | Blue (Yellow Colors Backport & CrysAudio) (19.06.26).gb | `ca73df9ca3eb3893c2b33d4d522eba6f69d589c290c5e8f5cb48ec808028f855` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 287 | Blue (Yellow Backport) (04.07.26).gbc | `3b6ea64e3d7c82c83bd800ee400da8c8ee6b02131af15217150316379d61ced5` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 288 | Brown (v6.1.2).gb | `33ca3bd175c35a770662c65e1519aa05778c6182bb71112cfbfecc2f90558af0` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 289 | Celebrations Blue (CrysAudio) (11.05.26).gb | `45a1f65aad09183a0b6fd3326f083ac735b30dee54577d398e06faeeaa4318d4` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 290 | Celebrations Blue (Custom GFX & CrysAudio) (11.05.26).gb | `de217ee2b0ef728dc4a7c81e09bc420ab2be57cc5ea91488e6b64f4bf730ec8b` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 291 | Celebrations Green (CrysAudio) (11.05.26).gb | `9dbe725c2b69bd90340ae069c771977053224237b8dcd7d77ba36b74ef5015c0` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 292 | Celebrations Green (Custom GFX & CrysAudio) (11.05.26).gb | `4dd3625563480ec06285074fc4e517c2b8051546cb613535e73108442838a3f0` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 293 | Celebrations Red (CrysAudio) (11.05.26).gb | `2b6d30c400792c9e7ae1dfaa76b58ab001ab6a847bb0a50d642dd8d9bf0d0f69` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 294 | Celebrations Red (Custom GFX & CrysAudio) (11.05.26).gb | `9f2f3d551b69b8891e84952c5494c04238dfddc93ce484ce762354fe0d7dd270` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 295 | Celebrations Blue.gb | `b8553ff16fe7210551b54289b46173ca06f14e1599ec1090e2f2ec6418c4dade` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 296 | Celebrations Blue (New Sprites).gb | `76157231979f113ae9b1318f1dc1e016f8989b209142a52230b7b29e40c3308c` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 297 | Celebrations Green.gb | `ca0136ebd44b36a0b42ff9567b450ff0093dc5a56c410dcb078e9f9a99266dbe` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 298 | Celebrations Red.gb | `aa9d23a6a4b230ae1533c190868a3c3dc37c1f3dfb2b0210e306eb9a6d294e6a` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 299 | Celebrations Red (New Sprites).gb | `3addcc11b3342cd88f832bb1c727fd8119a654f57cea4dca68a3d435c6c5973d` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 300 | Celebrations Green (New Sprites).gbc | `18b303805d805b802f8d5f84f3a6721b45e4e8cf607874a7982f94d02f9f77e1` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 301 | Grape (Final 1.7).gb | `082154ea8e4cc24efc0a7b460eb2e1314a0deb411749649201626e82b3c7d609` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 302 | Intense Indigo (Blue QOL) (28.07.26).gb | `3cdd39af5cb0a3018c46898021fc5d1cc003d6bec24691d2da2cca983e237624` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 303 | Intense Indigo (Blue) (28.07.26).gb | `1dd7b9950b00c7fb829f8fad5aad7ccaa8081297ec0ebc5d480e88efc026dab1` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 304 | Intense Indigo (Red QOL) (28.07.26).gb | `693a9b99d814868b7f226062a7d781e70439509d6548395c1a65c90aca8c0cb1` | NO_FAMILY_MATCH | - | - | 0 | HARD_GATE_REJECTED |
| 305 | Intense Indigo (Red) (28.07.26).gb | `c64428f740559383711a2db229acf29d66ce2f5844e8574aa909f17e77378465` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 306 | Intense IndigoLite (Blue QOL) (28.07.26).gb | `2878f9f4d77505f034ec71466f22b23b322dc41a1b410677cac8fae5d4b938e6` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 307 | Intense IndigoLite (Blue) (28.07.26).gb | `223e7a3214e4f76616285e7992d1de96047d61aaf9d7d997fe159818b1b54e5f` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 308 | Intense IndigoLite (Red QOL) (28.07.26).gb | `ab6e7a69dd2fa8e08b2f217a6e3eb4900fe7d398f210170e192de4b5d0d90584` | NO_FAMILY_MATCH | - | - | 0 | HARD_GATE_REJECTED |
| 309 | Intense IndigoLite (Red) (28.07.26).gb | `e2f6165f7102d93f6264af389a35f3ede5f0469aaf797059487cfe07b1586dae` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 310 | Blue Kaizo (19.06.26).gb | `b2b97f1a5b63288333ef04928000a1362e7ce50eb56141d53f2f219dbd2277b7` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 311 | Red Kaizo (19.06.26).gb | `6bea33d6ad6e7873ecf6cd1e8ebe5eab4323ce840fa8f10657144e25641bfe61` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 312 | Yellow Kaizo (1.0.3) (QOL).gb | `77a75d95b6684e62ac8fa22da110a74e1733e2e80f3d2907679ac6c719662aad` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 313 | Yellow Kaizo (1.0.3).gb | `d3e6dee2816770d2f4f8c08c0c30566c28891f3684a6a58999926beb61a14211` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 314 | Nova (v1.0.2).gb | `9ff825918dfb23d04dd35bfd92c6790a8b7c8596b93223ea1858758b24e7dad6` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 315 | Pink (v1.0.7).gb | `c5315361318ac9286131350b3d51e618eb3dfc6a44748072db63d6917476b6ff` | SELECTED | YELLOW | yes | 0 | SELECTED |
| 316 | PureBlue (05.07.26).gb | `672ddf7dceabb431121a98321a4a144bcdcbc38ecc75ed554c6f36731c0590b3` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 317 | PureGreen (05.07.26).gb | `85783be819b69bf24c1a333a84d366e877c64599342a37d4e18674212f7187be` | NO_FAMILY_MATCH | - | - | 0 | INSUFFICIENT_INDEPENDENT_ANCHORS |
| 318 | PureRed (05.07.26).gb | `13975f9b48ed63858106528e6eeb62354fb1455a44a5a8279ed464ffd8de1bf5` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 319 | Red (Gen 2 & CrysAudio) (19.06.26).gb | `54c25f6a645ac7c037e35eff97eab270caace00b826848fa0e800961c8f73a0c` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 320 | Red (Gen 2) (19.06.26).gb | `d4db1be185eaf6b4e5890b305f9a1bada25f8db90a16249d210ef9b2b86836b9` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 321 | Red (Yellow Backport) (04.07.26).gbc | `f664cede8f06127673dd14bbfb41dab0be10ab5dd87bac1f4eb5c12132d976e7` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 322 | Red (Yellow Colors Backport & CrysAudio) (19.06.26).gb | `53b1a93c415a6b29cadeaa1f83903c5eefb6743524ac49ee5271ba671fd15fd0` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 323 | Red++ (v3.0).gb | `f244f8c31ff3dfa907b6730fce410ba96f74bc1f920bb318c7065288fa13fc3b` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 324 | Red++ (Hard Mode) (v3.0).gb | `f207d55284b44ba8d5db3701758fbe8f7197147c6419145a8fa4710215ef319b` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 325 | Regulation Blue (19.11.23).gb | `d775dae4e402192bdc59df6a4ec2aae0e6c9147386c8f50d68450027de9e5ec0` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 326 | Regulation Red (19.11.23).gb | `d794d715c6ed2946f684808cbb00071f99a391514baaa8e946f28c731d3e4e5b` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 327 | Shin Blue (18.03.26).gb | `25e39e5ef5ef0de0f7faf481827927a4033ac1d31782a2b9be9a8412d8fd1158` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 328 | Shin Green (18.03.26).gb | `c99d737043ae5cbb60f1dd90c2376098a13a7abe393c61c10f8b2204a0cce85b` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 329 | Shin Red (18.03.26).gb | `024a1c4dab1b12d0b963c6cf756d2c1082de0ccd53fe31384787dcf34edef718` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 330 | Static Yellow (25.07.26).gb | `e717d046385eda532a265964720be54b6770f0d06d93b054a9757e35ebb7266d` | SELECTED | YELLOW | yes | 0 | SELECTED |
| 331 | Unova Red (18.03.25).gb | `8d0a4ec1929027c8aab79b5706d90d348d4bbf280160c582b6b9b1bc1d4a83f3` | SELECTED | YELLOW | yes | 0 | SELECTED |
| 332 | Unova Red (Vanilla + QoL).gb | `e3ae2e8726cdcaf8bc149ddc2c97d425c99604504b77ff93a4caa544756b4294` | SELECTED | RED_BLUE | yes | 0 | SELECTED |
| 333 | Yellow Legacy (17.03.26).gb | `b742764b64dd983a7efaee1f926a1475c801166b405ec7d388ba19c241730929` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
| 334 | Yellow Legacy+ (08.09.25).gb | `a4d90dd1b23e008bdae717777b379cb1abb3c3d2aaf671a05bf3e46c82e50f41` | NO_FAMILY_MATCH | - | - | 0 | BELOW_SELECTION_SCORE |
