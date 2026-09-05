package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3WorldMapResolverTest {
    @Test
    fun semanticPlaneNamesDoNotChangeNumericGeometry() {
        val layoutType = Gen3WorldMapResolver::class.java.declaredClasses.single { it.simpleName == "SemanticLayout" }
        val constructor = layoutType.declaredConstructors.single { it.parameterCount == 2 }.apply { isAccessible = true }
        val cells = listOf(com.enrpau.dualscreendex.parser.catalog.WorldMapCell(4, 11, 1, 1))
        val layout = constructor.newInstance(mapOf(7 to cells), emptyMap<Int, Any>())
        val method = Gen3WorldMapResolver::class.java.declaredMethods.single { it.name == "textLocations" }.apply { isAccessible = true }
        fun locations(names: Map<Int, String>): List<com.enrpau.dualscreendex.parser.catalog.WorldMapLocation> {
            val args = listOf(layout, mapOf(7 to listOf(0x300)), names)
            @Suppress("UNCHECKED_CAST")
            return method.invoke(Gen3WorldMapResolver, *args.toTypedArray()) as List<com.enrpau.dualscreendex.parser.catalog.WorldMapLocation>
        }
        val named = locations(mapOf(7 to "マサラタウン")).single()
        org.junit.Assert.assertEquals("マサラタウン", named.displayName)
        org.junit.Assert.assertEquals(cells, named.geometry)
        org.junit.Assert.assertEquals(named.copy(displayName = null), locations(emptyMap()).single())
    }

    @Test
    fun referencedLiteralIsNotAnOpposingBranchButRealOpposingArmsRemainDistinct() {
        val bytes = ByteArray(0x400)
        fun op(at: Int, value: Int) { bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte() }
        op(0x120, 0xD10E) // real conditional -> 0x140
        op(0x130, 0xE086) // fallthrough joins at 0x240
        op(0x150, 0x4B03) // literal word at 0x160
        op(0x15C, 0xE002) // skip literal pool to 0x164
        op(0x160, 0xDA5C) // low halfword of an asset pointer, not a branch
        op(0x162, 0x0800)
        op(0x170, 0xE066) // join at 0x240
        val method = Gen3WorldMapResolver::class.java.getDeclaredMethod("branchArmAtSite", com.enrpau.dualscreendex.parser.io.RomImage::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        method.isAccessible = true
        fun arm(site: Int) = method.invoke(Gen3WorldMapResolver, com.enrpau.dualscreendex.parser.io.RomImage(bytes), 0x100, site)
        org.junit.Assert.assertEquals(arm(0x150), arm(0x168))
        org.junit.Assert.assertNotEquals(arm(0x124), arm(0x168))
        op(0x150, 0) // now the same DA5C is a real instruction, not a referenced literal
        org.junit.Assert.assertNotEquals(arm(0x150), arm(0x168))
    }

    @Test
    fun unrelatedTruncatedTargetDoesNotVetoCompleteRequiredAssetTargets() {
        val completeTarget = 0x100
        val unrelatedTruncatedTarget = 0x200
        val references = GbaReferenceIndex.fromTargets(
            mapOf(
                completeTarget to GbaTargetReferenceEvidence(
                    count = 1,
                    instructionSites = listOf(0x20),
                    observedSites = 1,
                    limitSites = 16,
                    overflowReason = null,
                ),
                unrelatedTruncatedTarget to GbaTargetReferenceEvidence(
                    count = 17,
                    instructionSites = emptyList(),
                    observedSites = 17,
                    limitSites = 16,
                    overflowReason = "candidate-local site budget exceeded",
                ),
            ),
            limitTargets = 32,
        )

        assertTrue(Gen3WorldMapResolver.requiredReferenceSitesComplete(references, setOf(completeTarget)))
        assertFalse(
            Gen3WorldMapResolver.requiredReferenceSitesComplete(
                references,
                setOf(completeTarget, unrelatedTruncatedTarget),
            ),
        )
    }
}
