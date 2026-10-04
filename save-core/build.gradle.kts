plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
