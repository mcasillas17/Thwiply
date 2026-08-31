# Bouncy Castle Build Classpath Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve the Android build-tool classpath's vulnerable Bouncy Castle 1.79 modules to the minimum patched version, 1.80.2, without adding Bouncy Castle to the app runtime.

**Architecture:** The root build declares a narrow security policy for the plugin classpath and a verification task that resolves that same classpath. Strict constraints are attempted first; they are retained only when the resolved dependency graph proves they affect the plugins DSL classpath.

**Tech Stack:** Gradle 9.4.1 Kotlin DSL, Android Gradle Plugin 9.2.1, Java 21

---

## File Map

- Modify `build.gradle.kts`: define the three guarded modules, add the buildscript classpath assertion, and add the minimal effective version alignment.
- Verify `app/build.gradle.kts` without modifying it: confirm `debugRuntimeClasspath` remains free of Bouncy Castle.
- Create `docs/superpowers/specs/2026-08-31-bouncycastle-build-classpath-remediation-design.md`: committed design and scope record.
- Create `docs/superpowers/plans/2026-08-31-bouncycastle-build-classpath-remediation.md`: executable test-first plan.

### Task 1: Add the dependency-resolution assertion

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Define the expected modules and verification task without changing resolution**

Add this below the existing `plugins` block:

```kotlin
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
```

- [ ] **Step 2: Run the assertion to prove the vulnerable baseline**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew verifyBuildscriptBouncyCastle --stacktrace --no-daemon
```

Expected: `FAILED` with all three module names mapped to `1.79`.

### Task 2: Align the buildscript classpath

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add strict buildscript classpath constraints**

Add this above the `plugins` block:

```kotlin
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
```

- [ ] **Step 2: Run the targeted assertion**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew verifyBuildscriptBouncyCastle --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: If constraints are ineffective, replace them with a targeted classpath rule**

Do not keep both mechanisms. Remove the ineffective constraint block and add:

```kotlin
buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (
                requested.group == "org.bouncycastle" &&
                requested.name in setOf(
                    "bcprov-jdk18on",
                    "bcpkix-jdk18on",
                    "bcutil-jdk18on",
                )
            ) {
                useVersion("1.80.2")
                because("Bouncy Castle 1.79 is vulnerable to CVE-2025-14813")
            }
        }
    }
}
```

Re-run the targeted assertion. Expected: `BUILD SUCCESSFUL`. If this also fails, stop and investigate a settings-level plugin classpath mechanism rather than claiming remediation.

- [ ] **Step 4: Prove the resolved build environment**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew buildEnvironment --no-daemon
```

Expected: every occurrence of `bcprov-jdk18on`, `bcpkix-jdk18on`, and `bcutil-jdk18on` resolves to `1.80.2`; no occurrence resolves to `1.79`.

- [ ] **Step 5: Commit the implementation**

```bash
git add build.gradle.kts
git commit -m "Fix vulnerable Bouncy Castle build dependencies" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>" \
  -m "Copilot-Session: f886cd42-d3af-4244-92ca-f284082ce74a"
```

### Task 3: Verify scope and regression safety

**Files:**
- Verify: `build.gradle.kts`
- Verify: `app/build.gradle.kts`

- [ ] **Step 1: Confirm Bouncy Castle remains absent from the app runtime**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:dependencies --configuration debugRuntimeClasspath --no-daemon
```

Expected: `BUILD SUCCESSFUL` and no `org.bouncycastle` output.

- [ ] **Step 2: Run the complete project validation**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Review the final change set**

Run:

```bash
git diff --check main...HEAD
git diff --stat main...HEAD
git diff main...HEAD -- build.gradle.kts docs/superpowers/specs docs/superpowers/plans
```

Expected: only the root build remediation, its assertion, and the design and plan documents differ from `main`.

### Task 4: Publish the separate remediation PR

**Files:**
- No file changes

- [ ] **Step 1: Push the remediation branch**

```bash
git push -u origin mcasillas17-fix-critical-bouncycastle
```

- [ ] **Step 2: Open a non-draft PR against `main`**

Create a PR that names Dependabot alert #35, explains that this patches only the build-tool classpath to 1.80.2, includes exact `buildEnvironment` and runtime-classpath evidence, and states that the alert should be rechecked after merge and refreshed dependency submission.

- [ ] **Step 3: Wait for required PR checks**

Run:

```bash
gh pr checks --watch
```

Expected: all checks pass. Do not merge the PR or dismiss the Dependabot alert.
