# Thwiply 🕸️

**Thwiply** is an Android application that "thwips" useful items out of the noise. It quietly catches notifications and screenshots, uses an on-device LLM to extract actionable intent, and surfaces it as a smart task list.

## 🛡️ Core Principle: Privacy First
**Everything runs locally.** No backend, no cloud inference, no account, and no telemetry. After a one-time model download, the app never phones home. Your notifications and screenshots stay on your silicon.

## ✨ Features
- **Passive Capture (v2):** Observes notifications and screenshots in the background.
- **On-Device LLM:** Uses **Gemma 3 1B** via LiteRT-LM (Google AI Edge) to decide what's actionable.
- **Smart Task List:** A simple, high-signal surface for extracted tasks.
- **Automatic Cleanup:** Optionally deletes processed screenshots to save space.

## 🛠️ Tech Stack
- **Language:** Kotlin (Modern Idiomatic)
- **UI:** Jetpack Compose with Material 3
- **LLM Runtime:** [LiteRT-LM](https://ai.google.dev/edge/litert) (formerly MediaPipe LLM Inference)
- **Model:** Gemma 3 1B IT (Quantized int4)
- **DI:** Hilt
- **Async:** Coroutines + Flow
- **Persistence:** Room (v2)
- **Networking:** OkHttp (for resumable model download)

## 🚀 Getting Started

### Prerequisites
- Android device or emulator with **Min SDK 31 (Android 12)**.
- Pixel 6 or newer (or equivalent) is recommended for smooth LLM inference.
- **Hugging Face Token:** The Gemma 3 model is gated. You'll need a Hugging Face account and a Read token.

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/mcasillas17/Thwiply.git
   ```
2. Open the project in **Android Studio (Ladybug or newer)**.
3. Build and run the app.

### First Launch (The Onboarding)
On the first launch, Thwiply will guide you through a one-time download of the AI model (~550 MB). 
- Provide a **Model URL** (default points to Hugging Face).
- Provide your **Hugging Face Read Token** if the model is gated.
- Once the download completes and integrity is verified, you can access the **LLM Debug Inference** screen to test the model.

## 🗺️ Roadmap

### v1: Scaffolding (Current)
- [x] Project architecture (Hilt, Compose, Navigation)
- [x] Model management and resumable download
- [x] LiteRT-LM integration and streaming inference
- [x] Debug inference UI

### v2: Capture & Process (Coming Soon)
- [ ] Notification Listener Service
- [ ] Screenshot media observation
- [ ] OCR pre-filtering
- [ ] Task extraction logic
- [ ] Room database for persistence
- [ ] "Today" screen UI

## 📄 License
This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

---
*The name Thwiply is inspired by the sound of Spider-Man's web-shooters — catching the important things before they fall through the cracks.*
