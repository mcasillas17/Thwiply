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
            if (requested.group == "io.netty") {
                useVersion("4.1.136.Final")
                because("Keep Netty modules aligned with the patched HTTP/2 codec")
            }
        }
    }

    dependencies {
        constraints {
            classpath("io.netty:netty-codec-http2:4.1.136.Final") {
                version {
                    strictly("4.1.136.Final")
                }
                because("Netty HTTP/2 decompression leak is fixed in GHSA-93wv-jw9v-4972")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
