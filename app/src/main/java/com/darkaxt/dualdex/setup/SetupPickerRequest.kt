package com.darkaxt.dualdex.setup

enum class SetupPickerRequest(val encoded: String) {
    RETROARCH("retroarch"),
    ROMS("roms");

    companion object {
        const val EXTRA = "com.darkaxt.dualdex.SETUP_PICKER_REQUEST"

        fun parse(value: String?): SetupPickerRequest? = entries.firstOrNull { it.encoded == value }

        fun consume(read: () -> String?, clear: () -> Unit): SetupPickerRequest? {
            val request = parse(read())
            clear()
            return request
        }
    }
}
