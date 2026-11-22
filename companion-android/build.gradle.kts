plugins {
    alias(libs.plugins.android.library)
}

// The DualDex companion: catalog runtime, RetroArch session, saves, knowledge, loopback server and web UI assets.
// `app` (launcher activity, overlay service, Application) and embedding hosts such as DualPlay depend on it.
val companionWebDirectory = rootProject.layout.projectDirectory.dir("companion-web")
val companionWebDist = companionWebDirectory.dir("dist")
val generatedWebAssets = layout.buildDirectory.dir("generated/dualdexWebAssets")
val buildCompanionWeb by tasks.registering(Exec::class) {
    workingDir(companionWebDirectory)
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm", "run", "build")
    inputs.files(
        companionWebDirectory.file("package.json"),
        companionWebDirectory.file("package-lock.json"),
        companionWebDirectory.file("index.html"),
        companionWebDirectory.file("vite.config.ts"),
        companionWebDirectory.file("tsconfig.json"),
        companionWebDirectory.file("tsconfig.app.json"),
        companionWebDirectory.file("tsconfig.node.json"),
    )
    inputs.dir(companionWebDirectory.dir("src"))
    outputs.dir(companionWebDist)
}

val packageCompanionWeb by tasks.registering(Sync::class) {
    dependsOn(buildCompanionWeb)
    from(companionWebDist)
    into(generatedWebAssets.map { it.dir("dualdex-web") })
}

android {
    namespace = "com.darkaxt.dualdex.companion.android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(generatedWebAssets.get().asFile.path)
    }
}

tasks.named("preBuild").configure { dependsOn(packageCompanionWeb) }

dependencies {
    api(project(":catalog-store"))
    api(project(":companion-core"))
    api(project(":parser-core"))
    api(project(":save-core"))
    api(project(":retroarch-session"))
    api(project(":memory-mapper-lab"))
    api(project(":battle-memory"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation("androidx.activity:activity-ktx:1.8.2")
    api("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
    testImplementation("org.xerial:sqlite-jdbc:3.53.1.0")
}
