plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":save-core"))
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()
}
