package com.darkaxt.dualdex

import com.darkaxt.dualdex.display.DisplayCandidate
import com.darkaxt.dualdex.display.DisplayEvent
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityDisplayTest {
    private val handheld = DisplayCandidate(0, isDefault = true, isPresentation = false)
    private val external = DisplayCandidate(7, isDefault = false, isPresentation = true)
    private val routeMarker = "/#dualdex=%7B%22version%22%3A1%7D"

    @Test fun lifecycleRegistersAndUnregistersOneListener() {
        val port = FakeDisplayPort(environment(DisplayTarget.AUTO, 0, listOf(handheld)))
        val continuity = MainActivityDisplayContinuity(port)

        continuity.onStart()
        continuity.onStart()
        assertEquals(1, port.registerCount)

        continuity.onStop()
        continuity.onStop()
        assertEquals(1, port.unregisterCount)
        assertNull(port.listener)
    }

    @Test fun oneDisplayEventCanLaunchAtMostOneMove() {
        val port = FakeDisplayPort(environment(DisplayTarget.EXTERNAL, 0, listOf(handheld, external)))
        val continuity = MainActivityDisplayContinuity(port)
        continuity.onStart()

        port.fire(DisplayEvent.Added(7))

        assertEquals(listOf(DisplayLaunch(7, routeMarker)), port.launches)
    }

    @Test fun aUniqueExternalDisplayRestoresAfterRemovalAndReturn() {
        val port = FakeDisplayPort(environment(DisplayTarget.EXTERNAL, 7, listOf(handheld)))
        val continuity = MainActivityDisplayContinuity(port)
        continuity.onStart()

        port.fire(DisplayEvent.Removed(7))
        assertEquals(emptyList<DisplayLaunch>(), port.launches)

        port.current = environment(DisplayTarget.EXTERNAL, 0, listOf(handheld))
        continuity.onResume()
        assertEquals(emptyList<DisplayLaunch>(), port.launches)

        port.current = environment(DisplayTarget.EXTERNAL, 0, listOf(handheld, external))
        port.fire(DisplayEvent.Added(7))
        assertEquals(listOf(DisplayLaunch(7, routeMarker)), port.launches)
    }

    @Test fun aFailedAttemptDoesNotCreateAForegroundLaunchLoop() {
        val port = FakeDisplayPort(environment(DisplayTarget.EXTERNAL, 0, listOf(handheld, external)))
        val continuity = MainActivityDisplayContinuity(port, attemptedDisplayId = 7)
        continuity.onStart()

        continuity.onResume()
        port.fire(DisplayEvent.Changed(7))

        assertEquals(emptyList<DisplayLaunch>(), port.launches)
    }

    @Test fun removingAnAttemptedDisplayAllowsALaterRestorationAttempt() {
        val port = FakeDisplayPort(environment(DisplayTarget.EXTERNAL, 0, listOf(handheld, external)))
        val continuity = MainActivityDisplayContinuity(port, attemptedDisplayId = 7)
        continuity.onStart()

        port.current = environment(DisplayTarget.EXTERNAL, 0, listOf(handheld))
        port.fire(DisplayEvent.Removed(7))
        port.current = environment(DisplayTarget.EXTERNAL, 0, listOf(handheld, external))
        port.fire(DisplayEvent.Added(7))

        assertEquals(listOf(DisplayLaunch(7, routeMarker)), port.launches)
    }

    @Test fun targetChangesAreEvaluatedWithoutWaitingForResume() {
        val port = FakeDisplayPort(environment(DisplayTarget.AUTO, 0, listOf(handheld, external)))
        val continuity = MainActivityDisplayContinuity(port)

        continuity.onTargetChanged(DisplayTarget.EXTERNAL)

        assertEquals(listOf(DisplayLaunch(7, routeMarker)), port.launches)
    }

    @Test fun displayTransferAcceptsOnlyTheBoundedRouteCodecMarker() {
        assertEquals(routeMarker, WebRouteMarker.normalize(routeMarker))
        assertNull(WebRouteMarker.normalize("/party?slot=2"))
        assertNull(WebRouteMarker.normalize("/#dualdex="))
        assertNull(WebRouteMarker.normalize("/#dualdex=%7Bbroken%"))
        assertNull(WebRouteMarker.normalize("/#dualdex=${"A".repeat(8192)}"))
    }

    private fun environment(
        target: DisplayTarget,
        currentDisplayId: Int,
        candidates: List<DisplayCandidate>,
    ) = DisplayEnvironment(
        target = target,
        currentDisplayId = currentDisplayId,
        candidates = candidates,
        webRouteMarker = routeMarker,
    )

    private class FakeDisplayPort(initial: DisplayEnvironment) : MainActivityDisplayPort {
        var current = initial
        var listener: ((DisplayEvent) -> Unit)? = null
        var registerCount = 0
        var unregisterCount = 0
        val launches = mutableListOf<DisplayLaunch>()

        override fun environment(): DisplayEnvironment = current

        override fun register(listener: (DisplayEvent) -> Unit) {
            registerCount += 1
            this.listener = listener
        }

        override fun unregister() {
            unregisterCount += 1
            listener = null
        }

        override fun launch(request: DisplayLaunch) {
            launches += request
        }

        fun fire(event: DisplayEvent) = requireNotNull(listener).invoke(event)
    }
}
