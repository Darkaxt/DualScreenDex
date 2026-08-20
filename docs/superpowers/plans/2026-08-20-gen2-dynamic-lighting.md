# Gen II Dynamic Lighting Implementation Plan

> **Status:** Implemented and validated. This is a historical execution record, not an active plan. The merge of `fork/release/v1.1.0-rc.17` superseded the detailed clock-state instructions below: the shipped implementation extends `AppSnapshot.gameTime`, `GameClockView`, and `GameClockIndicator` and does **not** add `liveMapLighting`, `LiveMapLightingChanged`, or `currentMapLighting`.

**Goal:** Replace baked daytime Gen II Local-map PNGs with one compressed indexed raster plus four palette tables per map, follow the game clock through a bounded WRAM read, and render only the requested lighting PNG without changing Gen I/III maps.

**Implemented architecture:** `LocalMapCatalog` retains existing PNG assets and adds a disjoint indexed-asset map. A bounded parser-core codec/renderer applies map policy and requested game lighting and emits PNG bytes lazily. The structurally resolved Gen II byte is projected as a phase-only value through the existing shared game-clock API/widget path; Gen III numeric clock behavior remains unchanged.

**Tech Stack:** Kotlin/JVM 17, JUnit 4, Gradle 9.4.1, Gson gzip+JSON catalog persistence, Android loopback HTTP, JDK `HttpServer`, Preact, TypeScript, Vite, Vitest.

---

## Scope and file structure

This plan implements only Stage 1 of `docs/superpowers/specs/2026-08-20-local-raster-seamless-map-design.md`.

Source-oracle roots supplied for this work:

- `D:/Temp/PokemonHacks/sources/Official/pokegold`
- `D:/Temp/PokemonHacks/sources/Official/pokecrystal`

Use these files to verify intent and symbol relationships:

- `engine/gfx/color.asm` — compiled consumer shape for `EnvironmentColorsPointers`, `TilesetBGPalette`, `RoofPals`, and `wTimeOfDayPal`.
- `data/maps/environment_colors.asm` — four environment palette-index rows.
- `engine/tilesets/timeofday_pals.asm` — normalized morning/day/night/dark values written to `wTimeOfDayPal`.
- `ram/wram.asm` — WRAM symbol ordering.
- `gfx/tilesets/bg_tiles.pal` and `gfx/tilesets/roofs.pal` — source palette data used only as a validation oracle.

Production recognition must continue to derive addresses and data from compiled ROM bytes; source paths, labels, and offsets must not enter production selection.

New focused files:

- `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/LocalMapRasterRenderer.kt` — bounded compression/inflation, policy resolution, full/clipped indexed rendering, and PNG encoding.
- `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/LocalMapRasterRendererTest.kt` — generation-neutral codec, policy, clipping, and static compatibility tests.

Existing responsibilities remain in place:

- `CatalogModels.kt` owns persisted map and runtime metadata types/invariants.
- `Gen2LocalMapResolver.kt` recognizes compiled Gen II authorities and emits indexed assets.
- `CatalogParser.kt` merges optional Local-map runtime metadata without weakening failure isolation.
- `BattleMemoryCoordinator.kt` owns bounded RetroArch memory reads and change-only publication.
- Android and desktop runtimes own map lookup; HTTP servers own query validation and cache headers.
- `MapPage.tsx` only chooses the URL for the active `<img>`; it does not decode map pixels.

Explicitly excluded: connection-derived scene topology, global scene composition, slippy-map tiles, unified Local/Atlas viewport replacement, interactables, and GBA investigation.

---

### Task 1: Add the indexed Local-map asset and clipped renderer

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt:236-312`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/LocalMapRasterRenderer.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/LocalMapRasterRendererTest.kt`

- [ ] **Step 1: Write failing model and renderer tests.**

Create `LocalMapRasterRendererTest.kt` with concrete two-dimensional indices, four distinguishable palettes, an `AUTO` asset, and a forced `DARK` asset:

```kotlin
package com.enrpau.dualscreendex.parser.catalog

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalMapRasterRendererTest {
    private val palettes = MapLightingPalettes(
        morning = IntArray(32) { 0xff100000.toInt() + it },
        day = IntArray(32) { 0xff200000.toInt() + it },
        night = IntArray(32) { 0xff300000.toInt() + it },
        dark = IntArray(32) { 0xff400000.toInt() + it },
    )
    private val indices = byteArrayOf(0, 1, 4, 7, 8, 31)

    @Test
    fun compressedIndicesRoundTripWithoutRetainingTheRawSurface() {
        val compressed = LocalMapRasterCodec.compress(indices)
        val asset = IndexedMapAsset(3, 2, compressed, LocalMapLightingPolicy.AUTO, palettes)

        assertArrayEquals(indices, LocalMapRasterCodec.inflate(asset))
        assertEquals(6, asset.pixelCount)
        assertEquals(false, compressed.contentEquals(indices))
    }

    @Test
    fun autoPolicyUsesTheRequestedPaletteAndClippingMatchesTheFullRaster() {
        val asset = IndexedMapAsset(
            3,
            2,
            LocalMapRasterCodec.compress(indices),
            LocalMapLightingPolicy.AUTO,
            palettes,
        )

        val full = LocalMapRasterRenderer.render(asset, MapLighting.NIGHT)
        val clipped = LocalMapRasterRenderer.render(asset, MapLighting.NIGHT, RasterRect(1, 0, 2, 2))

        assertArrayEquals(
            intArrayOf(palettes.night[0], palettes.night[1], palettes.night[4], palettes.night[7], palettes.night[8], palettes.night[31]),
            full.argb,
        )
        assertArrayEquals(
            intArrayOf(palettes.night[1], palettes.night[4], palettes.night[8], palettes.night[31]),
            clipped.argb,
        )
    }

    @Test
    fun everyExplicitPolicyOverridesTheRequestedGameLighting() {
        MapLighting.entries.forEach { forced ->
            val requested = MapLighting.entries.first { it != forced }
            val asset = IndexedMapAsset(
                3,
                2,
                LocalMapRasterCodec.compress(indices),
                LocalMapLightingPolicy.valueOf(forced.name),
                palettes,
            )

            val rendered = LocalMapRasterRenderer.renderPng(asset, requested)

            assertEquals(forced, rendered.effectiveLighting)
            assertArrayEquals(
                LocalMapRasterRenderer.render(asset, forced).argb,
                LocalMapRasterRenderer.render(asset, requested).argb,
            )
        }
    }

    @Test
    fun malformedCompressionAndOutOfBoundsClipsFailClosed() {
        val malformed = IndexedMapAsset(3, 2, byteArrayOf(1, 2, 3), LocalMapLightingPolicy.AUTO, palettes)
        val valid = IndexedMapAsset(3, 2, LocalMapRasterCodec.compress(indices), LocalMapLightingPolicy.AUTO, palettes)

        assertThrows(IllegalArgumentException::class.java) { malformed.validate() }
        assertThrows(IllegalArgumentException::class.java) {
            LocalMapRasterRenderer.render(valid, MapLighting.DAY, RasterRect(2, 1, 2, 1))
        }
    }

    @Test
    fun catalogRequiresEachMapKeyInExactlyOneAssetStore() {
        val map = LocalMap("local/0001", "Test", 1, 48, 32, 3, 2, "local/0001/map")
        val indexed = IndexedMapAsset(48, 32, LocalMapRasterCodec.compress(ByteArray(48 * 32)), LocalMapLightingPolicy.AUTO, palettes)
        val png = PngMapAsset(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))

        LocalMapCatalog(listOf(map), indexedAssets = mapOf(map.imageAssetKey to indexed)).validate()
        assertThrows(IllegalArgumentException::class.java) {
            LocalMapCatalog(
                listOf(map),
                assets = mapOf(map.imageAssetKey to png),
                indexedAssets = mapOf(map.imageAssetKey to indexed),
            ).validate()
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify that the new types are unresolved.**

Run:

```bash
./gradlew :parser-core:test --tests '*LocalMapRasterRendererTest' --no-daemon --console=plain
```

Expected: compilation fails on `MapLighting`, `IndexedMapAsset`, `LocalMapRasterCodec`, `RasterRect`, and `LocalMapRasterRenderer`.

- [ ] **Step 3: Add persisted lighting and indexed-asset types to `CatalogModels.kt`.**

Add the following types after `CatalogRuntimeMetadata` and before `PngMapAsset`:

```kotlin
enum class MapLighting { MORNING, DAY, NIGHT, DARK }

enum class LocalMapLightingPolicy {
    AUTO, MORNING, DAY, NIGHT, DARK;

    fun resolve(requested: MapLighting): MapLighting = when (this) {
        AUTO -> requested
        MORNING -> MapLighting.MORNING
        DAY -> MapLighting.DAY
        NIGHT -> MapLighting.NIGHT
        DARK -> MapLighting.DARK
    }
}

data class MapLightingPalettes(
    val morning: IntArray,
    val day: IntArray,
    val night: IntArray,
    val dark: IntArray,
) {
    fun validate(): MapLightingPalettes = apply {
        listOf(morning, day, night, dark).forEach { colors ->
            require(colors.size == COLORS_PER_LIGHTING) { "indexed map lighting palettes must contain 32 colors" }
        }
    }

    operator fun get(lighting: MapLighting): IntArray = when (lighting) {
        MapLighting.MORNING -> morning
        MapLighting.DAY -> day
        MapLighting.NIGHT -> night
        MapLighting.DARK -> dark
    }

    override fun equals(other: Any?): Boolean = other is MapLightingPalettes &&
        morning.contentEquals(other.morning) && day.contentEquals(other.day) &&
        night.contentEquals(other.night) && dark.contentEquals(other.dark)

    override fun hashCode(): Int = listOf(
        morning.contentHashCode(), day.contentHashCode(), night.contentHashCode(), dark.contentHashCode(),
    ).fold(1) { result, value -> 31 * result + value }

    private companion object {
        const val COLORS_PER_LIGHTING = 32
    }
}

data class IndexedMapAsset(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val compressedIndices: ByteArray,
    val lightingPolicy: LocalMapLightingPolicy,
    val palettes: MapLightingPalettes,
) {
    val pixelCount: Int
        get() = (pixelWidth.toLong() * pixelHeight).also {
            require(it in 1..Int.MAX_VALUE.toLong()) { "indexed map pixel count is invalid" }
        }.toInt()

    fun validate(): IndexedMapAsset = apply {
        require(pixelWidth > 0 && pixelHeight > 0) { "indexed map dimensions must be positive" }
        require(compressedIndices.isNotEmpty()) { "indexed map data must not be empty" }
        palettes.validate()
        LocalMapRasterCodec.inflate(this).forEach { value ->
            require((value.toInt() and 0xff) in 0..31) { "indexed map pixels must fit the 32-color domain" }
        }
    }

    override fun equals(other: Any?): Boolean = other is IndexedMapAsset &&
        pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight &&
        lightingPolicy == other.lightingPolicy && compressedIndices.contentEquals(other.compressedIndices) &&
        palettes == other.palettes

    override fun hashCode(): Int = 31 * (
        31 * (31 * (31 * pixelWidth + pixelHeight) + lightingPolicy.hashCode()) + compressedIndices.contentHashCode()
    ) + palettes.hashCode()
}
```

Extend `LocalMapCatalog` without changing existing constructor callers:

```kotlin
data class LocalMapCatalog(
    val maps: List<LocalMap> = emptyList(),
    val assets: Map<String, PngMapAsset> = emptyMap(),
    val indexedAssets: Map<String, IndexedMapAsset> = emptyMap(),
) {
    init { validate() }

    fun validate(): LocalMapCatalog = apply {
        require(maps.map(LocalMap::key).toSet().size == maps.size) { "local-map keys must be unique" }
        require(maps.map(LocalMap::baseAreaId).toSet().size == maps.size) {
            "local maps must bind unique base-area IDs"
        }
        require(assets.keys.intersect(indexedAssets.keys).isEmpty()) {
            "local-map asset keys must belong to exactly one raster store"
        }
        val referencedAssetKeys = maps.map(LocalMap::imageAssetKey).toSet()
        require(assets.keys + indexedAssets.keys == referencedAssetKeys) {
            "local-map assets must exactly match map asset keys"
        }
        indexedAssets.values.forEach(IndexedMapAsset::validate)
        maps.forEach { map ->
            require(map.key.isNotBlank()) { "local-map keys must not be blank" }
            require(map.baseAreaId in 0..0xFFFF) { "local-map base-area IDs must fit group/map identity" }
            require(map.pixelWidth > 0 && map.pixelHeight > 0) { "local-map pixel dimensions must be positive" }
            require(map.gridWidth > 0 && map.gridHeight > 0) { "local-map grid dimensions must be positive" }
            require(
                map.pixelWidth.toLong() == map.gridWidth.toLong() * LOCAL_METATILE_PIXELS &&
                    map.pixelHeight.toLong() == map.gridHeight.toLong() * LOCAL_METATILE_PIXELS,
            ) { "local-map pixel dimensions must match the metatile grid" }
            val indexed = indexedAssets[map.imageAssetKey]
            require(indexed == null || indexed.pixelWidth == map.pixelWidth && indexed.pixelHeight == map.pixelHeight) {
                "local map ${map.key} indexed raster dimensions do not match metadata"
            }
        }
    }

    private companion object { const val LOCAL_METATILE_PIXELS = 16 }
}
```

- [ ] **Step 4: Implement bounded compression, inflation, clipping, and PNG encoding.**

Create `LocalMapRasterRenderer.kt`:

```kotlin
package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

data class RasterRect(val x: Int, val y: Int, val width: Int, val height: Int)

data class RenderedMapAsset(
    val bytes: ByteArray,
    val effectiveLighting: MapLighting?,
)

object LocalMapRasterCodec {
    fun compress(indices: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        DeflaterOutputStream(output).use { it.write(indices) }
    }.toByteArray()

    fun inflate(asset: IndexedMapAsset): ByteArray = try {
        val expected = asset.pixelCount
        val output = ByteArray(expected)
        InflaterInputStream(ByteArrayInputStream(asset.compressedIndices)).use { input ->
            var offset = 0
            while (offset < output.size) {
                val read = input.read(output, offset, output.size - offset)
                require(read > 0) { "indexed map data ended before $expected pixels" }
                offset += read
            }
            require(input.read() == -1) { "indexed map data exceeds $expected pixels" }
        }
        output
    } catch (failure: IllegalArgumentException) {
        throw failure
    } catch (failure: Exception) {
        throw IllegalArgumentException("indexed map data is not valid zlib", failure)
    }
}

object LocalMapRasterRenderer {
    fun effectiveLighting(asset: IndexedMapAsset, requested: MapLighting): MapLighting =
        asset.lightingPolicy.resolve(requested)

    fun render(
        asset: IndexedMapAsset,
        requested: MapLighting,
        source: RasterRect = RasterRect(0, 0, asset.pixelWidth, asset.pixelHeight),
    ): RgbaSprite {
        require(source.x >= 0 && source.y >= 0 && source.width > 0 && source.height > 0)
        require(source.x.toLong() + source.width <= asset.pixelWidth.toLong())
        require(source.y.toLong() + source.height <= asset.pixelHeight.toLong())
        val indices = LocalMapRasterCodec.inflate(asset)
        val colors = asset.palettes[effectiveLighting(asset, requested)]
        val pixels = IntArray(source.width * source.height)
        repeat(source.height) { y ->
            repeat(source.width) { x ->
                val index = indices[(source.y + y) * asset.pixelWidth + source.x + x].toInt() and 0xff
                require(index in colors.indices) { "indexed map pixel $index has no palette color" }
                pixels[y * source.width + x] = colors[index]
            }
        }
        return RgbaSprite(source.width, source.height, pixels)
    }

    fun renderPng(asset: IndexedMapAsset, requested: MapLighting): RenderedMapAsset {
        val effective = effectiveLighting(asset, requested)
        return RenderedMapAsset(PngEncoder.encode(render(asset, requested)), effective)
    }
}

object LocalMapAssetRenderer {
    fun render(catalog: LocalMapCatalog, key: String, requested: MapLighting): RenderedMapAsset? =
        catalog.assets[key]?.let { RenderedMapAsset(it.bytes, null) }
            ?: catalog.indexedAssets[key]?.let { LocalMapRasterRenderer.renderPng(it, requested) }
}
```

- [ ] **Step 5: Run the renderer and existing Gen I/III Local-map tests.**

Run:

```bash
./gradlew :parser-core:test \
  --tests '*LocalMapRasterRendererTest' \
  --tests '*Gen1LocalMapResolverRealControlTest' \
  --tests '*Gen3LocalMapResolverRealControlTest' \
  --no-daemon --console=plain
```

Expected: synthetic renderer tests pass; real Gen I/III tests pass when their existing environment variables are configured and otherwise skip. Existing static PNG catalogs compile unchanged.

- [ ] **Step 6: Commit and push the model checkpoint.**

```bash
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/LocalMapRasterRenderer.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/LocalMapRasterRendererTest.kt
git commit -m "feat: add indexed local map rasters" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork design/gen2-dynamic-lighting
```

---

### Task 2: Emit dynamic Gen II assets and structurally resolved clock metadata

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolver.kt:3-802`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapResolution.kt:1-14`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt:236-240`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt:160-581`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolverRealControlTest.kt:1-182`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParserTest.kt:62-107`
- Reference: `D:/Temp/PokemonHacks/sources/Official/pokegold/engine/gfx/color.asm:1132-1220`
- Reference: `D:/Temp/PokemonHacks/sources/Official/pokecrystal/engine/gfx/color.asm:1206-1310`
- Reference: `D:/Temp/PokemonHacks/sources/Official/pokegold/engine/tilesets/timeofday_pals.asm`
- Reference: `D:/Temp/PokemonHacks/sources/Official/pokecrystal/engine/tilesets/timeofday_pals.asm`
- Reference: `D:/Temp/PokemonHacks/sources/Official/pokegold/ram/wram.asm:2477-2480`
- Reference: `D:/Temp/PokemonHacks/sources/Official/pokecrystal/ram/wram.asm:3062-3065`

- [ ] **Step 1: Change the real-ROM controls to require indexed assets and runtime offsets.**

Update each `Control` with `timeOfDayWramOffset` (`0x1568` for Gold/Silver and `0x1841` for Crystal). Replace PNG decoding in `assertMap` with the shared renderer:

```kotlin
assertEquals(control.mapCount, localMaps.maps.size)
assertEquals(0, localMaps.assets.size)
assertEquals(control.mapCount, localMaps.indexedAssets.size)
assertEquals(control.timeOfDayWramOffset, catalog.runtimeMetadata.gen2TimeOfDayWramOffset)
assertTrue(
    localMaps.indexedAssets.values.sumOf { it.compressedIndices.size.toLong() } <
        localMaps.maps.sumOf { it.pixelWidth.toLong() * it.pixelHeight },
)
```

Use this rendering assertion for every representative map:

```kotlin
val asset = localMaps.indexedAssets.getValue(map.imageAssetKey)
assertEquals(map.pixelWidth, asset.pixelWidth)
assertEquals(map.pixelHeight, asset.pixelHeight)
MapLighting.entries.forEach { lighting ->
    assertEquals(32, asset.palettes[lighting].size)
    assertEquals(map.pixelWidth * map.pixelHeight, LocalMapRasterRenderer.render(asset, lighting).argb.size)
}
val day = LocalMapRasterRenderer.render(asset, MapLighting.DAY)
assertEquals(expected.argbSha256, day.argbSha256())
```

For New Bark Town, assert four distinct automatic-time renders:

```kotlin
if (expected.baseAreaId == 0x1804) {
    assertEquals(LocalMapLightingPolicy.AUTO, asset.lightingPolicy)
    assertEquals(
        4,
        MapLighting.entries.map { lighting -> LocalMapRasterRenderer.render(asset, lighting).argbSha256() }.toSet().size,
    )
}
```

Implement `IntArray.argbSha256()` using the existing `MessageDigest`/`ByteBuffer` loop, removing `ImageIO` and `ByteArrayInputStream` imports.

Also extend `CatalogParserTest.optionalLocalMapResolverFailureKeepsTheAtlasUsable` with:

```kotlin
assertTrue(catalog.localMaps.indexedAssets.isEmpty())
assertEquals(null, catalog.runtimeMetadata.gen2TimeOfDayWramOffset)
```

- [ ] **Step 2: Run the official Gen II controls and verify the old PNG expectations fail.**

```bash
export DUALDEX_POKEGOLD_ROM='D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc'
export DUALDEX_POKESILVER_ROM='D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc'
export DUALDEX_POKECRYSTAL_ROM='D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc'
./gradlew :parser-core:test --tests '*Gen2LocalMapResolverRealControlTest' --no-daemon --console=plain
```

Expected: failures because Gen II still fills `assets`, does not fill `indexedAssets`, and does not publish a time-of-day WRAM offset.

- [ ] **Step 3: Carry the structurally recognized WRAM address through the optional resolution result.**

Before changing the recognizer, compare the raw-ROM opcode sequence with `LoadMapPals` in each referenced `engine/gfx/color.asm`: the first `ld a, [wTimeOfDayPal]` selects the `EnvironmentColorsPointers` row and the later load selects the `RoofPals` pair. Confirm `engine/tilesets/timeofday_pals.asm` defines the four normalized values and `ram/wram.asm` places the symbol in WRAM. Do not compile source-derived addresses or labels into production code.

Extend `CatalogRuntimeMetadata`:

```kotlin
data class CatalogRuntimeMetadata(
    val gen2TimeOfDayWramOffset: Int? = null,
    val gen3SaveBlock1PointerAddress: Long? = null,
    val gen3RuntimeMemoryLayout: CatalogGen3RuntimeMemoryLayout? = null,
    val areaNamesByBaseId: Map<Int, String> = emptyMap(),
) {
    fun validate(): CatalogRuntimeMetadata = apply {
        require(gen2TimeOfDayWramOffset == null || gen2TimeOfDayWramOffset in 0 until 0x2000) {
            "Gen II time-of-day offset must remain inside WRAM"
        }
    }
}
```

Extend `LocalMapResolution.Resolved`:

```kotlin
data class Resolved(
    val catalog: LocalMapCatalog,
    val reasons: List<String>,
    val skippedMaps: Int = 0,
    val gen2TimeOfDayWramOffset: Int? = null,
) : LocalMapResolution
```

In `parsePaletteAuthorityAt`, bind the already recognized `ld a, [nn]` operand and require the roof path to use the same address:

```kotlin
val timeOfDayAddress = rom.u16le(offset + 17)
require(timeOfDayAddress in WRAM_START until WRAM_END)
require(rom.u16le(roofOperand + 11) == timeOfDayAddress)
PaletteAuthority(
    bank,
    environmentPointers,
    tilesetPalettes,
    roofPalettes,
    timeOfDayAddress - WRAM_START,
)
```

Add `timeOfDayWramOffset: Int` to `PaletteAuthority`. Prevent two consumers that differ only in clock/palette operands from collapsing by replacing both distinctness keys:

```kotlin
}.distinctBy {
    listOf(it.environmentPointers, it.tilesetPalettes, it.roofPalettes, it.timeOfDayWramOffset)
}
```

```kotlin
}.distinctBy { authority ->
    listOf(
        authority.groups.tableOffset,
        authority.tilesets.root,
        authority.roofs.table,
        authority.palettes.environmentPointers,
        authority.palettes.tilesetPalettes,
        authority.palettes.roofPalettes,
        authority.palettes.timeOfDayWramOffset,
        authority.tilePaletteBank,
    )
}
```

Define:

```kotlin
private const val WRAM_START = 0xc000
private const val WRAM_END = 0xe000
```

Return the offset from `Gen2LocalMapResolver.resolve`:

```kotlin
gen2TimeOfDayWramOffset = authority.palettes.timeOfDayWramOffset,
```

In `CatalogParser`, merge only that optional field after Local-map resolution:

```kotlin
val finalRuntimeMetadata = runtimeMetadata.copy(
    gen2TimeOfDayWramOffset =
        (localMapResolution as? LocalMapResolution.Resolved)?.gen2TimeOfDayWramOffset,
).validate()
```

Use `finalRuntimeMetadata` in the final `ParsedCatalog`; do not alter relationship-phase Gen III metadata.

- [ ] **Step 4: Replace Gen II RGBA/PNG generation with a compressed index surface.**

Use `linkedMapOf<String, IndexedMapAsset>()` for Gen II assets. Replace `render`/`drawTile` with index-only methods:

```kotlin
private fun renderIndices(
    map: MapDescriptor,
    tileset: TilesetData,
    tiles: IndexedSprite,
): ByteArray {
    val pixelWidth = map.gridWidth * METATILE_PIXELS
    val pixels = ByteArray(map.pixelCount.toInt())
    repeat(map.blockHeight) { blockY ->
        repeat(map.blockWidth) { blockX ->
            val blockId = map.blockIds[blockY * map.blockWidth + blockX].toInt() and 0xff
            repeat(TILES_PER_BLOCK) { tileIndex ->
                val tileId = tileset.metatiles[blockId * TILES_PER_BLOCK + tileIndex].toInt() and 0xff
                drawIndexedTile(
                    tiles,
                    tileId,
                    tileset.paletteIndex(tileId),
                    pixels,
                    pixelWidth,
                    blockX * BLOCK_PIXELS + tileIndex % BLOCK_TILE_EDGE * TILE_PIXELS,
                    blockY * BLOCK_PIXELS + tileIndex / BLOCK_TILE_EDGE * TILE_PIXELS,
                )
            }
        }
    }
    return pixels
}

private fun drawIndexedTile(
    tiles: IndexedSprite,
    tileId: Int,
    paletteIndex: Int,
    pixels: ByteArray,
    pixelWidth: Int,
    originX: Int,
    originY: Int,
) {
    repeat(TILE_PIXELS) { y ->
        repeat(TILE_PIXELS) { x ->
            val colorIndex = tiles.indexAt(tileId * TILE_PIXELS + x, y)
            pixels[(originY + y) * pixelWidth + originX + x] =
                (paletteIndex * COLORS_PER_PALETTE + colorIndex).toByte()
        }
    }
}
```

Convert palette mode and all four color rows:

```kotlin
private val MapDescriptor.lightingPolicy: LocalMapLightingPolicy
    get() = when (paletteMode) {
        PALETTE_AUTO -> LocalMapLightingPolicy.AUTO
        PALETTE_DAY -> LocalMapLightingPolicy.DAY
        PALETTE_NITE -> LocalMapLightingPolicy.NIGHT
        PALETTE_MORN -> LocalMapLightingPolicy.MORNING
        PALETTE_DARK -> LocalMapLightingPolicy.DARK
        else -> error("unsupported Gen II map palette mode $paletteMode")
    }
```

```kotlin
fun palettesFor(rom: RomImage, map: MapDescriptor): MapLightingPalettes = MapLightingPalettes(
    morning = colorsFor(rom, map, MORN_TIME),
    day = colorsFor(rom, map, DAY_TIME),
    night = colorsFor(rom, map, NITE_TIME),
    dark = colorsFor(rom, map, DARK_TIME),
)

private fun colorsFor(rom: RomImage, map: MapDescriptor, time: Int): IntArray {
    require(time in MORN_TIME..DARK_TIME)
    val environmentColors = requireNotNull(
        rom.gbBankAddress(bank, rom.u16le(environmentPointers + map.environment * 2)),
    )
    val colors = IntArray(ACTIVE_PALETTE_COUNT * COLORS_PER_PALETTE)
    repeat(ACTIVE_PALETTE_COUNT) { palette ->
        val paletteIndex = rom.u8(environmentColors + time * ACTIVE_PALETTE_COUNT + palette)
        repeat(COLORS_PER_PALETTE) { color ->
            val bgr555 = rom.u16le(
                tilesetPalettes + paletteIndex * PALETTE_BYTES + color * COLOR_BYTES,
            )
            colors[palette * COLORS_PER_PALETTE + color] =
                TileRenderer.bgr555ToArgb(bgr555, transparent = false)
        }
    }
    if (map.environment in OUTDOOR_ENVIRONMENTS) {
        val roofTimeOffset = if (time >= NITE_TIME) ROOF_TIME_PALETTE_BYTES else 0
        val source = roofPalettes + map.group * ROOF_PALETTE_BYTES + roofTimeOffset
        repeat(ROOF_OVERRIDE_COLOR_COUNT) { color ->
            val bgr555 = rom.u16le(source + color * COLOR_BYTES)
            colors[
                ROOF_PALETTE_INDEX * COLORS_PER_PALETTE +
                    ROOF_OVERRIDE_COLOR_START + color
            ] = TileRenderer.bgr555ToArgb(bgr555, transparent = false)
        }
    }
    return colors
}
```

For each descriptor, create:

```kotlin
val indices = renderIndices(descriptor, tileset, tiles)
val asset = IndexedMapAsset(
    pixelWidth = descriptor.gridWidth * METATILE_PIXELS,
    pixelHeight = descriptor.gridHeight * METATILE_PIXELS,
    compressedIndices = LocalMapRasterCodec.compress(indices),
    lightingPolicy = descriptor.lightingPolicy,
    palettes = authority.palettes.palettesFor(session.rom, descriptor),
).validate()
encodedBytes += asset.compressedIndices.size
assets[descriptor.assetKey] = asset
```

Build the result with:

```kotlin
catalog = LocalMapCatalog(maps = maps, indexedAssets = assets).validate()
```

Keep the 64 MiB encoded budget, but rename its reason to `compressed-assets` and its diagnostic to `compressed local-map index assets exceed ...`. Remove `PngEncoder` and `RgbaSprite` imports from the resolver.

- [ ] **Step 5: Run parser unit tests and all three official controls.**

```bash
./gradlew :parser-core:test \
  --tests '*LocalMapRasterRendererTest' \
  --tests '*CatalogParserTest' \
  --tests '*Gen2LocalMapResolverRealControlTest' \
  --tests '*Gen2WorldMapResolverTest' \
  --no-daemon --console=plain
```

Expected: Gold 368, Silver 368, Crystal 388 indexed assets; existing daytime ARGB hashes unchanged; New Bark has four distinct renders; runtime offsets are `0x1568`, `0x1568`, and `0x1841`; World Map remains available.

- [ ] **Step 6: Commit and push the parser checkpoint.**

```bash
git add parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolver.kt \
  parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/LocalMapResolution.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParserTest.kt \
  parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2LocalMapResolverRealControlTest.kt
git commit -m "feat: emit dynamic Gen 2 map lighting" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork design/gen2-dynamic-lighting
```

---

### Task 3: Persist compressed assets and runtime metadata

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt:131-180`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt:3-21`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt:63-203,313-356`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt:674-689`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Add failing synthetic and Crystal cache assertions.**

In the first catalog round-trip test, add a second Local map backed by:

```kotlin
val indexedKey = "local/0103/map"
val indexedPalettes = MapLightingPalettes(
    morning = IntArray(32) { 0xff100000.toInt() + it },
    day = IntArray(32) { 0xff200000.toInt() + it },
    night = IntArray(32) { 0xff300000.toInt() + it },
    dark = IntArray(32) { 0xff400000.toInt() + it },
)
val indexedAsset = IndexedMapAsset(
    32,
    32,
    LocalMapRasterCodec.compress(ByteArray(32 * 32) { (it % 32).toByte() }),
    LocalMapLightingPolicy.AUTO,
    indexedPalettes,
)
```

Store it in `indexedAssets`, set `runtimeMetadata = CatalogRuntimeMetadata(gen2TimeOfDayWramOffset = 0x1841)`, and assert exact reopened compressed bytes, palettes, policy, and offset.

Change both parser schema assertions from 14 to 15.

Change the Crystal round-trip assertions to:

```kotlin
assertEquals(388, reopened.localMaps.maps.size)
assertTrue(reopened.localMaps.assets.isEmpty())
assertEquals(catalog.localMaps.indexedAssets.keys, reopened.localMaps.indexedAssets.keys)
val battleTowerAsset = catalog.localMaps.maps.single { it.baseAreaId == 0x1610 }.imageAssetKey
assertEquals(
    catalog.localMaps.indexedAssets.getValue(battleTowerAsset),
    reopened.localMaps.indexedAssets.getValue(battleTowerAsset),
)
assertEquals(0x1841, reopened.runtimeMetadata.gen2TimeOfDayWramOffset)
```

Add a production progress-policy test around an internal `changedCatalogSections` function:

```kotlin
@Test
fun extendedProgressPersistsMapAndRuntimeSections() {
    assertTrue(changedCatalogSections("EXTENDED").containsAll(setOf("runtime_metadata", "world_maps", "local_maps")))
    assertEquals(emptySet<String>(), changedCatalogSections("COMPLETE"))
}
```

- [ ] **Step 2: Run store/runtime tests and verify schema and progress failures.**

```bash
./gradlew :catalog-store:test --tests '*CatalogStoreTest' \
  :app:testDebugUnitTest --tests '*ProductionCompanionRuntimeTest.extendedProgressPersistsMapAndRuntimeSections' \
  --no-daemon --console=plain
```

Expected: failures on schema 14, Crystal PNG assumptions, runtime metadata validation, and the inaccessible/incomplete progress-section helper.

- [ ] **Step 3: Validate decoded runtime metadata and bump the parser schema.**

In `CatalogReader.decode`, change runtime decoding to:

```kotlin
runtimeMetadata = decode<CatalogRuntimeMetadata>(
    sections.getValue("runtime_metadata"),
    runtimeMetadataType,
).validate(),
```

Set:

```kotlin
const val parserSchemaVersion = 15
```

The existing schema mismatch path invalidates old catalogs; do not add an in-place migration.

- [ ] **Step 4: Persist final map/runtime sections during progressive parsing.**

Move `changedCatalogSections` outside `ProductionCompanionRuntime` as an `internal` top-level function and use:

```kotlin
internal fun changedCatalogSections(phase: String): Set<String> = when (phase) {
    "ESSENTIAL" -> com.darkaxt.dualdex.catalog.CatalogSchema.requiredSections
    "SPECIES_MEDIA" -> setOf("species")
    "RELATIONSHIPS" -> setOf("species", "encounters", "runtime_metadata")
    "EXTENDED" -> setOf(
        "species",
        "moves",
        "abilities",
        "capture_balls",
        "learnset_rulesets",
        "runtime_metadata",
        "world_maps",
        "local_maps",
        "capabilities",
        "diagnostics",
    )
    "COMPLETE" -> emptySet()
    else -> com.darkaxt.dualdex.catalog.CatalogSchema.requiredSections
}
```

This keeps Atlas usable after a progressive cache reopen and ensures the newly resolved Gen II offset and indexed Local assets replace the empty essential-phase sections.

- [ ] **Step 5: Run cache tests with the official Crystal control.**

```bash
export DUALDEX_POKECRYSTAL_ROM='D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc'
./gradlew :catalog-store:test --tests '*CatalogStoreTest' \
  :app:testDebugUnitTest --tests '*ProductionCompanionRuntimeTest' \
  --no-daemon --console=plain
```

Expected: schema 15; synthetic PNG and indexed assets both round-trip; all 388 Crystal indexed assets and offset `0x1841` reopen exactly; Gen I/III static tests remain unchanged.

- [ ] **Step 6: Commit and push the persistence checkpoint.**

```bash
git add catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt \
  catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt \
  catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt \
  app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt \
  app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "feat: persist dynamic local maps" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork design/gen2-dynamic-lighting
```

---

### Task 4: Publish map lighting through companion state and API

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt:96-139`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt:34-114`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt:33-42,173-200,434-445,518-535`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`

- [ ] **Step 1: Add failing reducer and API projection tests.**

Add to `CompanionGatewayTest`:

```kotlin
@Test
fun liveMapLightingChangesIndependentlyFromAreaAndPosition() {
    val gateway = CompanionGateway()
    gateway.dispatch(CompanionAction.LiveAreaChanged(0x1803))
    gateway.dispatch(CompanionAction.LiveMapPositionChanged(LiveMapPosition(14, 9)))

    val night = gateway.dispatch(CompanionAction.LiveMapLightingChanged(MapLighting.NIGHT))
    val disconnected = gateway.dispatch(CompanionAction.LiveMapLightingChanged(null))

    assertEquals(MapLighting.NIGHT, night.liveMapLighting)
    assertEquals(LiveMapPosition(14, 9), night.liveMapPosition)
    assertEquals(null, disconnected.liveMapLighting)
    assertEquals(0x1803, disconnected.liveAreaBaseId)
}
```

In `ApiViewBuilderTest`, construct one indexed Local asset and assert:

```kotlin
assertTrue(ApiViewBuilder.catalog(catalog).localMaps.single().dynamicLighting)
val state = ApiViewBuilder.state(AppSnapshot(liveMapLighting = MapLighting.NIGHT), catalog)
assertEquals("NIGHT", state.currentMapLighting)
```

Also retain a static PNG Local fixture and assert `dynamicLighting == false`.

- [ ] **Step 2: Run companion-core tests and verify missing state/API fields.**

```bash
./gradlew :companion-core:test \
  --tests '*CompanionGatewayTest' \
  --tests '*ApiViewBuilderTest' \
  --no-daemon --console=plain
```

Expected: compilation fails on `liveMapLighting`, `LiveMapLightingChanged`, `currentMapLighting`, and `dynamicLighting`.

- [ ] **Step 3: Add the state field/action and reducer branch.**

Import parser `MapLighting`, then insert this property in `AppSnapshot` immediately after `liveMapPosition`:

```kotlin
val liveMapPosition: LiveMapPosition? = null,
val liveMapLighting: MapLighting? = null,
val battle: BattleState? = null,
```

```kotlin
data class LiveMapLightingChanged(val lighting: MapLighting?) : CompanionAction
```

Add to `CompanionGateway.reduce`:

```kotlin
is CompanionAction.LiveMapLightingChanged -> state.copy(liveMapLighting = action.lighting)
```

Lighting is global clock state and must not be cleared by `LiveAreaChanged`.

- [ ] **Step 4: Add API fields and dynamic-asset metadata.**

Extend `LocalMapView`:

```kotlin
val imageUrl: String,
val dynamicLighting: Boolean,
```

Extend `StateView` immediately after `currentMapPosition`:

```kotlin
val currentMapLighting: String?,
```

Populate catalog and state projections:

```kotlin
dynamicLighting = map.imageAssetKey in catalog.localMaps.indexedAssets,
```

```kotlin
snapshot.liveMapPosition?.let { MapPositionView(it.x, it.y) },
snapshot.liveMapLighting?.name,
currentAreaSpeciesIds,
```

- [ ] **Step 5: Run the full companion-core suite.**

```bash
./gradlew :companion-core:test --no-daemon --console=plain
```

Expected: all tests pass; null lighting serializes as null; static Local maps advertise false; indexed Local maps advertise true.

- [ ] **Step 6: Commit and push the state/API checkpoint.**

```bash
git add companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt \
  companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt \
  companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt \
  companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt \
  companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt
git commit -m "feat: publish live map lighting state" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork design/gen2-dynamic-lighting
```

---

### Task 5: Read and publish the Gen II game clock

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt:17-75,110-162,202-229,336-477,752-820`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt:23-62,255-303,423-431`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt:72-80`
- Modify: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt:508-562,683-697`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt:203-252`

- [ ] **Step 1: Extend the Crystal memory test with discovery, cached, invalid, and disconnect lighting behavior.**

In `readsGen2ThroughTheGameBoyCoreIdentityAndPublishesCrystalBattles`, add:

```kotlin
wram[0x1841] = 2
val lightings = mutableListOf<MapLighting?>()
```

Pass `lightingPublisher = lightings::add`, then assert after discovery:

```kotlin
assertEquals(listOf(MapLighting.NIGHT), lightings)
```

Before the cached transition, set:

```kotlin
wram[0x1841] = 3
```

After two heartbeats, assert:

```kotlin
assertEquals(MapLighting.DARK, lightings.last())
assertTrue(transport.commands.any { it.startsWith("READ_CORE_MEMORY d841 1") })
val publications = lightings.size
repeat(2) { coordinator.heartbeat() }
assertEquals(publications, lightings.size)
```

Then exercise invalid and disconnect states:

```kotlin
wram[0x1841] = 4
repeat(2) { coordinator.heartbeat() }
assertEquals(null, lightings.last())
coordinator.updateSession(false, null, null)
assertEquals(1, lightings.count { it == null })
```

Set `gen2TimeOfDayWramOffset = 0x1841` in `gen2Context()`.

In `ProductionCompanionRuntimeTest.exposesGen1Gen2AndGen3CatalogsAsProductionBattleContexts`, give the Crystal catalog `CatalogRuntimeMetadata(gen2TimeOfDayWramOffset = 0x1841)` and assert the battle context exposes it. Add a focused state test:

```kotlin
runtime.updateLiveMapLighting(MapLighting.NIGHT)
assertEquals("NIGHT", runtime.stateView().currentMapLighting)
runtime.updateLiveMapLighting(null)
assertEquals(null, runtime.stateView().currentMapLighting)
```

- [ ] **Step 2: Run app tests and verify the missing context, publisher, and runtime methods.**

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*BattleMemoryCoordinatorTest.readsGen2ThroughTheGameBoyCoreIdentityAndPublishesCrystalBattles' \
  --tests '*ProductionCompanionRuntimeTest' \
  --no-daemon --console=plain
```

Expected: compilation fails on the new offset, publisher, and runtime update method.

- [ ] **Step 3: Extend the battle context and cached Gen II read set.**

Add to `BattleCatalogContext`:

```kotlin
val gen2TimeOfDayWramOffset: Int? = null,
```

Add to `BattleMemoryCoordinator` constructor and state:

```kotlin
private val lightingPublisher: (MapLighting?) -> Unit = {},
```

```kotlin
private var lastPublishedMapLighting: MapLighting? = null
```

In the cached Gen II `buildList`, add exactly one byte when metadata is available:

```kotlin
context.gen2TimeOfDayWramOffset?.let { offset ->
    add(CoreMemoryRegion("live-lighting", GEN1_WRAM_BASE + offset, 1))
}
```

Implement bounded decoding and change-only publication:

```kotlin
private fun resolveCurrentGen2Lighting(
    regions: Map<String, ByteArray>,
    context: BattleCatalogContext,
): MapLighting? {
    if (context.generation != 2) return null
    val offset = context.gen2TimeOfDayWramOffset ?: return null
    val value = regions["live-lighting"]?.singleOrNull()?.toInt()?.and(0xff)
        ?: regions["wram"]?.getOrNull(offset)?.toInt()?.and(0xff)
        ?: return null
    return MapLighting.entries.getOrNull(value)
}

private fun publishMapLighting(lighting: MapLighting?) {
    if (lighting != lastPublishedMapLighting) {
        lastPublishedMapLighting = lighting
        lightingPublisher(lighting)
    }
}
```

Call `publishMapLighting(resolveCurrentGen2Lighting(regions, context))` once per Gen II `process`. Clear stale lighting at every session/error boundary with these exact insertions:

```kotlin
if (!nextEligible || sessionIdentity != nextIdentity || sessionGeneration != nextGeneration) {
    publishMapLighting(null)
}
```

Add `publishMapLighting(null)` to the `CoreMemoryReadState.Failed` branch before closing the transport. Replace `safeHeartbeat` with:

```kotlin
private fun safeHeartbeat() {
    runCatching(::heartbeat).onFailure {
        synchronized(this) { publishMapLighting(null) }
    }
}
```

Call `publishMapLighting(null)` inside the synchronized block in `close`. The nullable last-value guard must suppress duplicate null callbacks while still clearing a previously published valid mode.

- [ ] **Step 4: Wire parsed metadata and companion state through the Android runtime.**

In `ProductionCompanionRuntime.battleCatalogContext`, pass:

```kotlin
gen2TimeOfDayWramOffset = current.runtimeMetadata.gen2TimeOfDayWramOffset,
```

Add:

```kotlin
@Synchronized
fun updateLiveMapLighting(lighting: MapLighting?) {
    if (gateway.bootstrap().liveMapLighting != lighting) {
        gateway.dispatch(CompanionAction.LiveMapLightingChanged(lighting))
    }
}
```

In `RetroArchSetupCoordinator`, wire:

```kotlin
lightingPublisher = runtime::updateLiveMapLighting,
```

- [ ] **Step 5: Run the complete battle-memory and production runtime tests.**

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*BattleMemoryCoordinatorTest' \
  --tests '*ProductionCompanionRuntimeTest' \
  --no-daemon --console=plain
```

Expected: discovery uses the existing full 8 KiB WRAM read; cached mode adds only `READ_CORE_MEMORY d841 1`; NIGHT→DARK publishes once per change; invalid/disconnected state publishes one null; existing location, position, battle, and party behavior remains green.

- [ ] **Step 6: Commit and push the runtime-memory checkpoint.**

```bash
git add app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt \
  app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt \
  app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt \
  app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt \
  app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "feat: read Gen 2 map lighting" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork design/gen2-dynamic-lighting
```

---

### Task 6: Render lighting variants through both map endpoints

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt:540-552`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt:123-215`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt:70-133`
- Modify: `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexRuntime.kt:157-169`
- Modify: `companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt:131-146`
- Modify: `companion-server/src/test/kotlin/com/enrpau/dualscreendex/server/ServerContractTest.kt:42-77`

- [ ] **Step 1: Add endpoint tests for day fallback, night recoloring, ETags, invalid modes, and static compatibility.**

Create a two-pixel indexed fixture in each server test:

```kotlin
val palettes = MapLightingPalettes(
    morning = IntArray(32) { 0xff110000.toInt() + it },
    day = IntArray(32) { 0xff220000.toInt() + it },
    night = IntArray(32) { 0xff330000.toInt() + it },
    dark = IntArray(32) { 0xff440000.toInt() + it },
)
val asset = IndexedMapAsset(
    16,
    16,
    LocalMapRasterCodec.compress(ByteArray(256) { (it % 32).toByte() }),
    LocalMapLightingPolicy.AUTO,
    palettes,
)
```

Build a `LocalMapCatalog` with `indexedAssets`. Request omitted lighting, `?lighting=DAY`, `?lighting=NIGHT`, and `?lighting=INVALID`. After the runtime accepts the valid catalog, corrupt a copy of `asset.compressedIndices` and request that asset once to exercise endpoint failure isolation. Assert:

```kotlin
assertEquals(200, omitted.responseCode)
assertEquals(200, day.responseCode)
assertEquals(200, night.responseCode)
assertTrue(omitted.inputStream.readBytes().contentEquals(day.inputStream.readBytes()))
assertTrue(day.getHeaderField("ETag") != night.getHeaderField("ETag"))
assertEquals(400, invalid.responseCode)
assertEquals(404, corrupt.responseCode)
```

Decode one day and one night PNG with `ImageIO.read` before consuming streams and assert the first ARGB pixel equals `palettes.day[0]` and `palettes.night[0]`. Keep the existing static PNG request and assert adding `?lighting=NIGHT` does not change its body or static ETag variant.

- [ ] **Step 2: Run both endpoint contract tests and verify that query lighting is ignored.**

```bash
./gradlew :app:testDebugUnitTest --tests '*AndroidLoopbackServerTest' \
  :companion-server:test --tests '*ServerContractTest' \
  --no-daemon --console=plain
```

Expected: dynamic fixture lookup fails because both runtimes only inspect `localMaps.assets`, and invalid lighting is not rejected.

- [ ] **Step 3: Return encoded map metadata from both runtimes.**

Change both runtime methods to:

```kotlin
@Synchronized
fun mapAsset(key: String, requestedLighting: MapLighting): RenderedMapAsset? = catalog?.let { current ->
    LocalMapAssetRenderer.render(current.localMaps, key, requestedLighting)
        ?: current.worldMaps.assets[key]?.let { RenderedMapAsset(PngEncoder.encode(it), null) }
}
```

Static PNG and World Map results carry `effectiveLighting = null`. Indexed assets carry their policy-resolved mode.

- [ ] **Step 4: Validate the query and include the rendered variant in HTTP caching.**

In each server, parse:

```kotlin
private fun requestedLighting(value: String?): MapLighting = if (value == null) {
    MapLighting.DAY
} else {
    requireNotNull(MapLighting.entries.singleOrNull { it.name == value.uppercase() }) {
        "unsupported map lighting: $value"
    }
}
```

Android routes the whole request to `mapResponse(request)`; desktop reads `query(exchange.requestURI.rawQuery)["lighting"]`. Lookup becomes:

```kotlin
val requested = requestedLighting(queryValue)
val rendered = runCatching { runtime.mapAsset(key, requested) }.getOrNull()
    ?: return mapNotAvailable
```

Keep `requestedLighting` outside `runCatching`, so malformed query values reach the existing HTTP 400 boundary. Catch only lookup/render failures and translate them to each server's existing 404 `map not available` response; the companion runtime must remain alive.

Use `rendered.bytes` for the response. Build ETags with a stable variant:

```kotlin
val variant = rendered.effectiveLighting?.name ?: "STATIC"
"${catalogHash}-map-${key.hashCode()}-$variant"
```

Retain `Cache-Control: public, max-age=31536000, immutable`; the query URL and ETag now distinguish dynamic variants. Missing lighting canonically renders day. Unknown values produce HTTP 400 through each server's existing error boundary.

- [ ] **Step 5: Run endpoint and renderer suites.**

```bash
./gradlew :parser-core:test --tests '*LocalMapRasterRendererTest' \
  :app:testDebugUnitTest --tests '*AndroidLoopbackServerTest' \
  :companion-server:test --tests '*ServerContractTest' \
  --no-daemon --console=plain
```

Expected: omitted and DAY bytes match; NIGHT differs; ETags identify effective modes; forced map policies return their forced ETag mode; static and World Map bytes remain unchanged; invalid lighting returns 400; malformed indexed data returns map-not-available 404 without stopping the runtime; traversal remains 404.

- [ ] **Step 6: Commit and push the endpoint checkpoint.**

```bash
git add app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt \
  app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt \
  app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt \
  companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexRuntime.kt \
  companion-server/src/main/kotlin/com/enrpau/dualscreendex/server/DualDexServer.kt \
  companion-server/src/test/kotlin/com/enrpau/dualscreendex/server/ServerContractTest.kt
git commit -m "feat: render dynamic map endpoints" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork design/gen2-dynamic-lighting
```

---

### Task 7: Switch the active Local-map URL without resetting its viewport

**Files:**
- Modify: `companion-web/src/models.ts:73-82,169-206`
- Modify: `companion-web/src/pages/MapPage.tsx:18-40,175-210`
- Modify: `companion-web/src/pages/MapPage.test.tsx:145-185`

- [ ] **Step 1: Add a failing dynamic-lighting rerender test.**

Keep the existing static fixture with `dynamicLighting: false`. Add a dynamic variant and test:

```tsx
it('changes only the dynamic Local image URL when game lighting changes and preserves zoom', () => {
  const dynamicCatalog: Catalog = {
    ...localCatalog,
    localMaps: localCatalog.localMaps!.map(map => ({ ...map, dynamicLighting: true })),
  };
  const view = render(<MapPage
    catalog={dynamicCatalog}
    state={{ ...state, currentMapLighting: 'DAY' }}
    onOpenAreaDex={vi.fn()}
    onOpenSettings={vi.fn()}
  />);
  const stage = screen.getByRole('region', { name: 'Interactive local map' });
  fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }));
  const zoomedScale = stage.dataset.scale;
  expect(view.container.querySelector('.map-plane img')?.getAttribute('src'))
    .toBe('/api/maps/local%2F0010%2Fmap.png?lighting=DAY');

  view.rerender(<MapPage
    catalog={dynamicCatalog}
    state={{ ...state, currentMapLighting: 'NIGHT' }}
    onOpenAreaDex={vi.fn()}
    onOpenSettings={vi.fn()}
  />);

  expect(view.container.querySelector('.map-plane img')?.getAttribute('src'))
    .toBe('/api/maps/local%2F0010%2Fmap.png?lighting=NIGHT');
  expect(stage.dataset.scale).toBe(zoomedScale);
});
```

Add a null fallback assertion:

```tsx
view.rerender(<MapPage
  catalog={dynamicCatalog}
  state={{ ...state, currentMapLighting: null }}
  onOpenAreaDex={vi.fn()}
  onOpenSettings={vi.fn()}
/>);
expect(view.container.querySelector('.map-plane img')?.getAttribute('src'))
  .toBe('/api/maps/local%2F0010%2Fmap.png?lighting=DAY');
```

Retain the existing static test expectation exactly as `/api/maps/local%2F0010%2Fmap.png` even if state contains `currentMapLighting: 'NIGHT'`.

- [ ] **Step 2: Install deterministic web dependencies and run the focused test.**

```bash
cd companion-web && npm ci && npm test -- src/pages/MapPage.test.tsx
```

Expected: TypeScript fails because `dynamicLighting` and `currentMapLighting` are absent; after adding the model fields but before changing `MapPage`, the URL remains static.

- [ ] **Step 3: Extend TypeScript API models.**

Add:

```ts
export type MapLighting = 'MORNING' | 'DAY' | 'NIGHT' | 'DARK';
```

Extend `LocalMapView`:

```ts
dynamicLighting: boolean;
```

Extend `State`:

```ts
currentMapLighting?: MapLighting | null;
```

Update all typed Local-map fixtures to provide `dynamicLighting`.

- [ ] **Step 4: Derive the active image URL without adding lighting to effect dependencies.**

In `MapPage`, derive:

```tsx
const localImageUrl = localMap?.dynamicLighting
  ? `${localMap.imageUrl}?lighting=${state.currentMapLighting ?? 'DAY'}`
  : localMap?.imageUrl;
const activeImageUrl = activeMode === 'LOCAL' ? localImageUrl : region?.imageUrl;
```

Render:

```tsx
<img
  src={activeImageUrl}
  alt={`${displayName} ${activeMode === 'LOCAL' ? 'local' : 'region'} map`}
  draggable={false}
/>
```

Do not add lighting to the viewport reset effect (`[activeMode, activeMap?.key]`), the fit effect, region selection, or gesture state.

- [ ] **Step 5: Run web tests and production build.**

```bash
cd companion-web && npm test -- src/pages/MapPage.test.tsx && npm run build
```

Expected: dynamic Local URLs switch DAY→NIGHT; null uses DAY; zoom remains unchanged; static Local and Atlas URLs have no lighting query; TypeScript and Vite build pass.

- [ ] **Step 6: Commit and push the web checkpoint.**

```bash
git add companion-web/src/models.ts companion-web/src/pages/MapPage.tsx companion-web/src/pages/MapPage.test.tsx
git commit -m "feat: switch local maps with game lighting" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
git push fork design/gen2-dynamic-lighting
```

---

### Task 8: Verify official GBC controls, publish, and clean disposable outputs

**Files:**
- Create outside the repository: `D:/Temp/PokemonHacks/validation/map-forensics/2026-08-20-official-gen2-dynamic-lighting.md`
- Modify repository files only if a directly affected Stage 1 test exposes a defect; commit that correction separately before publication.

- [ ] **Step 1: Run the three official parser controls twice for deterministic output.**

```bash
export DUALDEX_POKEGOLD_ROM='D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc'
export DUALDEX_POKESILVER_ROM='D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc'
export DUALDEX_POKECRYSTAL_ROM='D:/Temp/PokemonHacks/roms/official/Gen I-II/Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc'
./gradlew :parser-core:test --tests '*Gen2LocalMapResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
./gradlew :parser-core:test --tests '*Gen2LocalMapResolverRealControlTest' --rerun-tasks --no-daemon --console=plain
```

Expected both times: Gold 368, Silver 368, Crystal 388 indexed assets; accepted daytime hashes unchanged; all four lighting modes render; WRAM offsets `0x1568`, `0x1568`, and `0x1841`; no Gen II Local-map PNG assets retained.

- [ ] **Step 2: Run directly affected Kotlin suites in parallel.**

```bash
./gradlew \
  :parser-core:test \
  :catalog-store:test \
  :companion-core:test \
  :companion-server:test \
  :app:testDebugUnitTest \
  --parallel --no-daemon --console=plain
```

Expected: zero failures. Do not add or run GBA real-ROM controls for this stage.

- [ ] **Step 3: Run the web suite/build and Android debug build.**

```bash
cd companion-web && npm test && npm run build
cd .. && ./gradlew :app:assembleDebug --no-daemon --console=plain
```

Expected: all Vitest tests pass, Vite production build passes, and `app/build/outputs/apk/debug/app-debug.apk` is produced.

- [ ] **Step 4: Record retained validation evidence.**

Write `D:/Temp/PokemonHacks/validation/map-forensics/2026-08-20-official-gen2-dynamic-lighting.md` with these concrete observations from the successful commands:

- Git commit under validation.
- The three SHA-256 controls already locked in `Gen2LocalMapResolverRealControlTest`.
- Map/indexed-asset counts and total compressed-index bytes for each ROM.
- Structurally resolved WRAM offsets and the matching source-oracle consumers under `sources/Official/pokegold/engine/gfx/color.asm` and `sources/Official/pokecrystal/engine/gfx/color.asm`.
- Source-oracle confirmation from each `engine/tilesets/timeofday_pals.asm`, `data/maps/environment_colors.asm`, and `ram/wram.asm`; explicitly state that production authority remained raw ROM bytes.
- Morning/day/night/dark representative ARGB hashes printed from the accepted test fixtures.
- Cached command evidence showing `READ_CORE_MEMORY d841 1` and change-only publication.
- Catalog round-trip, endpoint, web viewport, and Android build outcomes.
- Explicit statement that Stage 1 ran no GBA investigation.

- [ ] **Step 5: Run repository integrity checks.**

```bash
git diff --check
git status --short
git log --oneline fork/master..HEAD
```

Expected: no whitespace errors; only deliberate source/test/docs commits are present; no ROMs, reports, APKs, `node_modules`, Gradle build directories, credentials, or personal files are staged.

- [ ] **Step 6: Publish the validated fast-forward to `fork/master`.**

```bash
git fetch fork master
git merge-base --is-ancestor fork/master HEAD
git push fork HEAD:master
git ls-remote fork refs/heads/master
```

Expected: the ancestry check succeeds and the remote master hash equals local `HEAD`. If master advanced, stop before pushing and integrate it in the worktree, rerun Steps 1-5, then publish.

- [ ] **Step 7: Remove disposable dependencies and build outputs after preserving the evidence.**

```bash
./gradlew clean
rm -rf companion-web/node_modules companion-web/dist
```

Expected: source, committed specifications/plans, retained validation evidence, and Git history remain; disposable Gradle/Vite/npm outputs are removed. Confirm with:

```bash
git status --short --branch
```

Expected: clean branch tracking `fork/design/gen2-dynamic-lighting` (the branch may remain until post-publication review).
