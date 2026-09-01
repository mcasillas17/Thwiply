# Thwiply 🕸️

**Thwiply** is an early Android alpha exploring private, on-device notification intelligence. The current build provides a local LLM Lab and a durable manual task interface; it does not yet read or modify Android notifications or screenshots.

## 🛡️ Core Principle: Privacy First
LLM inference runs on the Android device. Internet access is used to download a pinned model from Hugging Face, and the downloaded file is verified before activation. The alpha has no account, backend, analytics, notification listener, or screenshot observer. Its local Room schema stores approved display fields, decisions, corrections, rules, and minimal source provenance; it has no column for a raw notification body, text, payload, extras, or prompt.

## ✨ Features
- **Verified Model Installation:** Resumable download, exact-size validation, SHA-256 verification, and atomic activation.
- **On-Device LLM Lab:** Runs Qwen 2.5 1.5B through LiteRT-LM for local prompt experiments.
- **Durable Today Tasks:** Manual tasks, completion-state updates, and deletions survive app and database recreation through the repository layer.
- **Privacy-Minimized Data Foundation:** Versioned Room schemas, explicit migrations, 30-day retention for future notification-derived records, a confirmed delete-all control, and explicit database exclusions from cloud backup and device transfer.
- **Real Empty and Failure States:** Today reflects repository-backed `Flow` state instead of hardcoded sample tasks and distinguishes an empty database from a storage failure.

## 🛠️ Tech Stack
- **Language:** Kotlin (Modern Idiomatic)
- **UI:** Jetpack Compose with Material 3
- **LLM Runtime:** [LiteRT-LM](https://ai.google.dev/edge/litert) (formerly MediaPipe LLM Inference)
- **Model:** Qwen 2.5 1.5B Instruct (pinned LiteRT-LM build)
- **DI:** Hilt
- **Local Data:** Room with exported schemas and tested manual migrations
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

The dependency-ordered product roadmap, launch gates, privacy requirements, and non-goals live in [`docs/ROADMAP.md`](docs/ROADMAP.md).

### Secure alpha and durable data foundation (Current)
- [x] Project architecture (Hilt, Compose, Navigation)
- [x] Verified, resumable model installation
- [x] LiteRT-LM integration and streaming inference
- [x] Local inference Lab
- [x] Truthful feature and privacy status
- [x] Compose-independent triage domain and repository contracts
- [x] Durable Room storage, migrations, retention, and privacy erasure
- [x] Repository-backed Today tasks and real empty/error states

### Notification triage MVP (Planned)
- [ ] Notification Listener Service
- [ ] Per-app consent and allowlist
- [x] Durable privacy-minimized local persistence foundation
- [ ] Strict structured triage decisions
- [ ] Explanation and one-tap correction loop
- [ ] Real Today / Needs Review surfaces

---
*The name Thwiply is inspired by the sound of Spider-Man's web-shooters — catching the important things before they fall through the cracks.*
