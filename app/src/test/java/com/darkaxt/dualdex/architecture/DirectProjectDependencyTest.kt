package com.darkaxt.dualdex.architecture

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectProjectDependencyTest {
    @Test
    fun `every imported included-project declaration owner is a direct app dependency`() {
        val root = repositoryRoot()
        val appDeclarations = kotlinDeclarations(root.resolve("app/src/main"))
        val directDependencies = activeProjectDependencies(read(root.resolve("app/build.gradle.kts")))
        val owners = includedProjectDeclarationOwners(root, appDeclarations)
        val references = kotlinReferences(root.resolve("app/src/main"), owners)
        val violations = directDependencyViolations(references, directDependencies, owners)

        assertTrue("Expected every direct app project dependency to own an app reference", directDependencies.all { project ->
            references.any { reference -> project in owners[reference].orEmpty() }
        })
        assertTrue("App imports included-project declarations without direct dependencies: $violations", violations.isEmpty())
    }

    @Test
    fun `removing each active direct app project dependency is rejected`() {
        val root = repositoryRoot()
        val appDeclarations = kotlinDeclarations(root.resolve("app/src/main"))
        val directDependencies = activeProjectDependencies(read(root.resolve("app/build.gradle.kts")))
        val owners = includedProjectDeclarationOwners(root, appDeclarations)
        val references = kotlinReferences(root.resolve("app/src/main"), owners)

        directDependencies.forEach { removed ->
            val violations = directDependencyViolations(references, directDependencies - removed, owners)
            assertTrue("Removing $removed must reject its owned app imports", violations.any { removed in it.projects })
        }
    }

    @Test
    fun `shared package declarations use exact owner instead of package prefix`() {
        val owners = mapOf("com.example.shared.ModuleType" to setOf(":module"))
        val appDeclarations = setOf("com.example.shared.AppType")
        val violations = directDependencyViolations(
            imports = setOf("com.example.shared.AppType", "com.example.shared.ModuleType"),
            directDependencies = emptySet(),
            owners = owners.filterKeys { it !in appDeclarations },
        )

        assertEquals(listOf(DependencyViolation("com.example.shared.ModuleType", setOf(":module"))), violations)
    }

    @Test
    fun `commented project dependencies do not satisfy active dependency declarations`() {
        val dependencies = activeProjectDependencies(
            """
            implementation(project(":catalog-store"))
            // implementation(project(":parser-core"))
            /* implementation(project(":save-core")) */
            """.trimIndent(),
        )

        assertEquals(setOf(":catalog-store"), dependencies)
    }

    @Test
    fun `catalog functional interface declarations retain catalog store ownership`() {
        val root = repositoryRoot()
        val owners = includedProjectDeclarationOwners(
            root = root,
            appDeclarations = kotlinDeclarations(root.resolve("app/src/main")),
        )
        val imports = setOf(
            "com.darkaxt.dualdex.catalog.CatalogDatabaseFactory",
            "com.darkaxt.dualdex.catalog.CatalogRows",
        )

        assertEquals(
            listOf(
                DependencyViolation("com.darkaxt.dualdex.catalog.CatalogDatabaseFactory", setOf(":catalog-store")),
                DependencyViolation("com.darkaxt.dualdex.catalog.CatalogRows", setOf(":catalog-store")),
            ),
            directDependencyViolations(imports, emptySet(), owners),
        )
    }

    private fun directDependencyViolations(
        imports: Set<String>,
        directDependencies: Set<String>,
        owners: Map<String, Set<String>>,
    ): List<DependencyViolation> = imports.mapNotNull { imported ->
        owners[imported]
            ?.takeIf { projects -> projects.none { it in directDependencies } }
            ?.let { projects -> DependencyViolation(imported, projects) }
    }.sortedBy(DependencyViolation::declaration)

    private fun includedProjectDeclarationOwners(
        root: Path,
        appDeclarations: Set<String>,
    ): Map<String, Set<String>> = includedProjects(read(root.resolve("settings.gradle.kts")))
        .filterNot { it == ":app" }
        .flatMap { project ->
            kotlinDeclarations(root.resolve(project.removePrefix(":"))).map { declaration -> declaration to project }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, projects) -> projects.toSet() }
        .filterKeys { declaration -> declaration !in appDeclarations }

    private fun includedProjects(settings: String): Set<String> =
        Regex("\\\":([A-Za-z0-9-]+)\\\"").findAll(settings)
            .map { match -> ":${match.groupValues[1]}" }
            .toSet()

    private fun activeProjectDependencies(build: String): Set<String> = activeGradleSource(build)
        .lineSequence()
        .mapNotNull { line -> ACTIVE_PROJECT_DEPENDENCY.matchEntire(line)?.groupValues?.get(1) }
        .toSet()

    private fun activeGradleSource(source: String): String {
        val output = StringBuilder(source.length)
        var index = 0
        var blockComment = false
        var quote: Char? = null
        while (index < source.length) {
            val current = source[index]
            val next = source.getOrNull(index + 1)
            when {
                blockComment && current == '*' && next == '/' -> {
                    blockComment = false
                    index += 2
                }
                blockComment -> {
                    if (current == '\n') output.append('\n')
                    index++
                }
                quote != null -> {
                    output.append(current)
                    if (current == quote && source.getOrNull(index - 1) != '\\') quote = null
                    index++
                }
                current == '/' && next == '/' -> {
                    while (index < source.length && source[index] != '\n') index++
                }
                current == '/' && next == '*' -> {
                    blockComment = true
                    index += 2
                }
                current == '\'' || current == '"' -> {
                    quote = current
                    output.append(current)
                    index++
                }
                else -> {
                    output.append(current)
                    index++
                }
            }
        }
        return output.toString()
    }

    private fun kotlinReferences(sourceRoot: Path, owners: Map<String, Set<String>>): Set<String> = buildSet {
        addAll(kotlinImports(sourceRoot))
        kotlinSources(sourceRoot).forEach { source ->
            val text = activeGradleSource(read(source))
            val packageName = PACKAGE.find(text)?.groupValues?.get(1) ?: return@forEach
            owners.keys
                .asSequence()
                .filter { declaration -> declaration.substringBeforeLast('.') == packageName }
                .filter { declaration -> Regex("\\b${Regex.escape(declaration.substringAfterLast('.'))}\\b").containsMatchIn(text) }
                .forEach(::add)
        }
    }

    private fun kotlinImports(sourceRoot: Path): Set<String> = kotlinSources(sourceRoot)
        .flatMap { source -> IMPORT.findAll(read(source)).map { it.groupValues[1] }.toList() }
        .filterNot { it.endsWith(".*") }
        .toSet()

    private fun kotlinDeclarations(sourceRoot: Path): Set<String> = kotlinSources(sourceRoot)
        .flatMap { source -> declarations(read(source)).toList() }
        .toSet()

    private fun declarations(source: String): Set<String> {
        val packageName = PACKAGE.find(source)?.groupValues?.get(1) ?: return emptySet()
        val declarations = linkedSetOf<String>()
        var depth = 0
        source.lineSequence().forEach { line ->
            val code = line.substringBefore("//")
            if (depth == 0) {
                DECLARATION.find(code.trimStart())
                    ?.groupValues
                    ?.drop(1)
                    ?.firstOrNull(String::isNotEmpty)
                    ?.let { name ->
                        declarations += "$packageName.$name"
                    }
            }
            depth += code.count { it == '{' } - code.count { it == '}' }
        }
        return declarations
    }

    private fun kotlinSources(sourceRoot: Path): List<Path> = Files.walk(sourceRoot).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }.toList()
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)

    private fun repositoryRoot(): Path {
        var candidate: Path? = Path.of("").toAbsolutePath().normalize()
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) return candidate
            candidate = candidate.parent
        }
        error("Could not locate the repository root from ${Path.of("").toAbsolutePath()}")
    }

    private data class DependencyViolation(val declaration: String, val projects: Set<String>)

    private companion object {
        val ACTIVE_PROJECT_DEPENDENCY = Regex(
            """\s*implementation\s*\(\s*project\s*\(\s*"(:[A-Za-z0-9-]+)"\s*\)\s*\)\s*""",
        )
        val IMPORT = Regex("(?m)^import\\s+([A-Za-z_][A-Za-z0-9_.]*)")
        val PACKAGE = Regex("(?m)^package\\s+([A-Za-z_][A-Za-z0-9_.]*)")
        val DECLARATION = Regex(
            """(?:(?:public|internal|private|protected|open|abstract|final|inline|suspend|operator|infix|tailrec|external|expect|actual|const|lateinit|override|data|sealed|enum|annotation|value)\s+)*(?:class|interface|object|typealias|val|var)\s+`?([A-Za-z_][A-Za-z0-9_]*)|fun\s+interface\s+`?([A-Za-z_][A-Za-z0-9_]*)|fun\s+(?:<[^>]+>\s+)?(?:[A-Za-z_][A-Za-z0-9_.<>?]*\.)*`?([A-Za-z_][A-Za-z0-9_]*)""",
        )
    }
}
