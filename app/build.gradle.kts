plugins {
    alias(libs.plugins.android.application)
}

val companionWebDirectory = rootProject.layout.projectDirectory.dir("companion-web")
val companionWebDist = companionWebDirectory.dir("dist")
val generatedWebAssets = layout.buildDirectory.dir("generated/dualdexWebAssets")
val dualDexVersionName = providers.gradleProperty("dualdexVersionName").getOrElse("1.0.0")
val dualDexVersionCode = providers.gradleProperty("dualdexVersionCode").orNull?.let { value ->
    value.toIntOrNull()?.takeIf { it > 0 }
        ?: error("dualdexVersionCode must be a positive integer")
} ?: 1

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
    namespace = "com.darkaxt.dualdex"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.darkaxt.dualdex"
        minSdk = 30
        targetSdk = 36
        versionCode = dualDexVersionCode
        versionName = dualDexVersionName

        testInstrumentationRunner = "com.darkaxt.dualdex.QaAndroidJUnitRunner"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testOptions {
        managedDevices {
            localDevices {
                create("qaApi35") {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").assets.srcDir(generatedWebAssets.get().asFile)
}

tasks.named("preBuild").configure { dependsOn(packageCompanionWeb) }

dependencies {
    implementation(project(":catalog-store"))
    implementation(project(":companion-core"))
    implementation(project(":parser-core"))
    implementation(project(":save-core"))
    implementation(project(":retroarch-session"))
    implementation(project(":memory-mapper-lab"))
    implementation(project(":battle-memory"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    testImplementation("org.xerial:sqlite-jdbc:3.53.1.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestUtil("androidx.test.services:test-services:1.5.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("com.google.code.gson:gson:2.10.1")
}
