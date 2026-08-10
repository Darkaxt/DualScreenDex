plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":retroarch-session"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
