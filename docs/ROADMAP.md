# Thwiply Product Roadmap

**Status:** Durable local data foundation complete; consent and bounded ingestion are next
**Last updated:** 2026-08-31

## Product direction

Thwiply is becoming a private, opt-in, on-device importance inbox that helps people decide what deserves attention without sending notification content to a cloud inference service.

The long-term north star is **fewer interruptions without regret**. The first MVP will not promise control over Android's notification shade. It will observe notifications from user-approved apps, classify them locally, and present a calmer in-app view with explanations and easy corrections.

### MVP promise

- Notifications are read only after explicit Android permission and per-app opt-in.
- Local decisions sort items into **Now**, **Later**, or **Needs review**.
- Every decision has provenance and a useful explanation.
- A correction takes one tap and updates an editable local rule.
- Model or processing failures never hide or cancel the source notification.

### MVP non-promise

- No autonomous notification cancellation, snoozing, channel changes, or shade ranking.
- No passive screenshot monitoring or automatic screenshot deletion.
- No cloud inference, cloud sync, analytics, or account system.
- No arbitrary model URLs, model marketplace, or model-generated device actions.

## Current state

| Area | State | Evidence |
|---|---|---|
| Secure model installation | Complete | PR [#7](https://github.com/mcasillas17/Thwiply/pull/7) |
| Local LiteRT-LM Lab | Complete | Process-owned serialized engine and streaming UI |
| Product copy and privacy status | Complete | Alpha UI no longer presents unfinished capture features as active |
| Durable task and decision data | Complete | Room v2, exported schemas, repository-backed Today state, and restart tests |
| Notification ingestion | Not started | No `NotificationListenerService` is registered |
| Structured triage pipeline | Not started | Lab output is not connected to persisted product state |
| Explanations, corrections, and rules | Foundation complete | Durable contracts, tables, DAOs, and repositories exist; correction UX remains Phase 4 |
| Notification-data lifecycle | Foundation complete | 30-day expiry, automatic purge on Today entry, and confirmed delete-all in Settings |

## Dependency-ordered delivery plan

### Phase 0 — Truthful, secure alpha

**Status:** Complete

Delivered:

- immutable model manifest with commit-pinned URL, exact size, and SHA-256;
- resumable temporary downloads and atomic activation;
- model files excluded from Android backup;
- serialized process-wide engine ownership and conversation cleanup;
- real model status, accurate privacy copy, and removal of fake capture controls;
- regression coverage for downloader and engine lifecycle behavior.

### Phase 1 — Durable local data foundation

**Status:** Complete

**Outcome:** Product state survives process death and restarts without retaining unnecessary notification content.

Scope:

1. Define domain contracts for `TriageItem`, `TriageDecision`, `SourceReference`, `UserCorrection`, and `UserRule`.
2. Keep domain models independent from Compose presentation models.
3. Add Room entities, DAOs, repository boundaries, migrations, and Hilt wiring.
4. Replace hardcoded `TodayViewModel` tasks with repository-backed flows and a real empty state.
5. Persist approved triage items, decisions, corrections, and minimal provenance.
6. Do not persist raw notification bodies by default.
7. Add delete-all and retention behavior before ingestion exists.

Delivered:

- Compose-independent, validated contracts for triage items, decisions, sources, corrections, and rules;
- four privacy-reviewed Room tables with foreign keys, indices, transactional writes, exported v1/v2 schemas, and an explicit 1→2 migration;
- repository boundaries with `Flow` reads, typed not-found/database failures, and no broad exception translation;
- repository-backed Today loading, empty, content, and storage-error states plus validated durable manual create, atomic complete/uncomplete, and delete actions;
- a 30-day expiry field only for notification-derived items, retryable expired-record purge on every Today entry, and transactional delete-all for notification-derived records and all rules;
- a confirmed Settings delete-all control that preserves manual tasks and reports success or failure;
- no notification listener, access request, allowlist, ingestion queue, or notification processing from Phase 2.

Evidence:

- `TriageModelsTest`, `RoomRepositoriesTest`, `TodayViewModelTest`, and `NotificationDataSettingsViewModelTest` cover contracts, mapping, failure semantics, input bounds, repository-backed UI state, retryable retention activation, and privacy erasure UX;
- `ThwiplyDatabaseTest` covers on-disk create/update/complete/delete across reopen, atomic rapid completion toggles, transaction rollback, correction/rule persistence, cascade behavior, retention cutoffs, delete-all, and exact privacy-reviewed columns;
- `ThwiplyMigrationTest` seeds every v1 table and proves the v2 migration preserves supported values while backfilling notification retention only;
- `BackupConfigurationTest` verifies `android:allowBackup="false"` plus explicit database exclusions in both cloud-backup and device-transfer rules.

Exit gates:

- create, update, complete, and delete operations survive app restart;
- migration tests preserve supported data;
- no raw notification text appears in durable tables;
- delete-all removes every notification-derived record;
- Android backup and device-transfer behavior remain explicitly disabled or excluded;
- unit and database tests cover repositories and failure paths.

### Phase 2 — Consent and bounded notification ingestion

**Status:** Next

**Outcome:** Thwiply can observe notifications from explicitly approved apps without doing expensive work on Android callback threads.

Scope:

1. Register a `NotificationListenerService` with conservative notification-type filters.
2. Build onboarding around the real Android notification-access state.
3. Start with an empty per-app allowlist; the user selects every source.
4. Normalize bounded title, body, sender, package, channel, timestamp, and notification-key fields.
5. Skip empty or redacted content instead of inventing meaning.
6. Add idempotency, burst deduplication, a bounded processing queue, and backpressure.
7. Handle listener disconnect, permission revocation, reboot, and duplicate delivery explicitly.

Exit gates:

- denial or revocation leaves the Lab and manual features usable;
- only allowlisted packages enter the pipeline;
- listener callbacks enqueue and return without LLM inference;
- duplicate and burst fixtures create one normalized event;
- overflow is observable and never silently reported as success;
- physical-device tests cover representative messaging and email apps.

Reference: [Android `NotificationListenerService`](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

### Phase 3 — Reviewable local triage

**Status:** Blocked by Phase 2

**Outcome:** Bounded notification events become safe, structured decisions that never mutate source notifications.

Pipeline:

```text
Allowlisted notification
  -> deterministic filters and user rules
  -> deduplication
  -> one serialized local-LLM decision when needed
  -> strict schema validation
  -> Now / Later / Needs review
  -> minimal durable record
```

Scope:

1. Apply user-authored allow, ignore, and priority rules before model inference.
2. Use deterministic handling for known non-candidates such as ongoing media and empty content.
3. Bound every untrusted input field before prompt construction.
4. Treat notification text as data using model-specific role formatting and clear data boundaries.
5. Validate output against a closed schema for decision, concise title, explanation, due time, and confidence evidence.
6. Reject malformed output; do not use regex repair as semantic truth.
7. Send uncertain or unsupported cases to **Needs review**.
8. Store privacy-safe diagnostics without notification bodies.

Exit gates:

- malformed and adversarial fixtures cannot create approved items;
- invalid output produces a visible review state or explicit failure;
- user rules deterministically override model output;
- cancellation releases native inference resources;
- no extraction runs on the main thread;
- a versioned, anonymized evaluation corpus establishes the initial quality baseline.

### Phase 4 — Trustworthy product experience

**Status:** Blocked by Phase 3

**Outcome:** Users can understand, correct, and gradually trust Thwiply's decisions.

Scope:

1. Replace the prototype Today screen with **Now**, **Later**, and **Needs review**.
2. Show source, decision reason, and relevant signals for every item.
3. Add one-tap corrections such as “important,” “later,” and “ignore this source.”
4. Turn repeated corrections into visible, editable rules.
5. Make current permission, model, queue, and processing states visible in Settings.
6. Add a first-run sample demonstration before requesting notification access.
7. Add accessibility coverage and privacy-safe diagnostic export.

Pilot gates:

- every displayed decision includes provenance and explanation;
- corrections take one interaction and affect subsequent matching events;
- users can inspect, edit, and delete learned rules;
- no notification is hidden because inference failed;
- pilot data establishes regret rate, correction rate, time-to-decision, retention, and perceived usefulness.

## Evidence-led follow-ups

These capabilities remain deferred until measurements justify them:

- a lightweight classifier before the generative model;
- sender-affinity personalization beyond explicit rules;
- device-tier model selection;
- battery and thermal scheduling policies;
- user-initiated screenshot intake through a share target or Android Photo Picker;
- explicitly opted-in notification snoozing with an audit trail and undo behavior;
- production signing, release hardening, and Play distribution.

No fixed battery, latency, RAM, confidence, or quality threshold is accepted without measurements on supported physical devices.

Reference: [Android Photo Picker and selected-media access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access).

## Cross-cutting requirements

### Privacy

- Capture is off until Android permission and per-app consent are both present.
- Raw notification content is memory-only by default.
- Durable records contain only the minimum information needed for the user-facing feature.
- Notification-derived data is excluded from cloud backup and device transfer.
- Users can delete all derived data and rules.
- Network access is limited to approved model downloads; notification content is never an outbound request body.

Reference: [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

### Reliability

- Fail open: source notifications remain untouched when Thwiply is unavailable.
- Every queue, payload, retry, and inference request is bounded.
- Duplicate delivery is safe.
- Errors remain distinguishable from empty or low-priority results.
- Process death and permission changes have explicit recovery behavior.

### Security

- Notification text, model output, restored data, and downloaded bytes are untrusted.
- Input fields are allowlisted and length-bounded at ingestion.
- Model output is schema-validated before persistence.
- Only app-approved, digest-pinned model artifacts can become active.
- No model output can directly execute an Android action.

## Roadmap maintenance

Each implementation PR should update this document when it:

- completes or materially changes a phase;
- changes a dependency or exit gate;
- validates or disproves a listed uncertainty;
- deliberately accepts a new product capability or non-goal.

Implementation plans belong in `docs/superpowers/plans/`; this file remains the durable product-level source of truth.
