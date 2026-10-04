plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":parser-core"))
    api(project(":save-core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
