package com.darkaxt.dualdex.setup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.darkaxt.dualdex.performance.PrivacySafeDiagnostics
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
import com.darkaxt.dualdex.knowledge.CheckpointReadResult
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
import com.darkaxt.dualdex.storage.SafRomIndexCommitResult
import com.darkaxt.dualdex.storage.SafRomIndexTransaction
import com.darkaxt.dualdex.storage.SafOperationGenerations
import com.darkaxt.dualdex.storage.SafProviderOperationTimeout
import com.darkaxt.dualdex.storage.SafProviderOperationUnavailable
import com.darkaxt.dualdex.storage.SafProviderRetryDisposition
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
    private val guideLoadFault: GuideLoadFault = NoGuideLoadFault,
    private val sessionMonitorFactory: (() -> SessionMonitor)? = null,
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
    private val commandMonitor = CommandMonitorLifecycle(
        sessionMonitorFactory ?: { SessionMonitor(NetworkCommandClient(UdpNetworkCommandTransport(commandPort))) },
    )
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
    private val pendingForcedDirectRescan = AtomicBoolean(false)
    private val safRescanning = AtomicBoolean(false)
    private val safIndexGenerations = SafOperationGenerations()
    private val directRefreshStarted = AtomicBoolean(false)
    private val directConfigAttempt = AtomicReference<String?>(null)
    private val lastStorageAccess = AtomicBoolean(sharedStorage.isGranted())
    private val lastSafGrant = AtomicBoolean(storedSafGrantIsValid())
    private val sessionEpoch = SessionEpochGate()
    private val activationCoordinator = SessionActivationCoordinator(sessionEpoch, activationGate)
    private val activeEntry = AtomicReference<RomIndexEntry?>(null)
    private val lastSaveCandidates = AtomicReference<List<SaveDocumentSource>>(emptyList())
    private val discoveredSaveRom = AtomicReference<String?>(null)
    private val discoveredSaveBasename = AtomicReference<String?>(null)
    private val restoredSaveRom = AtomicReference<String?>(null)

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
            try {
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
            } catch (_: Throwable) {
                update {
                    it.copy(
                        configState = "FAILED",
                        restartRequired = false,
                        message = "RetroArch configuration could not be updated safely. Retry the setup action.",
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
        val token = safIndexGenerations.begin()
        update { it.copy(romGrant = "INDEXING", message = "Indexing granted GB, GBC, GBA, and ZIP sources…") }
        worker.execute {
            try {
                val previousEntries = indexStore.read(uri.toString())
                val indexed = AndroidRomLibraryIndexer(context.contentResolver).index(uri, previousEntries)
                if (!hasReadGrant(uri) || sharedStorage.isGranted()) {
                    refreshStorageAccess()
                    return@execute
                }
                var persistenceFailed = false
                val published = safIndexGenerations.commitIfCurrent(token) {
                    if (SafRomIndexTransaction { entries -> indexStore.write(uri.toString(), entries) }.commit(indexed.entries) == SafRomIndexCommitResult.Failed) {
                        persistenceFailed = true
                    } else {
                        preferences.edit().putString(ROM_TREE_URI, uri.toString()).commit()
                        lastSafGrant.set(hasReadGrant(uri))
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
                    }
                }
                if (published && persistenceFailed) publishSafIndexFailure()
            } catch (failure: Throwable) {
                if (safIndexGenerations.isCurrent(token)) publishSafIndexFailure(failure)
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

    fun rescanGameLibrary() {
        if (sharedStorage.isGranted()) {
            indexSharedStorage(forceRefresh = true)
            return
        }
        val uri = storedRomTree()
        if (uri == null || !hasReadGrant(uri)) {
            quarantineSafEntries()
            return
        }
        rescanSafTree(uri)
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
        val selected = saveMonitor.select(entry.sha256, documentId) { commit ->
            sessionEpoch.commitIfCurrent(token, commit)
        }
        if (!selected) return false
        val autosaveStatus = readAutosaveStatus()
        return sessionEpoch.commitIfCurrent(token) {
            transientGameState.acceptRecoveryStatus(
                SaveRamView(
                    status = "LOCATING",
                    autosaveStatus = autosaveStatus,
                    message = "Validating the selected SaveRAM…",
                ),
            )
        }
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
        val catalogCancellation = runtime.cancelPendingCatalogLoadForAuthorityTransition()
        sessionEpoch.close()
        catalogCancellation?.complete()
        activeEntry.getAndSet(null)?.let { activationGate.cancel(it.sourceId) }
        heartbeat.shutdownNow()
        battleMemory.close()
        worker.shutdown()
        indexWorker.shutdown()
        commandMonitor.close()
    }

    private fun providerResetRequired(failure: Throwable): Boolean =
        (failure as? SafProviderOperationTimeout)?.disposition == SafProviderRetryDisposition.ResetRequired ||
            (failure as? SafProviderOperationUnavailable)?.disposition == SafProviderRetryDisposition.ResetRequired

    private fun publishSafIndexFailure(failure: Throwable? = null) {
        update {
            it.copy(
                romGrant = "FAILED",
                indexedRoms = entries.get().size,
                message = if (failure != null && providerResetRequired(failure)) {
                    "The selected document provider needs reset or a full app restart before game indexing can continue. The previous game index remains active."
                } else {
                    "The selected game folder could not be indexed. The previous game index remains active; retry or select the folder again."
                },
            )
        }
    }

    private fun rescanSafTree(uri: Uri) {
        if (!safRescanning.compareAndSet(false, true)) return
        val token = safIndexGenerations.begin()
        val retainedEntries = entries.get()
        update {
            it.copy(
                romGrant = "INDEXING",
                message = "Rescanning the selected game folder…",
            )
        }
        worker.execute {
            try {
                val indexed = AndroidRomLibraryIndexer(context.contentResolver).index(uri, emptyList())
                if (!hasReadGrant(uri) || sharedStorage.isGranted()) {
                    refreshStorageAccess()
                    return@execute
                }
                var persistenceFailed = false
                val published = safIndexGenerations.commitIfCurrent(token) {
                    if (SafRomIndexTransaction { entries -> indexStore.write(uri.toString(), entries) }.commit(indexed.entries) == SafRomIndexCommitResult.Failed) {
                        persistenceFailed = true
                    } else {
                        preferences.edit().putString(ROM_TREE_URI, uri.toString()).commit()
                        entries.set(indexed.entries)
                        activationGate.clearFailure()
                        update {
                            it.copy(
                                romGrant = "GRANTED",
                                indexedRoms = indexed.entries.size,
                                message = when {
                                    indexed.entries.isEmpty() -> "No GB, GBC, GBA, or single-ROM ZIP sources were found in the selected folder."
                                    indexed.warnings.isEmpty() -> "Rescan found ${indexed.entries.size} ROM sources."
                                    else -> "Rescan found ${indexed.entries.size} sources; ${indexed.warnings.size} unreadable sources were skipped."
                                },
                            )
                        }
                    }
                }
                if (published && persistenceFailed) publishSafIndexFailure()
            } catch (failure: Throwable) {
                if (!safIndexGenerations.isCurrent(token)) return@execute
                if (sharedStorage.isGranted() || !hasReadGrant(uri)) {
                    refreshStorageAccess()
                    return@execute
                }
                val status = StorageSetupStatusPolicy.failed(
                    allFilesGranted = false,
                    retainedDirectIndex = false,
                    safIndexGranted = hasReadGrant(uri),
                )
                update {
                    it.copy(
                        storageGrant = status.storageGrant,
                        romGrant = "FAILED",
                        indexedRoms = retainedEntries.size,
                        message = if (providerResetRequired(failure)) {
                            "The selected document provider needs reset or a full app restart before game indexing can continue. The previous game index remains active."
                        } else {
                            "Game rescan could not finish. The previous game index remains active."
                        },
                    )
                }
            } finally {
                safRescanning.set(false)
            }
        }
    }

    private fun indexSharedStorage(forceRefresh: Boolean = false) {
        if (!directIndexing.compareAndSet(false, true)) {
            if (forceRefresh) pendingForcedDirectRescan.set(true)
            return
        }
        val retainedDirectIndex = directIndexReady.get()
        val indexingStatus = StorageSetupStatusPolicy.indexing(allFilesGranted = sharedStorage.isGranted())
        update {
            it.copy(
                storageGrant = indexingStatus.storageGrant,
                romGrant = indexingStatus.romGrant,
                message = if (forceRefresh) {
                    "Rescanning GB, GBC, GBA, and ZIP sources across shared storage…"
                } else {
                    "Indexing GB, GBC, GBA, and ZIP sources across shared storage…"
                },
            )
        }
        indexWorker.execute {
            try {
                val roots = sharedStorage.roots()
                require(roots.isNotEmpty()) { "All files access is granted, but no mounted shared-storage root is readable" }
                val indexed = DirectRomLibraryIndexer().index(
                    roots,
                    directIndexStore.read(ALL_FILES_INDEX_KEY),
                    forceRefresh = forceRefresh,
                )
                if (!sharedStorage.isGranted()) return@execute
                directIndexStore.write(ALL_FILES_INDEX_KEY, indexed.entries)
                entries.set(indexed.entries)
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
            } catch (failure: Throwable) {
                directIndexReady.set(retainedDirectIndex)
                if (!forceRefresh) directRefreshStarted.set(false)
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
                        romGrant = if (forceRefresh) "FAILED" else failedStatus.romGrant,
                        indexedRoms = entries.get().size,
                        message = if (forceRefresh && retainedDirectIndex) {
                            "Game rescan could not finish. The previous game index remains active."
                        } else {
                            "Game discovery could not finish. The folder fallback remains available."
                        },
                    )
                }
            } finally {
                directIndexing.set(false)
                if (pendingForcedDirectRescan.getAndSet(false) && sharedStorage.isGranted()) {
                    indexSharedStorage(forceRefresh = true)
                }
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
                val nextAuthorizedEntry = resolvedEntry?.takeIf { connected }
                val catalogCancellation = if (activeEntry.get() != nextAuthorizedEntry) {
                    runtime.cancelPendingCatalogLoadForAuthorityTransition()
                } else {
                    null
                }
                val token = sessionEpoch.observe(
                    nextAuthorizedEntry?.sessionIdentity(),
                )
                catalogCancellation?.complete()
                val authorizedEntry = nextAuthorizedEntry?.takeIf { token != null }
                val previousEntry = activeEntry.getAndSet(authorizedEntry)
                if (previousEntry != authorizedEntry) {
                    previousEntry?.let { activationGate.cancel(it.sourceId) }
                    restoredSaveRom.set(null)
                    lastSaveCandidates.set(emptyList())
                    discoveredSaveRom.set(null)
                    discoveredSaveBasename.set(null)
                }
                val active = authorizedEntry != null &&
                    token != null &&
                    activationCoordinator.isVerified(token) &&
                    runtime.catalogHash() == authorizedEntry.sha256
                val loading = authorizedEntry != null && token != null &&
                    activationCoordinator.isLoading(authorizedEntry.sourceId, token)
                val failed = authorizedEntry != null && token != null &&
                    activationCoordinator.isFailed(authorizedEntry.sourceId, token)
                val publishBattleSession = {
                    battleMemory.updateSession(
                        connected = connected && active,
                        systemId = status?.systemId,
                        romIdentity = authorizedEntry?.sha256,
                    )
                }
                if (token != null) {
                    sessionEpoch.commitIfCurrent(token, publishBattleSession)
                } else {
                    publishBattleSession()
                }
                val publishSessionView = {
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
                }
                if (token != null) {
                    sessionEpoch.commitIfCurrent(token, publishSessionView)
                } else if (!closed) {
                    publishSessionView()
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
        val catalogCancellation = runtime.cancelPendingCatalogLoadForAuthorityTransition()
        sessionEpoch.observe(null)
        catalogCancellation?.complete()
        activeEntry.getAndSet(null)?.let { activationGate.cancel(it.sourceId) }
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
        if (!activationCoordinator.requiresSourceVerification(token, runtime.catalogHash(), entry.sha256)) return
        if (!activationCoordinator.begin(token, entry.sourceId) {
                update {
                    it.copy(
                        resolution = "LOADING",
                        message = "Verifying the active ROM before opening its catalog…",
                    )
                }
            }
        ) return
        worker.execute {
            if (!sessionEpoch.isCurrent(token)) return@execute
            try {
                guideLoadFault.beforeLoad(entry)?.let { throw it }
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
                if (!sessionEpoch.isCurrent(token)) return@execute
                require(RomSessionResolver.verifySha(entry, loaded.rom.sha256)) {
                    "the matched ROM changed after indexing; reselect the ROM library"
                }
                if (!sessionEpoch.commitIfCurrent(token) {
                        update {
                            it.copy(
                                resolution = "LOADING",
                                message = "Opening the SHA-256-verified active catalog…",
                            )
                        }
                    }
                ) return@execute
                runtime.load(
                    source = loaded,
                    commitIfCurrent = { commit -> sessionEpoch.commitIfCurrent(token, commit) },
                    onComplete = completion@{ result ->
                        result.onSuccess {
                            activationCoordinator.finish(token, entry.sourceId) {
                                update {
                                    it.copy(
                                        activeSource = entry.sourceName,
                                        contentSha256 = entry.sha256,
                                        sessionEpoch = token.epoch,
                                        resolution = "ACTIVE",
                                        message = "Opened ${entry.sourceName}.",
                                    )
                                }
                            }
                        }.onFailure { failure -> failActivation(entry, token, failure) }
                    },
                )
            } catch (failure: OutOfMemoryError) {
                failActivation(entry, token, failure, recordSourceFailure = true)
            } catch (failure: Exception) {
                failActivation(entry, token, failure, recordSourceFailure = true)
            }
        }
    }

    private fun failActivation(
        entry: RomIndexEntry,
        token: SessionWorkToken,
        failure: Throwable,
        recordSourceFailure: Boolean = false,
    ) {
        val publicFailure = GuideLoadFailure.from(failure)
        if (recordSourceFailure) runCatching { runtime.recordRomSourceLoadFailure(entry.sha256, failure) }
        activationCoordinator.fail(token, entry.sourceId) {
            update { it.copy(resolution = "FAILED", message = publicFailure.message) }
        }
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
                if (!sessionEpoch.commitIfCurrent(token) {
                        if (cachedCandidates == null) {
                            discoveredSaveRom.set(entry.sha256)
                            discoveredSaveBasename.set(activeGameBasename)
                        }
                        lastSaveCandidates.set(candidates)
                    }
                ) return@execute
                val autosaveStatus = readAutosaveStatus()
                if (!sessionEpoch.isCurrent(token)) return@execute
                val commitIfCurrent: ((() -> Unit) -> Boolean) = { commit ->
                    sessionEpoch.commitIfCurrent(token, commit)
                }
                val result = saveMonitor.poll(
                    context = parseContext,
                    candidates = candidates,
                    autosaveStatus = autosaveStatus,
                    isCurrent = { sessionEpoch.isCurrent(token) },
                    commitIfCurrent = commitIfCurrent,
                    persistAcceptance = false,
                ) ?: return@execute
                val saveView = result.toView()
                if ((result.snapshot != null || result.retained?.snapshot != null) && result.observation != null) {
                    checkpointCoordinator.apply(
                        result = result,
                        saveView = saveView,
                        commitIfCurrent = commitIfCurrent,
                        stagePrepared = { digest ->
                            saveMonitor.stagePrepared(result, digest) { sessionEpoch.isCurrent(token) }
                        },
                        commitPrepared = { persistence, publishAuthority ->
                            saveMonitor.commitPrepared(result, persistence, publishAuthority)
                        },
                        completePrepared = saveMonitor::completePrepared,
                    )
                } else if (result.snapshot != null) {
                    commitIfCurrent {
                        transientGameState.acceptRecovery(
                            RecoveryProjection(
                                snapshot = result.snapshot,
                                saveRam = saveView,
                            ),
                        )
                    }
                } else {
                    commitIfCurrent { transientGameState.acceptRecoveryStatus(saveView) }
                }
            } catch (failure: Exception) {
                val autosaveStatus = readAutosaveStatus()
                sessionEpoch.commitIfCurrent(token) {
                    transientGameState.acceptRecoveryStatus(
                        SaveRamView(
                            status = "UNAVAILABLE",
                            autosaveStatus = autosaveStatus,
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
        val acceptedCheckpoint = when (val read = checkpointCoordinator.readLatest(parseContext.romIdentity)) {
            is CheckpointReadResult.Present -> read.checkpoint
            CheckpointReadResult.Absent -> return
            is CheckpointReadResult.Corrupt, is CheckpointReadResult.Unavailable -> {
                sessionEpoch.commitIfCurrent(token) {
                    transientGameState.acceptRecoveryStatus(
                        SaveRamView(
                            status = "STALE",
                            autosaveStatus = autosaveStatus,
                            message = "The accepted SaveRAM recovery pair could not be verified; live monitoring will retry.",
                        ),
                    )
                }
                return
            }
        }
        val snapshotDigest = acceptedCheckpoint.snapshotDigestSha256
        if (snapshotDigest == null) {
            sessionEpoch.commitIfCurrent(token) {
                transientGameState.acceptRecoveryStatus(
                    SaveRamView(
                        status = "STALE",
                        autosaveStatus = autosaveStatus,
                        message = "The accepted SaveRAM recovery pair is incomplete; live monitoring will retry.",
                    ),
                )
            }
            return
        }
        val restored = try {
            val snapshotVersionId = acceptedCheckpoint.snapshotVersionId
            if (snapshotVersionId != null) {
                saveMonitor.restore(
                    parseContext,
                    autosaveStatus,
                    snapshotVersionId = snapshotVersionId,
                    snapshotDigestSha256 = snapshotDigest,
                    isCurrent = { sessionEpoch.isCurrent(token) },
                )
            } else {
                saveMonitor.restore(parseContext, autosaveStatus) { sessionEpoch.isCurrent(token) }
            }
        } catch (failure: Exception) {
            Log.e(
                LOG_TAG,
                PrivacySafeDiagnostics.message(
                    category = "SAVE_RAM",
                    outcome = "RESTORE_FAILED",
                    failure = failure,
                ),
            )
            sessionEpoch.commitIfCurrent(token) {
                transientGameState.acceptRecoveryStatus(
                    SaveRamView(
                        status = "STALE",
                        autosaveStatus = autosaveStatus,
                        message = "The cached SaveRAM snapshot could not be reopened; live monitoring will retry.",
                    ),
                )
            }
            return
        }
        if (restored?.snapshot == null) return
        checkpointCoordinator.applyPersisted(
            result = restored,
            saveView = restored.toView(),
            commitIfCurrent = { commit -> sessionEpoch.commitIfCurrent(token, commit) },
            acceptPrepared = { prepared ->
                saveMonitor.restoreAccepted(prepared).also { accepted ->
                    if (accepted) restoredSaveRom.set(parseContext.romIdentity)
                }
            },
            preloadedCheckpoint = acceptedCheckpoint,
        )
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

    private fun storedRomTree(): Uri? = indexStore.readActive()
        ?.rootUri
        ?.let(Uri::parse)
        ?: preferences.getString(ROM_TREE_URI, null)?.let(Uri::parse)

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
        val storedUri = storedRomTree()?.toString()
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
        val catalogCancellation = runtime.cancelPendingCatalogLoadForAuthorityTransition()
        sessionEpoch.observe(null)
        catalogCancellation?.complete()
        previousEntry?.let { activationGate.cancel(it.sourceId) }
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
        val uri = storedRomTree()?.toString() ?: return emptyList()
        if (!storedSafGrantIsValid()) return emptyList()
        return indexStore.read(uri)
    }

    private fun initialView(): RetroArchView {
        val storageGranted = sharedStorage.isGranted()
        val configUri = preferences.getString(CONFIG_TREE_URI, null)
        val romUri = storedRomTree()?.toString()
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
