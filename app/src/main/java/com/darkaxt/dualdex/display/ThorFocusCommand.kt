package com.darkaxt.dualdex.display

object ThorFocusCommand {
    private const val SETTINGS_BINARY = "/system/bin/settings"
    private const val NAMESPACE = "secure"
    private const val SETTING = "screen_focus_lock"

    fun read(): List<String> = listOf(SETTINGS_BINARY, "get", NAMESPACE, SETTING)

    fun write(mode: Int): List<String> {
        require(mode in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM)
        return listOf(SETTINGS_BINARY, "put", NAMESPACE, SETTING, mode.toString())
    }

    fun parse(output: String): Int? = when (output.trim()) {
        ThorFocusMode.AUTO.toString() -> ThorFocusMode.AUTO
        ThorFocusMode.TOP.toString() -> ThorFocusMode.TOP
        ThorFocusMode.BOTTOM.toString() -> ThorFocusMode.BOTTOM
        else -> null
    }
}
