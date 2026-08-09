pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DualScreenDex"
include(
    ":app",
    ":parser-core",
    ":parser-cli",
    ":companion-core",
    ":catalog-store",
    ":retroarch-session",
    ":companion-simulator",
    ":companion-server",
)
