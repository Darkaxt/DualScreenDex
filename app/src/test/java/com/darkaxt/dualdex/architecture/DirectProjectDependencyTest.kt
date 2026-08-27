package com.darkaxt.dualdex.architecture

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectProjectDependencyTest {
    @Test
    fun `direct parser and save imports have direct app dependencies`() {
        val root = repositoryRoot()
        val sources = Files.walk(root.resolve("app/src/main")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .map { String(Files.readAllBytes(it), Charsets.UTF_8) }
                .toList()
                .joinToString("\n")
        }
        val build = String(Files.readAllBytes(root.resolve("app/build.gradle.kts")), Charsets.UTF_8)
        val contracts = mapOf(
            ":parser-core" to "import com.enrpau.dualscreendex.parser.",
            ":save-core" to "import com.darkaxt.dualdex.save.gen3.",
        )

        contracts.forEach { (project, importPrefix) ->
            assertTrue("Expected an app import owned by $project", importPrefix in sources)
            assertTrue(
                "App imports $project directly but does not declare it directly",
                "implementation(project(\"$project\"))" in build,
            )
        }
    }

    private fun repositoryRoot(): Path {
        var candidate: Path? = Path.of("").toAbsolutePath().normalize()
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) return candidate
            candidate = candidate.parent
        }
        error("Could not locate the repository root from ${Path.of("").toAbsolutePath()}")
    }
}
