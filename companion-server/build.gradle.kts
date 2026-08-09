plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":companion-core"))
    implementation(project(":companion-simulator"))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.enrpau.dualscreendex.server.MainKt")
}

tasks.test {
    useJUnit()
}
