package com.darkaxt.dualdex.setup

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class SetupDocumentPicker(
    activity: ComponentActivity,
    private val onConfigTree: (Uri) -> Unit,
    private val onRomTree: (Uri) -> Unit,
) {
    private val configLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onConfigTree(uri)
    }
    private val romLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onRomTree(uri)
    }

    fun openConfigTree() = configLauncher.launch(RETROARCH_INITIAL_URI)

    fun openRomTree() = romLauncher.launch(null)

    private companion object {
        val RETROARCH_INITIAL_URI: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:RetroArch",
        )
    }
}
