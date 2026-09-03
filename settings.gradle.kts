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

val patchedNettyVersion = "4.1.137.Final"

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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val patchedBouncyCastleVersion = "1.84"
val patchedJdomVersion = "2.0.6.1"

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "io.netty" -> {
                    useVersion(patchedNettyVersion)
                    because("Netty 4.1.136.Final and earlier are vulnerable to CVE-2026-55833 and GHSA-8c42-7qj2-3j46")
                }
                "org.bouncycastle" -> {
                    useVersion(patchedBouncyCastleVersion)
                    because("Bouncy Castle versions before 1.84 are vulnerable to CVE-2026-0636")
                }
                "org.jdom" -> if (requested.name == "jdom2") {
                    useVersion(patchedJdomVersion)
                    because("JDOM versions before 2.0.6.1 are vulnerable to CVE-2021-33813")
                }
            }
        }
    }
    buildscript.configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jdom" && requested.name == "jdom2") {
                useVersion(patchedJdomVersion)
                because("JDOM versions before 2.0.6.1 are vulnerable to CVE-2021-33813")
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
