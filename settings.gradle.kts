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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        listOf(
            "bcprov-jdk18on",
            "bcpkix-jdk18on",
            "bcutil-jdk18on",
        ).forEach { module ->
            classpath("org.bouncycastle:$module:1.84") {
                version {
                    strictly("1.84")
                }
                because("Bouncy Castle versions before 1.84 are vulnerable to CVE-2026-0636")
            }
        }
    }
}

gradle.settingsEvaluated {
    val guardedModules = setOf(
        "bcprov-jdk18on",
        "bcpkix-jdk18on",
        "bcutil-jdk18on",
    )
    val resolved = buildscript.configurations.getByName("classpath")
        .incoming.resolutionResult.allComponents
        .mapNotNull { it.moduleVersion }
        .filter { it.group == "org.bouncycastle" && it.name in guardedModules }
        .associate { it.name to it.version }
    val unexpectedVersions = guardedModules.associateWith(resolved::get)
        .filterValues { it != "1.84" }
    check(unexpectedVersions.isEmpty()) {
        "Expected settings Bouncy Castle modules at 1.84, but resolved $unexpectedVersions"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Thwiply"
include(":app")
