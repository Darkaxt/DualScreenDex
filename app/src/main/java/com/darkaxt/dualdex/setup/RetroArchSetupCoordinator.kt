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
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveCandidateView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class RetroArchSetupCoordinator(
    private val context: Context,
    private val runtime: ProductionCompanionRuntime,
    private val transientGameState: UnifiedGameStateDecoder,
    private val checkpointCoordinator: SaveKnowledgeCheckpointCoordinator,
    private val commandPort: Int = UdpNetworkCommandTransport.DEFAULT_PORT,
) : AutoCloseable {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val indexStore = RomIndexStore(File(context.filesDir, "retroarch/rom-index.json"))
    private val directIndexStore = RomIndexStore(File(context.filesDir, "retroarch/direct-rom-index.json"))
    private val sharedStorage = SharedStorageGateway.android(context)
    private val saveMonitor = SavePollingMonitor(
        SaveAssociationStore(File(context.filesDir, "retroarch/save-associations.json")),
        SaveSnapshotStore(File(context.filesDir, "catalogs"), AndroidCatalogDatabaseFactory),
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
    private val monitor = AtomicReference<SessionMonitor?>(null)
    private val battleMemory = BattleMemoryCoordinator(
        catalogProvider = runtime::battleCatalogContext,
        publisher = runtime::applyBattleTracking,
        locationPublisher = runtime::updateLiveArea,
        positionPublisher = runtime::updateLiveMapPosition,
        partyPublisher = runtime::updateLiveParty,
        liveGamePublisher = runtime::updateLiveGameState,
        gen2LightingPublisher = runtime::updateGen2GameClock,
        transientGameState = transientGameState,
        transportFactory = { UdpNetworkCommandTransport(commandPort) },
        pollingIntervalProvider = runtime::battlePollingIntervalMs,
    )
    private val restartVerifier = RestartVerifier()
    private val cachedDirectEntries = if (sharedStorage.isGranted()) directIndexStore.read(ALL_FILES_INDEX_KEY) else emptyList()
    private val entries = AtomicReference(cachedDirectEntries.ifEmpty(::loadSafStoredIndex))
    private val directIndexReady = AtomicBoolean(cachedDirectEntries.isNotEmpty())
    private val view = AtomicReference(initialView())
    private val activating = AtomicReference<String?>(null)
    private val pollingSave = AtomicBoolean(false)
    private val directIndexing = AtomicBoolean(false)
    private val directRefreshStarted = AtomicBoolean(false)
    private val directConfigAttempt = AtomicReference<String?>(null)
    private val lastStorageAccess = AtomicBoolean(sharedStorage.isGranted())
    private val activeEntry = AtomicReference<RomIndexEntry?>(null)
    private val lastSaveCandidates = AtomicReference<List<SaveDocumentSource>>(emptyList())
    private val discoveredSaveRom = AtomicReference<String?>(null)
    private val discoveredSaveBasename = AtomicReference<String?>(null)
    private val restoredSaveRom = AtomicReference<String?>(null)
    @Volatile private var lastActivatedSha: String? = null

    init {
        publish(view.get())
        refreshStorageAccess()
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
        update { it.copy(romGrant = "INDEXING", message = "Indexing granted GB, GBC, GBA, and ZIP sources…") }
        worker.execute {
            val previousEntries = indexStore.read(uri.toString())
            val result = runCatching { AndroidRomLibraryIndexer(context.contentResolver).index(uri, previousEntries) }
            result.onSuccess { indexed ->
                indexStore.write(uri.toString(), indexed.entries)
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
                update { it.copy(romGrant = "FAILED", message = failure.message ?: failure.javaClass.simpleName) }
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
                entries.set(loadSafStoredIndex())
                lastSaveCandidates.set(emptyList())
                discoveredSaveRom.set(null)
                discoveredSaveBasename.set(null)
                val romGranted = storedRomTree()?.let(::hasReadGrant) == true
                val configGranted = storedConfigTree()?.let(::hasConfigGrant) == true
                update {
                    it.copy(
                        storageGrant = "MISSING",
                        configGrant = if (configGranted) "GRANTED" else "MISSING",
                        romGrant = if (romGranted) "GRANTED" else "MISSING",
                        indexedRoms = entries.get().size,
                        message = "Grant All files access for automatic multi-folder ROM and SaveRAM discovery; folder selection remains available as a fallback.",
                    )
                }
            }

            StorageIndexAction.USE_DIRECT -> {
                update {
                    it.copy(
                        storageGrant = "GRANTED",
                        romGrant = "GRANTED",
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

    fun selectSave(documentId: String): Boolean {
        val entry = activeEntry.get() ?: return false
        if (lastSaveCandidates.get().none { it.id == documentId }) return false
        saveMonitor.select(entry.sha256, documentId)
        runtime.updateSaveRam(
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
        heartbeat.shutdown()
        battleMemory.close()
        worker.shutdown()
        indexWorker.shutdown()
        monitor.get()?.close()
    }

    private fun indexSharedStorage() {
        if (!directIndexing.compareAndSet(false, true)) return
        val retainedDirectIndex = directIndexReady.get()
        update {
            it.copy(
                storageGrant = "INDEXING",
                romGrant = "INDEXING",
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
                update {
                    it.copy(
                        storageGrant = "GRANTED",
                        romGrant = "GRANTED",
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
                update {
                    it.copy(
                        storageGrant = when {
                            !sharedStorage.isGranted() -> "MISSING"
                            retainedDirectIndex -> "GRANTED"
                            else -> "FAILED"
                        },
                        romGrant = when {
                            retainedDirectIndex -> "GRANTED"
                            storedRomTree()?.let(::hasReadGrant) == true -> "GRANTED"
                            else -> "FAILED"
                        },
                        message = failure.message ?: failure.javaClass.simpleName,
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
        val storageGranted = sharedStorage.isGranted()
        if (lastStorageAccess.getAndSet(storageGranted) != storageGranted) refreshStorageAccess()
        restorePersistedSave()
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
                activeEntry.set(resolvedEntry)
                battleMemory.updateSession(
                    connected = connected && active,
                    systemId = status?.systemId,
                    romIdentity = resolvedEntry?.sha256,
                )
                update { current ->
                    current.copy(
                        configState = if (connected && restartVerified) "VERIFIED" else current.configState,
                        restartRequired = if (restartVerifier.restartRequired) true else if (restartVerified) false else current.restartRequired,
                        connection = session.connection.name,
                        systemId = status?.systemId,
                        gameBasename = status?.gameBasename,
                        contentCrc32 = status?.crc32,
                        savefileDirectory = session.savefileDirectory,
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
                if (active) pollSave(requireNotNull(resolvedEntry))
            }
            .onFailure { failure ->
                battleMemory.updateSession(false, null, null)
                update { it.copy(connection = "DISCONNECTED", message = failure.message) }
            }
    }

    private fun activate(entry: RomIndexEntry) {
        if (entry.sha256 == lastActivatedSha && runtime.catalogHash() == entry.sha256) return
        if (!activating.compareAndSet(null, entry.sourceId)) return
        update { it.copy(resolution = "LOADING", message = "Verifying the active ROM before opening its catalog…") }
        worker.execute {
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

    private fun pollSave(entry: RomIndexEntry) {
        if (!pollingSave.compareAndSet(false, true)) return
        worker.execute {
            try {
                val parseContext = runtime.saveParseContext()
                if (parseContext == null || !parseContext.romIdentity.equals(entry.sha256, ignoreCase = true)) return@execute
                val resolver = AndroidSaveDocumentResolver(context.contentResolver)
                val activeGameBasename = view.get().gameBasename
                val cachedCandidates = lastSaveCandidates.get().takeIf {
                    discoveredSaveRom.get().equals(entry.sha256, ignoreCase = true) &&
                        discoveredSaveBasename.get().equals(activeGameBasename, ignoreCase = true) &&
                        it.isNotEmpty()
                }
                val candidates = cachedCandidates?.let { refreshSaveCandidates(it, resolver) }
                    ?: discoverSaveCandidates(entry, resolver, activeGameBasename).also {
                        discoveredSaveRom.set(entry.sha256)
                        discoveredSaveBasename.set(activeGameBasename)
                    }
                lastSaveCandidates.set(candidates)
                val result = saveMonitor.poll(parseContext, candidates, readAutosaveStatus())
                val saveView = result.toView()
                result.snapshot?.let { snapshot ->
                    transientGameState.acceptRecovery(
                        RecoveryProjection(
                            snapshot = snapshot,
                            saveRam = saveView,
                            observation = result.observation,
                        ),
                    )
                }
                if (result.snapshot != null && result.observation != null) checkpointCoordinator.apply(result, saveView)
                else if (result.snapshot != null) runtime.applySaveSnapshot(result.snapshot, saveView)
                else runtime.updateSaveRam(saveView)
            } catch (failure: Exception) {
                runtime.updateSaveRam(
                    SaveRamView(
                        status = "UNAVAILABLE",
                        autosaveStatus = readAutosaveStatus(),
                        message = failure.message ?: failure.javaClass.simpleName,
                    ),
                )
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

    private fun restorePersistedSave() {
        val parseContext = runtime.saveParseContext() ?: return
        if (restoredSaveRom.get().equals(parseContext.romIdentity, ignoreCase = true)) return
        val autosaveStatus = readAutosaveStatus()
        val restored = try {
            saveMonitor.restore(parseContext, autosaveStatus)
        } catch (failure: Exception) {
            Log.e(LOG_TAG, "Could not restore the cached SaveRAM snapshot", failure)
            runtime.updateSaveRam(
                SaveRamView(
                    status = "STALE",
                    autosaveStatus = autosaveStatus,
                    message = "The cached SaveRAM snapshot could not be reopened; live monitoring will retry.",
                ),
            )
            return
        }
        val snapshot = restored?.snapshot ?: return
        transientGameState.acceptRecovery(
            RecoveryProjection(
                snapshot = snapshot,
                saveRam = restored.toView(),
            ),
        )
        if (runtime.applySaveSnapshot(snapshot, restored.toView())) {
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

    private fun loadSafStoredIndex(): List<RomIndexEntry> {
        val uri = preferences.getString(ROM_TREE_URI, null) ?: return emptyList()
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
        return RetroArchView(
            storageGrant = if (storageGranted) "GRANTED" else "MISSING",
            configGrant = if (configGranted) "GRANTED" else "MISSING",
            romGrant = when {
                storageGranted && directIndexReady.get() -> "GRANTED"
                storageGranted -> "INDEXING"
                safRomGranted -> "GRANTED"
                else -> "MISSING"
            },
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
        const val HEARTBEAT_INTERVAL_SECONDS = 2L
        const val LOG_TAG = "DualDexSaveRAM"
        val RETROARCH_PACKAGES = listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
    }
}
