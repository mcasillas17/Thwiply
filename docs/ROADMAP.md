# Thwiply Product Roadmap

**Status:** Phase 0 and Phase 1 delivered scope complete; foundation hardening and Phase 2 design are ready; notification ingestion is not started
**Last updated:** 2026-09-03

## Product direction

Thwiply is becoming a private, opt-in, on-device importance inbox that helps people decide what deserves attention without sending notification content to a cloud inference service.

The long-term north star is **fewer interruptions without regret**. The first MVP will not promise control over Android's notification shade. It will observe notifications from user-approved apps, classify them locally, and present a calmer in-app view with explanations and easy corrections.

### MVP promise

- Notifications are read only after explicit Android permission and per-app opt-in.
- Local decisions sort items into **Now**, **Later**, or **Needs review**.
- Every decision has provenance and a useful explanation.
- A correction takes one tap; applying that choice to future notifications is
  explicit, visible, and editable.
- Model or processing failures never hide or cancel the source notification.

### MVP non-promise

- No autonomous notification cancellation, snoozing, channel changes, or shade ranking.
- No passive screenshot monitoring or automatic screenshot deletion.
- No cloud inference, cloud sync, analytics, or account system.
- No arbitrary model URLs, model marketplace, or model-generated device actions.

## Current state

| Area | State | Evidence and open work |
|---|---|---|
| Secure model installation | Core delivered; hardening open | Pinned HTTPS artifact, exact size, SHA-256 activation, and atomic install exist; restart adoption checks length but not the digest |
| Local LiteRT-LM Lab | Alpha available; hardening open | Process-owned serialized engine and streaming UI exist; initialization, cancellation, arbitration, and throughput labels need correction |
| Product copy and privacy status | User-facing copy truthful; dead state remains | The UI says capture is unavailable, but unused notification/screenshot capture flags still default to enabled in `SettingsViewModel` |
| Durable task and decision data | Phase 1 delivered scope complete | Room v2, exported schemas, repositories, and restart tests exist; category projection and end-to-end correction application are not implemented |
| Notification ingestion | Not started | No notification-access state, app allowlist, `NotificationListenerService`, normalizer, or ingestion queue exists |
| Structured triage pipeline | Not started | Lab output is not connected to deterministic rules, a strict output schema, or persisted product state |
| Explanations, corrections, and rules | Contracts and storage only | Decision explanations, correction rows, and rule rows exist; Today drops the category, and no correction/rule workflow consumes them |
| Notification-data lifecycle | Phase 1 foundation delivered; hardening open | 30-day expiry, purge on Today entry, and confirmed delete-all exist; purge failure can hide manual data and inactive-app cleanup is not scheduled |
| Alpha distribution | Workflow delivered; runtime proof open | Signed, minified, per-ABI prereleases, checksums, and a 32 MiB arm64 size gate exist; the minified LiteRT-LM path lacks a recorded device smoke gate |
| Android quality | Hardening open | Lifecycle-aware collection, target SDK 36 insets, string resources, accessibility semantics/touch targets, and adaptive-layout evidence remain open |
| Project metadata | Hardening open | Settings hardcodes its version, and README declares MIT while the linked `LICENSE` file is absent |

## Task model

Task IDs are stable and do not change when work is reordered:

- `FND` - foundation hardening;
- `P2`, `P3`, and `P4` - product phase delivery;
- `PILOT` - pilot measurement and release readiness;
- `DEF` - explicitly deferred work.

Task states:

- **Complete** - implementation and fresh required evidence satisfy the task;
- **Ready** - every listed prerequisite is complete or the task is an approved
  design activity that may proceed without product code;
- **Blocked** - at least one listed prerequisite is open;
- **Deferred** - excluded from the MVP until its evidence gate promotes it.

Every implementation task must update its owning tests and cite the fresh
automated or physical-device evidence used to mark it complete. Exact constants,
schemas, fixtures, and third-party choices belong in the accepted design and
implementation plan for that task.

## Implementation constraints

These decisions are prerequisites, not open implementation options:

- manual tasks and Settings remain usable without a downloaded or healthy
  model;
- raw notification fields live only in a bounded in-memory queue;
- enqueue returns an explicit accepted, duplicate, overflow, or unavailable
  result, and overflow is never reported as success;
- a SHA-256 hash of the platform notification key is the durable identity;
  repeated delivery is idempotent, and same-key updates coalesce latest-wins;
- content-based revision fingerprints, if needed, stay memory-only;
- user corrections never overwrite the rule or model decision; corrections
  are append-only, and a tested projection supplies the effective category;
- a same-key source update may replace the base decision only through an
  explicit versioned reprocessing transaction that preserves correction
  history and keeps the latest correction effective;
- recording a correction and any user-approved future rule is atomic;
- durable provenance is limited to bounded package, app label, channel ID,
  stable key hash, source time, origin, matched rule or model/schema version,
  and concise explanation;
- model output gets one exact, strict closed-schema parse with unknown keys and
  wrappers rejected; there is no regex repair or inference retry;
- invalid, uncertain, timed-out, or unsupported inference becomes a visible
  **Needs review** result;
- source notifications are never hidden, canceled, snoozed, ranked, or mutated
  by MVP code.

## Current execution order

1. Start `FND-01` through `FND-05`, `FND-07`, `FND-12`, and `FND-14` in
   parallel where ownership permits.
2. Complete `FND-06` after its resource prerequisite and complete the model and
   Lab chain `FND-08` through `FND-11`.
3. Prove the shipped minified path with `FND-13`.
4. `P2-00` may design contracts in parallel, but no listener code starts until
   its foundation prerequisites and accepted design are complete.
5. Phase 3 data contracts may overlap late Phase 2 only after both designs
   agree on identity, provenance, queue outcomes, and failure states.
6. Phase 4 and pilot work remain dependency-blocked.

## Detailed implementation backlog

### Foundation hardening

| ID | Status | Outcome and implementation | Depends on | Completion evidence |
|---|---|---|---|---|
| FND-01 | Ready | Run existing Room reopen, migration, backup, and future service instrumentation in CI using a managed emulator job separate from the fast JVM/lint/build job. Preserve logs and make the device job required before merge. | None | `ThwiplyDatabaseTest`, `ThwiplyMigrationTest`, and `BackupConfigurationTest` execute in CI; a deliberately failing instrumentation test fails the job. |
| FND-02 | Ready | Replace model-gated root navigation with an app shell that always exposes manual Today and Settings. Model setup becomes a resumable feature state, not an entrance requirement. | None | Cold launches with missing, downloading, corrupt, removed, and failed models can create/read manual tasks, open Settings, and start or retry model setup. |
| FND-03 | Ready | Add lifecycle-aware Compose flow collection and subscription policies. Replace screen-level `collectAsState()` usage, stop off-screen Room observation, and test foreground/background transitions. | None | App flows are collected only while their owners are active; process/background tests show no duplicate observers or lost visible state. |
| FND-04 | Ready | Implement target SDK 36 edge-to-edge, status/navigation/IME insets, light/dark system-bar appearance, and adaptive phone/tablet/foldable layouts without changing product information architecture. | None | API 31 and 36 device tests cover gesture and three-button navigation, cutouts, IME use, rotation, and representative window sizes without clipped controls. |
| FND-05 | Ready | Move user-facing copy, formatted counts, dates, accessibility labels, and errors into Android string/plural resources. Keep locale expansion separate until pilot scope chooses supported locales. | None | Android lint reports no production hardcoded-text violations; formatted/plural strings render correctly in unit or Compose tests. |
| FND-06 | Blocked | Establish the accessibility baseline for existing surfaces: at least 48 dp interactive targets, meaningful state/action semantics, scalable text, contrast review, traversal order, and TalkBack paths for Today, Lab, onboarding, and Settings. Downstream features own their additional accessibility evidence. | FND-05 | Compose semantics tests and a documented TalkBack pass cover existing add, complete, delete, Lab generation/copy, model setup, and Settings flows. |
| FND-07 | Ready | Add a typed preference repository for theme and durable education state; remove unused notification/screenshot capture flags and orphaned debug UI or isolate it to debug builds. Preferences never contain notification content. | None | Theme and education state survive process recreation; searches find no production capture placeholders; preference tests distinguish read/write failures from unset values. |
| FND-08 | Blocked | Introduce explicit model states (`Missing`, `Verifying`, `Ready`, `Corrupt`, `Removing`, `Failed`), revalidate the active digest after restart off the main thread, and make every state visible without blocking manual features. | FND-02, FND-07 | Equal-length tampering and truncation are rejected after restart; original causes remain attached to failures; no JNI or full-file digest runs on the main thread. |
| FND-09 | Blocked | Define single-engine ownership, off-main initialization, cancellation, stop, close, and arbitration between user-initiated Lab work and product triage. Never swallow coroutine cancellation or leak a conversation/native engine. | FND-08 | Fake-engine tests prove cancel/stop closes conversations, the mutex is released, user-visible states remain distinguishable, and one engine is active per process. |
| FND-10 | Blocked | Make model download single-flight and resumable with validated range continuity, bounded/throttled progress, explicit cancellation, disk-space preflight, metered-network confirmation, timeouts, and user-initiated removal. | FND-07, FND-08 | Slow, partial, ignored-range, corrupt, low-space, metered, concurrent, cancel, restart, and remove cases pass without activating unverified bytes or reporting failure as success. |
| FND-11 | Blocked | Correct Lab metrics and behavior: count characters or verified runtime tokens, bound prompt/output presentation, expose busy/stop states, reuse the engine arbitration contract, and keep Lab output separate from product persistence. | FND-09 | Metric tests use known streams and elapsed time; UI never labels chunks as tokens; Lab cancellation and contention states are visible and recoverable. |
| FND-12 | Ready | Centralize notification-data cleanup at app startup, Today entry, and active ingestion boundaries; make purge failure diagnostic but never hide manual rows; add at most one best-effort local cleanup run per day with no network or model work. | None | Expired notification-derived records are purged on the next eligible boundary; periodic work is uniquely scheduled and bounded to one delete transaction per run; injected purge failure still renders manual tasks. |
| FND-13 | Blocked | Audit packaged consumer rules, add only demonstrated R8/serialization/JNI rules, and run the minified alpha on an emulator and representative arm64 device through launch, model verification, initialization, and one generation. | FND-01, FND-08, FND-09, FND-10, FND-11 | The exact signed/minified variant launches and infers on device; mapping/keep evidence is archived; the arm64 size gate and per-ABI checks remain green. |
| FND-14 | Ready | Repair release truth and maintenance policy: add the declared MIT license, display `BuildConfig.VERSION_NAME`, document supported toolchain/dependency baselines, remove or justify unused dependencies, and keep security maintenance separate from product phase status. | None | README license link resolves; installed build reports the packaged version; dependency verification and latest `main` CI are green; prerelease toolchain use has an explicit rationale or is replaced with evidence. |

### Phase 2 - consent and bounded notification ingestion

| ID | Status | Outcome and implementation | Depends on | Completion evidence |
|---|---|---|---|---|
| P2-00 | Ready | Write and approve the Phase 2 design and implementation plan. Define consent states, live access observation, allowlist schema, app discovery, listener filters, field limits, skip reasons, queue outcomes, identity/update semantics, recovery, diagnostics, privacy lifetime, and device matrix before code. | None | Accepted spec and executable plan resolve every listed contract without choosing unmeasured queue or timing constants. |
| P2-01 | Blocked | Add Android-independent ingestion contracts for raw callback input adapters, normalized events, skip reasons, enqueue outcomes, listener state, and content-free diagnostics. Android framework types stop at the adapter boundary. | P2-00 | Unit tests reject blank/invalid identifiers, unsupported types, overlong fields, invalid timestamps, and invalid hashes without exposing field values in errors. |
| P2-02 | Blocked | Implement a live notification-access observer and a separate persisted education/consent state. The Android system grant is always authoritative; persisted state must never impersonate permission. | P2-00, FND-02, FND-07 | Grant, denial, revocation, process recreation, and return-from-settings tests update state correctly while manual and Lab features remain usable. |
| P2-03 | Blocked | Add an empty-by-default allowlist entity, DAO, repository, migration, and transactional enable/disable operations keyed by package. Store bounded app metadata only; never store notification content. | P2-01, FND-01, FND-07 | Fresh install allows zero packages; selections survive restart; missing/duplicate writes are explicit; migration and delete-all behavior run in CI. |
| P2-04 | Blocked | Build a scoped app-discovery adapter and searchable selection UI. Prefer the narrowest package visibility that supports the pilot, represent uninstalled/stale packages, and require a design review before any broad package-query permission. | P2-03 | App discovery and stale/uninstalled fixtures merge deterministically with the allowlist; no package becomes allowed through discovery alone. |
| P2-05 | Blocked | Add staged consent UX: truthful explanation, system-settings handoff, return-state refresh, app selection, visible grant/allowlist status, denial, revocation, and reconnect guidance. | P2-02, P2-03, P2-04, FND-06 | Compose tests cover every state transition; permission denial/revocation leaves manual tasks, Settings, and model setup functional. |
| P2-07 | Blocked | Implement defensive normalization of explicitly allowed extras and metadata. Handle unexpected `CharSequence`/bundle shapes, trim and bound fields before allocation/prompt use, hash the platform key, and return typed empty/redacted/unsupported results. | P2-01 | Table-driven and fuzz fixtures cover empty, redacted, huge, bidirectional, emoji, styled, group, ongoing, media, and malformed inputs; raw bodies never reach logs or durable tables. |
| P2-06 | Blocked | Register a non-exported, conservatively filtered `NotificationListenerService` with the platform bind permission and service action. Callback code checks live access and allowlist, normalizes bounded fields, tries enqueue, records the typed outcome, and returns without Room, network, or LLM work. | P2-02, P2-03, P2-05, P2-07 | Manifest tests verify registration/filter metadata; callback tests prove unapproved packages and unsupported notification types never enter the queue and no expensive dependency is invoked. |
| P2-08 | Blocked | Implement the bounded memory-only queue with one consumer and typed accepted, duplicate, overflow, unavailable, and closed outcomes. Capacity is measured in the Phase 2 plan; overflow rejects the incoming event and increments content-free diagnostics. | P2-07 | Concurrency tests prove memory/capacity bounds, FIFO behavior for accepted events, cancellation/close semantics, and that overflow can never return accepted or silently discard an earlier accepted event. |
| P2-09 | Blocked | Separate identity, update coalescing, and burst handling. Persist only the platform-key hash; same-key repeats are idempotent, same-key revisions are latest-wins while pending, and any content revision fingerprint stays bounded and memory-only. | P2-08 | Duplicate, repost, update, group, and cross-package fixtures produce the documented outcomes without durable body/content hashes or collapsing distinct notifications. |
| P2-10 | Blocked | Handle listener connected/disconnected state, permission revocation, process death, app restart, and reboot through explicit state transitions and connected-only active-notification rescan. Do not add a durable raw queue or report lost in-flight work as processed. | P2-06, P2-08, P2-09 | Reconnect/reboot tests show no crash or duplicate accepted event; diagnostics distinguish dropped in-flight work from processed work; raw event fields disappear with process memory. |
| P2-11 | Blocked | Persist or expose only content-free counters and states: callback totals, skip reasons, accepted/duplicate/overflow counts, queue depth, connection state, and last error code. Give diagnostics retention/reset/delete behavior. | P2-08, P2-10, FND-07 | Schema/log tests fail if title, body, sender, prompt, model output, or un-hashed platform key reaches diagnostics; reset and delete paths are visible and tested. |
| P2-12 | Blocked | Close Phase 2 with JVM fixtures, manifest checks, managed-emulator scenarios, and representative messaging/email physical-device tests across grant, allowlist, burst, update, overflow, revocation, reconnect, reboot, and denial-safe use. | P2-01 through P2-11, FND-01, FND-13 | Every Phase 2 exit gate has named fresh evidence; tested apps/devices and known platform differences are recorded without inventing quality thresholds. |

### Phase 3 - reviewable local triage

| ID | Status | Outcome and implementation | Depends on | Completion evidence |
|---|---|---|---|---|
| P3-00 | Blocked | Write and approve the Phase 3 design and plan. Freeze the effective-decision model, provenance schema, rule precedence, prompt/schema versions, engine arbitration, timeout/failure taxonomy, atomic persistence, evaluation format, and resource measurement hooks. | P2-00 | Accepted spec and plan map every input, failure, and durable field before a database or model integration change. |
| P3-01 | Blocked | Add privacy-minimal provenance and effective-decision storage/query support: bounded channel ID, rule/model/schema identifiers, a base decision separate from append-only corrections, and a deterministic latest-correction projection. Migrate existing v2 rows without inventing values. | P3-00, FND-01 | Exported schema and migration tests preserve v2 data; projection tests cover no, one, multiple, tied, and deleted corrections; no raw sender/body column exists. |
| P3-02 | Blocked | Implement deterministic candidate filters for empty/redacted/ongoing/media/group/unsupported events with typed reasons. Filters operate on normalized contracts before rules or inference. | P2-07, P3-00 | Table-driven fixtures prove each filter and show no model call, durable item, or success-shaped fallback for skipped input. |
| P3-03 | Blocked | Implement the enabled-rule engine with deterministic specificity and conflict policy: exact package+channel before package wildcard, then explicit action precedence. `IGNORE` creates no item; `PRIORITIZE` and `DEFER` decisions bypass inference. | P3-01, P3-02 | Rule matrix tests cover exact/wildcard, enable/disable, conflict, missing channel, stale app, and delete behavior; matched rules never invoke the model. |
| P3-04 | Blocked | Build a versioned prompt formatter that treats every normalized field as untrusted data, applies per-field bounds before formatting, uses model-specific role boundaries, and never interpolates data into code, SQL, paths, or Android actions. | P2-07, P3-00 | Golden prompt tests preserve data boundaries under injection-like, delimiter, bidi, emoji, and maximum-length fixtures without logging the payload. |
| P3-05 | Blocked | Define a closed typed output schema for category, concise display title/summary, explanation, optional due time, and any validated evidence needed for review routing. Parse one exact JSON object with unknown/missing/extra/wrapped/out-of-range data rejected; do not repair or retry. | P3-04 | Valid fixtures map to domain types; fences, preambles, trailing text, unknown keys, invalid enums/times, truncation, and oversized fields become typed validation failures and cannot create Now/Later items. |
| P3-06 | Blocked | Implement one application-scoped triage coordinator that consumes accepted events serially, applies filters and rules, acquires the shared engine only when needed, bounds total wait/generation/output, preserves cancellation, and emits explicit processing states. | P2-08, P3-02, P3-03, P3-05, FND-09 | Fake-engine integration tests prove one in-flight inference, bounded work, Lab/product arbitration, cancellation cleanup, and no main-thread extraction or inference. |
| P3-07 | Blocked | Make every failure explicit. An accepted normalized candidate with invalid output, timeout, unavailable model, or thermal/resource pause becomes a privacy-minimal Needs Review item. Only events rejected before candidate acceptance or lost to lifecycle cancellation may end as typed skip/failure diagnostics; no path touches the source notification. | P3-06 | Failure-matrix tests prove every accepted candidate that cannot be decided becomes Needs Review, while pre-acceptance skips and lifecycle loss remain distinguishable diagnostics and never report an approved decision. |
| P3-08 | Blocked | Persist validated decisions idempotently and atomically. Same-key accepted updates use a versioned reprocessing transaction that replaces only permitted derived/base fields, preserves append-only correction history and its latest effective category, retains provenance/expiry, and releases raw fields after processing. | P3-01, P3-06, P3-07 | Transaction, rollback, duplicate, update-before-correction, update-after-correction, process-death, and retention tests pass across reopen with no partial row, lost correction, or stale effective category. |
| P3-09 | Blocked | Add content-free pipeline diagnostics and a versioned synthetic/anonymized evaluation corpus. Record rule/model/schema versions, outcome/error code, queue and inference timing, and aggregate counts without payloads. | P3-06, P3-08, P2-11 | Fixture review confirms no real user content; diagnostics secret/content scans are clean; fake-model CI reproduces rule/schema/fallback results deterministically. |
| P3-10 | Blocked | Close Phase 3 with adversarial schema/prompt fixtures, real-model device evaluation, cancellation/native-resource checks, latency/memory/thermal collection, and a documented baseline. | P3-01 through P3-09, P2-12, FND-13 | Malformed/adversarial inputs cannot create approved items; rules override; every accepted candidate with invalid output becomes visible Needs Review; device evidence establishes measurements without pre-accepting thresholds. |

### Phase 4 - trustworthy product experience

| ID | Status | Outcome and implementation | Depends on | Completion evidence |
|---|---|---|---|---|
| P4-00 | Blocked | Write and approve the Phase 4 UX/data-flow plan covering inbox information architecture, item detail, correction and rule scope, onboarding, status, diagnostics export, accessibility, adaptive layouts, and all failure states. | P3-00 | Accepted interaction/state diagrams map every action to a typed domain operation and recovery path. |
| P4-01 | Blocked | Replace prototype Today filters with Now, Later, and Needs Review surfaces driven by effective category while retaining manual add, completion/archive behavior, storage errors, and useful empty states. | P3-01, P3-08, P4-00 | ViewModel and Compose tests route base and corrected items to the right surface across process recreation and filtering. |
| P4-02 | Blocked | Add item detail that shows privacy-minimal source, source time, decision origin, matched rule/model/schema version, concise explanation, due time, and processing/review state without presenting hidden chain-of-thought or raw bodies. | P4-01 | Every displayed item has tested provenance and explanation; absent metadata renders an explicit unavailable state rather than fabricated copy. |
| P4-03 | Blocked | Implement one-tap category correction as an atomic append-only correction operation. Optimistically update only with rollback/error state, never overwrite the base decision during correction, and make the effective projection update immediately. | P3-01, P4-01 | Rapid, repeated, conflicting, failed, and reopened correction tests preserve history and show the correct effective category without partial writes. |
| P4-04 | Blocked | Offer explicit item-only and apply-to-future correction actions. The future action defaults to package+channel when available, explains scope before commit, requires a deliberate package-wide choice, and records the correction plus new/updated rule in one transaction. Never auto-learn silently. | P3-03, P4-03 | UI/domain tests cover item-only/channel/package scope, duplicate rule update, conflict, cancel, transaction rollback, and the next matching event bypassing inference. |
| P4-05 | Blocked | Add rule management for list, search/group, explanation, enable/disable, action/scope edit, conflict resolution, and delete. Keep correction history valid when a rule is deleted. | P4-04 | Repository and Compose tests prove edits affect subsequent matching, deleted links become null safely, and delete-all removes every rule. |
| P4-06 | Blocked | Add a clearly labeled synthetic first-run demonstration before notification access. Demo records never claim notification provenance or enter retention/delete-all as real captured data. | P4-01, P4-00 | New-user tests complete or skip the demo without permission/model dead ends; synthetic data is distinguishable and removable. |
| P4-07 | Blocked | Build truthful Settings status for notification access, allowlist count, model verification, engine busy state, queue/processor health, retention, diagnostics reset/export, model removal, and recovery actions. | P2-11, P3-09, P4-00 | Settings reacts to grant/revocation, model failure/removal, overflow, and processing failure with distinct tested states and no fake capture toggle. |
| P4-08 | Blocked | Add user-initiated privacy-safe diagnostic export containing versions, counters, timings, outcome/error codes, and device/app metadata approved by the privacy review. Exclude notification fields, prompts, model output, and secrets. | P3-09, P4-07 | Export schema allowlist and tests fail on forbidden keys/content; cancellation and destination I/O failure remain visible; no background upload exists. |
| P4-09 | Blocked | Complete accessibility, dynamic-text, contrast, reduced-motion, state-restoration, phone/tablet/foldable, orientation, IME, and TalkBack coverage for every Phase 4 flow. | P4-01 through P4-08, FND-03, FND-04, FND-06 | Compose/device matrix covers all major states at supported text/window sizes with no inaccessible control or clipped critical action. |
| P4-10 | Blocked | Run end-to-end trust scenarios proving source notifications remain untouched through denial, overflow, model absence, invalid output, cancellation, correction, rule edits, purge failure, and app/process restart. | P4-01 through P4-09, P3-10 | Every Phase 4 pre-pilot trust gate has named fresh automated and physical-device evidence; no failure path hides or mutates a source notification. |

### Pilot readiness

| ID | Status | Outcome and implementation | Depends on | Completion evidence |
|---|---|---|---|---|
| PILOT-00 | Blocked | Approve the pilot protocol: supported build/device/app matrix, recruitment and feedback boundaries, local measurement taxonomy, privacy notice, rollback, issue severity, and go/no-go ownership. | P4-00 | A versioned protocol maps each pilot decision to a measurable signal and owner without cloud analytics. |
| PILOT-01 | Blocked | Implement local aggregate measurement for regret proxies, correction and rule rates, time-to-visible-decision, queue wait/overflow, inference result/latency, retention, and failures. Bound storage and retention; make reset/export user-controlled. | P3-09, P4-08, PILOT-00 | Tests show one bounded aggregate update per event/outcome and zero payload retention; deletion/export are complete and distinguish failures. |
| PILOT-02 | Blocked | Run the versioned evaluation corpus and real-model device sessions; report schema validity, confusion/correction behavior, latency, memory, battery, and thermal observations by build/model/schema version. | P3-10, PILOT-01 | Reproducible reports contain sample counts and distributions, explain exclusions, and use synthetic or explicitly consented data only. |
| PILOT-03 | Blocked | Execute representative API 31 and 36 physical-device scenarios across messaging/email apps, OEM/lifecycle variance, offline/metered states, reconnect/reboot, long bursts, backgrounding, model removal, and low-resource conditions. | P2-12, P4-10, PILOT-00 | Device matrix records exact builds and outcomes; unsupported configurations are named from evidence rather than assumed. |
| PILOT-04 | Blocked | Define and verify fail-open resource safety using measured evidence. At minimum, system-reported severe thermal/resource pressure pauses new inference and routes work to visible review/failure without retry storms; tuned battery/thermal scheduling remains deferred. | PILOT-02, PILOT-03 | Safety transitions and recovery pass on supported devices; any numeric threshold cites the measurement that justified it. |
| PILOT-05 | Blocked | Repeat privacy, security, backup/transfer, data-lifecycle, package-visibility, logging, dependency, accessibility, and source-notification reviews against real ingested data and the minified pilot build. | PILOT-01 through PILOT-04, FND-12, FND-13 | Review evidence confirms allowlist default-off, no forbidden durable/log/export content, complete deletion, and no notification mutation. |
| PILOT-06 | Blocked | Publish a signed, per-ABI, checksummed, size-bounded pilot alpha only after required gates pass. Include upgrade/migration, rollback/uninstall, supported-device, privacy, and known-limitation notes. | PILOT-05, P4-10, FND-14 | Release workflow and device install/upgrade smoke pass for the tagged commit; artifacts, checksums, notes, license, packaged version, and rollback instructions resolve. |
| PILOT-07 | Blocked | Run the consented pilot and collect structured participant feedback on perceived usefulness, missed-important-item regret, trust, explanation clarity, correction effort, and rule control. Store no notification content in research records. | PILOT-06 | Pilot record names recruitment/consent boundaries, build/device/app coverage, participant and session counts, attrition, structured feedback method, incidents, and privacy-preserving results. |
| PILOT-08 | Blocked | Review pilot evidence and make explicit promote, iterate, narrow-support, or stop decisions for each MVP promise and evidence-led follow-up, and decide whether the experimental Lab remains top-level, is demoted to a diagnostic surface, or is excluded from pilot builds. | PILOT-07 | Decision record cites measured regret/correction/usefulness, participant feedback, resource evidence, and observed Lab value/contention; deferred work remains deferred unless its promotion gate is satisfied. |

## Phase outcomes and exit gates

### Phase 0 — Truthful, secure alpha

**Status:** Complete

Delivered:

- immutable model manifest with commit-pinned URL, exact size, and SHA-256;
- resumable temporary downloads and atomic activation;
- model files excluded from Android backup;
- serialized process-wide engine ownership and conversation cleanup;
- real model status, accurate privacy copy, and removal of fake capture controls
  from the UI; unused placeholder state remains tracked by `FND-07`;
- regression coverage for downloader and engine lifecycle behavior.

### Phase 1 — Durable local data foundation

**Status:** Delivered scope complete; foundation hardening and continuous
device evidence remain open

**Outcome:** Product state survives process death and restarts without retaining unnecessary notification content.

Scope:

1. Define domain contracts for `TriageItem`, `TriageDecision`, `SourceReference`, `UserCorrection`, and `UserRule`.
2. Keep domain models independent from Compose presentation models.
3. Add Room entities, DAOs, repository boundaries, migrations, and Hilt wiring.
4. Replace hardcoded `TodayViewModel` tasks with repository-backed flows and a real empty state.
5. Persist approved triage items, decisions, corrections, and minimal provenance.
6. Do not persist raw notification bodies.
7. Add delete-all and retention behavior before ingestion exists.

Delivered:

- Compose-independent, validated contracts for triage items, decisions, sources, corrections, and rules;
- four privacy-reviewed Room tables with foreign keys, indices, transactional writes, exported v1/v2 schemas, and an explicit 1→2 migration;
- repository boundaries with `Flow` reads, immutable provenance/retention on updates, typed not-found/database failures, and no broad exception translation;
- repository-backed Today loading, empty, content, and storage-error states plus validated durable manual create, atomic complete/uncomplete, and delete actions;
- a 30-day expiry field only for notification-derived items, retryable expired-record purge on every Today entry, and transactional delete-all for notification-derived records and all rules;
- a confirmed Settings delete-all control that preserves manual tasks and reports success or failure;
- no notification listener, access request, allowlist, ingestion queue, or notification processing from Phase 2.

Evidence:

- `TriageModelsTest`, `RoomRepositoriesTest`, `TodayViewModelTest`, and `NotificationDataSettingsViewModelTest` cover contracts, mapping, failure semantics, input bounds, repository-backed UI state, retryable retention activation, and privacy erasure UX;
- `ThwiplyDatabaseTest` covers on-disk create/update/complete/delete across reopen, immutable notification provenance, atomic rapid completion toggles, transaction rollback, correction/rule persistence, cascade behavior, retention cutoffs, delete-all, and exact privacy-reviewed columns;
- `ThwiplyMigrationTest` seeds every v1 table and proves the v2 migration preserves supported values while backfilling notification retention only;
- `BackupConfigurationTest` verifies `android:allowBackup="false"` plus explicit database exclusions in both cloud-backup and device-transfer rules.

The Android instrumentation evidence above exists in the repository but does
not run continuously until `FND-01` is complete.

Exit gates:

- create, update, complete, and delete operations survive app restart;
- migration tests preserve supported data;
- no raw notification text appears in durable tables;
- delete-all removes every notification-derived record;
- Android backup and device-transfer behavior remain explicitly disabled or excluded;
- unit and database tests cover repositories and failure paths.

### Phase 2 — Consent and bounded notification ingestion

**Status:** Design ready (`P2-00`); implementation blocked by foundation
hardening and the accepted Phase 2 design

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
  -> Phase 2 normalization, identity, update coalescing, and bounded queue
  -> deterministic candidate filters
  -> enabled user rules
  -> one serialized local-LLM decision when needed
  -> one strict closed-schema validation
  -> Now / Later / Needs review or explicit failure
  -> privacy-minimal base decision and effective-category projection
```

Scope:

1. Apply user-authored `PRIORITIZE`, `DEFER`, and `IGNORE` rules before model
   inference.
2. Use deterministic handling for known non-candidates such as ongoing media and empty content.
3. Bound every untrusted input field before prompt construction.
4. Treat notification text as data using model-specific role formatting and clear data boundaries.
5. Validate one exact output against a closed schema for decision, concise
   title/summary, explanation, due time, and any accepted review-routing
   evidence.
6. Reject malformed, wrapped, extra-key, and out-of-range output; do not repair
   or retry it.
7. Send uncertain or unsupported cases to **Needs review**.
8. Store privacy-safe diagnostics without notification bodies.

Exit gates:

- malformed and adversarial fixtures cannot create approved items;
- invalid output from an accepted candidate produces visible Needs Review;
  pre-acceptance rejection or lifecycle loss remains an explicit typed
  skip/failure;
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
3. Add one-tap item corrections such as "important" and "later."
4. Offer explicit, scoped apply-to-future actions, including "ignore this
   source," and make every created rule visible and editable.
5. Make current permission, model, queue, and processing states visible in Settings.
6. Add a first-run sample demonstration before requesting notification access.
7. Add accessibility coverage and privacy-safe diagnostic export.

Pilot gates:

- every displayed decision includes provenance and explanation;
- corrections update the current item in one interaction;
- explicitly created rules affect subsequent matching events;
- users can inspect, edit, and delete learned rules;
- no notification is hidden because inference failed;
- pilot data establishes regret rate, correction rate, time-to-decision, retention, and perceived usefulness.

## Evidence-led follow-ups

These capabilities remain deferred until measurements or an explicit product
decision satisfy their promotion gates:

| ID | Deferred capability | Promotion evidence |
|---|---|---|
| DEF-01 | Lightweight classifier before the generative model | Corpus and device measurements show meaningful quality/resource improvement over deterministic rules plus the current model. |
| DEF-02 | Sender-affinity personalization beyond explicit rules | Pilot corrections demonstrate value and a privacy review accepts the additional identity data. |
| DEF-03 | Device-tier model selection | Supported-device measurements show one pinned model cannot meet accepted quality/resource goals. |
| DEF-04 | Productized battery/thermal scheduling | `PILOT-02` through `PILOT-04` justify a policy beyond the minimum fail-open safety state. |
| DEF-05 | User-initiated screenshot intake through a share target or Android Photo Picker | Separate consent, retention, deletion, and threat-model design is approved. |
| DEF-06 | Explicitly opted-in notification snoozing with audit and undo | The read-only MVP proves trust and a separate source-notification mutation design is approved. |
| DEF-07 | Production signing and Play distribution | Pilot gates pass and production release, policy, support, rollback, and key-management plans are approved. |
| DEF-08 | Accounts, cloud sync, cloud inference, or analytics | A separate product/privacy decision deliberately changes the local-only non-promise. |
| DEF-09 | Notification cancellation, ranking, channel mutation, or other shade control | A separate trust/safety design proves auditability, undo, and fail-open behavior. |
| DEF-10 | Model marketplace, arbitrary URLs, or automatic multi-model switching | Supply-chain, compatibility, storage, UX, and support designs are approved. |

No fixed battery, latency, RAM, confidence, or quality threshold is accepted without measurements on supported physical devices.

Reference: [Android Photo Picker and selected-media access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access).

## Cross-cutting requirements

### Privacy

- Capture is off until Android permission and per-app consent are both present.
- Raw notification content is memory-only.
- Durable records contain only the minimum information needed for the user-facing feature.
- Notification-derived data is excluded from cloud backup and device transfer.
- Users can delete all derived data, rules, and diagnostics; downloaded model
  data has a separate user-controlled removal path.
- Network access is limited to approved model downloads; notification content is never an outbound request body.

Reference: [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

### Reliability

- Fail open: source notifications remain untouched when Thwiply is unavailable.
- Every queue, payload, retry, and inference request is bounded.
- Queue admission, overflow, cancellation, and closed states are distinguishable
  from success.
- Duplicate delivery is safe.
- Same-key source updates follow an explicit versioned replacement policy.
- Errors remain distinguishable from empty or low-priority results.
- Process death and permission changes have explicit recovery behavior.

### Security

- Notification text, model output, restored data, and downloaded bytes are untrusted.
- Input fields are allowlisted and length-bounded at ingestion.
- Model output is parsed once against an exact closed schema before
  persistence; unknown or wrapped output is rejected rather than repaired.
- Only app-approved, digest-pinned model artifacts can become active.
- No model output can directly execute an Android action.

### Android quality

- State collection is lifecycle-aware.
- Target SDK 36 edge-to-edge and window-inset behavior is tested.
- Interactive controls expose semantics and meet accessible touch targets.
- User-facing strings are Android resources.
- Supported phone, tablet, foldable, and window-size layouts remain usable.

### Delivery

- JVM, lint, build, workflow, and Android instrumentation gates run in CI.
- Release artifacts remain signed, per-ABI, checksummed, and size-bounded.
- The exact minified release candidate passes model initialization and
  generation on a representative physical device before publication.
- Roadmap completion claims cite code and fresh automated or device evidence.

## Roadmap maintenance

Each implementation PR should update this document when it:

- completes or materially changes a phase;
- changes a dependency or exit gate;
- validates or disproves a listed uncertainty;
- deliberately accepts a new product capability or non-goal.

## Related implementation guidance

- [`docs/RELEASING.md`](RELEASING.md) defines alpha build and publication
  procedures.

This file is the sole source of truth for current product status, ordering,
exit gates, and deferred work.
