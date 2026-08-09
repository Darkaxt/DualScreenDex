plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":parser-core"))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.53.1.0")
}

tasks.test {
    useJUnit()
}
