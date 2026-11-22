package com.darkaxt.dualdex.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeSetupRouteTest {
    @Test
    fun acceptsOnlyTheExactNativeRoutes() {
        assertEquals(NativeSetupRoute.GRANT_ALL_FILES, NativeSetupRoute.parse("dualdex://grant/files"))
        assertEquals(NativeSetupRoute.GRANT_RETROARCH, NativeSetupRoute.parse("dualdex://grant/retroarch"))
        assertEquals(NativeSetupRoute.GRANT_ROMS, NativeSetupRoute.parse("dualdex://grant/roms"))
        assertEquals(NativeSetupRoute.RESCAN_ROMS, NativeSetupRoute.parse("dualdex://games/rescan"))
        assertEquals(NativeSetupRoute.OPEN_RETROARCH, NativeSetupRoute.parse("dualdex://open/retroarch"))
        assertEquals(NativeSetupRoute.EXPORT_MAPPER, NativeSetupRoute.parse("dualdex://mapper/export"))
        assertEquals(NativeSetupRoute.EXPORT_PERFORMANCE, NativeSetupRoute.parse("dualdex://performance/export"))
        assertEquals(NativeSetupRoute.EXPORT_COMPATIBILITY, NativeSetupRoute.parse("dualdex://compatibility/export"))
        assertEquals(NativeSetupRoute.RETRY_GUIDE, NativeSetupRoute.parse("dualdex://guide/retry"))
        assertEquals(NativeSetupRoute.SHOW_OVERLAY, NativeSetupRoute.parse("dualdex://overlay/show"))
        assertEquals(NativeSetupRoute.DOCK_OVERLAY, NativeSetupRoute.parse("dualdex://overlay/dock"))

        assertNull(NativeSetupRoute.parse("dualdex://grant/retroarch/extra"))
        assertNull(NativeSetupRoute.parse("dualdex://grant/roms?unexpected=true"))
        assertNull(NativeSetupRoute.parse("dualdex://grant/files/extra"))
        assertNull(NativeSetupRoute.parse("dualdex://games/rescan?force=false"))
        assertNull(NativeSetupRoute.parse("dualdex://performance/export?path=private"))
        assertNull(NativeSetupRoute.parse("dualdex://compatibility/export?path=private"))
        assertNull(NativeSetupRoute.parse("dualdex://guide/retry/extra"))
        assertNull(NativeSetupRoute.parse("dualdex://overlay/show/extra"))
        assertNull(NativeSetupRoute.parse("https://grant/retroarch"))
        assertNull(NativeSetupRoute.parse("javascript:alert(1)"))
    }
}
