# DualDex 1.1.0-rc.14

RC14 fixes the Pokemon Unbound loading crash and treats ZIP and 7z as equal, standard ROM containers.

## ZIP and 7z parity

- Direct and document-tree libraries now discover `.7z` alongside `.zip`, `.gb`, `.gbc`, and `.gba`.
- ZIP and 7z archives use the same strict rule: exactly one supported ROM member. Empty, corrupt, encrypted, and multi-ROM containers fail closed.
- ROM identity remains the extracted payload SHA-256. The two observed Unbound archives contain the same exact 32 MiB ROM, so they converge on one catalog/session identity instead of being treated as different games.
- Direct and seekable Android sources decode 7z from the source file descriptor; indexing streams the member hashes/header instead of retaining a second full ROM.

## Large-ROM memory fix

- SQLite catalog sections now stream JSON directly through GZIP. The parser no longer creates a complete JSON `String` and UTF-8 copy before compression.
- Cache writes encode and commit one changed section at a time; cache reopen fetches and expands one section at a time while preserving the existing `gzip+json` schema.
- ARM7/THUMB helper proofs reuse the parser's immutable ROM instead of cloning a full ROM repeatedly.
- Catalog JSON responses stream directly to the loopback connection instead of staging a second complete catalog representation.
- Raw browser uploads transfer their request buffer directly into the immutable ROM image instead of allocating a second full-ROM byte array.

## Verification

- The exact Unbound ZIP and 7z payloads resolve to SHA-256 `7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`, CRC32 `4B3D4957`, and 33,554,432 bytes.
- The exact Unbound ROM completes all production incremental catalog writes and reopens from SQLite successfully.
- Focused parser, archive-index, cache, ARM7, loopback, and desktop-server tests pass, including a 12 MiB binary catalog-section round trip.
- The signed APK is built and signed only by the protected GitHub release workflow. It is not installed or launched on a device by this release task; device acceptance remains with the user.
