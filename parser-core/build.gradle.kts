plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

// Test-only G2 provenance. Embed actual source inputs, not a caller-supplied source label.
val codecGoldenSourcePaths = listOf(
    "parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/*.kt",
    "parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/io/RomImage.kt",
    "parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/analysis/ParserCancellation.kt",
    "parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/language/LanguageModels.kt",
    "parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt",
    "parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/CodecGolden*.kt",
    "parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/text/*TextCodec*Test.kt",
    "parser-core/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "tools/localization/official_matrix.py", "tools/localization/build_official_matrix_plan.py",
    "docs/reports/localization/stage-04-codec-checkpoint.md",
)
val codecGoldenSources = fileTree(rootDir) { include(codecGoldenSourcePaths) }
val codecGoldenHead = providers.exec {
    commandLine("git", "-C", rootDir.absolutePath, "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }
val prepareCodecGoldenSources by tasks.registering(Copy::class) {
    from(rootDir) { include(codecGoldenSourcePaths) }
    into(layout.buildDirectory.dir("generated/codec-golden-sources"))
    inputs.property("repositoryHead", codecGoldenHead)
    doLast {
        destinationDir.resolve("index.txt").writeText(codecGoldenSources.files
            .map { it.relativeTo(rootDir).invariantSeparatorsPath }.sorted().joinToString("\n"))
        destinationDir.resolve("git-head.txt").writeText(codecGoldenHead.get())
    }
}
tasks.processTestResources {
    from(prepareCodecGoldenSources) { into("codec-golden-sources") }
}
