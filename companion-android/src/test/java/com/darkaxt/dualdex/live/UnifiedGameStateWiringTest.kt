package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedGameStateWiringTest {
    @Test
    fun productionRuntimeOwnsExactlyOneClosableSubscription() {
        val source = RecordingSource()
        val runtime = ProductionCompanionRuntime(transientGameState = source)

        assertEquals(1, source.subscribeCount)
        assertEquals(0, source.closeCount)

        runtime.close()

        assertEquals(1, source.subscribeCount)
        assertEquals(1, source.closeCount)
    }

    private class RecordingSource : TransientGameStateSource {
        override val current: ResolvedGameSnapshot? = null
        var subscribeCount = 0
        var closeCount = 0

        override fun subscribe(listener: TransientGameStateListener): AutoCloseable {
            subscribeCount += 1
            listener.onStateChanged(ResolvedGameStateUpdate(current, emptySet()))
            return AutoCloseable { closeCount += 1 }
        }
    }
}
