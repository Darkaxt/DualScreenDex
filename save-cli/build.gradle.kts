plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":parser-core"))
    implementation(project(":save-core"))
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.darkaxt.dualdex.save.cli.MainKt")
}

tasks.test {
    useJUnit()
}
