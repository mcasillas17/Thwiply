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

buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty" && requested.name == "netty-codec-http") {
                useVersion("4.1.133.Final")
                because("Netty HTTP codec versions through 4.1.132.Final are vulnerable to GHSA-v8h7-rr48-vmmv")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val patchedNettyVersion = "4.1.137.Final"

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion(patchedNettyVersion)
                because("Netty 4.1.136.Final and earlier are vulnerable to CVE-2026-55833 and GHSA-8c42-7qj2-3j46")
            }
        }
    }
}
gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (
                requested.group == "org.apache.httpcomponents" &&
                requested.name in setOf("httpclient", "httpmime")
            ) {
                useVersion("4.5.14")
                because("Use patched Apache HttpComponents releases for CVE-2020-13956")
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
