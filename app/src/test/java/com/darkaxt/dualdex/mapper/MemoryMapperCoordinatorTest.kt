package com.darkaxt.dualdex.mapper

import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.enrpau.dualscreendex.companion.api.RetroArchView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor

class MemoryMapperCoordinatorTest {
    @Test fun disabledMapperSchedulesNoIdleHeartbeat() {
        val root = testDirectory()
        val scheduler = ScheduledThreadPoolExecutor(1)
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "mapper")), { playingView() },
            commitIfSessionCurrent = ::commitCurrent,
            scheduler = scheduler,
        )
        try {
            assertEquals(0, scheduler.queue.size)
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    @Test fun startsDisabledAndDoesNotOpenOrReadRetroArch() {
        var opened = 0
        val root = testDirectory()
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "mapper")), { playingView() },
            commitIfSessionCurrent = ::commitCurrent,
            transportFactory = { opened++; FakeTransport() },
            scheduler = Executors.newSingleThreadScheduledExecutor(), startHeartbeat = false,
        )
        try {
            assertFalse(coordinator.snapshot().enabled)
            assertEquals(0, opened)
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.action("CAPTURE", mapOf("label" to "OVERWORLD"))
            }
            assertEquals(0, opened)
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    @Test fun capturesOnlyReadCommandsAndCannotChangeTheProductionNamespace() {
        val root = testDirectory()
        val production = File(root, "catalogs/active.sqlite").apply { requireNotNull(parentFile).mkdirs(); writeText("production") }
        val before = sha256(production.readBytes())
        val network = FakeTransport()
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "memory-mapper")), { playingView() },
            commitIfSessionCurrent = ::commitCurrent,
            transportFactory = { network }, scheduler = Executors.newSingleThreadScheduledExecutor(), startHeartbeat = false,
        )
        try {
            coordinator.action("ENABLE", mapOf("privacyAcknowledged" to "true"))
            coordinator.action("CAPTURE", mapOf("label" to "BATTLE_START"))
            coordinator.heartbeatNow()

            assertEquals(1, coordinator.snapshot().snapshots.size)
            assertTrue(network.commands.isNotEmpty())
            assertTrue(network.commands.all { it.startsWith("READ_CORE_MEMORY ") })
            assertFalse(network.commands.any { it.contains("WRITE") })

            coordinator.action("DISABLE", emptyMap())
            assertFalse(coordinator.snapshot().enabled)
            assertEquals(before, sha256(production.readBytes()))
            assertTrue(coordinator.exportRaw().toString(Charsets.UTF_8).contains("\"containsRawMemory\":true"))
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    @Test fun captureFailsClosedWhenVerifiedContentAuthorityChangesBeforePublication() {
        val root = testDirectory()
        var session = playingView()
        val network = FakeTransport()
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "memory-mapper")), { session },
            commitIfSessionCurrent = ::commitCurrent,
            transportFactory = { network }, scheduler = Executors.newSingleThreadScheduledExecutor(), startHeartbeat = false,
        )
        try {
            coordinator.action("ENABLE", mapOf("privacyAcknowledged" to "true"))
            coordinator.action("CAPTURE", mapOf("label" to "OVERWORLD"))
            session = playingView(contentSha256 = "b".repeat(64))

            coordinator.heartbeatNow()

            assertFalse(coordinator.snapshot().enabled)
            assertTrue(coordinator.snapshot().snapshots.isEmpty())
            assertTrue(network.closed)
            assertTrue(coordinator.snapshot().error!!.contains("authority"))
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    @Test fun captureCannotCommitAfterTheSessionEpochFenceRejectsPublication() {
        val root = testDirectory()
        val mapperDirectory = File(root, "memory-mapper")
        var session = playingView()
        var commitAttempted = false
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(mapperDirectory),
            { session },
            commitIfSessionCurrent = { expectedEpoch, _ ->
                assertEquals(1L, expectedEpoch)
                commitAttempted = true
                session = playingView(contentSha256 = "b".repeat(64), sessionEpoch = 2)
                false
            },
            transportFactory = ::FakeTransport,
            scheduler = Executors.newSingleThreadScheduledExecutor(),
            startHeartbeat = false,
        )
        try {
            coordinator.action("ENABLE", mapOf("privacyAcknowledged" to "true"))
            coordinator.action("CAPTURE", mapOf("label" to "OVERWORLD"))

            coordinator.heartbeatNow()

            assertTrue(commitAttempted)
            assertFalse(coordinator.snapshot().enabled)
            assertTrue(coordinator.snapshot().snapshots.isEmpty())
            assertTrue(mapperDirectory.listFiles().isNullOrEmpty())
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    @Test fun delayedReplyFromCancelledCaptureCannotReachTheNextCapture() {
        val root = testDirectory()
        val transports = mutableListOf<FakeTransport>()
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "memory-mapper")), { playingView() },
            commitIfSessionCurrent = ::commitCurrent,
            transportFactory = { FakeTransport().also(transports::add) },
            scheduler = Executors.newSingleThreadScheduledExecutor(), startHeartbeat = false,
        )
        try {
            coordinator.action("ENABLE", mapOf("privacyAcknowledged" to "true"))
            coordinator.action("CAPTURE", mapOf("label" to "OVERWORLD"))
            coordinator.action("DISABLE", emptyMap())
            coordinator.action("ENABLE", mapOf("privacyAcknowledged" to "true"))
            coordinator.action("CAPTURE", mapOf("label" to "BATTLE_START"))
            coordinator.heartbeatNow()

            assertTrue(transports[0].closed)
            assertTrue(transports[0].pendingReplies > 0)
            assertEquals(1, coordinator.snapshot().snapshots.size)
            assertEquals("BATTLE_START", coordinator.snapshot().snapshots.single().label)
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    @Test fun eachCaptureOwnsAndClosesAFreshTransport() {
        val root = testDirectory()
        val transports = mutableListOf<FakeTransport>()
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "memory-mapper")), { playingView() },
            commitIfSessionCurrent = ::commitCurrent,
            transportFactory = { FakeTransport().also(transports::add) },
            scheduler = Executors.newSingleThreadScheduledExecutor(), startHeartbeat = false,
        )
        try {
            coordinator.action("ENABLE", mapOf("privacyAcknowledged" to "true"))
            repeat(2) {
                coordinator.action("CAPTURE", mapOf("label" to "OVERWORLD"))
                coordinator.heartbeatNow()
            }

            assertEquals(2, coordinator.snapshot().snapshots.size)
            assertEquals(2, transports.size)
            assertTrue(transports.all(FakeTransport::closed))
            assertFalse(transports[0] === transports[1])
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    private fun commitCurrent(@Suppress("UNUSED_PARAMETER") expectedEpoch: Long, commit: () -> Unit): Boolean {
        commit()
        return true
    }

    private fun playingView(
        contentSha256: String = "a".repeat(64),
        sessionEpoch: Long = 1,
    ) = RetroArchView(
        connection = "PLAYING",
        systemId = "Nintendo - Game Boy",
        gameBasename = "Pokemon Red",
        contentCrc32 = "1234ABCD",
        contentSha256 = contentSha256,
        sessionEpoch = sessionEpoch,
        resolution = "ACTIVE",
    )

    private fun testDirectory() = File("build/tmp/mapper-tests/${UUID.randomUUID()}").apply { mkdirs() }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeTransport : NetworkCommandTransport {
        val commands = mutableListOf<String>()
        var closed = false
        private val replies = ArrayDeque<ByteArray>()
        val pendingReplies: Int get() = replies.size
        override fun send(payload: ByteArray) {
            val command = payload.toString(Charsets.US_ASCII)
            commands += command
            val parts = command.split(' ')
            val bytes = List(parts[2].toInt()) { "00" }.joinToString(" ")
            replies += "READ_CORE_MEMORY ${parts[1]} $bytes".toByteArray(Charsets.US_ASCII)
        }
        override fun poll(): ByteArray? = replies.pollFirst()
        override fun close() {
            closed = true
        }
    }
}
