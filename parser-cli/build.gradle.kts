plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
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
