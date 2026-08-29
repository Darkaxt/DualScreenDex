package com.darkaxt.dualdex.setup

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

fun interface SetupPickerActivityResultLauncher {
    fun launch(initialUri: Uri?)
}

interface SetupPickerActivityResultRegistry {
    fun registerOpenDocumentTree(onResult: (Uri?) -> Unit): SetupPickerActivityResultLauncher
}

class AndroidSetupPickerActivityResultRegistry(
    private val activity: ComponentActivity,
) : SetupPickerActivityResultRegistry {
    override fun registerOpenDocumentTree(onResult: (Uri?) -> Unit): SetupPickerActivityResultLauncher {
        val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree(), onResult)
        return SetupPickerActivityResultLauncher(launcher::launch)
    }
}
