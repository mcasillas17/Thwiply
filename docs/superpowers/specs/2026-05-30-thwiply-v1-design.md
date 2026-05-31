# Thwiply v1 Design Spec

## Overview
Thwiply is an Android app that captures notifications and screenshots, processes them locally using an on-device LLM, and surfaces actionable items. 

The scope of **v1** is restricted exclusively to scaffolding the application and proving the end-to-end functionality of the on-device LLM (LiteRT-LM). Capture and processing workflows are explicitly out of scope for v1 and belong to v2.

## Architecture
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Dependency Injection**: Hilt
- **Concurrency**: Kotlin Coroutines + Flow
- **Background Tasks**: WorkManager
- **LLM Runtime**: `com.google.ai.edge.litertlm:litertlm-android:0.12.0` (LiteRT-LM)

## Components

### 1. Model Management (`com.elopenmike.thwiply.llm.model`)
Manages the lifecycle of the `.litertlm` model file.
- **Responsibility**: Detect if the model exists. If not, download it into private app storage (`Context.filesDir`).
- **Download**: Must support pausing, resuming, and integrity verification. Use a robust HTTP client like OkHttp.
- **Crucial Note on Model Source**: The spec defines using Gemma 3 1B IT from `huggingface.co/litert-community/Gemma3-1B-IT`. **Research shows this model is gated by a Hugging Face license and cannot be downloaded anonymously.** 
    - *Resolution for v1*: The app will implement a simple Hugging Face Auth Token input in the debug/onboarding screen OR download a publicly hosted dummy model if the token isn't provided, to ensure the download works end-to-end. (Ideally, the user provides a Hugging Face token in the UI or local config).

### 2. LLM Engine Management (`com.elopenmike.thwiply.llm.engine`)
A singleton wrapper around the LiteRT-LM `Engine`.
- **Initialization**: Deferred until the model is fully downloaded. Initialization (`engine.initialize()`) is a heavy operation and must run on `Dispatchers.IO`.
- **API Surface**: Expose streaming text generation via `Flow<String>` (`conversation.sendMessageAsync()`). Ensure the engine is safely closed on application teardown.

### 3. UI - Onboarding Flow (`com.elopenmike.thwiply.ui.onboarding`)
A Jetpack Compose screen shown on the first launch.
- Communicates privacy value proposition.
- Manages the model download visually (progress bar, ETA, pause/resume/cancel).
- Routes to the Main/Debug screen upon success.

### 4. UI - Debug Inference (`com.elopenmike.thwiply.ui.debug`)
A developer-facing screen to verify the model.
- Simple text input field for prompts.
- Displays streaming token output.
- Logs performance (time to first token, tokens per second).

## Data Flow
1. App Launch -> Check `filesDir` for model.
2. If missing -> Route to Onboarding -> Download Model.
3. If present -> Route to Placeholder Main Screen.
4. Main Screen -> Developer can navigate to Debug Inference.
5. Debug Inference -> User inputs prompt -> Sent to `LlmEngineManager` -> `Flow<String>` emitted -> UI updates reactively.

## Testing & Verification
- **Success Criteria**:
  - The app routes to onboarding on first launch.
  - The model downloads successfully and handles restarts properly (resuming the download).
  - Dev inference screen streams tokens back from the LLM within ~10 seconds.
  - Subsequent launches bypass onboarding.

## Action Items for Plan
1. Setup Android project structure with Hilt, Compose, and LiteRT-LM dependencies.
2. Implement Model Downloader using OkHttp (with resume capability).
3. Implement LlmEngineManager wrapper.
4. Build Compose UIs (Onboarding + Inference Screen).
5. Tie components together in MainActivity using a Navigation Graph.
