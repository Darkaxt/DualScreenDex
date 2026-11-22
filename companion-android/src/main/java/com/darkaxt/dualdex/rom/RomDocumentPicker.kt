package com.darkaxt.dualdex.rom

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class RomDocumentPicker(activity: ComponentActivity) {
    private var pending: ValueCallback<Array<Uri>>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pending ?: return@registerForActivityResult
        pending = null
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }

    fun open(callback: ValueCallback<Array<Uri>>): Boolean {
        pending?.onReceiveValue(null)
        pending = callback
        launcher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                // ROM MIME registration differs by document provider; parser-side extension validation stays strict.
                type = "*/*"
            },
        )
        return true
    }

    fun cancel() {
        pending?.onReceiveValue(null)
        pending = null
    }
}
