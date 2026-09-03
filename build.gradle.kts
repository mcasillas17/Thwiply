import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        constraints {
            classpath("org.bitbucket.b_c:jose4j:0.9.6") {
                version {
                    strictly("0.9.6")
                }
                because("jose4j before 0.9.6 is vulnerable to CVE-2024-29371")
            }
            classpath("org.apache.commons:commons-lang3:3.18.0") {
                version {
                    strictly("3.18.0")
                }
                because("Apache Commons Lang before 3.18.0 is vulnerable to CVE-2025-48924")
            }
            listOf(
                "bcprov-jdk18on",
                "bcpkix-jdk18on",
                "bcutil-jdk18on",
            ).forEach { module ->
                classpath("org.bouncycastle:$module:1.80.2") {
                    version {
                        strictly("1.80.2")
                    }
                    because("Bouncy Castle 1.79 is vulnerable to CVE-2025-14813")
                }
            }
        }
    }
}

abstract class VerifyBouncyCastleBuildscriptTask : DefaultTask() {
    @get:Input
    abstract val expectedVersion: Property<String>

    @get:Input
    abstract val guardedModules: SetProperty<String>

    @get:Input
    abstract val resolvedVersions: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val resolution = guardedModules.get().associateWith {
            resolvedVersions.get()[it] ?: "<missing>"
        }
        val unexpectedVersions = resolution.filterValues { it != expectedVersion.get() }

        check(unexpectedVersions.isEmpty()) {
            "Expected Bouncy Castle build-tool modules at ${expectedVersion.get()}, " +
                "but resolved $unexpectedVersions"
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

allprojects {
    configurations.configureEach {
        resolutionStrategy.force("org.bitbucket.b_c:jose4j:0.9.6")
    }
}

val patchedNettyVersion = "4.1.137.Final"

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion(patchedNettyVersion)
                because("Align Netty modules with the fix for GHSA-8c42-7qj2-3j46")
            }
        }
    }
}

val patchedBouncyCastleVersion = "1.80.2"
val guardedBouncyCastleModules = setOf(
    "bcprov-jdk18on",
    "bcpkix-jdk18on",
    "bcutil-jdk18on",
)
val resolvedBouncyCastleVersions = buildscript.configurations.named("classpath").map { configuration ->
    configuration.incoming.resolutionResult.allComponents
        .mapNotNull { it.moduleVersion }
        .filter { it.group == "org.bouncycastle" }
        .associate { it.name to it.version }
}

tasks.register<VerifyBouncyCastleBuildscriptTask>("verifyBuildscriptBouncyCastle") {
    group = "verification"
    description = "Verifies that build-tool Bouncy Castle modules use the patched version."
    expectedVersion.set(patchedBouncyCastleVersion)
    guardedModules.set(guardedBouncyCastleModules)
    resolvedVersions.set(resolvedBouncyCastleVersions)
}
