package com.darkaxt.dualdex

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RetroArchFreeUiQaIsolationTest {
    @Test
    fun `debug source overlay selects the QA application and label`() {
        val manifest = File("src/debug/AndroidManifest.xml")
        val strings = File("src/debug/res/values/strings.xml")

        val application = File("src/debug/java/com/darkaxt/dualdex/RetroArchFreeUiQaApplication.kt").readText()

        assertTrue("debug manifest overlay must exist", manifest.isFile)
        assertTrue("debug application class must be selected", manifest.readText().contains("android:name=\".RetroArchFreeUiQaApplication\""))
        assertTrue("debug string overlay must exist", strings.isFile)
        assertTrue(strings.readText().contains(">DualDex RetroArch-Free UI QA<"))
        assertFalse(application.contains("UdpNetworkCommandTransport"))
        assertFalse(application.contains("NetworkCommandClient"))
        assertFalse(application.contains("BuildConfig"))
    }

    @Test
    fun `production sources remain isolated and instrumentation substitution remains compatible`() {
        val productionRoot = File("src/main")
        val productionManifest = File(productionRoot, "AndroidManifest.xml").readText()
        val runner = File("src/androidTest/java/com/darkaxt/dualdex/QaAndroidJUnitRunner.kt").readText()
        val forbidden = listOf("RetroArchFreeUiQaApplication", "retroarch-free-ui-qa")

        assertTrue(productionManifest.contains("android:name=\".DualDexApplication\""))
        productionRoot.walkTopDown().filter(File::isFile).forEach { file ->
            val text = file.readText()
            forbidden.forEach { value ->
                assertFalse("release-owned source ${file.path} contains $value", text.contains(value))
            }
        }
        assertTrue(runner.contains("QaDualDexApplication::class.java.name"))
        assertTrue(runner.contains("class QaDualDexApplication : DualDexApplication()"))
    }

    @Test
    fun `built manifests and APKs preserve the debug release boundary`() {
        assumeTrue(
            "Set DUALDEX_QA_REQUIRE_ARTIFACTS=1 after assembling both variants to enforce artifact isolation",
            System.getenv("DUALDEX_QA_REQUIRE_ARTIFACTS") == "1",
        )

        val debugManifest = mergedManifest("debug")
        val releaseManifest = mergedManifest("release")
        assertEquals("com.darkaxt.dualdex.RetroArchFreeUiQaApplication", applicationName(debugManifest))
        assertEquals("com.darkaxt.dualdex.DualDexApplication", applicationName(releaseManifest))

        val debugApk = singleApk("debug")
        val releaseApk = singleApk("release")
        assertApkContains(debugApk, "RetroArchFreeUiQaApplication")
        assertApkContains(debugApk, "retroarch-free-ui-qa")
        assertApkHasNoRomAssets(debugApk)
        assertReleaseApkIsolated(releaseApk)
    }

    private fun mergedManifest(variant: String): String {
        val root = File("build/intermediates/merged_manifest/$variant")
        val candidates = if (!root.isDirectory) emptyList() else {
            root.walkTopDown().filter { it.isFile && it.name == "AndroidManifest.xml" }.toList()
        }
        assertEquals("expected one $variant merged main manifest, found ${candidates.map { it.path }}", 1, candidates.size)
        return candidates.single().readText()
    }

    private fun applicationName(manifest: String): String {
        val applicationTag = Regex("<application\\b[^>]*>", setOf(RegexOption.DOT_MATCHES_ALL)).find(manifest)?.value
            ?: error("merged manifest has no application element")
        return Regex("android:name=\"([^\"]+)\"").find(applicationTag)?.groupValues?.get(1)
            ?: error("merged application has no android:name")
    }

    private fun singleApk(variant: String): File {
        val apks = File("build/outputs/apk/$variant").listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
        assertEquals("expected one $variant APK, found ${apks.map { it.path }}", 1, apks.size)
        return apks.single()
    }

    private fun assertReleaseApkIsolated(apk: File) {
        assertApkHasNoRomAssets(apk)
        val forbidden = listOf(
            "RetroArchFreeUiQaApplication",
            "retroarch-free-ui-qa",
            "DualDex RetroArch-Free UI QA",
        )
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                forbidden.forEach { value ->
                    assertFalse("release APK entry name ${entry.name} contains $value", entry.name.contains(value))
                }
                if (!entry.isDirectory) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    forbidden.forEach { value ->
                        assertFalse("release APK entry ${entry.name} contains $value", bytes.containsUtf8(value))
                    }
                }
            }
        }
    }

    private fun assertApkHasNoRomAssets(apk: File) {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                assertFalse("${apk.name} contains a ROM-like asset ${entry.name}", ROM_ASSET.matches(entry.name))
            }
        }
    }

    private fun assertApkContains(apk: File, value: String) {
        ZipFile(apk).use { zip ->
            val found = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .any { entry -> zip.getInputStream(entry).use { it.readBytes().containsUtf8(value) } }
            assertTrue("${apk.name} does not contain $value", found)
        }
    }

    private fun ByteArray.containsUtf8(value: String): Boolean {
        val expected = value.toByteArray(StandardCharsets.UTF_8)
        if (expected.isEmpty() || size < expected.size) return false
        for (offset in 0..size - expected.size) {
            var matches = true
            for (index in expected.indices) {
                if (this[offset + index] != expected[index]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    private companion object {
        val ROM_ASSET = Regex("(?i)^assets/.+\\.(gb|gbc|gba|rom|zip)$")
    }
}
