package com.darkaxt.dualdex

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.darkaxt.dualdex.storage.SharedStorageGateway
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class QaAndroidJUnitRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(classLoader, QaDualDexApplication::class.java.name, context)
}

class QaDualDexApplication : DualDexApplication() {
    private val storageGranted = AtomicBoolean(false)
    private val storageRootsAvailable = AtomicBoolean(true)
    private val runtime = AtomicReference<ProductionCompanionRuntime?>()

    fun setSharedStorage(granted: Boolean, rootsAvailable: Boolean = true) {
        storageRootsAvailable.set(rootsAvailable)
        storageGranted.set(granted)
    }

    fun publishGuideFailure() {
        val setup = requireNotNull(retroArchSetup)
        requireNotNull(runtime.get()).updateRetroArch(
            setup.snapshot().copy(
                resolution = "FAILED",
                message = GUIDE_FAILURE_MESSAGE,
            ),
        )
    }

    protected override fun sharedStorageGateway(): SharedStorageGateway = SharedStorageGateway(
        accessCheck = storageGranted::get,
        rootProvider = {
            if (!storageRootsAvailable.get()) emptyList()
            else listOf(File(filesDir, "qa-shared-storage").apply { mkdirs() })
        },
    )

    protected override fun onCompanionRuntimeCreated(runtime: ProductionCompanionRuntime) {
        this.runtime.set(runtime)
    }

    private companion object {
        const val GUIDE_FAILURE_MESSAGE = "This game guide could not be opened. You can try again."
    }
}
