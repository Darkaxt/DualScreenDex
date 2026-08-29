plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

val dualDexSourceCommit = providers.gradleProperty("dualdexSourceCommit")
    .orElse(providers.environmentVariable("GITHUB_SHA"))
    .orElse(providers.provider {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        check(process.waitFor() == 0 && output.matches(Regex("[0-9a-f]{40}"))) {
            "Unable to derive parser CLI source commit"
        }
        output
    })

tasks.jar {
    manifest.attributes["DualDex-Source-Commit"] = dualDexSourceCommit.get()
}

dependencies {
    implementation(project(":catalog-store"))
    implementation(project(":parser-core"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.enrpau.dualscreendex.parser.cli.MainKt")
}

tasks.test {
    useJUnit()
}

tasks.register<JavaExec>("mapFirst50Matrix") {
    group = "verification"
    description = "Runs the evidence-only first50 world-map compatibility matrix"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.enrpau.dualscreendex.parser.cli.MapFirst50Matrix")
}

tasks.register<JavaExec>("gbGbcLocalMapMatrix") {
    group = "verification"
    description = "Runs deterministic GB/GBC Local-map scene regression evidence"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.enrpau.dualscreendex.parser.cli.GbGbcLocalMapMatrix")
}

tasks.register<JavaExec>("evolutionFirst50Matrix") {
    group = "verification"
    description = "Runs the evidence-only exact first50 evolution completeness matrix"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.enrpau.dualscreendex.parser.cli.EvolutionFirst50Matrix")
}
