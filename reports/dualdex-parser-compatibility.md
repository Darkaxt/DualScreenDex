# DualDex ROM parser compatibility

This report contains structural parser evidence only. It contains no decoded Pokédex text, sprites, or ROM bytes.

## Summary

- Inputs evaluated: 20
- Selected: 14 (11 exact official, 3 structurally selected derivatives)
- Complete for implemented core datasets: 11
- Selected with partial core datasets: 3
- Ambiguous: 0
- Unsupported: 6
- Read/parse errors: 0
- Selection rule: score >= 75, runner-up margin >= 10, and at least two validated anchors

## Capability matrix

| ROM | Status | Family | Profile | Score | Names | Types | Stats | Moves | Move data | Type chart | Sprites | Abilities |
| --- | --- | --- | --- | ---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| Pokemon - Modern Emerald Version v3.5 (USA, Europe).zip!Pokemon - Modern Emerald Version v3.5 (USA, Europe).gba | SELECTED | EMERALD | Pokemon Emerald (USA/Europe) | 75 | yes | - | - | yes | yes | - | yes | yes |
| Pokemon - Sword and Shield Ultimate Plus (USA, Europe).zip!Pokemon - Sword and Shield Ultimate Plus (USA, Europe).gba | SELECTED | FIRERED_LEAFGREEN | Pokemon FireRed (USA) Rev 1 | 100 | yes | yes | yes | yes | yes | - | yes | yes |
| Pokemon Mystery Dungeon - Red Rescue Team EX (USA, Australia).zip!Pokemon Mystery Dungeon - Red Rescue Team EX (USA, Australia).gba | UNSUPPORTED | - | - | - | - | - | - | - | - | - | - | - |
| Pokemon Unbound.zip!Pokemon Unbound.gba | SELECTED | FIRERED_LEAFGREEN | Pokemon FireRed (USA) Rev 1 | 100 | yes | yes | yes | yes | yes | - | yes | - |
| Pokemon - Blue Version (USA, Europe) (SGB Enhanced).zip!Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb | SELECTED | RED_BLUE | Pokemon Blue (USA/Europe) | 100 | yes | yes | yes | yes | yes | yes | - | - |
| Pokemon - Crystal Version (USA, Europe) (Rev 1).zip!Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc | SELECTED | CRYSTAL | Pokemon Crystal (USA/Europe) Rev 1 | 100 | yes | yes | yes | yes | yes | yes | - | - |
| Pokemon - Emerald Version (USA, Europe).zip!Pokemon - Emerald Version (USA, Europe).gba | SELECTED | EMERALD | Pokemon Emerald (USA/Europe) | 100 | yes | yes | yes | yes | yes | yes | yes | yes |
| Pokemon - FireRed Version (USA).zip!Pokemon - FireRed Version (USA, Europe) (Rev 1).gba | SELECTED | FIRERED_LEAFGREEN | Pokemon FireRed (USA) Rev 1 | 100 | yes | yes | yes | yes | yes | yes | yes | yes |
| Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).zip!Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc | SELECTED | GOLD_SILVER | Pokemon Gold (USA/Europe) | 100 | yes | yes | yes | yes | yes | yes | - | - |
| Pokemon - LeafGreen Version (USA, Europe) (Rev 1).zip!Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba | SELECTED | FIRERED_LEAFGREEN | Pokemon LeafGreen (USA/Europe) Rev 1 | 100 | yes | yes | yes | yes | yes | yes | yes | yes |
| Pokemon - Red Version (USA, Europe) (SGB Enhanced).zip!Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb | SELECTED | RED_BLUE | Pokemon Red (USA/Europe) | 100 | yes | yes | yes | yes | yes | yes | - | - |
| Pokemon - Ruby Version (USA, Europe) (Rev 2).zip!Pokemon - Ruby Version (USA, Europe) (Rev 2).gba | SELECTED | RUBY_SAPPHIRE | Pokemon Ruby (USA/Europe) Rev 2 | 100 | yes | yes | yes | yes | yes | yes | yes | yes |
| Pokemon - Sapphire Version (USA, Europe) (Rev 2).zip!Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba | SELECTED | RUBY_SAPPHIRE | Pokemon Sapphire (USA/Europe) Rev 2 | 100 | yes | yes | yes | yes | yes | yes | yes | yes |
| Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).zip!Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc | SELECTED | GOLD_SILVER | Pokemon Silver (USA/Europe) | 100 | yes | yes | yes | yes | yes | yes | - | - |
| Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).zip!Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb | SELECTED | YELLOW | Pokemon Yellow (USA/Europe) | 100 | yes | yes | yes | yes | yes | yes | - | - |
| Pokemon Mystery Dungeon - Red Rescue Team (USA, Australia).zip!Pokemon Mystery Dungeon - Red Rescue Team (USA, Australia).gba | UNSUPPORTED | - | - | - | - | - | - | - | - | - | - | - |
| Pokemon Pinball (USA, Australia) (Rumble Version) (SGB Enhanced) (GB Compatible).zip!Pokemon Pinball (USA, Australia) (Rumble Version) (SGB Enhanced) (GB Compatible).gbc | UNSUPPORTED | - | - | - | - | - | - | - | - | - | - | - |
| Pokemon Pinball - Ruby & Sapphire (USA).zip!Pokemon Pinball - Ruby & Sapphire (USA).gba | UNSUPPORTED | - | - | - | - | - | - | - | - | - | - | - |
| Pokemon Puzzle Challenge (USA, Australia).zip!Pokemon Puzzle Challenge (USA, Australia).gbc | UNSUPPORTED | - | - | - | - | - | - | - | - | - | - | - |
| Pokemon Trading Card Game (USA, Australia) (SGB Enhanced) (GB Compatible).zip!Pokemon Trading Card Game (USA, Australia) (SGB Enhanced) (GB Compatible).gbc | UNSUPPORTED | - | - | - | - | - | - | - | - | - | - | - |

## Per-ROM evidence

### Pokemon - Modern Emerald Version v3.5 (USA, Europe).zip!Pokemon - Modern Emerald Version v3.5 (USA, Europe).gba

- Identity: `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895` (SHA-256), `8C7DBECA` (CRC32), 33554432 bytes
- Header: GBA, title `POKEMON EMER`, code `BPEE`, revision 0
- Decision: SELECTED; family EMERALD; profile Pokemon Emerald (USA/Europe); margin 20
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=75/4 anchors, FIRERED_LEAFGREEN=55/3 anchors
- Capabilities:
  - SPECIES_CATALOG: unavailable; confidence=0.150; offset=0x6DF474, count=468, recordSize=11; plausible base stats 70/468 below 90%
  - SPECIES_NAMES: compatible; confidence=0.998; offset=0x6DF474, count=468, recordSize=11
  - SPECIES_TYPES: unavailable; confidence=0.150; offset=0x8DB99C, count=468, recordSize=28; plausible base stats 70/468 below 90%
  - TYPE_CHART: unavailable; confidence=0.000; offset=0x31ACE8, recordSize=3; type chart lacks a valid terminator or enough entries
  - BASE_STATS: unavailable; confidence=0.150; offset=0x8DB99C, count=468, recordSize=28; plausible base stats 70/468 below 90%
  - SPRITES: compatible; confidence=0.989; offset=0x6D0474, count=468, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=0.987; offset=0x6E0850, count=378, recordSize=13
  - MOVE_DETAILS: compatible; confidence=0.966; offset=0x8D6924, count=378, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: compatible; confidence=0.988; offset=0x67F52C, count=82, recordSize=13

### Pokemon - Sword and Shield Ultimate Plus (USA, Europe).zip!Pokemon - Sword and Shield Ultimate Plus (USA, Europe).gba

- Identity: `f6d2e7092831b983318b685132a19567ff5e6428665255738c4e5a63371bcce3` (SHA-256), `D854EB09` (CRC32), 33554432 bytes
- Header: GBA, title `POKEMON FIRE`, code `BPRE`, revision 0
- Decision: SELECTED; family FIRERED_LEAFGREEN; profile Pokemon FireRed (USA) Rev 1; margin 20
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=80/4 anchors, FIRERED_LEAFGREEN=100/5 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.924; offset=0x16184B8, count=840, recordSize=11
  - SPECIES_NAMES: compatible; confidence=0.999; offset=0x16184B8, count=840, recordSize=11
  - SPECIES_TYPES: compatible; confidence=0.924; offset=0x19AFBEC, count=840, recordSize=28
  - TYPE_CHART: unavailable; confidence=0.000; offset=0x24F0C0, count=3, recordSize=3; type chart lacks a valid terminator or enough entries
  - BASE_STATS: compatible; confidence=0.924; offset=0x19AFBEC, count=840, recordSize=28
  - SPRITES: compatible; confidence=1.000; offset=0x19EDB5C, count=840, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=0.993; offset=0x14B2A3C, count=869, recordSize=13
  - MOVE_DETAILS: compatible; confidence=0.964; offset=0x14BFA7C, count=869, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: compatible; confidence=0.992; offset=0x14A9324, count=258, recordSize=13

### Pokemon Mystery Dungeon - Red Rescue Team EX (USA, Australia).zip!Pokemon Mystery Dungeon - Red Rescue Team EX (USA, Australia).gba

- Identity: `005b1c7dd52ea30f07e98059e8d0575aed2e8f7182f9270225e58a8dd78c3d3d` (SHA-256), `0A8C6648` (CRC32), 33554432 bytes
- Header: GBA, title `POKE DUNGEON`, code `B24E`, revision 0
- Decision: UNSUPPORTED; family -; profile -; margin -
- Diagnostics: no parser passed score and anchor requirements
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=10/1 anchors, FIRERED_LEAFGREEN=10/1 anchors
- Capabilities:

### Pokemon Unbound.zip!Pokemon Unbound.gba

- Identity: `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7` (SHA-256), `4B3D4957` (CRC32), 33554432 bytes
- Header: GBA, title `POKEMON FIRE`, code `BPRE`, revision 0
- Decision: SELECTED; family FIRERED_LEAFGREEN; profile Pokemon FireRed (USA) Rev 1; margin 20
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=80/4 anchors, FIRERED_LEAFGREEN=100/5 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.926; offset=0x166A98C, count=840, recordSize=11
  - SPECIES_NAMES: compatible; confidence=0.999; offset=0x166A98C, count=840, recordSize=11
  - SPECIES_TYPES: compatible; confidence=0.926; offset=0x19E0C9C, count=840, recordSize=28
  - TYPE_CHART: unavailable; confidence=0.000; offset=0x24F0C0, count=1, recordSize=3; type chart lacks a valid terminator or enough entries
  - BASE_STATS: compatible; confidence=0.926; offset=0x19E0C9C, count=840, recordSize=28
  - SPRITES: compatible; confidence=1.000; offset=0x1A1D5B4, count=840, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=0.996; offset=0xA40A10, count=962, recordSize=13
  - MOVE_DETAILS: compatible; confidence=0.931; offset=0xA769AF, count=962, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: unavailable; confidence=0.594; offset=0xA36398, count=64, recordSize=13; valid fixed names 38/64 below 0.85

### Pokemon - Blue Version (USA, Europe) (SGB Enhanced).zip!Pokemon - Blue Version (USA, Europe) (SGB Enhanced).gb

- Identity: `2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d` (SHA-256), `D6DA8A1A` (CRC32), 1048576 bytes
- Header: GB, title `POKEMON BLUE`, code `-`, revision 0
- Decision: SELECTED; family RED_BLUE; profile Pokemon Blue (USA/Europe); margin -
- Candidate scores: RED_BLUE=100/5 anchors, YELLOW=32/2 anchors, GOLD_SILVER=10/1 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.721; offset=0x1C21E, count=190, recordSize=10
  - SPECIES_NAMES: compatible; confidence=0.721; offset=0x1C21E, count=190, recordSize=10
  - SPECIES_TYPES: compatible; confidence=0.993; offset=0x383DE, count=151, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x3E474, count=82, recordSize=3
  - BASE_STATS: compatible; confidence=0.993; offset=0x383DE, count=151, recordSize=28
  - SPRITES: unavailable; confidence=0.000; sprite pointer validation is only implemented for GBA
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0xB0000, count=165
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x38000, count=165, recordSize=6
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: unavailable; confidence=0.000; abilities are not part of this engine

### Pokemon - Crystal Version (USA, Europe) (Rev 1).zip!Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc

- Identity: `fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2` (SHA-256), `3358E30A` (CRC32), 2097152 bytes
- Header: GBC, title `PM_CRYSTAL`, code `-`, revision 1
- Decision: SELECTED; family CRYSTAL; profile Pokemon Crystal (USA/Europe) Rev 1; margin -
- Candidate scores: RED_BLUE=10/1 anchors, YELLOW=10/1 anchors, GOLD_SILVER=10/1 anchors, CRYSTAL=100/5 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.928; offset=0x53384, count=251, recordSize=10
  - SPECIES_NAMES: compatible; confidence=0.928; offset=0x53384, count=251, recordSize=10
  - SPECIES_TYPES: compatible; confidence=1.000; offset=0x51424, count=251, recordSize=32
  - TYPE_CHART: compatible; confidence=1.000; offset=0x34BB1, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=1.000; offset=0x51424, count=251, recordSize=32
  - SPRITES: unavailable; confidence=0.000; sprite pointer validation is only implemented for GBA
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x1C9F29, count=251
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x41AFB, count=251, recordSize=7
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: unavailable; confidence=0.000; abilities are not part of this engine

### Pokemon - Emerald Version (USA, Europe).zip!Pokemon - Emerald Version (USA, Europe).gba

- Identity: `a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af` (SHA-256), `1F1C08FB` (CRC32), 16777216 bytes
- Header: GBA, title `POKEMON EMER`, code `BPEE`, revision 0
- Decision: SELECTED; family EMERALD; profile Pokemon Emerald (USA/Europe); margin -
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=100/5 anchors, FIRERED_LEAFGREEN=80/4 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.998; offset=0x3185C8, count=412, recordSize=11
  - SPECIES_NAMES: compatible; confidence=1.000; offset=0x3185C8, count=412, recordSize=11
  - SPECIES_TYPES: compatible; confidence=0.998; offset=0x3203CC, count=412, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x31ACE8, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=0.998; offset=0x3203CC, count=412, recordSize=28
  - SPRITES: compatible; confidence=1.000; offset=0x30A18C, count=412, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x31977C, count=355, recordSize=13
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x31C898, count=355, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: compatible; confidence=1.000; offset=0x31B6DB, count=78, recordSize=13

### Pokemon - FireRed Version (USA).zip!Pokemon - FireRed Version (USA, Europe) (Rev 1).gba

- Identity: `729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059` (SHA-256), `84EE4776` (CRC32), 16777216 bytes
- Header: GBA, title `POKEMON FIRE`, code `BPRE`, revision 1
- Decision: SELECTED; family FIRERED_LEAFGREEN; profile Pokemon FireRed (USA) Rev 1; margin -
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=80/4 anchors, FIRERED_LEAFGREEN=100/5 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.998; offset=0x245F50, count=412, recordSize=11
  - SPECIES_NAMES: compatible; confidence=1.000; offset=0x245F50, count=412, recordSize=11
  - SPECIES_TYPES: compatible; confidence=0.998; offset=0x2547F4, count=412, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x24F0C0, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=0.998; offset=0x2547F4, count=412, recordSize=28
  - SPRITES: compatible; confidence=1.000; offset=0x23511C, count=412, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x247104, count=355, recordSize=13
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x250C74, count=355, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: compatible; confidence=1.000; offset=0x24FCB0, count=78, recordSize=13

### Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).zip!Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc

- Identity: `fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e` (SHA-256), `6BDE3C3E` (CRC32), 2097152 bytes
- Header: GBC, title `POKEMON_GLDAAUE`, code `-`, revision 0
- Decision: SELECTED; family GOLD_SILVER; profile Pokemon Gold (USA/Europe); margin -
- Candidate scores: RED_BLUE=10/1 anchors, YELLOW=10/1 anchors, GOLD_SILVER=100/5 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.928; offset=0x1B0B74, count=251, recordSize=10
  - SPECIES_NAMES: compatible; confidence=0.928; offset=0x1B0B74, count=251, recordSize=10
  - SPECIES_TYPES: compatible; confidence=1.000; offset=0x51B0B, count=251, recordSize=32
  - TYPE_CHART: compatible; confidence=1.000; offset=0x34D01, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=1.000; offset=0x51B0B, count=251, recordSize=32
  - SPRITES: unavailable; confidence=0.000; sprite pointer validation is only implemented for GBA
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x1B1574, count=251
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x41AFE, count=251, recordSize=7
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: unavailable; confidence=0.000; abilities are not part of this engine

### Pokemon - LeafGreen Version (USA, Europe) (Rev 1).zip!Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba

- Identity: `2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825` (SHA-256), `DAFFECEC` (CRC32), 16777216 bytes
- Header: GBA, title `POKEMON LEAF`, code `BPGE`, revision 1
- Decision: SELECTED; family FIRERED_LEAFGREEN; profile Pokemon LeafGreen (USA/Europe) Rev 1; margin -
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=80/4 anchors, FIRERED_LEAFGREEN=100/5 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.998; offset=0x245F2C, count=412, recordSize=11
  - SPECIES_NAMES: compatible; confidence=1.000; offset=0x245F2C, count=412, recordSize=11
  - SPECIES_TYPES: compatible; confidence=0.998; offset=0x2547D0, count=412, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x24F09C, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=0.998; offset=0x2547D0, count=412, recordSize=28
  - SPRITES: compatible; confidence=1.000; offset=0x2350F8, count=412, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x2470E0, count=355, recordSize=13
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x250C50, count=355, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: compatible; confidence=1.000; offset=0x24FC8C, count=78, recordSize=13

### Pokemon - Red Version (USA, Europe) (SGB Enhanced).zip!Pokemon - Red Version (USA, Europe) (SGB Enhanced).gb

- Identity: `5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b` (SHA-256), `9F7FDD53` (CRC32), 1048576 bytes
- Header: GB, title `POKEMON RED`, code `-`, revision 0
- Decision: SELECTED; family RED_BLUE; profile Pokemon Red (USA/Europe); margin -
- Candidate scores: RED_BLUE=100/5 anchors, YELLOW=32/2 anchors, GOLD_SILVER=10/1 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.721; offset=0x1C21E, count=190, recordSize=10
  - SPECIES_NAMES: compatible; confidence=0.721; offset=0x1C21E, count=190, recordSize=10
  - SPECIES_TYPES: compatible; confidence=0.993; offset=0x383DE, count=151, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x3E474, count=82, recordSize=3
  - BASE_STATS: compatible; confidence=0.993; offset=0x383DE, count=151, recordSize=28
  - SPRITES: unavailable; confidence=0.000; sprite pointer validation is only implemented for GBA
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0xB0000, count=165
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x38000, count=165, recordSize=6
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: unavailable; confidence=0.000; abilities are not part of this engine

### Pokemon - Ruby Version (USA, Europe) (Rev 2).zip!Pokemon - Ruby Version (USA, Europe) (Rev 2).gba

- Identity: `0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c` (SHA-256), `AEAC73E6` (CRC32), 16777216 bytes
- Header: GBA, title `POKEMON RUBY`, code `AXVE`, revision 2
- Decision: SELECTED; family RUBY_SAPPHIRE; profile Pokemon Ruby (USA/Europe) Rev 2; margin -
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=100/5 anchors, EMERALD=10/1 anchors, FIRERED_LEAFGREEN=10/1 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.998; offset=0x1F7184, count=412, recordSize=11
  - SPECIES_NAMES: compatible; confidence=1.000; offset=0x1F7184, count=412, recordSize=11
  - SPECIES_TYPES: compatible; confidence=0.998; offset=0x1FEC30, count=412, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x1F9738, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=0.998; offset=0x1FEC30, count=412, recordSize=28
  - SPRITES: compatible; confidence=1.000; offset=0x1E836C, count=412, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x1F8338, count=355, recordSize=13
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x1FB144, count=355, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: compatible; confidence=1.000; offset=0x1FA260, count=78, recordSize=13

### Pokemon - Sapphire Version (USA, Europe) (Rev 2).zip!Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba

- Identity: `02ca41513580a8b780989dee428df747b52a0b1a55bec617886b4059eb1152fb` (SHA-256), `9CC4410E` (CRC32), 16777216 bytes
- Header: GBA, title `POKEMON SAPP`, code `AXPE`, revision 2
- Decision: SELECTED; family RUBY_SAPPHIRE; profile Pokemon Sapphire (USA/Europe) Rev 2; margin -
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=100/5 anchors, EMERALD=10/1 anchors, FIRERED_LEAFGREEN=10/1 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.998; offset=0x1F7114, count=412, recordSize=11
  - SPECIES_NAMES: compatible; confidence=1.000; offset=0x1F7114, count=412, recordSize=11
  - SPECIES_TYPES: compatible; confidence=0.998; offset=0x1FEBC0, count=412, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x1F96C8, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=0.998; offset=0x1FEBC0, count=412, recordSize=28
  - SPRITES: compatible; confidence=1.000; offset=0x1E82FC, count=412, recordSize=8
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x1F82C8, count=355, recordSize=13
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x1FB0D4, count=355, recordSize=12
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: compatible; confidence=1.000; offset=0x1FA1F0, count=78, recordSize=13

### Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).zip!Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc

- Identity: `72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c` (SHA-256), `8AD48636` (CRC32), 2097152 bytes
- Header: GBC, title `POKEMON_SLVAAXE`, code `-`, revision 0
- Decision: SELECTED; family GOLD_SILVER; profile Pokemon Silver (USA/Europe); margin -
- Candidate scores: RED_BLUE=10/1 anchors, YELLOW=10/1 anchors, GOLD_SILVER=100/5 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.928; offset=0x1B0B74, count=251, recordSize=10
  - SPECIES_NAMES: compatible; confidence=0.928; offset=0x1B0B74, count=251, recordSize=10
  - SPECIES_TYPES: compatible; confidence=1.000; offset=0x51B0B, count=251, recordSize=32
  - TYPE_CHART: compatible; confidence=1.000; offset=0x34D01, count=108, recordSize=3
  - BASE_STATS: compatible; confidence=1.000; offset=0x51B0B, count=251, recordSize=32
  - SPRITES: unavailable; confidence=0.000; sprite pointer validation is only implemented for GBA
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0x1B1574, count=251
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x41AFE, count=251, recordSize=7
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: unavailable; confidence=0.000; abilities are not part of this engine

### Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).zip!Pokemon - Yellow Version - Special Pikachu Edition (USA, Europe) (CGB+SGB Enhanced).gb

- Identity: `8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf` (SHA-256), `7D527D62` (CRC32), 1048576 bytes
- Header: GBC, title `POKEMON YELLOW`, code `-`, revision 0
- Decision: SELECTED; family YELLOW; profile Pokemon Yellow (USA/Europe); margin -
- Candidate scores: RED_BLUE=32/2 anchors, YELLOW=100/5 anchors, GOLD_SILVER=10/1 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:
  - SPECIES_CATALOG: compatible; confidence=0.721; offset=0xE8000, count=190, recordSize=10
  - SPECIES_NAMES: compatible; confidence=0.721; offset=0xE8000, count=190, recordSize=10
  - SPECIES_TYPES: compatible; confidence=1.000; offset=0x383DE, count=151, recordSize=28
  - TYPE_CHART: compatible; confidence=1.000; offset=0x3E5FA, count=82, recordSize=3
  - BASE_STATS: compatible; confidence=1.000; offset=0x383DE, count=151, recordSize=28
  - SPRITES: unavailable; confidence=0.000; sprite pointer validation is only implemented for GBA
  - POKEDEX_DESCRIPTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - EVOLUTIONS: unavailable; confidence=0.000; not implemented in parser POC
  - MOVE_CATALOG: compatible; confidence=1.000; offset=0xBC000, count=165
  - MOVE_DETAILS: compatible; confidence=1.000; offset=0x38000, count=165, recordSize=6
  - LEARNSETS: unavailable; confidence=0.000; not implemented in parser POC
  - ABILITIES: unavailable; confidence=0.000; abilities are not part of this engine

### Pokemon Mystery Dungeon - Red Rescue Team (USA, Australia).zip!Pokemon Mystery Dungeon - Red Rescue Team (USA, Australia).gba

- Identity: `ad316814c77ed083734d816ebcde2ece390efae8d15bcb6c66d7c2862d82eb68` (SHA-256), `DD0AC86C` (CRC32), 33554432 bytes
- Header: GBA, title `POKE DUNGEON`, code `B24E`, revision 0
- Decision: UNSUPPORTED; family -; profile -; margin -
- Diagnostics: no parser passed score and anchor requirements
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=10/1 anchors, FIRERED_LEAFGREEN=10/1 anchors
- Capabilities:

### Pokemon Pinball (USA, Australia) (Rumble Version) (SGB Enhanced) (GB Compatible).zip!Pokemon Pinball (USA, Australia) (Rumble Version) (SGB Enhanced) (GB Compatible).gbc

- Identity: `7672001d4710272009df6a41e3cbada65decd56e0eb2f185cb3d59c08d33ea0e` (SHA-256), `03CE8D9A` (CRC32), 1048576 bytes
- Header: GBC, title `POKEPINBALLVPHE`, code `-`, revision 0
- Decision: UNSUPPORTED; family -; profile -; margin -
- Diagnostics: no parser passed score and anchor requirements
- Candidate scores: RED_BLUE=10/1 anchors, YELLOW=10/1 anchors, GOLD_SILVER=10/1 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:

### Pokemon Pinball - Ruby & Sapphire (USA).zip!Pokemon Pinball - Ruby & Sapphire (USA).gba

- Identity: `1a2bbb845456873de0e50ba01383977d7b3500f85dceac929c5fb0111389d9a1` (SHA-256), `B992A3C0` (CRC32), 8388608 bytes
- Header: GBA, title `POKEPIN R/S`, code `BPPE`, revision 0
- Decision: UNSUPPORTED; family -; profile -; margin -
- Diagnostics: no parser passed score and anchor requirements
- Candidate scores: RED_BLUE=0/0 anchors, YELLOW=0/0 anchors, GOLD_SILVER=0/0 anchors, CRYSTAL=0/0 anchors, RUBY_SAPPHIRE=10/0 anchors, EMERALD=10/1 anchors, FIRERED_LEAFGREEN=10/1 anchors
- Capabilities:

### Pokemon Puzzle Challenge (USA, Australia).zip!Pokemon Puzzle Challenge (USA, Australia).gbc

- Identity: `42e6bf3b26f186f1ddf23d2ab0e78dea423c55562119f1988ffab906e8f86fe6` (SHA-256), `D06BBA96` (CRC32), 2097152 bytes
- Header: GBC, title `POKEMONPC`, code `-`, revision 0
- Decision: UNSUPPORTED; family -; profile -; margin -
- Diagnostics: no parser passed score and anchor requirements
- Candidate scores: RED_BLUE=10/1 anchors, YELLOW=10/1 anchors, GOLD_SILVER=10/1 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:

### Pokemon Trading Card Game (USA, Australia) (SGB Enhanced) (GB Compatible).zip!Pokemon Trading Card Game (USA, Australia) (SGB Enhanced) (GB Compatible).gbc

- Identity: `a54515bb6b3e364964d3c0226f5a6b0c8c0f7318c9296ef2e321df0bbb8541ce` (SHA-256), `81069D53` (CRC32), 1048576 bytes
- Header: GBC, title `POKECARD`, code `-`, revision 0
- Decision: UNSUPPORTED; family -; profile -; margin -
- Diagnostics: no parser passed score and anchor requirements
- Candidate scores: RED_BLUE=10/1 anchors, YELLOW=10/1 anchors, GOLD_SILVER=10/1 anchors, CRYSTAL=10/1 anchors, RUBY_SAPPHIRE=0/0 anchors, EMERALD=0/0 anchors, FIRERED_LEAFGREEN=0/0 anchors
- Capabilities:
