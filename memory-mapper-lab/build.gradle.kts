plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":retroarch-session"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
