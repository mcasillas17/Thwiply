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
val guardedNettyModules = setOf(
    "netty-common",
    "netty-buffer",
    "netty-transport",
    "netty-resolver",
    "netty-codec",
    "netty-codec-http",
    "netty-codec-http2",
    "netty-codec-socks",
    "netty-handler",
    "netty-handler-proxy",
    "netty-transport-native-unix-common",
)

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty" && requested.name in guardedNettyModules) {
                useVersion(patchedNettyVersion)
                because("Netty 4.1.136.Final and earlier are vulnerable to CVE-2026-55833 and GHSA-8c42-7qj2-3j46")
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
