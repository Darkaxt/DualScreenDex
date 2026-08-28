package com.darkaxt.dualdex.performance

import com.darkaxt.dualdex.live.ResolvedGameSection
import com.darkaxt.dualdex.live.ResolvedStateFieldChange
import com.darkaxt.dualdex.live.ResolvedStateFieldTrace
import com.darkaxt.dualdex.live.ResolvedStateTraceEvent
import com.darkaxt.dualdex.live.ResolvedStateTraceTrigger
import com.darkaxt.dualdex.live.ResolvedValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPerformanceLogTest {
    private val roots = mutableListOf<Path>()

    @After
    fun clean() {
        roots.asReversed().forEach { root ->
            if (Files.exists(root)) Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Path::deleteIfExists)
        }
    }

    @Test
    fun `rotates two bounded segments and exports older records before current records`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-log-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root, maximumSegmentBytes = 640)

        repeat(20) { index ->
            log.append(event(sessionId = "session-$index", elapsedMillis = index.toLong()))
        }

        val current = root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME)
        val previous = root.resolve(AndroidPerformanceLog.PREVIOUS_FILE_NAME)
        assertTrue(current.length() <= 640L)
        assertTrue(previous.length() <= 640L)
        assertTrue(current.length() + previous.length() <= 1_280L)

        val exported = log.exportedBytes().toString(Charsets.UTF_8)
        assertFalse(exported.contains("session-0"))
        assertTrue(exported.contains("session-19"))
        assertTrue(exported.indexOf("session-18") < exported.indexOf("session-19"))
    }

    @Test
    fun `persisted json exposes only the stable minimized event contract`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-json-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root)

        log.append(event(sessionId = "safe-session", elapsedMillis = 42L))

        val json = log.exportedBytes().toString(Charsets.UTF_8)
        assertTrue(json.contains("\"schemaVersion\":3"))
        assertFalse(json.contains("romSha256", ignoreCase = true))
        assertFalse(json.contains("romPath", ignoreCase = true))
        assertFalse(json.contains("player", ignoreCase = true))
        assertFalse(json.contains("rawMemory", ignoreCase = true))
    }

    @Test
    fun `upgrading the diagnostic contract purges legacy reversible records`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-upgrade-").also(roots::add).toFile()
        root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME).writeText(
            """{"schemaVersion":1,"fingerprint":"00000bb8","playerX":12}""",
        )
        root.resolve(AndroidPerformanceLog.PREVIOUS_FILE_NAME).writeText(
            """{"schemaVersion":2,"romSha256Prefix":"aaaaaaaaaaaa"}""",
        )

        val log = AndroidPerformanceLog(root)
        log.append(event(sessionId = "current-contract", elapsedMillis = 42L))

        val exported = log.exportedBytes().toString(Charsets.UTF_8)
        assertTrue(exported.contains("current-contract"))
        assertFalse(exported.contains("00000bb8"))
        assertFalse(exported.contains("playerX"))
        assertFalse(exported.contains("aaaaaaaaaaaa"))
        assertFalse(exported.contains("romSha256Prefix"))
        assertTrue(root.resolve(AndroidPerformanceLog.CONTRACT_FILE_NAME).isFile)
        assertTrue(AndroidPerformanceLog(root).exportedBytes().contentEquals(log.exportedBytes()))
    }

    @Test
    fun `state changes share the bounded log without exposing private values`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-state-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root, maximumSegmentBytes = 640)

        repeat(20) { index ->
            log.append(
                event(
                    sessionId = "state-$index",
                    elapsedMillis = index.toLong(),
                ).copy(
                    kind = PerformanceEventKind.STATE_CHANGED,
                    metrics = PerformanceMetrics(),
                    stateChange = stateTrace(index.toLong()),
                ),
            )
        }

        val current = root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME)
        val previous = root.resolve(AndroidPerformanceLog.PREVIOUS_FILE_NAME)
        assertTrue(current.length() <= 640L)
        assertTrue(previous.length() <= 640L)
        assertTrue(current.length() + previous.length() <= 1_280L)
        val json = log.exportedBytes().toString(Charsets.UTF_8)
        assertTrue(json.contains("\"kind\":\"STATE_CHANGED\""))
        assertTrue(json.contains("\"field\":\"pokedex.caught\""))
        assertFalse(json.contains("RED"))
        assertFalse(json.contains("save-a"))
    }

    @Test
    fun `previous process exit is locally exportable without raw platform detail`() {
        val root = Files.createTempDirectory(Path.of("build"), "previous-exit-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root)

        log.append(
            PreviousProcessExitEvent(
                category = PreviousProcessExitCategory.ANR,
                timestampBucket = 79_866,
                memoryBucket = "64_TO_127_MIB",
            ),
        )

        val json = log.exportedBytes().toString(Charsets.UTF_8)
        assertTrue(json.contains("\"category\":\"ANR\""))
        assertTrue(json.contains("\"timestampBucket\":79866"))
        assertTrue(json.contains("\"memoryBucket\":\"64_TO_127_MIB\""))
        assertFalse(json.contains("description", ignoreCase = true))
        assertFalse(json.contains("trace", ignoreCase = true))
    }

    @Test
    fun `truncates a crash fragment through dedupe ID before appending one valid exit and advancing marker`() {
        val root = Files.createTempDirectory(Path.of("build"), "previous-exit-fragment-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root)
        val dedupeId = "fragment-exit-id"
        root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME).writeText(
            "{\"schemaVersion\":1,\"dedupeId\":\"$dedupeId\"",
        )
        var marker: String? = null
        val recorder = PreviousProcessExitRecorder(
            source = PreviousProcessExitSource {
                PreviousProcessExitSnapshot(
                    category = PreviousProcessExitCategory.CRASH,
                    timestampEpochMillis = 1_725_123_456_789L,
                    pssKilobytes = 100_000L,
                    rssKilobytes = 100_000L,
                )
            },
            marker = object : PreviousProcessExitMarker {
                override fun read(): String? = marker
                override fun readPending() = PreviousProcessExitPending(
                    "1725123456789:CRASH:64_TO_127_MIB",
                    dedupeId,
                )
                override fun writePending(value: PreviousProcessExitPending): Boolean = error("pending already exists")
                override fun write(value: String): Boolean {
                    val records = log.exportedBytes().toString(Charsets.UTF_8).lines().filter(String::isNotBlank)
                    assertEquals(1, records.size)
                    assertTrue(records.single().endsWith("}"))
                    assertTrue(records.single().contains("\"dedupeId\":\"$dedupeId\""))
                    marker = value
                    return true
                }
            },
            sink = PreviousProcessExitSink(log::append),
        )

        assertTrue(recorder.recordLatest() != null)
        assertTrue(marker != null)
        val records = log.exportedBytes().toString(Charsets.UTF_8).lines().filter(String::isNotBlank)
        assertEquals(1, records.size)
        assertEquals(1, records.single().split("\"dedupeId\":\"$dedupeId\"").size - 1)
    }

    @Test
    fun `stable previous exit ID makes marker recovery append idempotent`() {
        val root = Files.createTempDirectory(Path.of("build"), "previous-exit-idempotence-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root)
        val event = PreviousProcessExitEvent(
            category = PreviousProcessExitCategory.CRASH,
            timestampBucket = 79_866,
            memoryBucket = "64_TO_127_MIB",
            dedupeId = "stable-exit-id",
        )

        assertTrue(log.append(event))
        assertTrue(log.append(event))

        val json = log.exportedBytes().toString(Charsets.UTF_8)
        assertEquals(1, json.split("\"dedupeId\":\"stable-exit-id\"").size - 1)
    }

    @Test
    fun `failed rotation drops the new record instead of exceeding the segment bound`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-rotation-failure-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root, maximumSegmentBytes = 640)
        val active = root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME)
        active.writeBytes(ByteArray(620))
        root.resolve(AndroidPerformanceLog.PREVIOUS_FILE_NAME).apply {
            mkdir()
            resolve("still-in-use").writeText("occupied")
        }

        log.append(event(sessionId = "must-be-dropped", elapsedMillis = 99L))

        assertTrue(active.length() <= 640L)
        assertFalse(active.readText().contains("must-be-dropped"))
    }

    @Test
    fun `diagnostic write failure is contained instead of escaping the app`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-write-failure-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root)
        root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME).mkdir()

        log.append(event(sessionId = "must-be-contained", elapsedMillis = 100L))

        assertEquals(PerformanceLogExport.Unavailable, log.export())
    }

    @Test
    fun `append and export report unavailable when the active segment is not writable`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-durability-failure-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root)
        root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME).mkdir()

        assertFalse(log.append(event(sessionId = "must-not-be-marked-durable", elapsedMillis = 101L)))
        assertEquals(PerformanceLogExport.Unavailable, log.export())
    }

    @Test
    fun `uncreatable diagnostic directory disables the optional log without throwing`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-startup-failure-").also(roots::add)
        val blockingFile = root.resolve("not-a-directory")
        Files.write(blockingFile, "blocked".toByteArray())

        assertTrue(runCatching { AndroidPerformanceLog(blockingFile.resolve("diagnostics").toFile()) }.isSuccess)
    }

    private fun AndroidPerformanceLog.exportedBytes(): ByteArray =
        (export() as? PerformanceLogExport.Available)?.bytes ?: error("diagnostics export is unavailable")

    private fun event(sessionId: String, elapsedMillis: Long) = PerformanceEvent(
        schemaVersion = PERFORMANCE_SCHEMA_VERSION,
        sessionId = sessionId,
        wallClockEpochMillis = 1_725_000_000_000L + elapsedMillis,
        elapsedMillis = elapsedMillis,
        kind = PerformanceEventKind.RUNTIME_MINUTE,
        generation = 3,
        runtimeMinute = elapsedMillis,
        metrics = PerformanceMetrics(javaHeapUsedBytes = 10L),
    )

    private fun stateTrace(revision: Long) = ResolvedStateTraceEvent(
        revision = revision,
        trigger = ResolvedStateTraceTrigger.LIVE_SAMPLE,
        generation = 3,
        sampleId = revision,
        recoveryApplicationId = 2,
        recoveryObservationKind = null,
        changedSections = setOf(ResolvedGameSection.PLAYER),
        fields = listOf(
            ResolvedStateFieldChange(
                field = "pokedex.caught",
                before = ResolvedStateFieldTrace(ResolvedValueSource.RECOVERY, true, count = 52),
                after = ResolvedStateFieldTrace(ResolvedValueSource.LIVE, true, count = 1),
            ),
        ),
    )
}
