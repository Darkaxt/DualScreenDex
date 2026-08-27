package com.darkaxt.dualdex.mapper

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkaxt.dualdex.retroarch.NetworkCommandTransport
import com.enrpau.dualscreendex.companion.api.RetroArchView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class MemoryMapperIsolationInstrumentedTest {
    @Test fun capturesAndExportsInsideItsOwnNamespaceWithoutChangingCatalogBytes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.filesDir, "instrumented-mapper-${UUID.randomUUID()}").apply { mkdirs() }
        val catalogMarker = File(root, "catalogs/active.sqlite").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("unchanged-catalog")
        }
        val network = FakeTransport()
        val coordinator = MemoryMapperCoordinator(
            MapperSessionStore(File(root, "memory-mapper")),
            {
                RetroArchView(
                    connection = "PLAYING",
                    systemId = "Nintendo - Game Boy",
                    contentCrc32 = "1234ABCD",
                    contentSha256 = "a".repeat(64),
                    sessionEpoch = 1,
                    resolution = "ACTIVE",
                )
            },
            commitIfSessionCurrent = { _, commit -> commit(); true },
            transportFactory = { network },
            scheduler = Executors.newSingleThreadScheduledExecutor(),
            startHeartbeat = false,
        )
        try {
            assertFalse(coordinator.snapshot().enabled)
            coordinator.action("ENABLE", mapOf("privacyAcknowledged" to "true"))
            coordinator.action("CAPTURE", mapOf("label" to "BATTLE_START"))
            coordinator.heartbeatNow()

            assertEquals("unchanged-catalog", catalogMarker.readText())
            assertTrue(network.commands.all { it.startsWith("READ_CORE_MEMORY ") })
            assertTrue(coordinator.exportRaw().toString(Charsets.UTF_8).contains("\"containsRawMemory\":true"))
        } finally {
            coordinator.close()
            root.deleteRecursively()
        }
    }

    private class FakeTransport : NetworkCommandTransport {
        val commands = mutableListOf<String>()
        private val replies = ArrayDeque<ByteArray>()
        override fun send(payload: ByteArray) {
            val command = payload.toString(Charsets.US_ASCII)
            commands += command
            val parts = command.split(' ')
            replies += "READ_CORE_MEMORY ${parts[1]} ${List(parts[2].toInt()) { "00" }.joinToString(" ")}".toByteArray()
        }
        override fun poll(): ByteArray? = replies.pollFirst()
        override fun close() = Unit
    }
}
