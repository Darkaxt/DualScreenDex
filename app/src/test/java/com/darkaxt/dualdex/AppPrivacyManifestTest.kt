package com.darkaxt.dualdex

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppPrivacyManifestTest {
    @Test
    fun `production manifest disables Android backup for private game data`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(
            "Android backup must remain disabled for catalogs, save-derived state, and mapper sessions",
            manifest.contains("android:allowBackup=\"false\""),
        )
    }

    @Test
    fun `cleartext transport is scoped to the local WebView origin`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val networkPolicy = File("src/main/res/xml/network_security_config.xml").readText()

        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(networkPolicy.contains("<base-config cleartextTrafficPermitted=\"false\""))
        assertTrue(networkPolicy.contains("<domain-config cleartextTrafficPermitted=\"true\""))
        assertTrue(networkPolicy.contains(">127.0.0.1</domain>"))
    }
}
