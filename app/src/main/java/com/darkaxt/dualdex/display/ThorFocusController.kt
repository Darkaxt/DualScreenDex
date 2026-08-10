package com.darkaxt.dualdex.display

object ThorFocusMode {
    const val AUTO = 0
    const val TOP = 1
    const val BOTTOM = 2
}

interface ThorFocusBackend {
    val supported: Boolean
    val writable: Boolean
    val current: Int
    var previous: Int?
    var owned: Boolean
    fun write(mode: Int): Boolean
}

enum class ThorFocusResult {
    ENFORCED,
    RESTORED,
    RELEASED,
    PERMISSION_REQUIRED,
    NOT_SUPPORTED,
    NO_CHANGE,
    WRITE_FAILED,
}

class ThorFocusController(
    private val backend: ThorFocusBackend,
) {
    fun sync(enabled: Boolean, docked: Boolean, secondaryDisplay: Boolean): ThorFocusResult {
        if (!backend.supported) return ThorFocusResult.NOT_SUPPORTED
        if (!enabled || !docked || !secondaryDisplay) return release()
        if (!backend.writable) return ThorFocusResult.PERMISSION_REQUIRED

        if (backend.owned && backend.current == ThorFocusMode.TOP) return ThorFocusResult.NO_CHANGE
        if (backend.owned) clearOwnership()

        backend.previous = backend.current.takeIf { it in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM }
            ?: ThorFocusMode.AUTO
        backend.owned = true
        return if (backend.write(ThorFocusMode.TOP)) {
            ThorFocusResult.ENFORCED
        } else {
            clearOwnership()
            ThorFocusResult.WRITE_FAILED
        }
    }

    private fun release(): ThorFocusResult {
        if (!backend.owned) return ThorFocusResult.NO_CHANGE
        if (!backend.writable) return ThorFocusResult.PERMISSION_REQUIRED
        if (backend.current != ThorFocusMode.TOP) {
            clearOwnership()
            return ThorFocusResult.RELEASED
        }
        val prior = backend.previous ?: ThorFocusMode.AUTO
        return if (backend.write(prior)) {
            clearOwnership()
            ThorFocusResult.RESTORED
        } else {
            ThorFocusResult.WRITE_FAILED
        }
    }

    private fun clearOwnership() {
        backend.owned = false
        backend.previous = null
    }
}
