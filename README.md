# Thwiply 🕸️

**Thwiply** is an early Android alpha exploring private, on-device notification intelligence. The current build provides a local LLM Lab and a prototype task interface; it does not yet read or modify Android notifications or screenshots.

## 🛡️ Core Principle: Privacy First
LLM inference runs on the Android device. Internet access is used to download a pinned model from Hugging Face, and the downloaded file is verified before activation. The alpha has no account, backend, analytics, notification listener, or screenshot observer.

## ✨ Features
- **Verified Model Installation:** Resumable download, exact-size validation, SHA-256 verification, and atomic activation.
- **On-Device LLM Lab:** Runs Qwen 2.5 1.5B through LiteRT-LM for local prompt experiments.
- **Prototype Task Interface:** Demonstrates the intended high-signal experience using sample data.

## 🛠️ Tech Stack
- **Language:** Kotlin (Modern Idiomatic)
- **UI:** Jetpack Compose with Material 3
- **LLM Runtime:** [LiteRT-LM](https://ai.google.dev/edge/litert) (formerly MediaPipe LLM Inference)
- **Model:** Qwen 2.5 1.5B Instruct (pinned LiteRT-LM build)
- **DI:** Hilt
- **Async:** Coroutines + Flow
- **Networking:** OkHttp

## 🚀 Getting Started

### Prerequisites
- Android device or emulator with **Min SDK 31 (Android 12)**.
- About 1.6 GB of free storage for the current pinned model, plus installation headroom.
- Pixel 6 or newer (or equivalent) is recommended for local inference experiments.

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/mcasillas17/Thwiply.git
   ```
2. Open the project in **Android Studio (Ladybug or newer)**.
3. Build and run the app.

### First Launch
On first launch, Thwiply downloads the pinned Qwen 2.5 1.5B model. Interrupted downloads can resume, and the app checks the exact size and SHA-256 digest before activating the model. Once complete, use the **Lab** tab to test local inference.

## 🗺️ Roadmap

### Secure alpha (Current)
- [x] Project architecture (Hilt, Compose, Navigation)
- [x] Verified, resumable model installation
- [x] LiteRT-LM integration and streaming inference
- [x] Local inference Lab
- [x] Truthful feature and privacy status

### Notification triage MVP (Planned)
- [ ] Notification Listener Service
- [ ] Per-app consent and allowlist
- [ ] Durable encrypted local persistence
- [ ] Strict structured triage decisions
- [ ] Explanation and one-tap correction loop
- [ ] Real Today / Needs Review surfaces

---
*The name Thwiply is inspired by the sound of Spider-Man's web-shooters — catching the important things before they fall through the cracks.*
