package com.darkaxt.dualdex

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.darkaxt.dualdex.storage.SharedStorageGateway
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class QaAndroidJUnitRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(classLoader, QaDualDexApplication::class.java.name, context)
}

class QaDualDexApplication : DualDexApplication() {
    private val storageGranted = AtomicBoolean(false)
    private val storageRootsAvailable = AtomicBoolean(true)

    fun setSharedStorage(granted: Boolean, rootsAvailable: Boolean = true) {
        storageRootsAvailable.set(rootsAvailable)
        storageGranted.set(granted)
    }

    protected override fun sharedStorageGateway(): SharedStorageGateway = SharedStorageGateway(
        accessCheck = storageGranted::get,
        rootProvider = {
            if (!storageRootsAvailable.get()) emptyList()
            else listOf(File(filesDir, "qa-shared-storage").apply { mkdirs() })
        },
    )
}
