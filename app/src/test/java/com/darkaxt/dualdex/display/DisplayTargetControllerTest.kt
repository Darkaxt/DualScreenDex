package com.darkaxt.dualdex.display

import com.enrpau.dualscreendex.companion.model.DisplayTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTargetControllerTest {
    private val displays = listOf(
        CompanionDisplay(0, isDefault = true, isPresentation = false),
        CompanionDisplay(7, isDefault = false, isPresentation = true),
    )

    @Test fun autoPreservesTheLauncherSelectedDisplay() {
        assertEquals(7, DisplayTargetController.resolve(DisplayTarget.AUTO, 7, displays))
    }

    @Test fun explicitTargetsResolveToHandheldAndExternalDisplays() {
        assertEquals(0, DisplayTargetController.resolve(DisplayTarget.HANDHELD, 7, displays))
        assertEquals(7, DisplayTargetController.resolve(DisplayTarget.EXTERNAL, 0, displays))
    }

    @Test fun missingExternalDisplayLeavesTheCurrentSessionInPlace() {
        assertEquals(0, DisplayTargetController.resolve(DisplayTarget.EXTERNAL, 0, displays.take(1)))
    }
}
