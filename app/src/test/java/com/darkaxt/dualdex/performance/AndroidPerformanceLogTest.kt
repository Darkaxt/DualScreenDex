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

        val exported = log.export().toString(Charsets.UTF_8)
        assertFalse(exported.contains("session-0"))
        assertTrue(exported.contains("session-19"))
        assertTrue(exported.indexOf("session-18") < exported.indexOf("session-19"))
    }

    @Test
    fun `persisted json exposes only the stable minimized event contract`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-json-").also(roots::add).toFile()
        val log = AndroidPerformanceLog(root)

        log.append(event(sessionId = "safe-session", elapsedMillis = 42L))

        val json = log.export().toString(Charsets.UTF_8)
        assertTrue(json.contains("\"schemaVersion\":3"))
        assertFalse(json.contains("romSha256", ignoreCase = true))
        assertFalse(json.contains("romPath", ignoreCase = true))
        assertFalse(json.contains("player", ignoreCase = true))
        assertFalse(json.contains("rawMemory", ignoreCase = true))
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
        val json = log.export().toString(Charsets.UTF_8)
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

        val json = log.export().toString(Charsets.UTF_8)
        assertTrue(json.contains("\"category\":\"ANR\""))
        assertTrue(json.contains("\"timestampBucket\":79866"))
        assertTrue(json.contains("\"memoryBucket\":\"64_TO_127_MIB\""))
        assertFalse(json.contains("description", ignoreCase = true))
        assertFalse(json.contains("trace", ignoreCase = true))
    }

    @Test
    fun `failed rotation drops the new record instead of exceeding the segment bound`() {
        val root = Files.createTempDirectory(Path.of("build"), "performance-rotation-failure-").also(roots::add).toFile()
        val active = root.resolve(AndroidPerformanceLog.ACTIVE_FILE_NAME)
        active.writeBytes(ByteArray(620))
        root.resolve(AndroidPerformanceLog.PREVIOUS_FILE_NAME).apply {
            mkdir()
            resolve("still-in-use").writeText("occupied")
        }
        val log = AndroidPerformanceLog(root, maximumSegmentBytes = 640)

        log.append(event(sessionId = "must-be-dropped", elapsedMillis = 99L))

        assertTrue(active.length() <= 640L)
        assertFalse(active.readText().contains("must-be-dropped"))
    }

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
