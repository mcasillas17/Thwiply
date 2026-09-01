<div align="center">
  <img src="artwork/play-store/ic_launcher-playstore-512.png" width="120" height="120" alt="Thwiply Icon" />
  <h1>Thwiply 🕸️</h1>
  <p><strong>Actionable task extraction out of everyday noise — 100% on-device.</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Package-thwiply.elopenmike.com-00A3FF?style=flat-square" alt="Package" />
    <img src="https://img.shields.io/badge/Platform-Android_12%2B_(API_31%2B)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform" />
    <img src="https://img.shields.io/badge/Runtime-LiteRT--LM-00687A?style=flat-square" alt="Runtime" />
    <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License" />
  </p>
</div>

---

**Thwiply** is an early Android alpha exploring private, on-device notification intelligence. The current build provides a local LLM Lab and a durable manual task interface; it does not yet read or modify Android notifications or screenshots.

---

## 🛡️ Core Principle: Privacy First
LLM inference runs on the Android device. Internet access is used solely to download a pinned model from Hugging Face, and the downloaded file is verified before activation. The alpha has no account, backend, analytics, notification listener, or screenshot observer. Its local Room schema stores approved display fields, decisions, corrections, rules, and minimal source provenance; it has no column for a raw notification body, text, payload, extras, or prompt.

---

## ✨ Features

- **Verified Model Installation:** Resumable download, exact-size validation, SHA-256 verification, and atomic activation.
- **On-Device LLM Lab:** Runs Qwen 2.5 1.5B through LiteRT-LM for local prompt experiments, structured extraction testing, and live performance metrics.
- **Durable Today Tasks:** Manual tasks, completion-state updates, and deletions survive app and database recreation through the repository layer.
- **Privacy-Minimized Data Foundation:** Versioned Room schemas, explicit migrations, 30-day retention for future notification-derived records, a confirmed delete-all control, and explicit database exclusions from cloud backup and device transfer.
- **Real Empty and Failure States:** Today reflects repository-backed `Flow` state instead of hardcoded sample tasks and distinguishes an empty database from a storage failure.
- **Settings & Theme Manager:** Live support for **System Default**, **Dark Mode** (Deep Electric Sapphire & Obsidian Slate), and **Light Mode** (Crisp Porcelain & Electric Cyan).
- **Official Adaptive Branding:** Custom spider-web spinneret icon design with Android 13+ monochrome dynamic theming support.

---

## 🛠️ Tech Stack
- **Language:** Kotlin (Modern Idiomatic)
- **UI Framework:** Jetpack Compose with Material 3
- **Package / Namespace:** `thwiply.elopenmike.com`
- **LLM Runtime:** [LiteRT-LM](https://ai.google.dev/edge/litert) (Google AI Edge on-device acceleration)
- **Model:** Qwen 2.5 1.5B Instruct (pinned LiteRT-LM build)
- **Dependency Injection:** Hilt
- **Local Data:** Room with exported schemas and tested manual migrations
- **Async & Reactive Architecture:** Kotlin Coroutines + Flow / StateFlow
- **Networking:** OkHttp (resumable downloads with byte progress streaming)

---

## 🚀 Getting Started

### Prerequisites
- Android device or emulator with **Min SDK 31 (Android 12+)**.
- About 1.6 GB of free storage for the pinned model, plus installation headroom.
- Pixel 6 or newer (or equivalent ARM64 / x86_64 device) recommended for hardware-accelerated LLM execution.

### Install an alpha release

Each GitHub alpha release provides separate installable APKs:

- `arm64-v8a` for physical Android devices and ARM64 emulators;
- `x86_64-emulator` for x86_64 emulators; and
- `SHA256SUMS` for download verification.

Download only the APK matching the device architecture, then verify it from the
same directory:

```bash
# Replace arm64-v8a with x86_64-emulator when verifying the emulator APK.
# Linux
grep 'arm64-v8a\.apk$' SHA256SUMS | sha256sum -c -

# macOS
grep 'arm64-v8a\.apk$' SHA256SUMS | shasum -a 256 -c -
```

Releases after `v1.0.0-alpha.3` use a persistent alpha signing identity and a
monotonically increasing Android version code. If `v1.0.0-alpha.3` or an earlier
debug-signed build is installed, uninstall it once before installing the first
persistently signed alpha. That uninstall removes local app data and downloaded
models. Later persistently signed alphas can be installed as updates.

The arm64 alpha is minified, contains only arm64 native libraries, and is
enforced below 32 MiB. The pinned model is not bundled in the APK; it is
downloaded and verified after installation.

### Build from source

1. Clone the repository:
   ```bash
   git clone https://github.com/mcasillas17/Thwiply.git
   ```
2. Open the project in **Android Studio (Ladybug 2024.2+ or newer)**.
3. Build and install the debug APK:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

Maintainers can find signing, artifact, versioning, and tagged-release
instructions in [`docs/RELEASING.md`](docs/RELEASING.md).

### First Launch
On first launch, Thwiply downloads the pinned Qwen 2.5 1.5B model. Interrupted downloads can resume, and the app checks the exact size and SHA-256 digest before activating the model. Once complete, use the **Lab** tab to test local inference.

---

## 🗺️ Roadmap

The dependency-ordered product roadmap, launch gates, privacy requirements, and non-goals live in [`docs/ROADMAP.md`](docs/ROADMAP.md).

### Secure alpha and durable data foundation (Current)
- [x] Project architecture (Hilt, Compose, Navigation)
- [x] Official adaptive vector branding and Android 13+ themed icon support
- [x] Verified, resumable model installation with SHA-256 validation
- [x] LiteRT-LM integration and streaming inference
- [x] Local inference Lab with real-time metrics
- [x] Truthful feature and privacy status
- [x] Compose-independent triage domain and repository contracts
- [x] Durable Room storage, migrations, retention, and privacy erasure
- [x] Repository-backed Today tasks and real empty/error states
- [x] Minified, signed, per-ABI alpha distribution with checksum and size gates

### Notification Triage MVP (Planned)
- [ ] Notification Listener Service
- [ ] Per-app consent and allowlist
- [x] Durable privacy-minimized local persistence foundation
- [ ] Strict structured triage decisions
- [ ] Explanation and one-tap correction loop
- [ ] Real Today / Needs Review surfaces

---

## 📄 License
This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

---

*The name Thwiply is inspired by the sound of Spider-Man's web-shooters — catching the important things before they fall through the cracks.*
