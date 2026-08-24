package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMapSceneBuilderTest {
    @Test
    fun reciprocalAgreementBuildsOneNormalizedScene() {
        val scenes = LocalMapSceneBuilder.build(
            maps = listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
            constraints = listOf(
                LocalMapSceneConstraint(1, 2, 10, 2),
                LocalMapSceneConstraint(2, 1, -10, -2),
            ),
        )

        assertEquals("scene/0001", scenes.single().key)
        assertEquals(22, scenes.single().gridWidth)
        assertEquals(8, scenes.single().gridHeight)
        assertEquals(
            listOf(Triple(1, 0, 0), Triple(2, 10, 2)),
            scenes.single().placements.map { Triple(it.baseAreaId, it.gridX, it.gridY) },
        )
    }

    @Test
    fun ambiguousPairIsDiscarded() {
        val scenes = LocalMapSceneBuilder.build(
            maps = listOf(localMap(1, 10, 8), localMap(2, 12, 6)),
            constraints = listOf(
                LocalMapSceneConstraint(1, 2, 10, 0),
                LocalMapSceneConstraint(2, 1, -10, -2),
            ),
        )

        assertTrue(scenes.isEmpty())
    }

    @Test
    fun excludesOnlyAnOverlappingBranch() {
        val scenes = LocalMapSceneBuilder.build(
            maps = listOf(
                localMap(1, 10, 10),
                localMap(2, 10, 20),
                localMap(3, 20, 10),
            ),
            constraints = listOf(
                LocalMapSceneConstraint(1, 2, 10, 0),
                LocalMapSceneConstraint(1, 3, 0, 10),
            ),
        )

        assertEquals(listOf(1, 2), scenes.single().placements.map { it.baseAreaId })
    }

    @Test
    fun ignoresConstraintsWithoutBothRenderedMaps() {
        val scenes = LocalMapSceneBuilder.build(
            maps = listOf(localMap(1, 10, 8)),
            constraints = listOf(LocalMapSceneConstraint(1, 2, 10, 0)),
        )

        assertTrue(scenes.isEmpty())
    }

    private fun localMap(baseAreaId: Int, width: Int, height: Int) = LocalMap(
        key = "local/${baseAreaId.toString(16).padStart(4, '0')}",
        displayName = null,
        baseAreaId = baseAreaId,
        pixelWidth = width * 16,
        pixelHeight = height * 16,
        gridWidth = width,
        gridHeight = height,
        imageAssetKey = "local/${baseAreaId.toString(16).padStart(4, '0')}/map",
    )
}
