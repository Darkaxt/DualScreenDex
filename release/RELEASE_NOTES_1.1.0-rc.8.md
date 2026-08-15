# DualDex 1.1.0-rc.8

RC8 preserves the RC7 interface and fixes an upgrade-only catalog-cache defect found on a real Modern Emerald installation.

## Catalog rebuild

- Catalog parser cache revision advances from 13 to 14.
- Revision-13 SQLite catalogs are rebuilt once instead of being accepted after newer Atlas and local-map parsing was integrated.
- Settings, discovery knowledge, and persisted SaveRAM snapshots are preserved; only the derived ROM catalog is regenerated.
- The first RC8 load is therefore expected to show the full module progress sequence. Later launches can reopen the rebuilt catalog quickly.

## Real-ROM verification

- The retained RC7 catalog was observed through the live loopback API with 428 species and 231 encounter areas but zero world maps, despite advertising the map capability.
- A fresh parse of Modern Emerald v3.5 reconstructed the exact normalized Atlas raster.
- The rebuilt catalog survived SQLite persistence/reopen and served the expected map PNG through the loopback API.

RC8 includes the RC7 loading-progress and Atlas presentation changes unchanged.
