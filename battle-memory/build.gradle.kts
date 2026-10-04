plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":parser-core"))
    implementation(project(":save-core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
