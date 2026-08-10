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

class MemoryMapperCoordinatorTest {
    @Test fun startsDisabledAndDoesNotOpenOrReadRetroArch() {
        var opened = 0
        val root = testDirectory()
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "mapper")), { playingView() },
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

    private fun playingView() = RetroArchView(
        connection = "PLAYING", systemId = "Nintendo - Game Boy", gameBasename = "Pokemon Red", contentCrc32 = "1234ABCD",
    )

    private fun testDirectory() = File("build/tmp/mapper-tests/${UUID.randomUUID()}").apply { mkdirs() }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeTransport : NetworkCommandTransport {
        val commands = mutableListOf<String>()
        private val replies = ArrayDeque<ByteArray>()
        override fun send(payload: ByteArray) {
            val command = payload.toString(Charsets.US_ASCII)
            commands += command
            val parts = command.split(' ')
            val bytes = List(parts[2].toInt()) { "00" }.joinToString(" ")
            replies += "READ_CORE_MEMORY ${parts[1]} $bytes".toByteArray(Charsets.US_ASCII)
        }
        override fun poll(): ByteArray? = replies.pollFirst()
        override fun close() = Unit
    }
}
