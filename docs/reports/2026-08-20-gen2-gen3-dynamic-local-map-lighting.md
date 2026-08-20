# Gen II/III dynamic Local-map lighting verification

Date: 2026-08-20

## Scope

This gate validates the dynamic Local-map raster path added after RC18. It covers official Pokémon Gold, Silver, and Crystal; official Pokémon Ruby, Sapphire, Emerald, FireRed, and LeafGreen static compatibility controls; and the source-backed Modern Emerald 3.5 timed-lighting control. Raw compiled ROM structures remain production authority. Recognition does not use filenames, ROM identities, hashes, fixed offsets, per-ROM profiles, or hack allowlists.

## Gen II indexed lighting

- Gold and Silver each resolve 368 Local maps; Crystal resolves 388.
- Every resolved Gen II Local map uses one independently compressed indexed raster rather than a baked PNG.
- Each indexed asset carries MORNING, DAY, NIGHT, and DARK palettes plus an explicit `AUTO` or fixed map-lighting policy.
- Exact New Bark Town renders produce four distinct palette hashes on all three official controls.
- The structurally resolved runtime selector is bounded to one byte at WRAM offset `0x1568` for Gold/Silver and `0x1841` for Crystal.
- Invalid, missing, or disconnected runtime lighting falls back to DAY without introducing a second clock state or widget.

## Gen III timed lighting

- Official Ruby and Sapphire retain 394 Local maps each, Emerald retains 518, and FireRed/LeafGreen retain 425 each with exact noon/static raster parity.
- Modern Emerald retains 557 Local maps, including primary- and secondary-tileset maps.
- A unique compiled normal/bright `BlendSettings` table pair is required before natural-light maps become timed indexed assets. The recognizer validates packed fields, table adjacency, shared rows, distinct night rows, and at least two independent aligned pointer references per table.
- Timed assets store one compressed 0–255 indexed raster, base and alternate 256-color raw GBA palettes, the alternate-palette mask, and the structurally decoded time-blend model.
- The renderer reproduces Modern Emerald's BGR555 alternate-palette mixing and night/twilight/day tint schedule lazily. Route 102 produces four distinct renders at 12:00, 19:00, 21:00, and 23:00.
- Non-natural maps and ROMs without a uniquely proven lighting ABI remain static PNGs.

## Persistence, API, and UI

- Catalog schema 21 persists Gen II indexed assets, Gen III timed assets, and RC18 ROM-derived themes together.
- Desktop and Android loopback endpoints accept Gen II `lighting=` and Gen III paired `hour=`/`minute=` queries, render full or clipped PNGs lazily, and vary ETags by effective lighting/time.
- Invalid or incomplete numeric time returns HTTP 400. Corrupt or unavailable optional map assets return 404 without stopping the runtime or disabling unrelated capabilities.
- The existing shared `gameTime` state and `GameClockIndicator` drive both generations. Numeric Gen III time takes precedence; phase-only Gen II state uses the four lighting modes. Image changes preserve map selection, pan, zoom, and player position.
- RC18 Pokémon Unbound/Odyssey support and ROM-derived GAME themes remain converged with the dynamic raster path.

## Verification results

- Full parser gate: 1,138 tests, 142 skipped optional controls, zero failures/errors across 142 suites.
- Exact Local-map controls: 9/9 passed across Gold, Silver, Crystal, Ruby, Sapphire, Emerald, FireRed, LeafGreen, and Modern Emerald.
- Integrated persistence/runtime gate: catalog-store 20 tests (4 optional controls skipped), companion-core 52, companion-server 11, and Android host 171 (25 optional controls skipped), with zero failures/errors.
- Web gate: 21 Vitest files and 126 tests passed; TypeScript and Vite production build passed.
- The signed production APK remains delegated exclusively to the protected GitHub `release-signing` environment and pinned production certificate.

No ROM bytes, saves, trainer data, signing material, or private filesystem paths are included in this report.
