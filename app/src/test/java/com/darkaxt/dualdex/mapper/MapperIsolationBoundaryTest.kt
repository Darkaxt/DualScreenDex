package com.darkaxt.dualdex.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MapperIsolationBoundaryTest {
    @Test fun productionModulesCannotImportMapperModels() {
        val root = projectRoot()
        val protectedRoots = listOf("parser-core", "catalog-store", "save-core", "companion-core", "retroarch-session")
            .map { File(root, "$it/src/main") } +
            File(root, "app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt")
        val violations = protectedRoots.flatMap { path ->
            if (path.isFile) listOf(path) else path.walkTopDown().filter(File::isFile).toList()
        }.filter { it.readText().contains("com.darkaxt.dualdex.mapper") }

        assertEquals(emptyList<File>(), violations)
    }

    @Test fun mapperTransportContractHasNoWriteOperation() {
        assertEquals(listOf("read"), ReadOnlyMemoryTransport::class.java.declaredMethods.map { it.name }.distinct())
        val source = File(projectRoot(), "memory-mapper-lab/src/main/kotlin").walkTopDown()
            .filter(File::isFile).joinToString("\n") { it.readText() }
        assertFalse(source.contains("WRITE_CORE_MEMORY"))
    }

    private fun projectRoot(): File {
        var candidate = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (!File(candidate, "settings.gradle.kts").isFile) {
            candidate = requireNotNull(candidate.parentFile) { "DualDex project root was not found" }
        }
        return candidate
    }
}
