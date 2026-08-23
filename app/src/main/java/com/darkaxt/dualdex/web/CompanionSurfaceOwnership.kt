package com.darkaxt.dualdex.web

interface CompanionSurface {
    val released: Boolean
    fun resumeSurface()
    fun pauseSurface()
    fun releaseSurface()
}

/** Owns the one WebView allowed to retain the catalog and poll live state. */
class CompanionSurfaceOwnership {
    private var owner: CompanionSurface? = null

    @Synchronized
    fun activate(surface: CompanionSurface) {
        check(!surface.released) { "a released companion surface cannot be activated" }
        if (owner !== surface) {
            owner?.releaseSurface()
            owner = surface
        }
        surface.resumeSurface()
    }

    @Synchronized
    fun pause(surface: CompanionSurface) {
        if (owner === surface && !surface.released) surface.pauseSurface()
    }

    @Synchronized
    fun release(surface: CompanionSurface) {
        if (owner === surface) owner = null
        if (!surface.released) surface.releaseSurface()
    }

    @Synchronized
    fun isOwnedBy(surface: CompanionSurface): Boolean = owner === surface && !surface.released
}
