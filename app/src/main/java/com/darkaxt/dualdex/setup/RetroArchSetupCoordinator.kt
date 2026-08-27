package com.darkaxt.dualdex.setup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.darkaxt.dualdex.catalog.AndroidCatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.SaveSnapshotRepository
import com.darkaxt.dualdex.catalog.SaveSnapshotStore
import com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpointCoordinator
import com.darkaxt.dualdex.live.UnifiedGameStateDecoder
import com.darkaxt.dualdex.live.RecoveryProjection
import com.darkaxt.dualdex.battle.BattleMemoryCoordinator
import com.darkaxt.dualdex.save.AndroidSaveDocumentResolver
import com.darkaxt.dualdex.save.DirectSaveDocumentResolver
import com.darkaxt.dualdex.save.SaveAssociationStore
import com.darkaxt.dualdex.save.SaveDocumentSource
import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveMonitorStatus
import com.darkaxt.dualdex.save.SavePollingMonitor
import com.darkaxt.dualdex.storage.AndroidRomLibraryIndexer
import com.darkaxt.dualdex.storage.DirectRomLibraryIndexer
import com.darkaxt.dualdex.storage.RomIndexStore
import com.darkaxt.dualdex.storage.AndroidRomSourceLoader
import com.darkaxt.dualdex.storage.SharedStorageGateway
import com.darkaxt.dualdex.storage.StorageAccessPolicy
import com.darkaxt.dualdex.storage.StorageIndexAction
import com.darkaxt.dualdex.storage.StorageSetupStatusPolicy
import com.darkaxt.dualdex.storage.StoredSafIndexEligibility
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.darkaxt.dualdex.web.GuideLoadFailure
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveCandidateView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class RetroArchSetupCoordinator(
    private val context: Context,
    private val runtime: ProductionCompanionRuntime,
    private val transientGameState: UnifiedGameStateDecoder,
    private val checkpointCoordinator: SaveKnowledgeCheckpointCoordinator,
    private val commandPort: Int = UdpNetworkCommandTransport.DEFAULT_PORT,
    private val saveSnapshotRepository: SaveSnapshotRepository = SaveSnapshotStore(
        File(context.filesDir, "catalogs"),
        AndroidCatalogDatabaseFactory,
    ),
    private val sharedStorage: SharedStorageGateway = SharedStorageGateway.android(context),
) : AutoCloseable {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val indexStore = RomIndexStore(File(context.filesDir, "retroarch/rom-index.json"))
    private val directIndexStore = RomIndexStore(File(context.filesDir, "retroarch/direct-rom-index.json"))
    private val saveMonitor = SavePollingMonitor(
        SaveAssociationStore(File(context.filesDir, "retroarch/save-associations.json")),
        saveSnapshotRepository,
    )
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-retroarch-setup").apply { isDaemon = true }
    }
    private val indexWorker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-storage-index").apply { isDaemon = true }
    }
    private val heartbeat: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "dualdex-retroarch-heartbeat").apply { isDaemon = true }
    }
    private val commandMonitor = CommandMonitorLifecycle {
        SessionMonitor(NetworkCommandClient(UdpNetworkCommandTransport(commandPort)))
    }
    private var heartbeatTask: ScheduledFuture<*>? = null
    @Volatile private var closed = false
    private val battleMemory = BattleMemoryCoordinator(
        catalogProvider = runtime::battleCatalogContext,
        transientGameState = transientGameState,
        transportFactory = { UdpNetworkCommandTransport(commandPort) },
        pollingIntervalProvider = runtime::battlePollingIntervalMs,
    )
    private val restartVerifier = RestartVerifier()
    private val cachedDirectEntries = if (sharedStorage.isGranted()) directIndexStore.read(ALL_FILES_INDEX_KEY) else emptyList()
    private val entries = AtomicReference(cachedDirectEntries.ifEmpty(::loadSafStoredIndex))
    private val directIndexReady = AtomicBoolean(cachedDirectEntries.isNotEmpty())
    private val view = AtomicReference(initialView())
    private val activationGate = GuideActivationGate()
    private val pollingSave = AtomicBoolean(false)
    private val directIndexing = AtomicBoolean(false)
    private val directRefreshStarted = AtomicBoolean(false)
    private val directConfigAttempt = AtomicReference<String?>(null)
    private val lastStorageAccess = AtomicBoolean(sharedStorage.isGranted())
    private val lastSafGrant = AtomicBoolean(storedSafGrantIsValid())
    private val sessionEpoch = SessionEpochGate()
    private val activeEntry = AtomicReference<RomIndexEntry?>(null)
    private val lastSaveCandidates = AtomicReference<List<SaveDocumentSource>>(emptyList())
    private val discoveredSaveRom = AtomicReference<String?>(null)
    private val discoveredSaveBasename = AtomicReference<String?>(null)
    private val restoredSaveRom = AtomicReference<String?>(null)
    @Volatile private var lastActivatedSha: String? = null

    init {
        publish(view.get())
        refreshStorageAccess()
        scheduleMonitorHeartbeat(0)
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
                        message = "Network Commands and 10-second SaveRAM autosave were written and verified. Fully restart RetroArch, then return here.",
                    )
                }
                ConfigInstallResult.AlreadyConfigured -> update {
                    restartVerifier.requireRestart(connectionOf(it.connection))
                    it.copy(
                        configState = "RESTART_REQUIRED",
                        restartRequired = true,
                        message = "The selected config already enables Network Commands and 10-second SaveRAM autosave. Fully restart RetroArch so DualDex can verify it.",
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
        lastSafGrant.set(storedSafGrantIsValid())
        update { it.copy(romGrant = "INDEXING", message = "Indexing granted GB, GBC, GBA, and ZIP sources…") }
        worker.execute {
            val previousEntries = indexStore.read(uri.toString())
            val result = runCatching { AndroidRomLibraryIndexer(context.contentResolver).index(uri, previousEntries) }
            result.onSuccess { indexed ->
                indexStore.write(uri.toString(), indexed.entries)
                activationGate.clearFailure()
                if (!sharedStorage.isGranted()) entries.set(indexed.entries)
                update {
                    it.copy(
                        romGrant = if (sharedStorage.isGranted()) it.romGrant else "GRANTED",
                        indexedRoms = if (sharedStorage.isGranted()) it.indexedRoms else indexed.entries.size,
                        message = when {
                            sharedStorage.isGranted() -> "The selected ROM folder is retained as a fallback; All files access remains the active library source."
                            indexed.entries.isEmpty() -> "No GB, GBC, GBA, or single-ROM ZIP sources were found in the selected folder."
                            indexed.warnings.isEmpty() -> "Indexed ${indexed.entries.size} ROM sources."
                            else -> "Indexed ${indexed.entries.size} sources; ${indexed.warnings.size} unreadable sources were skipped."
                        },
                    )
                }
            }.onFailure { failure ->
                update { it.copy(romGrant = "FAILED", message = "The selected game folder could not be indexed.") }
            }
        }
    }

    fun refreshStorageAccess() {
        val granted = sharedStorage.isGranted()
        lastStorageAccess.set(granted)
        when (StorageAccessPolicy.resolve(granted, directIndexReady.get())) {
            StorageIndexAction.USE_SAF -> {
                directIndexReady.set(false)
                directRefreshStarted.set(false)
                directConfigAttempt.set(null)
                val romGranted = storedRomTree()?.let(::hasReadGrant) == true
                lastSafGrant.set(romGranted)
                if (romGranted) entries.set(loadSafStoredIndex()) else quarantineSafEntries()
                lastSaveCandidates.set(emptyList())
                discoveredSaveRom.set(null)
                discoveredSaveBasename.set(null)
                val configGranted = storedConfigTree()?.let(::hasConfigGrant) == true
                val storageStatus = StorageSetupStatusPolicy.available(
                    allFilesGranted = false,
                    directIndexReady = false,
                    safIndexGranted = romGranted,
                )
                update {
                    it.copy(
                        storageGrant = storageStatus.storageGrant,
                        configGrant = if (configGranted) "GRANTED" else "MISSING",
                        romGrant = storageStatus.romGrant,
                        indexedRoms = entries.get().size,
                        message = "Grant All files access for automatic multi-folder ROM and SaveRAM discovery; folder selection remains available as a fallback.",
                    )
                }
            }

            StorageIndexAction.USE_DIRECT -> {
                val storageStatus = StorageSetupStatusPolicy.available(
                    allFilesGranted = true,
                    directIndexReady = true,
                    safIndexGranted = storedRomTree()?.let(::hasReadGrant) == true,
                )
                update {
                    it.copy(
                        storageGrant = storageStatus.storageGrant,
                        romGrant = storageStatus.romGrant,
                        indexedRoms = entries.get().size,
                    )
                }
                if (directRefreshStarted.compareAndSet(false, true)) indexSharedStorage()
                else worker.execute { configureDirectRetroArch(sharedStorage.roots()) }
            }

            StorageIndexAction.INDEX_DIRECT -> {
                if (directRefreshStarted.compareAndSet(false, true)) indexSharedStorage()
            }
        }
    }

    fun snapshot(): RetroArchView = view.get()

    fun commitMapperIfCurrent(expectedEpoch: Long, commit: () -> Unit): Boolean =
        sessionEpoch.commitIfCurrent(expectedEpoch, commit)

    fun retryGuideLoad(): Boolean {
        if (!sharedStorage.isGranted() && !storedSafGrantIsValid()) {
            quarantineSafEntries()
            return false
        }
        val entry = activeEntry.get() ?: return false
        val token = sessionEpoch.capture(entry.sessionIdentity()) ?: return false
        if (!activationGate.retry(entry.sourceId)) return false
        activate(entry, token)
        return true
    }

    fun selectSave(documentId: String): Boolean {
        val entry = activeEntry.get() ?: return false
        val token = sessionEpoch.capture(entry.sessionIdentity()) ?: return false
        if (lastSaveCandidates.get().none { it.id == documentId }) return false
        if (!sessionEpoch.isCurrent(token)) return false
        saveMonitor.select(entry.sha256, documentId)
        if (!sessionEpoch.isCurrent(token)) return false
        transientGameState.acceptRecoveryStatus(
            SaveRamView(
                status = "LOCATING",
                autosaveStatus = readAutosaveStatus(),
                message = "Validating the selected SaveRAM…",
            ),
        )
        return true
    }

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
        closed = true
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        sessionEpoch.close()
        activeEntry.getAndSet(null)?.let { activationGate.cancel(it.sourceId) }
        runtime.cancelPendingCatalogLoad()
        heartbeat.shutdownNow()
        battleMemory.close()
        worker.shutdown()
        indexWorker.shutdown()
        commandMonitor.close()
    }

    private fun indexSharedStorage() {
        if (!directIndexing.compareAndSet(false, true)) return
        val retainedDirectIndex = directIndexReady.get()
        val indexingStatus = StorageSetupStatusPolicy.indexing(allFilesGranted = sharedStorage.isGranted())
        update {
            it.copy(
                storageGrant = indexingStatus.storageGrant,
                romGrant = indexingStatus.romGrant,
                message = "Indexing GB, GBC, GBA, and ZIP sources across shared storage…",
            )
        }
        indexWorker.execute {
            try {
                val roots = sharedStorage.roots()
                require(roots.isNotEmpty()) { "All files access is granted, but no mounted shared-storage root is readable" }
                val indexed = DirectRomLibraryIndexer().index(
                    roots,
                    directIndexStore.read(ALL_FILES_INDEX_KEY),
                )
                if (!sharedStorage.isGranted()) return@execute
                entries.set(indexed.entries)
                directIndexStore.write(ALL_FILES_INDEX_KEY, indexed.entries)
                directIndexReady.set(true)
                activationGate.clearFailure()
                val readyStatus = StorageSetupStatusPolicy.available(
                    allFilesGranted = true,
                    directIndexReady = true,
                    safIndexGranted = storedRomTree()?.let(::hasReadGrant) == true,
                )
                update {
                    it.copy(
                        storageGrant = readyStatus.storageGrant,
                        romGrant = readyStatus.romGrant,
                        indexedRoms = indexed.entries.size,
                        message = when {
                            indexed.entries.isEmpty() -> "All files access is active, but no supported GB, GBC, GBA, or single-ROM ZIP source was found."
                            indexed.warnings.isEmpty() -> "Indexed ${indexed.entries.size} ROM sources across shared storage."
                            else -> "Indexed ${indexed.entries.size} sources; ${indexed.warnings.size} unreadable sources were skipped."
                        },
                    )
                }
                configureDirectRetroArch(roots)
            } catch (failure: Exception) {
                directIndexReady.set(retainedDirectIndex)
                directRefreshStarted.set(false)
                val safIndexGranted = storedRomTree()?.let(::hasReadGrant) == true
                if (!retainedDirectIndex && safIndexGranted) entries.set(loadSafStoredIndex())
                val failedStatus = StorageSetupStatusPolicy.failed(
                    allFilesGranted = sharedStorage.isGranted(),
                    retainedDirectIndex = retainedDirectIndex,
                    safIndexGranted = safIndexGranted,
                )
                update {
                    it.copy(
                        storageGrant = failedStatus.storageGrant,
                        romGrant = failedStatus.romGrant,
                        indexedRoms = entries.get().size,
                        message = "Game discovery could not finish. The folder fallback remains available.",
                    )
                }
            } finally {
                directIndexing.set(false)
            }
        }
    }

    private fun configureDirectRetroArch(roots: List<File>) {
        if (!sharedStorage.isGranted()) return
        val config = FileRetroArchConfigStore.findPublic(roots)
        if (config == null) {
            val fallbackGranted = storedConfigTree()?.let(::hasConfigGrant) == true
            update {
                it.copy(
                    configGrant = if (fallbackGranted) "GRANTED" else "MISSING",
                    configState = if (fallbackGranted) it.configState else "NOT_FOUND",
                    message = if (fallbackGranted) it.message
                    else "Shared storage is available, but RetroArch/retroarch.cfg was not found. The manual config-folder fallback remains available.",
                )
            }
            return
        }
        val configPath = config.path
        if (directConfigAttempt.getAndSet(configPath) == configPath) return
        update {
            it.copy(
                configGrant = "GRANTED",
                configState = "PATCHING",
                restartRequired = false,
                message = "Updating the public RetroArch config…",
            )
        }
        when (val result = RetroArchConfigInstaller.install(FileRetroArchConfigStore(config), commandPort)) {
            is ConfigInstallResult.Installed -> update {
                restartVerifier.requireRestart(connectionOf(it.connection))
                it.copy(
                    configState = "RESTART_REQUIRED",
                    restartRequired = true,
                    message = "Network Commands and 10-second SaveRAM autosave were written and verified. Fully restart RetroArch, then return here.",
                )
            }

            ConfigInstallResult.AlreadyConfigured -> update {
                val connected = connectionOf(it.connection) != RetroArchConnection.DISCONNECTED
                it.copy(
                    configState = if (connected) "VERIFIED" else "UNVERIFIED",
                    restartRequired = false,
                    message = if (connected) {
                        "The public config and RetroArch Network Commands are verified."
                    } else {
                        "The public config already enables Network Commands and 10-second SaveRAM autosave. Open RetroArch so DualDex can verify the live connection."
                    },
                )
            }

            is ConfigInstallResult.Failed -> {
                directConfigAttempt.compareAndSet(configPath, null)
                update {
                    it.copy(
                        configState = "FAILED",
                        restartRequired = false,
                        message = result.message,
                    )
                }
            }
        }
    }

    private fun monitorHeartbeat() {
        runtime.runtimePerformanceHeartbeat()
        val storageGranted = sharedStorage.isGranted()
        if (lastStorageAccess.getAndSet(storageGranted) != storageGranted) refreshStorageAccess()
        runCatching { commandMonitor.monitor().heartbeat() }
            .onSuccess { session ->
                commandMonitor.recordSuccess()
                val status = session.lastStatus as? RetroArchStatus.Running
                val resolution = session.lastStatus?.let { RomSessionResolver.resolve(it, eligibleRomEntries()) }
                    ?: SessionResolution.NoContent
                val connected = session.connection != RetroArchConnection.DISCONNECTED
                val restartVerified = restartVerifier.observe(session.connection)
                val resolvedEntry = (resolution as? SessionResolution.Resolved)?.entry
                val token = sessionEpoch.observe(
                    resolvedEntry?.takeIf { connected }?.sessionIdentity(),
                )
                val authorizedEntry = resolvedEntry?.takeIf { token != null }
                val previousEntry = activeEntry.getAndSet(authorizedEntry)
                if (previousEntry != authorizedEntry) {
                    previousEntry?.let { activationGate.cancel(it.sourceId) }
                    restoredSaveRom.set(null)
                    lastSaveCandidates.set(emptyList())
                    discoveredSaveRom.set(null)
                    discoveredSaveBasename.set(null)
                    if (previousEntry != null) runtime.cancelPendingCatalogLoad()
                }
                val active = authorizedEntry != null &&
                    authorizedEntry.sha256 == lastActivatedSha &&
                    runtime.catalogHash() == authorizedEntry.sha256
                val loading = authorizedEntry?.sourceId?.let(activationGate::isLoading) == true
                val failed = authorizedEntry?.sourceId?.let(activationGate::isFailed) == true
                battleMemory.updateSession(
                    connected = connected && active,
                    systemId = status?.systemId,
                    romIdentity = authorizedEntry?.sha256,
                )
                update { current ->
                    current.copy(
                        configState = if (connected && restartVerified) "VERIFIED" else current.configState,
                        restartRequired = if (restartVerifier.restartRequired) true else if (restartVerified) false else current.restartRequired,
                        connection = session.connection.name,
                        systemId = status?.systemId,
                        gameBasename = status?.gameBasename,
                        contentCrc32 = status?.crc32,
                        contentSha256 = authorizedEntry?.sha256?.takeIf { active },
                        sessionEpoch = token?.epoch?.takeIf { active },
                        activeSource = authorizedEntry?.sourceName?.takeIf { active },
                        savefileDirectory = session.savefileDirectory,
                        resolution = when (resolution) {
                            SessionResolution.NoContent -> "NO_CONTENT"
                            is SessionResolution.Resolved -> when {
                                active -> "ACTIVE"
                                loading -> "LOADING"
                                failed -> "FAILED"
                                else -> "RESOLVED"
                            }
                            is SessionResolution.Unverified -> "UNVERIFIED"
                            is SessionResolution.Ambiguous -> "AMBIGUOUS"
                            is SessionResolution.NotFound -> "NOT_FOUND"
                        },
                        message = session.error ?: when {
                            connected && active -> "Opened ${resolvedEntry.sourceName}."
                            connected && loading -> "Opening the SHA-256-verified active catalog…"
                            connected && failed -> current.message
                            connected && resolution is SessionResolution.Resolved -> "Active content matched; verifying its SHA-256."
                            connected && resolution is SessionResolution.Unverified ->
                                "A matching filename was found, but RetroArch did not provide content identity. Live features are paused."
                            connected && resolution is SessionResolution.Ambiguous -> "Multiple granted sources match the active content. Select the ROM manually."
                            connected && resolution is SessionResolution.NotFound -> resolution.reason
                            connected -> "RetroArch Network Commands verified."
                            else -> current.message
                        },
                    )
                }
                if (authorizedEntry != null && token != null) {
                    activate(authorizedEntry, token)
                    if (active) {
                        restorePersistedSave(authorizedEntry, token)
                        pollSave(authorizedEntry, token)
                    }
                }
            }
            .onFailure {
                commandMonitor.recordFailure()
                suspendCommandAuthority("RetroArch command monitoring is temporarily unavailable.")
            }
    }

    private fun suspendCommandAuthority(message: String) {
        sessionEpoch.observe(null)
        activeEntry.getAndSet(null)?.let { activationGate.cancel(it.sourceId) }
        runtime.cancelPendingCatalogLoad()
        battleMemory.updateSession(false, null, null)
        update {
            it.copy(
                connection = "RETRYING",
                systemId = null,
                gameBasename = null,
                contentCrc32 = null,
                contentSha256 = null,
                sessionEpoch = null,
                activeSource = null,
                savefileDirectory = null,
                resolution = "NO_CONTENT",
                message = message,
            )
        }
    }

    private fun activate(entry: RomIndexEntry, token: SessionWorkToken) {
        if (!sessionEpoch.isCurrent(token)) return
        if (entry.sha256 == lastActivatedSha && runtime.catalogHash() == entry.sha256) return
        if (!activationGate.tryBegin(entry.sourceId)) return
        if (!sessionEpoch.isCurrent(token)) {
            activationGate.cancel(entry.sourceId)
            return
        }
        update { it.copy(resolution = "LOADING", message = "Verifying the active ROM before opening its catalog…") }
        worker.execute {
            if (!sessionEpoch.isCurrent(token)) {
                activationGate.cancel(entry.sourceId)
                return@execute
            }
            try {
                val sourceUri = URI(entry.sourceId)
                val loaded = if (sourceUri.scheme.equals("file", ignoreCase = true)) {
                    RomSourceLoader.load(File(sourceUri).toPath())
                } else {
                    AndroidRomSourceLoader.load(
                        context.contentResolver,
                        Uri.parse(entry.sourceId),
                        entry.sourceName.substringBefore('!'),
                    )
                }
                if (!sessionEpoch.isCurrent(token)) {
                    activationGate.cancel(entry.sourceId)
                    return@execute
                }
                require(RomSessionResolver.verifySha(entry, loaded.rom.sha256)) {
                    "the matched ROM changed after indexing; reselect the ROM library"
                }
                if (!sessionEpoch.isCurrent(token)) {
                    activationGate.cancel(entry.sourceId)
                    return@execute
                }
                update { it.copy(resolution = "LOADING", message = "Opening the SHA-256-verified active catalog…") }
                runtime.load(loaded) completion@{ result ->
                    if (!sessionEpoch.isCurrent(token)) {
                        activationGate.cancel(entry.sourceId)
                        return@completion
                    }
                    result.onSuccess {
                        activationGate.finishSuccess(entry.sourceId)
                        lastActivatedSha = entry.sha256
                        update {
                            it.copy(
                                activeSource = entry.sourceName,
                                contentSha256 = entry.sha256,
                                sessionEpoch = token.epoch,
                                resolution = "ACTIVE",
                                message = "Opened ${entry.sourceName}.",
                            )
                        }
                    }.onFailure { failure -> failActivation(entry, failure) }
                }
            } catch (failure: OutOfMemoryError) {
                if (sessionEpoch.isCurrent(token)) {
                    runCatching { runtime.recordRomSourceLoadFailure(entry.sha256, failure) }
                    failActivation(entry, failure)
                } else {
                    activationGate.cancel(entry.sourceId)
                }
            } catch (failure: Exception) {
                if (sessionEpoch.isCurrent(token)) {
                    runCatching { runtime.recordRomSourceLoadFailure(entry.sha256, failure) }
                    failActivation(entry, failure)
                } else {
                    activationGate.cancel(entry.sourceId)
                }
            }
        }
    }

    private fun failActivation(entry: RomIndexEntry, failure: Throwable) {
        val publicFailure = GuideLoadFailure.from(failure)
        activationGate.finishFailure(entry.sourceId)
        update { it.copy(resolution = "FAILED", message = publicFailure.message) }
    }

    private fun pollSave(entry: RomIndexEntry, token: SessionWorkToken) {
        if (!sessionEpoch.isCurrent(token) || !pollingSave.compareAndSet(false, true)) return
        worker.execute {
            try {
                if (!sessionEpoch.isCurrent(token)) return@execute
                val parseContext = runtime.saveParseContext()
                if (parseContext == null ||
                    !parseContext.romIdentity.equals(entry.sha256, ignoreCase = true) ||
                    !sessionEpoch.isCurrent(token)
                ) return@execute
                val resolver = AndroidSaveDocumentResolver(context.contentResolver)
                val activeGameBasename = view.get().gameBasename
                val cachedCandidates = lastSaveCandidates.get().takeIf {
                    discoveredSaveRom.get().equals(entry.sha256, ignoreCase = true) &&
                        discoveredSaveBasename.get().equals(activeGameBasename, ignoreCase = true) &&
                        it.isNotEmpty()
                }
                if (!sessionEpoch.isCurrent(token)) return@execute
                val candidates = cachedCandidates?.let { refreshSaveCandidates(it, resolver) }
                    ?: discoverSaveCandidates(entry, resolver, activeGameBasename)
                if (!sessionEpoch.isCurrent(token)) return@execute
                if (cachedCandidates == null) {
                    discoveredSaveRom.set(entry.sha256)
                    discoveredSaveBasename.set(activeGameBasename)
                }
                lastSaveCandidates.set(candidates)
                val autosaveStatus = readAutosaveStatus()
                if (!sessionEpoch.isCurrent(token)) return@execute
                val result = saveMonitor.poll(parseContext, candidates, autosaveStatus) {
                    sessionEpoch.isCurrent(token)
                } ?: return@execute
                if (!sessionEpoch.isCurrent(token)) return@execute
                val saveView = result.toView()
                if ((result.snapshot != null || result.retained?.snapshot != null) && result.observation != null) {
                    checkpointCoordinator.apply(result, saveView)
                } else if (result.snapshot != null) {
                    transientGameState.acceptRecovery(
                        RecoveryProjection(
                            snapshot = result.snapshot,
                            saveRam = saveView,
                        ),
                    )
                } else {
                    transientGameState.acceptRecoveryStatus(saveView)
                }
            } catch (failure: Exception) {
                if (sessionEpoch.isCurrent(token)) {
                    transientGameState.acceptRecoveryStatus(
                        SaveRamView(
                            status = "UNAVAILABLE",
                            autosaveStatus = readAutosaveStatus(),
                            message = failure.message ?: failure.javaClass.simpleName,
                        ),
                    )
                }
            } finally {
                pollingSave.set(false)
            }
        }
    }

    private fun discoverSaveCandidates(
        entry: RomIndexEntry,
        safResolver: AndroidSaveDocumentResolver,
        activeGameBasename: String?,
    ): List<SaveDocumentSource> {
        if (sharedStorage.isGranted()) {
            val direct = DirectSaveDocumentResolver.discover(
                entry,
                directSaveDirectories(entry),
                activeGameBasename,
            )
            if (direct.isNotEmpty()) return direct
        }
        return safResolver.discover(
            entry,
            storedConfigTree()?.takeIf(::hasReadGrant),
            storedRomTree()?.takeIf(::hasReadGrant),
            activeGameBasename,
        )
    }

    private fun refreshSaveCandidates(
        sources: List<SaveDocumentSource>,
        safResolver: AndroidSaveDocumentResolver,
    ): List<SaveDocumentSource> {
        val (direct, saf) = sources.partition { source ->
            runCatching { URI(source.id).scheme.equals("file", ignoreCase = true) }.getOrDefault(false)
        }
        return buildList {
            if (sharedStorage.isGranted()) addAll(DirectSaveDocumentResolver.refresh(direct))
            addAll(safResolver.refresh(saf))
        }.distinctBy { it.id }
    }

    private fun directSaveDirectories(entry: RomIndexEntry): List<File> = buildList {
        view.get().savefileDirectory?.let(::absoluteDirectory)?.let(::add)
        runCatching { directConfigStore()?.readSaveSettings()?.savefileDirectory }
            .getOrNull()
            ?.let(::absoluteDirectory)
            ?.let(::add)
        sharedStorage.roots().mapTo(this) { File(it, "RetroArch/saves") }
        runCatching { URI(entry.sourceId) }.getOrNull()
            ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
            ?.let(::File)
            ?.parentFile
            ?.let(::add)
    }.mapNotNull { runCatching { it.canonicalFile }.getOrNull()?.takeIf(File::isDirectory) }
        .distinctBy { it.path }

    private fun absoluteDirectory(path: String): File? = File(path)
        .takeIf { it.isAbsolute && it.isDirectory }

    private fun directConfigStore(): FileRetroArchConfigStore? {
        if (!sharedStorage.isGranted()) return null
        return FileRetroArchConfigStore.findPublic(sharedStorage.roots())?.let(::FileRetroArchConfigStore)
    }

    private fun restorePersistedSave(entry: RomIndexEntry, token: SessionWorkToken) {
        if (!sessionEpoch.isCurrent(token)) return
        val parseContext = runtime.saveParseContext() ?: return
        if (!parseContext.romIdentity.equals(entry.sha256, ignoreCase = true) ||
            !sessionEpoch.isCurrent(token)
        ) return
        if (restoredSaveRom.get().equals(parseContext.romIdentity, ignoreCase = true)) return
        val autosaveStatus = readAutosaveStatus()
        if (!sessionEpoch.isCurrent(token)) return
        val restored = try {
            saveMonitor.restore(parseContext, autosaveStatus) { sessionEpoch.isCurrent(token) }
        } catch (failure: Exception) {
            if (!sessionEpoch.isCurrent(token)) return
            Log.e(LOG_TAG, "Could not restore the cached SaveRAM snapshot", failure)
            transientGameState.acceptRecoveryStatus(
                SaveRamView(
                    status = "STALE",
                    autosaveStatus = autosaveStatus,
                    message = "The cached SaveRAM snapshot could not be reopened; live monitoring will retry.",
                ),
            )
            return
        }
        val snapshot = restored?.snapshot ?: return
        if (!sessionEpoch.isCurrent(token)) return
        val application = transientGameState.acceptRecovery(
            RecoveryProjection(
                snapshot = snapshot,
                saveRam = restored.toView(),
            ),
        )
        if (application.accepted && sessionEpoch.isCurrent(token)) {
            restoredSaveRom.set(parseContext.romIdentity)
        }
    }

    private fun SaveMonitorResult.toView(): SaveRamView {
        val stored = retained
        val sourceDocument = source
        return SaveRamView(
            status = status.name,
            sourceName = sourceDocument?.displayPath,
            sourceLastModifiedEpochMs = sourceDocument?.lastModifiedEpochMs ?: stored?.sourceLastModifiedEpochMs,
            refreshedAtEpochMs = refreshedAtEpochMs ?: stored?.refreshedAtEpochMs,
            autosaveStatus = autosaveStatus,
            capabilities = (snapshot ?: stored?.snapshot)?.capabilities.orEmpty()
                .mapKeys { it.key.name }
                .mapValues { it.value.status.name },
            candidates = if (status == SaveMonitorStatus.AMBIGUOUS) candidates.map {
                SaveCandidateView(it.id, it.displayPath, it.lastModifiedEpochMs)
            } else emptyList(),
            message = message,
        )
    }

    private fun readAutosaveStatus(): String {
        directConfigStore()?.let { store ->
            return runCatching { store.readSaveSettings().autosaveStatus }.getOrDefault("UNVERIFIED")
        }
        val uri = storedConfigTree() ?: return "UNVERIFIED"
        if (!hasReadGrant(uri)) return "UNVERIFIED"
        return runCatching { SafRetroArchConfigStore(context.contentResolver, uri).readSaveSettings().autosaveStatus }
            .getOrDefault("UNVERIFIED")
    }

    private fun storedConfigTree(): Uri? = preferences.getString(CONFIG_TREE_URI, null)?.let(Uri::parse)

    private fun storedRomTree(): Uri? = preferences.getString(ROM_TREE_URI, null)?.let(Uri::parse)

    private fun hasReadGrant(uri: Uri): Boolean = context.contentResolver.persistedUriPermissions
        .any { permission -> permission.uri == uri && permission.isReadPermission }

    private fun hasConfigGrant(uri: Uri): Boolean = context.contentResolver.persistedUriPermissions
        .any { permission -> permission.uri == uri && permission.isReadPermission && permission.isWritePermission }

    private fun persistGrant(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    private fun RomIndexEntry.sessionIdentity() = VerifiedSessionIdentity(
        romSha256 = sha256.lowercase(),
        sourceId = sourceId,
    )

    private fun connectionOf(value: String): RetroArchConnection =
        RetroArchConnection.entries.firstOrNull { it.name == value } ?: RetroArchConnection.DISCONNECTED

    private fun scheduledMonitorHeartbeat() {
        synchronized(this) {
            heartbeatTask = null
            if (closed) return
        }
        runCatching(::monitorHeartbeat).onFailure {
            commandMonitor.recordFailure()
            suspendCommandAuthority("RetroArch command monitoring is temporarily unavailable.")
        }
        synchronized(this) {
            if (!closed) scheduleMonitorHeartbeat(commandMonitor.nextDelayMillis())
        }
    }

    @Synchronized
    private fun scheduleMonitorHeartbeat(delayMillis: Long) {
        if (closed || heartbeatTask != null) return
        heartbeatTask = heartbeat.schedule(::scheduledMonitorHeartbeat, delayMillis, TimeUnit.MILLISECONDS)
    }

    private fun eligibleRomEntries(): List<RomIndexEntry> {
        if (sharedStorage.isGranted()) return entries.get()
        val valid = storedSafGrantIsValid()
        val previous = lastSafGrant.getAndSet(valid)
        if (!valid) {
            quarantineSafEntries()
            return emptyList()
        }
        if (!previous) entries.set(loadSafStoredIndex())
        return entries.get()
    }

    private fun storedSafGrantIsValid(): Boolean {
        val storedUri = preferences.getString(ROM_TREE_URI, null)
        val readableGrants = context.contentResolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
        return StoredSafIndexEligibility.isEligible(storedUri, readableGrants)
    }

    private fun quarantineSafEntries() {
        val hadEntries = entries.getAndSet(emptyList()).isNotEmpty()
        val previousEntry = activeEntry.getAndSet(null)
        if (!hadEntries && previousEntry == null) return
        sessionEpoch.observe(null)
        previousEntry?.let { activationGate.cancel(it.sourceId) }
        runtime.cancelPendingCatalogLoad()
        battleMemory.updateSession(false, null, null)
        lastSaveCandidates.set(emptyList())
        update {
            it.copy(
                romGrant = "MISSING",
                indexedRoms = 0,
                resolution = "NO_CONTENT",
                activeSource = null,
                contentSha256 = null,
                sessionEpoch = null,
                message = "ROM folder access is missing. Select the folder again to restore indexed access.",
            )
        }
    }

    private fun loadSafStoredIndex(): List<RomIndexEntry> {
        val uri = preferences.getString(ROM_TREE_URI, null) ?: return emptyList()
        if (!storedSafGrantIsValid()) return emptyList()
        return indexStore.read(uri)
    }

    private fun initialView(): RetroArchView {
        val storageGranted = sharedStorage.isGranted()
        val configUri = preferences.getString(CONFIG_TREE_URI, null)
        val romUri = preferences.getString(ROM_TREE_URI, null)
        val persisted = context.contentResolver.persistedUriPermissions.associateBy { it.uri.toString() }
        val directConfigFound = storageGranted && FileRetroArchConfigStore.findPublic(sharedStorage.roots()) != null
        val configGranted = directConfigFound ||
            (configUri != null && persisted[configUri]?.isReadPermission == true && persisted[configUri]?.isWritePermission == true)
        val safRomGranted = romUri != null && persisted[romUri]?.isReadPermission == true
        val storageStatus = StorageSetupStatusPolicy.available(
            allFilesGranted = storageGranted,
            directIndexReady = directIndexReady.get(),
            safIndexGranted = safRomGranted,
        )
        return RetroArchView(
            storageGrant = storageStatus.storageGrant,
            configGrant = if (configGranted) "GRANTED" else "MISSING",
            romGrant = storageStatus.romGrant,
            configState = if (configGranted) "UNVERIFIED" else "NOT_CONFIGURED",
            indexedRoms = if (storageGranted || safRomGranted) entries.get().size else 0,
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
        const val ALL_FILES_INDEX_KEY = "all-files://shared-storage"
        const val LOG_TAG = "DualDexSaveRAM"
        val RETROARCH_PACKAGES = listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
    }
}
