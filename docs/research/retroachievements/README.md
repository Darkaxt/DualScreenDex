# RetroAchievements reference research

This directory contains commit-safe evidence derived from the current base achievement sets for the eleven official English Pokémon Generation I–III releases. It is research input for DualDex's independent portable challenge vocabulary; it is not an implementation of the RetroAchievements runtime or expression language.

## Provenance and controls

The developer extractor uses the official [`API_GetGameExtended`](https://api-docs.retroachievements.org/v1/get-game-extended.html) endpoint for these game IDs:

| Generation | Game IDs | Games | Achievements |
| ---: | --- | ---: | ---: |
| I | 724, 586, 723 | 3 | 259 |
| II | 576, 722, 810 | 3 | 260 |
| III | 790, 791, 668, 515, 788 | 5 | 484 |
| **Total** | **11 IDs** | **11** | **1,003** |

Counts describe the authenticated extraction recorded at `2026-08-26T15:04:52.604Z`; they are evidence, not application constants.

## Distribution boundary

The authenticated research payloads remain uncommitted under `D:\Temp\dualdex-retroachievements\research`. Each payload is normalized to exactly these fields:

- source system, generation, source game ID/title, expected title, source URL, source modification time, and extraction time;
- achievement ID, title, description, official classification, display order, author, source modification time, source URL, and extraction time.

The payloads exclude trigger expressions, memory addresses, points, badge references or artwork, award counts, unlock data, user profiles, leaderboards, ROMs, and browser state. A reused payload with any additional field fails closed.

The repository contains only:

- `official-gen1-gen3-manifest.json`: identity, counts, provenance, retrieval mode, and SHA-256 for each uncommitted research payload;
- `official-gen1-gen3-classification.json`: source IDs and hashes plus DualDex-authored semantic metadata; it contains no source title or description prose;
- `semantic-vocabulary.schema.json`: the closed commit-safe classification vocabulary.

## Credential and regeneration contract

The API key is accepted only through `RETROACHIEVEMENTS_WEB_API_KEY`. It must not be placed in an `.env` file, command-line argument, report, log, APK, or committed artifact.

Authenticated extraction:

```powershell
$env:RETROACHIEVEMENTS_WEB_API_KEY = '<developer key>'
node tools/retroachievements/extract-pokemon-achievements.mjs
Remove-Item Env:RETROACHIEVEMENTS_WEB_API_KEY
node tools/retroachievements/classify-pokemon-achievements.mjs
```

Cache-only verification does not require a credential and performs no request when all eleven sanitized payloads validate:

```powershell
$env:DUALDEX_RA_REUSE_EXISTING = '1'
node tools/retroachievements/extract-pokemon-achievements.mjs
node tools/retroachievements/classify-pokemon-achievements.mjs
Remove-Item Env:DUALDEX_RA_REUSE_EXISTING
```

The extractor writes payloads and the manifest atomically. Cache-only regeneration preserves the original extraction time and payload fingerprint. The classifier validates every fingerprint and exact field set before producing derived artifacts.

## Independent implementation boundary

DualDex does not package RetroAchievements prose or triggers, claim RetroAchievements credit, contact the service at runtime, or require a user account. Generic template wording, semantic facts/events, predicate operators, and future runtime evaluation are independently implemented. Source IDs, URLs, modification dates, and text hashes remain only for developer traceability.
