# Phase 1 Durable Local Data Design

**Date:** 2026-08-31
**Status:** Approved by the implementation request
**Roadmap:** `docs/ROADMAP.md`, Phase 1

## Goal

Give Thwiply a durable, privacy-minimized local data layer before notification ingestion exists. The implementation must preserve user-visible triage state across process death and database reopen, provide explicit retention and erasure boundaries, and remove the Today screen's sample notification content.

## Scope

Phase 1 includes Compose-independent domain contracts, Room persistence, repositories, migrations, Hilt wiring, a repository-backed Today screen, retention, notification-derived delete-all behavior, and tests. It does not add a notification listener, notification access onboarding, an allowlist, prompt construction, model-to-triage processing, cloud sync, or source-notification actions.

## Domain contracts

The `domain/triage` package owns the product vocabulary and has no Android UI or Room annotations:

- `TriageItem`: an approved, display-safe item with a derived or user-authored display title and optional display summary, source reference, priority, creation/due/completion timestamps.
- `TriageDecision`: the current `NOW`, `LATER`, or `NEEDS_REVIEW` decision, its concise explanation, origin, and decision timestamp.
- `SourceReference`: `MANUAL` or `NOTIFICATION` provenance. Notification provenance stores only package name, app label, and an optional one-way stable-key hash; it has no notification title, text, or body.
- `UserCorrection`: a user's explicit decision change for a triage item, with optional linkage to the rule created from it.
- `UserRule`: an editable package/channel-scoped rule with an allowlisted action and enabled state.

Timestamps use epoch milliseconds so the domain remains platform-independent. IDs remain opaque strings. Construction validates required strings, maximum display lengths, non-negative timestamps, hash shape, and source-specific invariants. Values read from disk are validated again while mapping back into domain objects.

## Persistence architecture

`ThwiplyDatabase` is a Room database with four tables:

1. `triage_items` stores display-safe fields, minimal embedded source provenance, completion state, and (in schema v2) notification retention expiry.
2. `triage_decisions` stores one current decision per item and cascades when the item is deleted.
3. `user_corrections` stores correction history and cascades when the item is deleted. Its optional rule link becomes null if a single rule is deleted.
4. `user_rules` stores package/channel matching and the user-selected action.

No durable table has a notification title, notification text, raw body, payload, serialized extras, or prompt field. SQL is compile-time Room SQL with bound parameters. Foreign keys are enabled by Room and writes that must remain consistent are transactional.

The database starts at schema v2. A checked-in schema v1 contains all four Phase 1 tables; migration 1→2 adds the nullable retention-expiry column and backfills existing notification-derived rows using the default retention period. The application builder registers this migration and never enables destructive fallback.

## Repository boundaries

- `TriageRepository` observes approved item/decision records and performs atomic create, update, completion, and delete operations.
- `CorrectionRepository` records and observes corrections.
- `UserRuleRepository` creates, updates, observes, and deletes rules.
- `NotificationDataLifecycleRepository` purges expired notification-derived items and deletes all notification-derived records plus all rules.

Repository operations return a typed `RepositoryResult` so an empty query cannot be confused with a storage failure. Known SQLite failures retain their original cause in `RepositoryResult.Failure`; unexpected failures and cancellation are not swallowed. Item updates change only approved display/priority/due fields; source provenance, creation time, retention, and completion cannot be reclassified through that path. Updates and deletes that match no row return a typed not-found failure.

The default notification retention period is 30 days. New notification-derived items receive an expiry when saved. Manual items have no retention expiry. Today invokes the explicit purge repository operation on every screen entry, so a retained ViewModel can retry a failure and cannot indefinitely display newly expired rows. Phase 2 can additionally call the same contract from ingestion or lifecycle scheduling.

Delete-all intentionally preserves manual items while deleting every notification-sourced item, its cascaded decision/corrections, and every user rule. A manual correction that referenced a deleted rule survives with its optional rule reference cleared. Settings exposes this transaction behind a confirmation and reports exact deletion counts or a visible storage error.

## Today state and user-visible behavior

`TodayViewModel` observes `TriageRepository` and exposes a sealed `TodayUiState`: `Loading`, `Empty`, `Content`, or `StorageError`. The screen shows a real first-run empty state rather than sample tasks. Manual quick-add validates the domain's title and summary bounds with visible field errors, then creates a durable manual `TriageItem` and manual `NOW` decision. It does not preserve the prototype's arbitrary source override or invent a hidden due time. Complete/uncomplete is an atomic database toggle, delete calls the repository, and storage failures remain visible and retryable rather than being represented as an empty list.

The UI keeps a presentation-only `TaskItem` mapped from domain records. It no longer contains or expands a raw AI/notification snippet. Filters remain presentation concerns and are reduced to states the Phase 1 data model can truthfully represent.

## Backup and transfer privacy

The existing manifest-level `android:allowBackup="false"` remains unchanged. Because Android 12+ device manufacturers may ignore that flag for device-to-device migration, the manifest also references explicit rules that exclude the database domain from cloud backup and device transfer. `BackupConfigurationTest` locks down all three protections.

## Boundary and failure decisions

- Empty tables: handled as `Success(emptyList())` and rendered as `TodayUiState.Empty`.
- Invalid caller input: rejected by domain constructors with a field-naming error that does not echo the input value.
- Duplicate keys and transaction failure: returned as a typed storage failure; atomic writes roll back.
- Missing update/delete target: returned as a typed not-found failure.
- Malformed/restored rows: fail loudly during mapping; they are never converted to a plausible empty/default value.
- Flow cancellation: propagates normally.
- Unknown exceptions: propagate; only SQLite failures are translated.

## Testing strategy

Every behavior follows red-green-refactor:

- JVM domain tests cover validation and source invariants.
- JVM repository tests cover mapping, typed missing-row behavior, SQLite failure translation, and atomic boundary requirements with focused DAO fakes where appropriate.
- Instrumented Room tests on API 36 cover create/update/complete/delete across a real on-disk database close/reopen, foreign-key cascades, corrections/rules, retention, notification delete-all, and manual-data preservation.
- `MigrationTestHelper` tests schema v1→v2, validates the exported schema, and proves every v1 table's supported data survives.
- JVM ViewModel tests cover loading, real empty state, content, durable action delegation, and distinguishable storage errors.
- Full validation uses the repository CI command plus `connectedDebugAndroidTest` and the Bouncy Castle buildscript verification task.

## Alternatives considered

1. A single all-purpose repository would reduce files but blur triage, feedback, and privacy-erasure authority. Separate boundaries make destructive operations explicit and independently testable.
2. Persisting the current `TaskItem` directly would be smaller but couple durable schema to Compose copy and preserve the prototype's raw snippet field. A domain-to-presentation mapper prevents that privacy and architecture leak.
3. Starting at schema v1 with no migration would reflect the fact that Room has not shipped yet, but it would not satisfy the roadmap's migration exit gate. Keeping a real v1 schema and shipping v2 exercises the migration path before user data depends on it.
