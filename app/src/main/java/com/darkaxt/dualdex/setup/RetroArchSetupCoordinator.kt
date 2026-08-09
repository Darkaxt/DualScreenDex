package com.darkaxt.dualdex.setup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.darkaxt.dualdex.retroarch.ConfigInstallResult
import com.darkaxt.dualdex.retroarch.NetworkCommandClient
import com.darkaxt.dualdex.retroarch.RetroArchConfigInstaller
import com.darkaxt.dualdex.retroarch.RetroArchConnection
import com.darkaxt.dualdex.retroarch.RetroArchStatus
import com.darkaxt.dualdex.retroarch.RestartVerifier
import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomSessionResolver
import com.darkaxt.dualdex.retroarch.SessionMonitor
import com.darkaxt.dualdex.retroarch.SessionResolution
import com.darkaxt.dualdex.retroarch.UdpNetworkCommandTransport
import com.darkaxt.dualdex.storage.AndroidRomLibraryIndexer
import com.darkaxt.dualdex.storage.RomIndexStore
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RetroArchSetupCoordinator(
    private val context: Context,
    private val runtime: ProductionCompanionRuntime,
    private val commandPort: Int = UdpNetworkCommandTransport.DEFAULT_PORT,
) : AutoCloseable {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val indexStore = RomIndexStore(File(context.filesDir, "retroarch/rom-index.json"))
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-retroarch-setup").apply { isDaemon = true }
    }
    private val heartbeat: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "dualdex-retroarch-heartbeat").apply { isDaemon = true }
    }
    private val monitor = AtomicReference<SessionMonitor?>(null)
    private val restartVerifier = RestartVerifier()
    private val entries = AtomicReference(loadStoredIndex())
    private val view = AtomicReference(initialView())
    private val activating = AtomicReference<String?>(null)
    @Volatile private var lastActivatedSha: String? = null

    init {
        publish(view.get())
        heartbeat.scheduleWithFixedDelay(::monitorHeartbeat, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    fun applyConfigTree(uri: Uri) {
        runCatching { persistGrant(uri, write = true) }.onFailure { failure ->
            update { it.copy(configGrant = "FAILED", configState = "FAILED", message = failure.message ?: failure.javaClass.simpleName) }
            return
        }
        preferences.edit().putString(CONFIG_TREE_URI, uri.toString()).apply()
        update {
            it.copy(
                configGrant = "GRANTED",
                configState = "PATCHING",
                restartRequired = false,
                message = "Updating the selected retroarch.cfg…",
            )
        }
        worker.execute {
            when (val result = RetroArchConfigInstaller.install(SafRetroArchConfigStore(context.contentResolver, uri), commandPort)) {
                is ConfigInstallResult.Installed -> update {
                    restartVerifier.requireRestart(connectionOf(it.connection))
                    it.copy(
                        configState = "RESTART_REQUIRED",
                        restartRequired = true,
                        message = "Network Commands were written and verified. Fully restart RetroArch, then return here.",
                    )
                }
                ConfigInstallResult.AlreadyConfigured -> update {
                    restartVerifier.requireRestart(connectionOf(it.connection))
                    it.copy(
                        configState = "RESTART_REQUIRED",
                        restartRequired = true,
                        message = "The selected config already enables Network Commands. Fully restart RetroArch so DualDex can verify it.",
                    )
                }
                is ConfigInstallResult.Failed -> update {
                    it.copy(
                        configState = "FAILED",
                        restartRequired = false,
                        message = result.message,
                    )
                }
            }
        }
    }

    fun applyRomTree(uri: Uri) {
        runCatching { persistGrant(uri, write = false) }.onFailure { failure ->
            update { it.copy(romGrant = "FAILED", message = failure.message ?: failure.javaClass.simpleName) }
            return
        }
        preferences.edit().putString(ROM_TREE_URI, uri.toString()).apply()
        update { it.copy(romGrant = "INDEXING", message = "Indexing granted GB, GBC, GBA, and ZIP sources…") }
        worker.execute {
            val result = runCatching { AndroidRomLibraryIndexer(context.contentResolver).index(uri) }
            result.onSuccess { indexed ->
                entries.set(indexed.entries)
                indexStore.write(uri.toString(), indexed.entries)
                update {
                    it.copy(
                        romGrant = "GRANTED",
                        indexedRoms = indexed.entries.size,
                        message = when {
                            indexed.entries.isEmpty() -> "No GB, GBC, GBA, or single-ROM ZIP sources were found in the selected folder."
                            indexed.warnings.isEmpty() -> "Indexed ${indexed.entries.size} ROM sources."
                            else -> "Indexed ${indexed.entries.size} sources; ${indexed.warnings.size} unreadable sources were skipped."
                        },
                    )
                }
            }.onFailure { failure ->
                update { it.copy(romGrant = "FAILED", message = failure.message ?: failure.javaClass.simpleName) }
            }
        }
    }

    fun snapshot(): RetroArchView = view.get()

    fun launchRetroArch(): Boolean {
        val intent = RETROARCH_PACKAGES.firstNotNullOfOrNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)
        }
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    override fun close() {
        heartbeat.shutdown()
        worker.shutdown()
        monitor.get()?.close()
    }

    private fun monitorHeartbeat() {
        runCatching { monitor().heartbeat() }
            .onSuccess { session ->
                val status = session.lastStatus as? RetroArchStatus.Running
                val resolution = session.lastStatus?.let { RomSessionResolver.resolve(it, entries.get()) }
                    ?: SessionResolution.NoContent
                val connected = session.connection != RetroArchConnection.DISCONNECTED
                val restartVerified = restartVerifier.observe(session.connection)
                val resolvedEntry = (resolution as? SessionResolution.Resolved)?.entry
                val active = resolvedEntry != null &&
                    resolvedEntry.sha256 == lastActivatedSha &&
                    runtime.catalogHash() == resolvedEntry.sha256
                val loading = resolvedEntry?.sourceId == activating.get()
                update { current ->
                    current.copy(
                        configState = if (connected && restartVerified) "VERIFIED" else current.configState,
                        restartRequired = if (restartVerifier.restartRequired) true else if (restartVerified) false else current.restartRequired,
                        connection = session.connection.name,
                        systemId = status?.systemId,
                        gameBasename = status?.gameBasename,
                        contentCrc32 = status?.crc32,
                        resolution = when (resolution) {
                            SessionResolution.NoContent -> "NO_CONTENT"
                            is SessionResolution.Resolved -> when {
                                active -> "ACTIVE"
                                loading -> "LOADING"
                                else -> "RESOLVED"
                            }
                            is SessionResolution.Ambiguous -> "AMBIGUOUS"
                            is SessionResolution.NotFound -> "NOT_FOUND"
                        },
                        message = session.error ?: when {
                            connected && active -> "Opened ${resolvedEntry.sourceName}."
                            connected && loading -> "Opening the SHA-256-verified active catalog…"
                            connected && resolution is SessionResolution.Resolved -> "Active content matched; verifying its SHA-256."
                            connected && resolution is SessionResolution.Ambiguous -> "Multiple granted sources match the active content. Select the ROM manually."
                            connected && resolution is SessionResolution.NotFound -> resolution.reason
                            connected -> "RetroArch Network Commands verified."
                            else -> current.message
                        },
                    )
                }
                if (resolution is SessionResolution.Resolved) activate(resolution.entry)
            }
            .onFailure { failure -> update { it.copy(connection = "DISCONNECTED", message = failure.message) } }
    }

    private fun activate(entry: RomIndexEntry) {
        if (entry.sha256 == lastActivatedSha && runtime.catalogHash() == entry.sha256) return
        if (!activating.compareAndSet(null, entry.sourceId)) return
        update { it.copy(resolution = "LOADING", message = "Verifying the active ROM before opening its catalog…") }
        worker.execute {
            try {
                val loaded = context.contentResolver.openInputStream(Uri.parse(entry.sourceId))?.use {
                    RomSourceLoader.load(entry.sourceName.substringBefore('!'), it)
                } ?: error("the matched ROM source is no longer readable")
                require(RomSessionResolver.verifySha(entry, loaded.rom.sha256)) {
                    "the matched ROM changed after indexing; reselect the ROM library"
                }
                update { it.copy(resolution = "LOADING", message = "Opening the SHA-256-verified active catalog…") }
                runtime.load(loaded) { result ->
                    result.onSuccess {
                        lastActivatedSha = entry.sha256
                        update {
                            it.copy(
                                activeSource = entry.sourceName,
                                resolution = "ACTIVE",
                                message = "Opened ${entry.sourceName}.",
                            )
                        }
                    }.onFailure { failure ->
                        update { it.copy(resolution = "FAILED", message = failure.message ?: failure.javaClass.simpleName) }
                    }
                    activating.compareAndSet(entry.sourceId, null)
                }
            } catch (failure: Exception) {
                update { it.copy(resolution = "FAILED", message = failure.message ?: failure.javaClass.simpleName) }
                activating.compareAndSet(entry.sourceId, null)
            }
        }
    }

    private fun persistGrant(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    private fun connectionOf(value: String): RetroArchConnection =
        RetroArchConnection.entries.firstOrNull { it.name == value } ?: RetroArchConnection.DISCONNECTED

    private fun monitor(): SessionMonitor {
        monitor.get()?.let { return it }
        val candidate = SessionMonitor(NetworkCommandClient(UdpNetworkCommandTransport(commandPort)))
        return if (monitor.compareAndSet(null, candidate)) candidate else {
            candidate.close()
            requireNotNull(monitor.get())
        }
    }

    private fun loadStoredIndex(): List<RomIndexEntry> {
        val uri = preferences.getString(ROM_TREE_URI, null) ?: return emptyList()
        return indexStore.read(uri)
    }

    private fun initialView(): RetroArchView {
        val configUri = preferences.getString(CONFIG_TREE_URI, null)
        val romUri = preferences.getString(ROM_TREE_URI, null)
        val persisted = context.contentResolver.persistedUriPermissions.associateBy { it.uri.toString() }
        val configGranted = configUri != null && persisted[configUri]?.isReadPermission == true && persisted[configUri]?.isWritePermission == true
        val romGranted = romUri != null && persisted[romUri]?.isReadPermission == true
        return RetroArchView(
            configGrant = if (configGranted) "GRANTED" else "MISSING",
            romGrant = if (romGranted) "GRANTED" else "MISSING",
            configState = if (configGranted) "UNVERIFIED" else "NOT_CONFIGURED",
            indexedRoms = if (romGranted) entries.get().size else 0,
            message = "DualDex remains usable with manual ROM selection while RetroArch is disconnected.",
        )
    }

    private fun update(transform: (RetroArchView) -> RetroArchView) {
        while (true) {
            val before = view.get()
            val after = transform(before)
            if (view.compareAndSet(before, after)) {
                publish(after)
                return
            }
        }
    }

    private fun publish(state: RetroArchView) {
        runtime.updateRetroArch(state)
    }

    private companion object {
        const val PREFERENCES_NAME = "dualdex-retroarch"
        const val CONFIG_TREE_URI = "config-tree-uri"
        const val ROM_TREE_URI = "rom-tree-uri"
        const val HEARTBEAT_INTERVAL_SECONDS = 2L
        val RETROARCH_PACKAGES = listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
    }
}
