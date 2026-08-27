package com.darkaxt.dualdex.web

import java.net.URI

enum class NativeSetupRoute {
    GRANT_ALL_FILES,
    GRANT_RETROARCH,
    GRANT_ROMS,
    OPEN_RETROARCH,
    EXPORT_MAPPER,
    EXPORT_PERFORMANCE,
    EXPORT_COMPATIBILITY,
    RETRY_GUIDE,
    SHOW_OVERLAY,
    DOCK_OVERLAY;

    companion object {
        fun parse(raw: String): NativeSetupRoute? = runCatching {
            val uri = URI(raw)
            if (uri.scheme != "dualdex" || uri.query != null || uri.fragment != null || uri.userInfo != null || uri.port != -1) {
                return null
            }
            when (uri.host to uri.path) {
                "grant" to "/files" -> GRANT_ALL_FILES
                "grant" to "/retroarch" -> GRANT_RETROARCH
                "grant" to "/roms" -> GRANT_ROMS
                "open" to "/retroarch" -> OPEN_RETROARCH
                "mapper" to "/export" -> EXPORT_MAPPER
                "performance" to "/export" -> EXPORT_PERFORMANCE
                "compatibility" to "/export" -> EXPORT_COMPATIBILITY
                "guide" to "/retry" -> RETRY_GUIDE
                "overlay" to "/show" -> SHOW_OVERLAY
                "overlay" to "/dock" -> DOCK_OVERLAY
                else -> null
            }
        }.getOrNull()
    }
}
