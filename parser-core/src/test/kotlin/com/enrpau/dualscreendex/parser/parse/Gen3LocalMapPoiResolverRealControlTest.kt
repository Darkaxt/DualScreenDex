package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3LocalMapPoiResolverRealControlTest {
    @Test
    fun `official Emerald exposes Fallarbor hidden Nugget from MapEvents`() {
        val localMaps = parse("DUALDEX_OFFICIAL_EMERALD_ROM", EMERALD_SHA).localMaps
        val pois = localMaps.pois.filter { it.baseAreaId == 0x000D }

        val nugget = pois.single { it.tileX == 2 && it.tileY == 15 }
        assertEquals(LocalMapPoiKind.HIDDEN_ITEM, nugget.kind)
        assertEquals(LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE, nugget.organicVisibility)
        assertEquals(110, nugget.item?.itemId)
        assertTrue(nugget.key.startsWith("local/000d/bg/"))

        val route102Potion = localMaps.pois.single {
            it.baseAreaId == 0x0011 && it.tileX == 11 && it.tileY == 15
        }
        assertEquals(LocalMapPoiKind.VISIBLE_ITEM, route102Potion.kind)
        assertEquals(13, route102Potion.item?.itemId)
        assertTrue(route102Potion.item?.collectionFlagId != null)
        assertTrue(route102Potion.key.startsWith("local/0011/object/"))
    }

    @Test
    fun `official FireRed exposes Celadon hidden PP Up from MapEvents`() {
        val pois = parse("DUALDEX_FIRERED_ROM", FIRERED_SHA)
            .localMaps.pois.filter { it.baseAreaId == 0x0306 }

        val ppUp = pois.single { it.tileX == 55 && it.tileY == 20 }
        assertEquals(LocalMapPoiKind.HIDDEN_ITEM, ppUp.kind)
        assertEquals(LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE, ppUp.organicVisibility)
        assertEquals(69, ppUp.item?.itemId)
        assertTrue(ppUp.key.startsWith("local/0306/bg/"))
    }

    private fun parse(variable: String, sha256: String) = CatalogParser.parseCatching(realRom(variable, sha256))
        .catalog
        .let(::requireNotNull)
        .getOrThrow()

    private fun realRom(variable: String, sha256: String): RomImage {
        val configured = System.getenv(variable)
        assumeTrue("set $variable to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(sha256, it.sha256) }
    }

    private companion object {
        const val EMERALD_SHA = "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"
        const val FIRERED_SHA = "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059"
    }
}
