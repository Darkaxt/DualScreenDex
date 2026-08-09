package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDocumentEditorTest {
    @Test
    fun patchesOnlyApprovedKeysAndPreservesCrLfAndUnrelatedBytes() {
        val original = (
            "# network_cmd_enable = \"false\"\r\n" +
                "video_driver = \"vulkan\"\r\n" +
                "network_cmd_enable = \"false\"\r\n" +
                "network_cmd_port = \"55354\"\r\n"
            ).toByteArray()

        val patch = ConfigDocumentEditor.patchNetworkCommands(original, 55355)

        assertEquals(
            "# network_cmd_enable = \"false\"\r\n" +
                "video_driver = \"vulkan\"\r\n" +
                "network_cmd_enable = \"true\"\r\n" +
                "network_cmd_port = \"55355\"\r\n",
            patch.updated.toString(Charsets.UTF_8),
        )
        assertEquals(setOf("network_cmd_enable", "network_cmd_port"), patch.changedKeys)
        assertTrue(ConfigDocumentEditor.verifyNetworkCommands(patch.updated, 55355).valid)
    }

    @Test
    fun appendsMissingKeysUsingTheExistingLineEnding() {
        val patch = ConfigDocumentEditor.patchNetworkCommands("video_driver = \"gl\"\n".toByteArray(), 55355)

        assertEquals(
            "video_driver = \"gl\"\nnetwork_cmd_enable = \"true\"\nnetwork_cmd_port = \"55355\"\n",
            patch.updated.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun rejectsAnInvalidPortWithoutChangingTheDocument() {
        val original = "network_cmd_enable = \"false\"\n".toByteArray()

        val failure = runCatching { ConfigDocumentEditor.patchNetworkCommands(original, 0) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(original.toString(Charsets.UTF_8).contains("true"))
    }
}
