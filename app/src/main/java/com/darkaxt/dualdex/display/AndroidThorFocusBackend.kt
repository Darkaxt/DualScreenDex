package com.darkaxt.dualdex.display

import android.content.Context
import android.os.Build
import android.provider.Settings

class AndroidThorFocusBackend(context: Context) : ThorFocusBackend {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override val supported: Boolean
        get() = Build.MANUFACTURER.equals("AYN", ignoreCase = true) &&
            Build.MODEL.equals("AYN Thor", ignoreCase = true) &&
            runCatching { applicationContext.packageManager.getPackageInfo(ASSISTANT_PACKAGE, 0) }.isSuccess

    override val writable: Boolean
        get() = Settings.System.canWrite(applicationContext)

    override val current: Int
        get() = Settings.System.getInt(applicationContext.contentResolver, SYSTEM_KEY, ThorFocusMode.AUTO)

    override var previous: Int?
        get() = if (preferences.contains(PREVIOUS_KEY)) preferences.getInt(PREVIOUS_KEY, ThorFocusMode.AUTO) else null
        set(value) {
            val editor = preferences.edit()
            if (value == null) editor.remove(PREVIOUS_KEY) else editor.putInt(PREVIOUS_KEY, value)
            check(editor.commit()) { "Thor focus ownership could not be persisted" }
        }

    override var owned: Boolean
        get() = preferences.getBoolean(OWNED_KEY, false)
        set(value) {
            check(preferences.edit().putBoolean(OWNED_KEY, value).commit()) {
                "Thor focus ownership could not be persisted"
            }
        }

    override fun write(mode: Int): Boolean {
        require(mode in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM)
        return writable && Settings.System.putInt(applicationContext.contentResolver, SYSTEM_KEY, mode)
    }

    private companion object {
        const val ASSISTANT_PACKAGE = "com.odin.dualscreen.assistant"
        const val SYSTEM_KEY = "screen_focus_lock"
        const val PREFERENCES = "thor-focus-ownership"
        const val PREVIOUS_KEY = "previous-mode"
        const val OWNED_KEY = "owned"
    }
}
