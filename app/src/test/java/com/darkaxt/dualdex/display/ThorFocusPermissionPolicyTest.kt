package com.darkaxt.dualdex.display

import org.junit.Assert.assertEquals
import org.junit.Test

class ThorFocusPermissionPolicyTest {
    @Test
    fun permissionRequestIsSingleFlightAndTheResultTriggersOneNonPromptingRecheck() {
        val policy = ThorFocusPermissionPolicy()

        assertEquals(
            ThorFocusPermissionAction.REQUEST_PERMISSION,
            policy.afterSync(ThorFocusResult.PERMISSION_REQUIRED, requestPermission = true),
        )
        assertEquals(
            ThorFocusPermissionAction.NO_ACTION,
            policy.afterSync(ThorFocusResult.PERMISSION_REQUIRED, requestPermission = true),
        )

        policy.permissionResultReturned()

        assertEquals(ThorFocusPermissionAction.SYNC_WITHOUT_PERMISSION_REQUEST, policy.nextAction())
        assertEquals(ThorFocusPermissionAction.NO_ACTION, policy.nextAction())
    }

    @Test
    fun denialDoesNotPromptAgainUntilAUserRequestRearmsThePolicy() {
        val policy = ThorFocusPermissionPolicy()
        policy.afterSync(ThorFocusResult.PERMISSION_REQUIRED, requestPermission = true)
        policy.permissionResultReturned()
        policy.nextAction()

        assertEquals(
            ThorFocusPermissionAction.NO_ACTION,
            policy.afterSync(ThorFocusResult.PERMISSION_REQUIRED, requestPermission = true),
        )

        policy.allowPermissionRequest()

        assertEquals(
            ThorFocusPermissionAction.REQUEST_PERMISSION,
            policy.afterSync(ThorFocusResult.PERMISSION_REQUIRED, requestPermission = true),
        )
    }

    @Test
    fun statusReportsOnlyConfirmedOwnedFocusAsActive() {
        assertEquals(
            ThorFocusStatus.ACTIVE,
            ThorFocusStatusPolicy.resolve(ThorFocusResult.ENFORCED, owned = true),
        )
        assertEquals(
            ThorFocusStatus.ACTIVE,
            ThorFocusStatusPolicy.resolve(ThorFocusResult.NO_CHANGE, owned = true),
        )
        assertEquals(
            ThorFocusStatus.PERMISSION_REQUIRED,
            ThorFocusStatusPolicy.resolve(ThorFocusResult.PERMISSION_REQUIRED, owned = false),
        )
        assertEquals(
            ThorFocusStatus.UNAVAILABLE,
            ThorFocusStatusPolicy.resolve(ThorFocusResult.NOT_SUPPORTED, owned = false),
        )
        assertEquals(
            ThorFocusStatus.UNAVAILABLE,
            ThorFocusStatusPolicy.resolve(ThorFocusResult.WRITE_FAILED, owned = false),
        )
        assertEquals(
            ThorFocusStatus.UNAVAILABLE,
            ThorFocusStatusPolicy.resolve(ThorFocusResult.NO_CHANGE, owned = false),
        )
    }
}
