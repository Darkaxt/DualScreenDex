package com.darkaxt.dualdex.display

enum class ThorFocusPermissionAction {
    REQUEST_PERMISSION,
    SYNC_WITHOUT_PERMISSION_REQUEST,
    NO_ACTION,
}

class ThorFocusPermissionPolicy {
    private var permissionRequestInFlight = false
    private var permissionRequestConsumed = false
    private var resultRecheckPending = false

    fun afterSync(result: ThorFocusResult, requestPermission: Boolean): ThorFocusPermissionAction {
        if (
            result != ThorFocusResult.PERMISSION_REQUIRED ||
            !requestPermission ||
            permissionRequestInFlight ||
            permissionRequestConsumed
        ) return ThorFocusPermissionAction.NO_ACTION

        permissionRequestInFlight = true
        permissionRequestConsumed = true
        return ThorFocusPermissionAction.REQUEST_PERMISSION
    }

    fun permissionResultReturned() {
        permissionRequestInFlight = false
        resultRecheckPending = true
    }

    fun nextAction(): ThorFocusPermissionAction {
        if (!resultRecheckPending) return ThorFocusPermissionAction.NO_ACTION
        resultRecheckPending = false
        return ThorFocusPermissionAction.SYNC_WITHOUT_PERMISSION_REQUEST
    }

    fun allowPermissionRequest() {
        if (!permissionRequestInFlight) permissionRequestConsumed = false
    }
}

enum class ThorFocusStatus(val displayValue: String) {
    ACTIVE("ACTIVE"),
    PERMISSION_REQUIRED("PERMISSION REQUIRED"),
    UNAVAILABLE("UNAVAILABLE"),
}

object ThorFocusStatusPolicy {
    fun resolve(result: ThorFocusResult, owned: Boolean): ThorFocusStatus = when {
        result == ThorFocusResult.PERMISSION_REQUIRED -> ThorFocusStatus.PERMISSION_REQUIRED
        owned && result in setOf(ThorFocusResult.ENFORCED, ThorFocusResult.NO_CHANGE) -> ThorFocusStatus.ACTIVE
        else -> ThorFocusStatus.UNAVAILABLE
    }
}
