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

val patchedJdomVersion = "2.0.6.1"

gradle.beforeProject {
    listOf(configurations, buildscript.configurations).forEach { configurationContainer ->
        configurationContainer.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jdom" && requested.name == "jdom2") {
                    useVersion(patchedJdomVersion)
                    because("JDOM versions before 2.0.6.1 are vulnerable to CVE-2021-33813")
                }
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
