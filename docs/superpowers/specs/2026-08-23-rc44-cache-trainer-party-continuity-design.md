# RC44 Cache, Trainer, and Party Continuity Design

## Objective

RC44 must preserve a completed ROM catalog across ordinary companion restarts, publish the active trainer artwork to every trainer-dependent view for Modern Emerald, and render the Party-to-Pokédex action with the exact shared icon treatment already used by Combat.

## Evidence and root causes

### Catalog cache

The RC43 Modern Emerald catalog pulled from the installed app is structurally complete and passes SQLite integrity checks. Its `local_maps` section is a single 8,153,020-byte compressed BLOB. Android reads query results through a bounded cursor window, so reopening that row throws before `CatalogReader` can decode it. `CatalogCache.readComplete` currently catches every exception, deletes the database, and returns `null` without logging the rejection. The runtime therefore transitions from the short yellow reopen phase to a full red parse.

ROM catalogs are keyed by ROM SHA-256. Save writes and the future transient knowledge sidecar do not participate in this decision.

### Height comparison

The current UI correctly uses `trainerAvatarUrl` when it is present, but falls back to a generic black silhouette when it is absent. Modern Emerald's completed catalog contains both 64x64 trainer avatars, while its runtime metadata omits the SaveBlock2 pointer and trainer ABI. Its source keeps the standard Emerald trainer fields at SaveBlock2 offsets `0x00`, `0x08`, `0x0A`, `0x0E`, and `0x10`; the real ROM must therefore be used to make the resolver admit its compiled SaveBlock2 pointer evidence without a ROM-name, hash, or fixed-address profile.

### Party Pokédex action

Combat uses the shared `DexIcon` SVG in a transparent 40x40 action. Party currently renders the literal word `DEX` in a separately themed square. Party must reuse the Combat component and the same CSS contract rather than imitate it.

## Design

### Android-safe catalog sections

- Keep physical database schema version `1` so `save_snapshot` survives parser format changes.
- Bump parser schema version from `32` to `33`, forcing exactly one catalog rebuild after upgrade while retaining save snapshots.
- Add `catalog_section_chunks(section_name, chunk_index, payload)` with a composite primary key and foreign-key-independent cleanup.
- Encode a section once, split its compressed bytes into ordered chunks no larger than 256 KiB, and write them in the same transaction as its section manifest.
- Store section encoding as `gzip+json+chunks-v1`; the manifest payload remains an empty BLOB so no catalog query row can exceed the cursor-window budget.
- Reassemble chunks in numeric order, reject missing/duplicate/non-contiguous indices, then use the existing codec.
- Parser-schema invalidation deletes manifests and chunks together while preserving `save_snapshot`.

### Cache decision observability

- `CatalogCache` accepts an optional event sink and reports `MISS_FILE_ABSENT`, `MISS_INCOMPLETE_OR_INCOMPATIBLE`, `HIT`, and `REJECTED_EXCEPTION` with the SHA and exception type/message.
- Android wires the sink to logcat tag `DualDexCache`.
- These diagnostics never appear in normal UI. The existing red/yellow user-facing loading colors remain the only normal indication.

### Modern Emerald trainer identity

- Add a real-ROM control for Modern Emerald v3.5 using the consolidated corpus path and expected SHA-256.
- Improve compiled SaveBlock2 pointer recognition only with semantic Thumb evidence from the real binary and the source-confirmed field layout.
- Publish SaveBlock1 and SaveBlock2 only as one coherent group with the Emerald save ABI; ambiguity continues to fail closed.
- Do not add ROM-name, SHA, or fixed-offset selection.
- Once the live trainer gender is decoded, the existing catalog avatar key supplies the trainer image to Height Comparison, Trainer Card, and map marker consumers.
- The generic silhouette remains only the pre-identity fallback; it must not appear after Modern Emerald's trainer state is available.

### Shared Party action

- Import and render `DexIcon` in `PartyPage`.
- Share the Combat button sizing, transparent background, focus treatment, and 30x30 SVG sizing with Party.
- Preserve the existing Party navigation target and accessibility label.

## Acceptance criteria

1. A section larger than Android's cursor-window budget round-trips through ordered sub-256-KiB rows.
2. Schema 32 catalogs invalidate once without deleting `save_snapshot`.
3. Cache hit, absence, incompatibility, and exception rejection are visible in logcat with no normal-page diagnostics.
4. Modern Emerald's real ROM resolves a coherent SaveBlock1/SaveBlock2 trainer ABI and the API publishes a trainer avatar URL once live gender is readable.
5. Party and Combat render the same `DexIcon` component and visual button contract.
6. RC44 is assembled, signed, and published without device installation or app launch; the user performs installation and visual/runtime acceptance.
