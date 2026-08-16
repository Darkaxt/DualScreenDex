# ZIP/7z ROM Parity and Cache Streaming Design

## Problem

The Thor contains two Pokemon Unbound archives:

- `Pokemon Unbound.zip`
- `Unbound (v2.1.1.1).7z`

Each contains one 32 MiB GBA ROM. The extracted payloads have the same CRC32 (`4B3D4957`) and SHA-256 (`7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7`). DualDex indexes ZIP but ignores 7z, so the launcher can expose a valid content source that DualDex cannot independently index and reopen.

The latest DualDex crash is separate: while Unbound is being materialized, `CatalogSectionCodec` asks Gson to create a roughly 49 MiB JSON `String` before GZIP compression. Android rejects that additional allocation at the 256 MiB heap limit.

## Design

### Archive parity

Treat `.zip` and `.7z` as equivalent single-ROM containers at every source boundary:

- direct all-files discovery;
- Android document-tree discovery;
- parser/server path loading;
- active-session reopening.

Both formats accept exactly one non-directory `.gb`, `.gbc`, or `.gba` member. Empty and multi-ROM archives fail closed. Identity is always calculated from the extracted ROM payload, so ZIP and 7z copies of the same ROM converge on the existing SHA-authoritative catalog and session-resolution behavior.

Use Apache Commons Compress for 7z container handling and XZ/LZMA decoding. Direct filesystem sources use a seekable file-backed reader. Stream-only document providers use an in-memory seekable channel as a compatibility fallback; the direct Cocoon/RetroArch path does not incur that archive copy.

### Memory-safe catalog persistence

Serialize Gson directly through an `OutputStreamWriter` into `GZIPOutputStream`; deserialize through an `InputStreamReader` over `GZIPInputStream`. Do not build a complete JSON `String` or UTF-8 byte array. The SQLite payload remains `gzip+json`, so schema and existing cache compatibility do not change.

Encode and commit one changed section at a time. On reopen, verify section names first and fetch/decode one payload at a time. This prevents the compressed form of the entire catalog from remaining live alongside the expanded `ParsedCatalog`.

### Other whole-buffer paths

The pipeline audit distinguishes the one canonical random-access ROM buffer from avoidable copies:

- ARM7 helper emulation reuses the immutable `RomImage`; it must not slice, copy, and wrap the full ROM for every proof probe.
- Direct 7z indexing streams the member header and hashes; only the later parser load materializes the ROM.
- Loopback and desktop JSON endpoints write Gson directly to the connection instead of staging a full JSON `String` and byte array.
- SaveRAM parsing, WRAM capture, PNG encoding, settings, and static packaged assets remain bounded by hardware/file-specific small sizes and do not overlap another full catalog representation.

Stream-only 7z inputs still require a seekable compatibility buffer because the format is random-access. Cocoon's direct `file:` source and normal direct-library indexing use file-backed `SevenZFile` and do not take this fallback.

### Failure behavior

- Unsupported, corrupt, empty, encrypted, or multi-ROM archives produce the existing index warning/load failure and never select a partial member.
- A cache write remains transactional. Serialization failure cannot mark an incomplete snapshot complete.
- No change is made to ROM matching, SHA deduplication, parsing, or gameplay state.

## Verification

1. RED/GREEN tests for 7z direct indexing, generic loading, and one-ROM/multi-ROM rules.
2. A real Unbound ZIP/7z parity test using the two copied device archives, asserting the exact shared payload SHA.
3. Exact Unbound `CatalogParser` incremental write and SQLite reopen through the production cache path.
4. Catalog codec round-trip with a large binary section through streaming reader/writer APIs.
5. ARM7 immutable-ROM reuse and loopback chunked-JSON regressions.
6. Focused `parser-core`, `catalog-store`, app storage/runtime, and server tests, followed by an Android release build.
