package com.darkaxt.dualdex.setup

import android.app.Activity
import android.content.Context
import android.content.Intent

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

        fun foregroundIntent(
            context: Context,
            target: Class<out Activity>,
            request: SetupPickerRequest,
        ): Intent = Intent(context, target)
            .putExtra(EXTRA, request.encoded)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
    }
}

interface SetupPickerDispatch {
    fun openConfigTree()

    fun openRomTree()
}

class SetupPickerRequestDispatcher(
    private val picker: SetupPickerDispatch,
) {
    fun consume(intent: Intent) {
        consume(
            read = { intent.getStringExtra(SetupPickerRequest.EXTRA) },
            clear = { intent.removeExtra(SetupPickerRequest.EXTRA) },
        )
    }

    fun consume(read: () -> String?, clear: () -> Unit) {
        when (SetupPickerRequest.consume(read, clear)) {
            SetupPickerRequest.RETROARCH -> picker.openConfigTree()
            SetupPickerRequest.ROMS -> picker.openRomTree()
            null -> Unit
        }
    }
}
