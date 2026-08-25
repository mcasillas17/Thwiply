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

**Thwiply** is an Android application that "thwips" useful items out of the noise. It catches notifications and screenshots, uses an on-device LLM to extract actionable intent, and surfaces it as a smart, distraction-free task feed.

---

## 🛡️ Core Principle: Privacy First
**Everything runs locally.** No backend, no cloud inference, no mandatory accounts, and zero telemetry. After a one-time on-device model download, the app operates completely offline. Your notifications and screenshots never leave your device's silicon.

---

## ✨ Features

- **📱 Today Feed:** High-signal task surface with origin badges (WhatsApp, Slack, Gmail, Screenshots), priority flags, completion toggles, and expandable raw AI intent quotes.
- **⚡ AI Playground:** Interactive sandbox for testing on-device prompt inference, structured JSON extraction schemas, and real-time generation metrics (tokens/sec, latency).
- **⚙️ Settings & Theme Manager:** Full support for **System Default**, **Dark Mode** (Deep Electric Sapphire & Obsidian Slate), and **Light Mode** (Crisp Porcelain & Electric Cyan), alongside model cache management and background capture toggles.
- **🧠 Flexible On-Device Engine:**
  - **Qwen 2.5 1.5B Instruct (Default):** 100% ungated direct download with zero accounts or tokens required (~900 MB).
  - **Gemma 3 1B IT (Google AI Edge):** Ultra-compact quantized int4 model with integrated Hugging Face license helper (~550 MB).
  - **Custom Model URL:** Power-user option for any compatible `.litertlm` model weights.
- **🎨 Official Adaptive Branding:** Custom spider-web spinneret icon design with Android 13+ monochrome dynamic theming support.

---

## 🛠️ Tech Stack
- **Language:** Kotlin (Modern Idiomatic)
- **UI Framework:** Jetpack Compose with Material 3
- **Package / Namespace:** `thwiply.elopenmike.com`
- **LLM Runtime:** [LiteRT-LM](https://ai.google.dev/edge/litert) (Google AI Edge on-device acceleration)
- **Dependency Injection:** Hilt
- **Async & Reactive Architecture:** Kotlin Coroutines + StateFlow
- **Networking:** OkHttp (for resumable model downloads with byte progress streaming)

---

## 🚀 Getting Started

### Prerequisites
- Android device or emulator with **Min SDK 31 (Android 12+)**.
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
On first launch, Thwiply provides a 1-click model setup:
- Select **Qwen 2.5 1.5B** for immediate, token-free download.
- Or choose **Gemma 3 1B** with direct links to accept Google terms and paste your Hugging Face read token.
- Once downloaded and verified, the app automatically transitions to the **Today** task feed.

---

## 🗺️ Roadmap

### v1: Scaffolding & Core Experience (Current)
- [x] Modern Material 3 UI with Electric Sapphire / Porcelain themes & live `ThemeManager`
- [x] Official adaptive vector branding & Android 13+ themed icon support
- [x] Model management, SHA-256 verification, and resumable downloading
- [x] LiteRT-LM integration with real-time streaming inference
- [x] Full App Shell with Bottom Navigation (`Today`, `AI Playground`, `Settings`)
- [x] Interactive task feed with origin badges and expandable AI inference quotes

### v2: Background Capture & Persistence (Coming Soon)
- [ ] Notification Listener Service
- [ ] Screenshot media observation
- [ ] OCR pre-filtering
- [ ] Room database persistence

---

## 📄 License
This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

---

*The name Thwiply is inspired by the sound of Spider-Man's web-shooters — catching the important things before they fall through the cracks.*
