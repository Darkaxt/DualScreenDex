package com.darkaxt.dualdex.overlay

import android.content.Context
import android.content.Intent
import com.darkaxt.dualdex.MainActivity
import com.darkaxt.dualdex.setup.SetupPickerRequest
import com.darkaxt.dualdex.web.NativeSetupRoute

fun interface OverlayActivityStarter {
    fun start(intent: Intent)
}

class OverlaySetupRouteHandler(
    private val context: Context,
    private val activityStarter: OverlayActivityStarter,
) {
    fun handleNativeRoute(route: NativeSetupRoute): Boolean = when (route) {
        NativeSetupRoute.GRANT_RETROARCH -> {
            foregroundSetup(SetupPickerRequest.RETROARCH)
            true
        }
        NativeSetupRoute.GRANT_ROMS -> {
            foregroundSetup(SetupPickerRequest.ROMS)
            true
        }
        else -> false
    }

    fun foregroundSetup(request: SetupPickerRequest) {
        activityStarter.start(SetupPickerRequest.foregroundIntent(context, MainActivity::class.java, request))
    }
}
