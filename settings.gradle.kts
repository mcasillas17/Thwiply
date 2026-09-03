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

val patchedNettyVersion = "4.1.137.Final"
val patchedJdomVersion = "2.0.6.1"

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion(patchedNettyVersion)
                because("Netty 4.1.136.Final and earlier are vulnerable to CVE-2026-55833 and GHSA-8c42-7qj2-3j46")
            }
            if (requested.group == "org.jdom" && requested.name == "jdom2") {
                useVersion(patchedJdomVersion)
                because("JDOM versions before 2.0.6.1 are vulnerable to CVE-2021-33813")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Thwiply"
include(":app")
