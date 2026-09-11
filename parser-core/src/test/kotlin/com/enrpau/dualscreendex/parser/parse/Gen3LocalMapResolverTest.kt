package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapNameDisposition
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Test

class Gen3LocalMapResolverTest {
    @Test
    fun dynamicMapSectionRequiresContextDependentName() {
        val bytes = ByteArray(0x1000)
        fun word(at: Int, value: Int) { repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() } }
        word(0x100, 0x08000200)
        bytes[0x114] = 0x57
        word(0x200, 24); word(0x204, 20)
        word(0x20C, 0x08000400); word(0x210, 0x08000800); word(0x214, 0x08000900)
        val method = Gen3LocalMapResolver::class.java.declaredMethods
            .single { it.name == "readDescriptor" }
            .apply { isAccessible = true }
        val descriptor = method.invoke(Gen3LocalMapResolver, RomImage(bytes), 0x300, 0x100, emptyMap<Int, String>())
        val toLocalMap = descriptor.javaClass.declaredMethods
            .single { it.name == "toLocalMap" }
            .apply { isAccessible = true }

        val map = toLocalMap.invoke(descriptor, "asset") as LocalMap

        assertEquals(LocalMapNameDisposition.CONTEXT_DEPENDENT, map.nameDisposition)
        assertEquals(null, map.displayName)
    }

    @Test
    fun sharedSectionNamesLeaveLocalNumericDescriptorsIntact() {
        val bytes = ByteArray(0x1000)
        fun word(at: Int, value: Int) { repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() } }
        word(0x100, 0x08000200)
        bytes[0x114] = 7
        word(0x200, 24); word(0x204, 20)
        word(0x20C, 0x08000400); word(0x210, 0x08000800); word(0x214, 0x08000900)
        val method = Gen3LocalMapResolver::class.java.declaredMethods.single { it.name == "readDescriptor" }.apply { isAccessible = true }
        fun descriptor(names: Map<Int, String>): Any = method.invoke(
            Gen3LocalMapResolver, RomImage(bytes), 0x300, 0x100,
            names,
        )
        fun field(value: Any, name: String): Any? = value.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(value)
        val named = descriptor(mapOf(7 to "マサラタウン"))
        assertEquals("マサラタウン", field(named, "displayName"))
        val numeric = descriptor(emptyMap())
        assertEquals(null, field(numeric, "displayName"))
        listOf("baseAreaId", "width", "height", "mapCells", "primaryTileset", "secondaryTileset", "mapType").forEach {
            assertEquals(it, field(named, it), field(numeric, it))
        }
        assertEquals(24, field(numeric, "width"))
        assertEquals(20, field(numeric, "height"))
    }
}
