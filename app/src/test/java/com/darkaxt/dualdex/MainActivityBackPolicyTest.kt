package com.darkaxt.dualdex

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityBackPolicyTest {
    @Test
    fun `Back is dispatched to the companion whenever its web view is alive`() {
        assertEquals(MainActivityBackAction.DISPATCH_TO_COMPANION, MainActivityBackPolicy.resolve(webViewAlive = true))
    }

    @Test
    fun `Back backgrounds recovery instead of finishing the task`() {
        assertEquals(MainActivityBackAction.BACKGROUND_TASK, MainActivityBackPolicy.resolve(webViewAlive = false))
    }
}
