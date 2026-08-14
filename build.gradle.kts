// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val secureVersion = when (requested.group to requested.name) {
                "org.bouncycastle" to "bcpkix-jdk18on",
                "org.bouncycastle" to "bcprov-jdk18on",
                "org.bouncycastle" to "bcutil-jdk18on" -> "1.84"
                "org.bitbucket.b_c" to "jose4j" -> "0.9.6"
                "org.jdom" to "jdom2" -> "2.0.6.1"
                "org.apache.commons" to "commons-lang3" -> "3.18.0"
                "org.apache.httpcomponents" to "httpclient" -> "4.5.14"
                else -> null
            }
            if (secureVersion != null) {
                useVersion(secureVersion)
                because("Keep the Android build toolchain above published security floors")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    id("org.jetbrains.kotlin.android") version "2.4.20-Beta2" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.20-Beta2" apply false
}

allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val secureVersion = when {
                requested.group == "io.netty" -> "4.1.136.Final"
                requested.group == "org.bouncycastle" && requested.name in setOf(
                    "bcpkix-jdk18on",
                    "bcprov-jdk18on",
                    "bcutil-jdk18on",
                ) -> "1.84"
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> "0.9.6"
                requested.group == "org.jdom" && requested.name == "jdom2" -> "2.0.6.1"
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> "3.18.0"
                requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> "4.5.14"
                else -> null
            }
            if (secureVersion != null) {
                useVersion(secureVersion)
                because("Keep build and test tooling above published security floors")
            }
        }
    }
}

tasks.register("verifySecureBuildDependencies") {
    group = "verification"
    description = "Verifies the resolved build and Android test toolchains against the RC29 security pins."

    doLast {
        val expectedVersions = mapOf(
            "org.jetbrains.kotlin:kotlin-gradle-plugin" to setOf("2.4.20-Beta2"),
            "org.bouncycastle:bcpkix-jdk18on" to setOf("1.84"),
            "org.bouncycastle:bcprov-jdk18on" to setOf("1.84"),
            "org.bitbucket.b_c:jose4j" to setOf("0.9.6"),
            "org.jdom:jdom2" to setOf("2.0.6.1"),
            "org.apache.commons:commons-lang3" to setOf("3.18.0"),
            "org.apache.httpcomponents:httpclient" to setOf("4.5.14"),
            "com.google.protobuf:protobuf-java" to setOf("3.25.5", "4.28.3"),
            "com.google.protobuf:protobuf-kotlin" to setOf("4.28.3"),
        )
        val resolved = mutableSetOf<Pair<String, String>>()
        val configurationsToCheck = buildList {
            addAll(rootProject.buildscript.configurations.filter { it.isCanBeResolved })
            rootProject.allprojects.forEach { project ->
                addAll(project.configurations.filter { it.isCanBeResolved })
            }
        }

        configurationsToCheck.forEach { configuration ->
            configuration.incoming.resolutionResult.allComponents.forEach { component ->
                component.moduleVersion?.let { module ->
                    resolved += "${module.group}:${module.name}" to module.version
                }
            }
        }

        val violations = resolved.mapNotNull { (coordinate, version) ->
            val expected = when {
                coordinate.startsWith("io.netty:") -> setOf("4.1.136.Final")
                else -> expectedVersions[coordinate]
            } ?: return@mapNotNull null
            if (version in expected) null else "$coordinate:$version (expected ${expected.joinToString(" or ")})"
        }.sorted()

        check(violations.isEmpty()) {
            "Unsafe build-tool dependency versions resolved:\n${violations.joinToString("\n")}"
        }
    }
}
