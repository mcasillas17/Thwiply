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
