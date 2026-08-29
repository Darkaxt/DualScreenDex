package com.darkaxt.dualdex

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.test.runner.AndroidJUnitRunner
import com.darkaxt.dualdex.retroarch.ConfigParameter
import com.darkaxt.dualdex.retroarch.NetworkResponse
import com.darkaxt.dualdex.retroarch.RetroArchCommandPort
import com.darkaxt.dualdex.retroarch.RetroArchStatus
import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.SessionMonitor
import com.darkaxt.dualdex.setup.GuideLoadFault
import com.darkaxt.dualdex.setup.SetupPickerActivityResultLauncher
import com.darkaxt.dualdex.setup.SetupPickerActivityResultRegistry
import com.darkaxt.dualdex.storage.RomIndexStore
import com.darkaxt.dualdex.storage.SharedStorageGateway
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class QaAndroidJUnitRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(classLoader, QaDualDexApplication::class.java.name, context)
}

class QaDualDexApplication : DualDexApplication() {
    private val storageGranted = AtomicReference(false)
    private val storageRootsAvailable = AtomicReference(true)
    private val pickerRegistry = QaSetupPickerActivityResultRegistry()
    private val pickerCallbacks = mutableListOf<String>()
    private val guideStatus = AtomicReference<RetroArchStatus>(RetroArchStatus.Contentless)
    private val guideLoadFault = QaGuideLoadFault()
    @Volatile private var guideFixture: GuideFixture? = null

    fun setSharedStorage(granted: Boolean, rootsAvailable: Boolean = true) {
        storageRootsAvailable.set(rootsAvailable)
        storageGranted.set(granted)
    }

    fun prepareGuideFixture() {
        resetGuideFailure()
        val root = File(filesDir, "qa-shared-storage").apply { mkdirs() }
        val rom = File(root, "qa-guide.gb")
        rom.writeBytes(ByteArray(0x150).apply {
            "QA GUIDE".encodeToByteArray().copyInto(this, destinationOffset = 0x134)
        })
        val inspected = RomSourceLoader.inspect(rom.toPath())
        guideFixture = GuideFixture(
            sourceId = rom.canonicalFile.toURI().normalize().toString(),
            sourceName = rom.name,
            crc32 = inspected.crc32,
            sha256 = inspected.sha256,
        )
        setSharedStorage(granted = true)
        requireNotNull(retroArchSetup).rescanGameLibrary()
    }

    fun isGuideFixtureIndexed(): Boolean {
        val fixture = guideFixture ?: return false
        return RomIndexStore(File(filesDir, "retroarch/direct-rom-index.json")).readActive()
            ?.entries
            ?.any { entry ->
                entry.sourceId == fixture.sourceId &&
                    entry.sourceName == fixture.sourceName &&
                    entry.crc32.equals(fixture.crc32, ignoreCase = true) &&
                    entry.sha256.equals(fixture.sha256, ignoreCase = true)
            } == true
    }

    fun armGuideFailure() {
        check(isGuideFixtureIndexed()) { "qa guide fixture must be indexed before arming failure" }
        val fixture = requireNotNull(guideFixture)
        guideLoadFault.arm()
        guideStatus.set(
            RetroArchStatus.Running(
                paused = false,
                systemId = "GB",
                gameBasename = fixture.sourceName,
                crc32 = fixture.crc32,
            ),
        )
    }

    fun resetGuideFailure() {
        guideLoadFault.reset()
        guideStatus.set(RetroArchStatus.Contentless)
    }

    fun releaseGuideRetryTerminal() = guideLoadFault.releaseRetryTerminal()

    fun guideLoadAttempts(): Int = guideLoadFault.completedAttempts()

    fun guideFailureArmed(): Boolean = guideLoadFault.isArmed()

    fun resetPickerDispatches() {
        pickerRegistry.reset()
        synchronized(pickerCallbacks) { pickerCallbacks.clear() }
    }

    fun pickerLaunches(): List<Uri?> = pickerRegistry.launches()

    fun pickerRegistrationCount(): Int = pickerRegistry.registrationCount()

    fun deliverLatestPickerResult(uri: Uri) = pickerRegistry.deliverLatest(uri)

    fun pickerCallbacks(): List<String> = synchronized(pickerCallbacks) { pickerCallbacks.toList() }

    protected override fun sharedStorageGateway(): SharedStorageGateway = SharedStorageGateway(
        accessCheck = storageGranted::get,
        rootProvider = {
            if (!storageRootsAvailable.get()) emptyList()
            else listOf(File(filesDir, "qa-shared-storage").apply { mkdirs() })
        },
    )

    protected override fun guideLoadFault(): GuideLoadFault = guideLoadFault

    internal override fun setupPickerActivityResultRegistry(activity: ComponentActivity): SetupPickerActivityResultRegistry =
        pickerRegistry

    internal override fun applyConfigTree(uri: Uri) {
        synchronized(pickerCallbacks) { pickerCallbacks += "config" }
    }

    internal override fun applyRomTree(uri: Uri) {
        synchronized(pickerCallbacks) { pickerCallbacks += "rom" }
    }

    protected override fun sessionMonitorFactory(): () -> SessionMonitor = {
        SessionMonitor(object : RetroArchCommandPort {
            override fun requestStatus() = Unit

            override fun requestVersion() = Unit

            override fun requestConfig(parameter: ConfigParameter) = Unit

            override fun poll(): List<NetworkResponse> = listOf(NetworkResponse.Status(guideStatus.get()))

            override fun close() = Unit
        })
    }

    private data class GuideFixture(
        val sourceId: String,
        val sourceName: String,
        val crc32: String,
        val sha256: String,
    )

    private class QaSetupPickerActivityResultRegistry : SetupPickerActivityResultRegistry {
        private val registrations = mutableListOf<(Uri?) -> Unit>()
        private val launches = mutableListOf<PickerLaunch>()

        override fun registerOpenDocumentTree(onResult: (Uri?) -> Unit): SetupPickerActivityResultLauncher = synchronized(this) {
            val registration = registrations.size
            registrations += onResult
            SetupPickerActivityResultLauncher { initialUri ->
                synchronized(this) { launches += PickerLaunch(registration, initialUri) }
            }
        }

        fun reset() = synchronized(this) {
            registrations.clear()
            launches.clear()
        }

        fun launches(): List<Uri?> = synchronized(this) { launches.map(PickerLaunch::initialUri) }

        fun registrationCount(): Int = synchronized(this) { registrations.size }

        fun deliverLatest(uri: Uri) {
            val callback = synchronized(this) {
                val launch = requireNotNull(launches.lastOrNull()) { "no picker launch is pending" }
                registrations[launch.registration]
            }
            callback(uri)
        }

        private data class PickerLaunch(val registration: Int, val initialUri: Uri?)
    }

    private class QaGuideLoadFault : GuideLoadFault {
        private val armed = AtomicBoolean(false)
        private val activeAttempts = AtomicInteger()
        private val completedAttempts = AtomicInteger()
        private val terminalRelease = AtomicReference(CountDownLatch(0))

        fun arm() {
            check(armed.compareAndSet(false, true)) { "qa guide failure is already armed" }
            terminalRelease.set(CountDownLatch(1))
            activeAttempts.set(0)
            completedAttempts.set(0)
        }

        fun reset() {
            armed.set(false)
            terminalRelease.getAndSet(CountDownLatch(0)).countDown()
            activeAttempts.set(0)
            completedAttempts.set(0)
        }

        fun releaseRetryTerminal() {
            terminalRelease.get().countDown()
        }

        fun completedAttempts(): Int = completedAttempts.get()

        fun isArmed(): Boolean = armed.get()

        override fun beforeLoad(entry: RomIndexEntry): Throwable? {
            if (!armed.get()) return null
            return when (activeAttempts.incrementAndGet()) {
                1 -> IllegalStateException("qa initial guide-load failure")
                2 -> {
                    check(terminalRelease.get().await(30, TimeUnit.SECONDS)) { "qa retry terminal was not released" }
                    completedAttempts.set(2)
                    IllegalStateException("qa terminal guide-load failure")
                }
                else -> null
            }
        }
    }
}
