// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        constraints {
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

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

val patchedBouncyCastleVersion = "1.80.2"
val guardedBouncyCastleModules = setOf(
    "bcprov-jdk18on",
    "bcpkix-jdk18on",
    "bcutil-jdk18on",
)
val buildscriptClasspath = buildscript.configurations.named("classpath")

tasks.register("verifyBuildscriptBouncyCastle") {
    group = "verification"
    description = "Verifies that build-tool Bouncy Castle modules use the patched version."

    doLast {
        val resolvedVersions = buildscriptClasspath.get()
            .incoming
            .resolutionResult
            .allComponents
            .mapNotNull { it.moduleVersion }
            .filter { it.group == "org.bouncycastle" && it.name in guardedBouncyCastleModules }
            .associate { it.name to it.version }

        val unexpectedVersions = guardedBouncyCastleModules.associateWith(resolvedVersions::get)
            .filterValues { it != patchedBouncyCastleVersion }

        check(unexpectedVersions.isEmpty()) {
            "Expected Bouncy Castle build-tool modules at $patchedBouncyCastleVersion, " +
                "but resolved $unexpectedVersions"
        }
    }
}
