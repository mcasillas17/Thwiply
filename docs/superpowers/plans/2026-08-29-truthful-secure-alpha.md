# Truthful Secure Alpha Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current prototype into an honest, safer alpha by verifying model downloads, making model state real, fixing shared inference ownership, and removing UI controls and claims for features that do not exist.

**Architecture:** Keep the current single-module Compose/Hilt structure. Model presets become immutable manifests with fixed URLs, byte sizes, and SHA-256 digests; `ModelManager` installs them under `noBackupFilesDir` through resumable temporary files and atomic activation. `LlmEngineManager` serializes engine and conversation access for the process lifetime, while screens observe explicit initialization errors instead of closing the shared engine.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coroutines/Flow, OkHttp, LiteRT-LM, JUnit 4, MockWebServer.

---

### Task 1: Define a verifiable model manifest

**Files:**
- Modify: `app/src/main/java/thwiply/elopenmike/com/llm/model/ModelPreset.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/onboarding/OnboardingScreen.kt`

- [x] **Step 1: Replace presentation-only presets with immutable download metadata**

Add `fileName`, `expectedBytes`, and `sha256` to `ModelPreset`. Keep only the vetted Qwen preset in `PRESETS`; remove gated-token and arbitrary-URL paths from the release UI.

- [x] **Step 2: Simplify onboarding state**

Remove custom URL and token state. `startDownload()` passes the selected `ModelPreset` directly to `ModelManager`.

- [x] **Step 3: Make onboarding copy accurate**

Describe a fixed verified model download and local inference. Do not claim notification or screenshot capture exists.

- [x] **Step 4: Compile the Kotlin sources**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Implement resumable, verified, atomic model installation

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/thwiply/elopenmike/com/llm/model/ModelManager.kt`
- Rewrite test: `app/src/test/java/thwiply/elopenmike/com/llm/model/ModelManagerTest.kt`

- [x] **Step 1: Add MockWebServer for downloader tests**

Add `com.squareup.okhttp3:mockwebserver:4.12.0` as a test dependency.

- [x] **Step 2: Write failing tests**

Cover:

```kotlin
@Test fun `partial download is never reported as active`()
@Test fun `valid download is atomically activated`()
@Test fun `digest mismatch deletes candidate and preserves active model`()
@Test fun `partial download resumes with range request`()
```

Use a temporary model preset whose expected digest is calculated from deterministic test bytes and `MockWebServer` responses.

- [x] **Step 3: Verify the tests fail**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest --tests '*ModelManagerTest' --no-daemon
```

Expected: failures because verified activation and resume behavior are not implemented.

- [x] **Step 4: Implement safe storage and activation**

`ModelManager` must:

- store models under `<noBackupFilesDir>/models`;
- keep `<preset>.part` separate from the active model;
- resume only when the server returns `206`;
- restart from byte zero when a server ignores `Range`;
- require exact byte count and SHA-256;
- atomically move a verified candidate into place;
- atomically persist the active preset ID;
- preserve the previous active model on every failure;
- surface specific failures without treating them as success.

- [x] **Step 5: Run downloader tests**

Run the Task 2 test command. Expected: all `ModelManagerTest` tests pass.

### Task 3: Serialize process-wide inference lifecycle

**Files:**
- Modify: `app/src/main/java/thwiply/elopenmike/com/llm/engine/LlmEngineManager.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/debug/DebugViewModel.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/playground/PlaygroundViewModel.kt`

- [x] **Step 1: Serialize initialization and generation**

Protect the engine with a coroutine `Mutex`. If a different model path is initialized, close the previous engine while holding the mutex.

- [x] **Step 2: Close each conversation**

The installed LiteRT-LM `Conversation` implements `AutoCloseable`; wrap each generated flow in `conversation.use { ... }` so cancellation and errors release native resources.

- [x] **Step 3: Stop ViewModels from owning the singleton**

Remove `engineManager.close()` from `onCleared()`. Capture initialization failures in explicit `error` state and prevent generation while initialization failed.

- [x] **Step 4: Compile**

Run the Task 1 compile command. Expected: `BUILD SUCCESSFUL`.

### Task 4: Make Settings and privacy surfaces truthful

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/main/MainAppScreen.kt`
- Modify: `README.md`

- [x] **Step 1: Disable Android backup**

Set `android:allowBackup="false"` and remove backup-rule attributes. Model files already live in `noBackupFilesDir`.

- [x] **Step 2: Expose real model status**

`SettingsViewModel` exposes `ModelManager.activeModel`. Render the model name and actual size when ready, or “No verified model installed”.

- [x] **Step 3: Remove fake capture controls**

Delete in-memory notification/screenshot toggles and replace the section with a non-interactive “Notification triage is not enabled in this alpha” status card.

- [x] **Step 4: Correct privacy and product copy**

State that inference runs locally and internet access is used only to download the selected model. Remove claims that notification/screenshot capture, resumability, Room, or automatic cleanup already ship.

- [x] **Step 5: Rename Playground to Lab**

Keep the only working product demonstration accessible, but clearly label it as an experimental local-inference lab.

### Task 5: Validate the foundation

**Files:**
- Inspect all files changed by Tasks 1-4.

- [x] **Step 1: Run focused and full checks**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Inspect the diff**

```bash
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors; only the scoped foundation files and this plan are changed.

- [x] **Step 3: Commit**

```bash
git add README.md app docs/superpowers/plans/2026-08-29-truthful-secure-alpha.md gradle/libs.versions.toml
git commit -m "feat: harden the local model alpha"
```
