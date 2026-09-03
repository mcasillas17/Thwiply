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
    configurations.classpath {
        resolutionStrategy {
            // Dependabot attributes buildscript/plugin transitive dependencies to this manifest.
            force("io.netty:netty-handler-proxy:4.1.133.Final")
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

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy {
            force("io.netty:netty-handler-proxy:4.1.133.Final")
        }
    }
    buildscript.configurations.configureEach {
        resolutionStrategy {
            force("io.netty:netty-handler-proxy:4.1.133.Final")
        }
    }
}

rootProject.name = "Thwiply"
include(":app")
