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

**Thwiply** is an early Android alpha exploring private, on-device notification intelligence. The current build provides an on-device local LLM Lab and a prototype task interface; it does not yet read or modify Android notifications or screenshots.

---

## 🛡️ Core Principle: Privacy First
LLM inference runs strictly on the Android device. Internet access is used solely to download a pinned model from Hugging Face, and the downloaded file is verified with SHA-256 before activation. The alpha has no mandatory accounts, cloud backends, analytics, or background trackers.

---

## ✨ Features

- **Verified Model Installation:** Resumable download, exact-size validation, SHA-256 digest verification, and atomic activation.
- **On-Device LLM Lab / Playground:** Runs Qwen 2.5 1.5B through LiteRT-LM for local prompt experiments and structured JSON extraction testing with live performance metrics.
- **Prototype Task Interface:** High-signal task surface with origin badges (WhatsApp, Slack, Gmail, Screenshots), priority flags, completion toggles, and expandable raw AI intent quotes.
- **Settings & Theme Manager:** Full live support for **System Default**, **Dark Mode** (Deep Electric Sapphire & Obsidian Slate), and **Light Mode** (Crisp Porcelain & Electric Cyan).
- **Official Adaptive Branding:** Custom spider-web spinneret icon design with Android 13+ monochrome dynamic theming support.

---

## 🛠️ Tech Stack
- **Language:** Kotlin (Modern Idiomatic)
- **UI Framework:** Jetpack Compose with Material 3
- **Package / Namespace:** `thwiply.elopenmike.com`
- **LLM Runtime:** [LiteRT-LM](https://ai.google.dev/edge/litert) (Google AI Edge on-device acceleration)
- **Model:** Qwen 2.5 1.5B Instruct (pinned LiteRT-LM build)
- **Dependency Injection:** Hilt
- **Async & Reactive Architecture:** Kotlin Coroutines + StateFlow
- **Networking:** OkHttp (resumable downloads with byte progress streaming)

---

## 🚀 Getting Started

### Prerequisites
- Android device or emulator with **Min SDK 31 (Android 12+)**.
- About 1.6 GB of free storage for the pinned model, plus installation headroom.
- Pixel 6 or newer (or equivalent ARM64 / x86_64 device) recommended for hardware-accelerated LLM execution.

### Installation
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

### First Launch
On first launch, Thwiply downloads the pinned Qwen 2.5 1.5B model. Interrupted downloads can resume, and the app checks the exact size and SHA-256 digest before activating the model. Once complete, use the **Playground** tab to test local inference.

---

## 🗺️ Roadmap

The dependency-ordered product roadmap, launch gates, privacy requirements, and non-goals live in [`docs/ROADMAP.md`](docs/ROADMAP.md).

### Secure Alpha (Current)
- [x] Project architecture (Hilt, Compose, Navigation)
- [x] Official adaptive vector branding & Android 13+ themed icon support
- [x] Verified, resumable model installation with SHA-256 validation
- [x] LiteRT-LM integration and streaming inference
- [x] Local inference Playground with real-time metrics
- [x] High-signal prototype task interface with Electric Sapphire / Porcelain themes
- [x] Truthful feature and privacy status

### Notification Triage MVP (Planned)
- [ ] Notification Listener Service
- [ ] Per-app consent and allowlist
- [ ] Durable encrypted local persistence
- [ ] Strict structured triage decisions
- [ ] Explanation and one-tap correction loop
- [ ] Real Today / Needs Review surfaces

---

## 📄 License
This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

---

*The name Thwiply is inspired by the sound of Spider-Man's web-shooters — catching the important things before they fall through the cracks.*
