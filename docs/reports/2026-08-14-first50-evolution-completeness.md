# Exact first-50 evolution completeness

Source commit: `5d77a9d2e0de04fd2e7c3caf3811bfa4828238f9`
Manifest SHA-256: `7146d2410231febfba550470d62ac179ef8c532d94bc15d2365211f862d03d5f`
Raw evidence SHA-256: `7f811711f518eba4a388bd693128a919ce50130c07faf9f308977c46e9729a64`

## Result

- 50/50 identities expose a complete, available evolution catalog.
- 0 malformed evolution rows.
- 50/50 semantic edge maps are deterministic across two fresh parses.
- 50/50 catalogs persist and reopen from SQLite with the same semantic edge hash.
- All 44 catalogs that were already complete preserve their exact prior semantic hash.
- Six previously incomplete catalogs are now complete.

A row counts as complete only when the catalog-level `EVOLUTIONS` capability is `AVAILABLE`, every navigable species has an `AVAILABLE` evolution field, the typed table has every selected row with no malformed row (or the integrated species-record path exposes the same complete fields), two fresh parses agree, and the SQLite round-trip agrees. `NOT_FOUND` and `NOT_APPLICABLE` do not count.

## Exact matrix

| Row | ROM | Family | Resolution | Selected rows | Navigable | Edges | Semantic SHA-256 |
|---:|---|---|---|---:|---:|---:|---|
| 1 | A Grand Day Out.gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 386 | 184 | `a888305760764bd75177d100297e58f3b47aa7ab554794faa869d095d97563ff` |
| 2 | Advanced Adventure (2021).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 410 | 192 | `a2ba88c8956366c836f1ea6e89e7deee8200870e15144ceb9bd03b62103e103e` |
| 3 | Adventure Red Chapter (Beta 15 + Expansion Fix C).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1460 | 1406 | 1385 | `5031d7c4faf457d8c3928cc898b981a607e81fd99a35d8785d47429259139514` |
| 4 | Aesthetic Red (DS Font & Sprites) (Faithful Version) (v1.2).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1268 | 1240 | 741 | `86123942036c609d903e9a074b55a1dcd9b7bbe2d7c2ad1e07e3e076af690c5c` |
| 5 | Aesthetic Red (DS Font & Sprites) (v1.2).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1268 | 1240 | 741 | `86123942036c609d903e9a074b55a1dcd9b7bbe2d7c2ad1e07e3e076af690c5c` |
| 6 | Aesthetic Red (GBC Font & Sprites) (Faithful Version) (v1.2).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1268 | 1240 | 741 | `86123942036c609d903e9a074b55a1dcd9b7bbe2d7c2ad1e07e3e076af690c5c` |
| 7 | Aesthetic Red (GBC Font & Sprites) (v1.2).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1268 | 1240 | 741 | `86123942036c609d903e9a074b55a1dcd9b7bbe2d7c2ad1e07e3e076af690c5c` |
| 8 | Aesthetic Red (Music & Graphics Only) (v1.2).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 386 | 184 | `6f6eba8a60c49e4c4406470acad200af3325a9e1f231e836a91412be3472218d` |
| 9 | All In (v1.0).gba | EMERALD | TYPED_ROWS | 412 | 386 | 184 | `288570691118cef73bc4df2d54f85af8b61645595145fc196d1f2b233a56e04b` |
| 10 | Altair (2019-06-13).gba | EMERALD | TYPED_ROWS | 412 | 385 | 106 | `4a569a1e4f4caac818c1f699718b5fcbe94b65874329801ad173cf4f64d737a4` |
| 11 | Altered Emerald (v4.2c).gba | EMERALD | TYPED_ROWS | 474 | 426 | 269 | `82a555fa52aa3934e9287f3e7be304e2b0f3f36b94a7dc34b4f1640cc42e3b82` |
| 12 | Amethyst (v1.3.0).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1268 | 894 | 588 | `d78521b0e31d817540310021f466fb33eb787183fa2fabd7c5ad0b106f8bc36f` |
| 13 | Amnesia (Save Fix).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 386 | 184 | `f186a78a48f4ef41ca7059d65cab5747a298cddb6c1db4b7ccfa91dc0c4c53be` |
| 14 | Arcoiris.gba | RUBY_SAPPHIRE | TYPED_ROWS | 412 | 386 | 184 | `6f6eba8a60c49e4c4406470acad200af3325a9e1f231e836a91412be3472218d` |
| 15 | AshGray - Newerest Edition (v1.0).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 389 | 184 | `91a6dc07e740b533e1cf046f43ddb023658601932bfdc0506c03941738b7bd9d` |
| 16 | AshGray (v4.6).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 389 | 184 | `91a6dc07e740b533e1cf046f43ddb023658601932bfdc0506c03941738b7bd9d` |
| 17 | Battle Theater (V2.3.0).gba | EMERALD | INTEGRATED_SPECIES_RECORD | 1573 | 1571 | 664 | `b1e3ad69eba823bcb2376c86f0a6ffff8ccdc7594da3975792549db1a8e407cb` |
| 18 | Bill's Secret Garden DX (v2.0).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1540 | 1509 | 852 | `ce5257f32aef49b66f1d571ef8804ac7333e837e471720608f17cf16de165a33` |
| 19 | Blazed Glazed (v1.3).gba | EMERALD | TYPED_ROWS | 412 | 411 | 226 | `d219b7ca0e0731d5d92b05fe78e8279798c16b191afdb4dade4713cda12fba80` |
| 20 | Blazing Emerald (v1.6).gba | EMERALD | TYPED_ROWS | 412 | 410 | 213 | `61d98d062c60693ca5959176ef7805158ee8e6561813d105d62947e19ce00735` |
| 21 | Bronze (Girl Patch) (v1.23).gbc | GOLD_SILVER | INTEGRATED_SPECIES_RECORD | 251 | 251 | 122 | `75ea52dbb1d3ceaf3bf3a89e51a2b3ec3e1d341db830117d15b527eefdd2e633` |
| 22 | Bronze (v1.23).gbc | GOLD_SILVER | INTEGRATED_SPECIES_RECORD | 251 | 251 | 122 | `75ea52dbb1d3ceaf3bf3a89e51a2b3ec3e1d341db830117d15b527eefdd2e633` |
| 23 | Bronze 2 (v1.05).gbc | CRYSTAL | INTEGRATED_SPECIES_RECORD | 251 | 251 | 122 | `5a78106699266c4789ac169e6d205527205729078a0004da7d4b25a3dd1f01e1` |
| 24 | CAWPS.gba | EMERALD | TYPED_ROWS | 412 | 386 | 184 | `55648fce8d916a9941a8dace1f6437c6f6d21dd5c8c2125225a2e4b31e8d1967` |
| 25 | Celia's Stupid Romhack (1.1.4).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1301 | 385 | 219 | `718ab0eecca5eeec77f23af1a7a9e1ed86256f9c95f6c481df3c6c469a3f7413` |
| 26 | Chaos Black (Fixed) (v3.1).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 386 | 184 | `3d4581eee88140466a70762751a9e902a8d282fd7a9efa6f1c7894fd8de79771` |
| 27 | Chaos Black Recreated (2026-01-25).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 386 | 211 | `4b63bd1a88a7acc8c49bed40ca4b51d345a2b43bf432d517cef700d2f656b8c6` |
| 28 | Chronicles of Soala (v9.0).gba | EMERALD | TYPED_ROWS | 412 | 386 | 184 | `a888305760764bd75177d100297e58f3b47aa7ab554794faa869d095d97563ff` |
| 29 | Classic (v1.5.0b).gba | EMERALD | TYPED_ROWS | 429 | 403 | 190 | `2f6437ba40dec067aa2af4c522c6d0ae90d065b7936285fd1b71905e045ebb7f` |
| 30 | Cloud White (v523d).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 546 | 492 | 299 | `fa6c856af6f5c267403babdaa9cdcabbef091833dc64f41db70f615d51cdd159` |
| 31 | Cloud White 2 (v279).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 997 | 943 | 468 | `eece3e72b13c2d81436ad9d090ec76617c4dafa3bca48286b50f1bbb4d3882ff` |
| 32 | Cloud White 3 (v277).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 997 | 943 | 470 | `f1eba357e0976a3092903f4020aef238e2e6499b3601393b4474646b61094f0c` |
| 33 | Clover (v1.3.3).gba | EMERALD | TYPED_ROWS | 412 | 387 | 178 | `5999acc0715cb6b2669d27d9c07922d7be6548ecfdb016544c4f517791a000a1` |
| 34 | Crippling Medical Debt Edition (v1.1).gba | EMERALD | TYPED_ROWS | 1528 | 1525 | 652 | `6b1edcde61b170c4196c9afc4142c167674763dd951adc57a812f2636a11f239` |
| 35 | Crown (v1.9).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 386 | 184 | `6f6eba8a60c49e4c4406470acad200af3325a9e1f231e836a91412be3472218d` |
| 36 | Crystal Advance Redux (7-8-26).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 760 | 699 | 273 | `b23af1252729a75af9c0648036452e639e91f83e58f1c226b7bd139f04b1d6bf` |
| 37 | Dark Cry - The Legend of Giratina (v2.6.7).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 408 | 218 | `b3b9df3824c6ad6acd823186e1f0bd3a4b73abcba461a45f127f7386d1f6f8a7` |
| 38 | Dark Energy (v5.01).gbc | GOLD_SILVER | INTEGRATED_SPECIES_RECORD | 251 | 251 | 127 | `aaeff184f1f57fac9f2c553f2ef33687c9b12f3434225945c11cd70698d8daf0` |
| 39 | Dark Rising - Order Destroyed.gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 386 | 170 | `4c5a0c418f96aa9e6be45375de357260c5a04f6a89ed835837700113ae3ccef9` |
| 40 | Dark Rising 2.gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 411 | 195 | `625bba2e7a344765789cf07d4f882974818b5101fab844fa05242967bf7d6597` |
| 41 | Dark Rising Origins - Worlds Collide.gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 411 | 252 | `4ce0c6404f97ad640de3ec6b15ad79d1c69ebcabc3f46cd2573adb351fb16770` |
| 42 | Dark Rising.gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 411 | 204 | `ca328431a01a9f120550b257425bcbc8017f5cc68ad9966704e418559ec49bb5` |
| 43 | Dark Violet.gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 366 | 151 | `3dce97bd2242a0dab5aec7f05fc85d6b4ce2cbe8f75f83978588f2dc8107ffe1` |
| 44 | Dark Violet (Fan-Patch).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 412 | 366 | 151 | `3dce97bd2242a0dab5aec7f05fc85d6b4ce2cbe8f75f83978588f2dc8107ffe1` |
| 45 | Dark Worship.gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1299 | 1236 | 662 | `6e9c9700de4cad469bc6d9619e94150eb892a565f4475f8e9ecf06c4195c95a5` |
| 46 | DarkFire (v2.1.3).gba | EMERALD | TYPED_ROWS | 494 | 493 | 269 | `f449a971b4c63977b8d35d500f47d14adcbf6afd735acffd045983ff3d73b806` |
| 47 | Delta Emerald (v1.1.5).gba | EMERALD | TYPED_ROWS | 540 | 486 | 350 | `68f9b5f010232a296b7f5c7750e3320ba37d2e5b2b368d800ffed3f725cfbb20` |
| 48 | Dragonstone (v1.63).gba | RUBY_SAPPHIRE | TYPED_ROWS | 412 | 386 | 184 | `22dd815fc5234c46cf3420945c6c75e7d3fcee5926a6740a8d708544d56e79fa` |
| 49 | Dreams (v1.5.3).gba | FIRERED_LEAFGREEN | TYPED_ROWS | 1166 | 1139 | 607 | `4d93afbcede39f2502b0b8621153044fb68c1b1b73d207216d08ca5ac208b467` |
| 50 | Dreamstone Mysteries.gba | EMERALD | TYPED_ROWS | 1525 | 1522 | 631 | `1aac9d884eee5025b25e7ad8c916eeec3867ba39ba9d99fb47e8973083884c9d` |
