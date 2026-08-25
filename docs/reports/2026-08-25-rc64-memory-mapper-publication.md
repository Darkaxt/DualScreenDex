# RC64 live-memory mapper publication

**Result:** PUBLISHED AND VERIFIED — the protected workflow published `v1.1.0-rc.64` from commit `157972f99c793342259e31b077288ee7e0c6bb43`, and the anonymous public APK is byte-identical to the authenticated release asset.

## Retained memory replay

| Dump | Snapshots | Production result |
| --- | ---: | --- |
| `dualdex-memory-20260810-115801.json` | 3 | Modern Emerald live trainer, ID, play time, money, Pokédex, Party, battle, area and coordinates resolved |
| `dualdex-memory-20260810-161229.json` | 8 | Yellow live battle, area and coordinates resolved; unsupported clock and trainer money remained unavailable |
| `dualdex-memory-20260810-180410.json` | 9 | Crystal live battle, area, coordinates and clock resolved; unsupported trainer money remained unavailable |
| `dualdex-memory-20260812-183724.json` | 6 | Modern Emerald live trainer, ID, play time, money, Pokédex, Party, battle, area and coordinates resolved |

Every raw region matched its descriptor bounds, decoded byte count and retained SHA-256. All 26 snapshots traversed the production memory transport, battle coordinator, unified decoder and companion runtime. Modern Emerald resolved the compiled SaveBlock2 encryption-key member at `0xBC` and returned the observed `3300` money value in all nine snapshots; retail Emerald retained `0xAC` through the same semantic resolver.

## Release evidence

| Gate | Result |
| --- | --- |
| Local all-module tests, Android lint and RC64 assembly | PASS |
| Clean protected verify-and-build job | PASS |
| Protected sign-and-publish job | PASS |
| Package identity | `com.darkaxt.dualdex`, version `1.1.0-rc.64`, code `1010064` |
| APK SHA-256 | `67582E2F58D1BBDDA0F0AA8F86374AA43CC95AEB912770561E0952850B4D6909` |
| APK signature | v3, one signer, certificate SHA-256 `C5A02CECB47CDA41B618817EA684CBB6CCFDCC17A3E7D8243448175C8E3B2FBA` |
| Workflow provenance | Run `32878404637`, tag `v1.1.0-rc.64`, commit `157972f99c793342259e31b077288ee7e0c6bb43` |
| Anonymous public download | Byte-identical to the release asset |

- Release: <https://github.com/Darkaxt/DualScreenDex/releases/tag/v1.1.0-rc.64>
- Workflow: <https://github.com/Darkaxt/DualScreenDex/actions/runs/32878404637>
- Asset: `DualDex-v1.1.0-rc.64.apk`

No APK was installed or launched during publication.
