package com.darkaxt.dualdex.setup

import android.net.Uri
import android.provider.DocumentsContract

class SetupDocumentPicker(
    registry: SetupPickerActivityResultRegistry,
    private val onConfigTree: (Uri) -> Unit,
    private val onRomTree: (Uri) -> Unit,
) : SetupPickerDispatch {
    private val configLauncher = registry.registerOpenDocumentTree { uri ->
        if (uri != null) onConfigTree(uri)
    }
    private val romLauncher = registry.registerOpenDocumentTree { uri ->
        if (uri != null) onRomTree(uri)
    }

    override fun openConfigTree() = configLauncher.launch(RETROARCH_INITIAL_URI)

    override fun openRomTree() = romLauncher.launch(null)

    private companion object {
        val RETROARCH_INITIAL_URI: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:RetroArch",
        )
    }
}
