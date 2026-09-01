# Phase 1 Durable Local Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a durable, privacy-minimized Room foundation and replace Today sample tasks with repository-backed state.

**Architecture:** Compose-independent triage contracts map through repository interfaces to a four-table Room database. Typed repository results distinguish empty state, missing records, and SQLite failures; a separate lifecycle repository owns retention and notification-derived erasure.

**Tech Stack:** Kotlin 2.2.10, Room 2.8.4, KSP, Hilt 2.59.2, coroutines/Flow 1.8.0, Compose Material 3, JUnit 4, AndroidX instrumented tests.

**Spec:** `docs/superpowers/specs/2026-08-31-phase-1-durable-local-data-design.md`

## Global Constraints

- Keep notification ingestion, permission, allowlist, model triage, and source-notification actions out of scope.
- Persist no raw notification title, text, body, payload, extras, or prompt.
- Preserve `android:allowBackup="false"`; add no backup or transfer include rule.
- Use tests first for each behavior and watch the focused test fail for the intended reason.
- Translate only known SQLite failures; do not catch cancellation or unknown failures.
- Keep manual items when deleting notification-derived data; delete all notification-derived children and all user rules.
- Preserve the Bouncy Castle buildscript constraint and verification task.

---

### Task 1: Domain contracts and input invariants

**Files:**
- Create: `app/src/main/java/thwiply/elopenmike/com/domain/triage/TriageModels.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/domain/triage/RepositoryResult.kt`
- Test: `app/src/test/java/thwiply/elopenmike/com/domain/triage/TriageModelsTest.kt`

**Interfaces:**
- Produces: `TriageItem`, `TriageDecision`, `SourceReference`, `UserCorrection`, `UserRule`, their closed enums, `TriageRecord`, and `RepositoryResult<T>`.

- [x] Write focused tests that reject blank/oversized display fields, invalid notification provenance, negative timestamps, mismatched correction/rule links, and accept a minimal manual item.
- [x] Run `./gradlew testDebugUnitTest --tests '*TriageModelsTest'` and confirm compilation/test failure because the contracts do not exist.
- [x] Implement immutable data classes and allowlisted enums with constructor invariants and named limits.
- [x] Re-run the focused test and refactor only after green.

### Task 2: Room dependency configuration and schema v1

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/local/entity/TriageEntities.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/local/dao/TriageDao.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/local/dao/CorrectionDao.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/local/dao/UserRuleDao.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/local/ThwiplyDatabase.kt`
- Test: `app/src/androidTest/java/thwiply/elopenmike/com/data/local/ThwiplyDatabaseTest.kt`
- Generate and commit: `app/schemas/thwiply.elopenmike.com.data.local.ThwiplyDatabase/1.json`

**Interfaces:**
- Consumes: Task 1 domain values.
- Produces: v1 tables `triage_items`, `triage_decisions`, `user_corrections`, `user_rules`; DAO row counts and transactional item/decision writes.

- [ ] Add Room 2.8.4 runtime, KTX, compiler, testing, and schema plugin aliases; add coroutines-test for JVM/instrumented tests and expose `app/schemas` to migration tests.
- [ ] Write an instrumented test that creates an on-disk database, inserts an item plus decision, closes/reopens it, then updates, completes, and deletes it while asserting each durable state.
- [ ] Run only `ThwiplyDatabaseTest` and confirm failure because the schema/DAOs do not exist.
- [ ] Implement v1 entities with foreign keys/indices, DAOs with affected-row return values, and a database class with schema export enabled.
- [ ] Run the focused database test to green and retain the generated v1 schema JSON.

### Task 3: Repository contracts and implementations

**Files:**
- Create: `app/src/main/java/thwiply/elopenmike/com/domain/triage/TriageRepository.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/domain/triage/CorrectionRepository.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/domain/triage/UserRuleRepository.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/repository/RoomTriageRepository.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/repository/RoomCorrectionRepository.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/repository/RoomUserRuleRepository.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/repository/TriageMappers.kt`
- Test: `app/src/test/java/thwiply/elopenmike/com/data/repository/RoomRepositoriesTest.kt`
- Test: extend `ThwiplyDatabaseTest.kt`

**Interfaces:**
- Produces: repository `Flow<RepositoryResult<List<...>>>` observers and typed create/update/complete/delete/correction/rule operations.

- [ ] Write JVM repository tests proving row mapping, no-row failures, exact SQLite failure translation with the original cause, and no translation of unknown exceptions.
- [ ] Run the focused repository test and confirm failure because implementations are absent.
- [ ] Implement mappers and repositories using typed `catch (SQLiteException)` blocks and affected-row checks.
- [ ] Add real-database tests for atomic item/decision rollback, correction cascade, and rule CRUD; run focused JVM and instrumented tests to green.

### Task 4: Schema v2 migration, retention, and privacy erasure

**Files:**
- Modify: `TriageEntities.kt`
- Modify: `TriageDao.kt`
- Modify: `ThwiplyDatabase.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/domain/triage/NotificationDataLifecycleRepository.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/data/repository/RoomNotificationDataLifecycleRepository.kt`
- Test: `app/src/androidTest/java/thwiply/elopenmike/com/data/local/ThwiplyMigrationTest.kt`
- Test: extend `ThwiplyDatabaseTest.kt`
- Generate and commit: `app/schemas/thwiply.elopenmike.com.data.local.ThwiplyDatabase/2.json`

**Interfaces:**
- Produces: `MIGRATION_1_2`, `DEFAULT_NOTIFICATION_RETENTION_MILLIS`, `purgeExpiredNotificationData(nowEpochMillis)`, and `deleteAllNotificationDataAndRules()`.

- [ ] Write a migration test that seeds all four v1 tables, migrates to v2, validates the exported schema, verifies every row/value survived, and verifies a notification expiry was backfilled while manual expiry remains null.
- [ ] Run the migration test and confirm it fails because schema v2/migration do not exist.
- [ ] Add nullable `retention_expires_at_epoch_millis`, the manual migration, expiry assignment for new notification records, and no destructive fallback.
- [ ] Write failing database tests for cutoff boundaries and delete-all; include notification/manual items, decisions, corrections, and rules.
- [ ] Implement transactional purge/delete-all and prove expired notification records/rules are removed while manual records remain.

### Task 5: Hilt wiring

**Files:**
- Create: `app/src/main/java/thwiply/elopenmike/com/di/DataModule.kt`
- Test: compile generated Hilt sources through `testDebugUnitTest` and `assembleDebug`.

**Interfaces:**
- Produces: singleton `ThwiplyDatabase`, its DAOs, and bindings for all four repository interfaces.

- [ ] Add providers/bindings and register `MIGRATION_1_2` in the production database builder.
- [ ] Run `testDebugUnitTest` and `assembleDebug`; treat any Hilt graph failure as the focused red signal, correct only wiring, and rerun to green.

### Task 6: Repository-backed Today state

**Files:**
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/today/TaskItem.kt`
- Create: `app/src/main/java/thwiply/elopenmike/com/ui/today/TodayUiState.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/thwiply/elopenmike/com/ui/today/TodayScreen.kt`
- Test: `app/src/test/java/thwiply/elopenmike/com/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `TriageRepository` and `RepositoryResult`.
- Produces: `StateFlow<TodayUiState>`, durable quick-add, complete/uncomplete, and delete actions.

- [ ] Write ViewModel tests for initial loading, repository success with no rows → `Empty`, mapped content, repository read failure → `StorageError`, and create/complete/delete result handling.
- [ ] Run only `TodayViewModelTest` and confirm failure against the sample-backed ViewModel.
- [ ] Inject `TriageRepository`, map domain records to presentation `TaskItem`, and implement repository actions without silent fallback.
- [ ] Update Compose rendering for Loading/Empty/Content/StorageError, remove sample/snippet presentation, and retain truthful filters only.
- [ ] Re-run ViewModel tests and `assembleDebug` to green.

### Task 7: Documentation and roadmap evidence

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Modify: this plan's checkboxes as tasks complete.

- [ ] Update README features/architecture/privacy copy to describe durable manual Today data, Room, retention/erasure, and the continued absence of notification ingestion.
- [ ] Mark only delivered Phase 1 scope complete in ROADMAP, record exact test classes/commands as evidence, keep Phase 2 blocked only by whatever prerequisites genuinely remain, and retain non-promises.
- [ ] Search docs for claims that Today uses sample data or that app-layer database encryption exists; correct only directly related stale claims.

### Task 8: Validation, independent review loop, and delivery

**Files:**
- Review all changed files and generated Room schema JSON.

- [ ] Run targeted JVM tests, targeted instrumented database/migration tests, `verifyBuildscriptBouncyCastle`, `test lint assembleDebug`, and `connectedDebugAndroidTest` with exact outcomes recorded.
- [ ] Inspect `git diff --check`, full diff, backup manifest, Room schemas, dependency graph, secrets/debug residue, and generated/untracked files.
- [ ] Dispatch read-only GPT-5.6 Terra and GPT-5.6 Luna reviewers in parallel with the full requirements, roadmap, final diff range, and fresh test evidence.
- [ ] Reconcile every concrete finding, add a failing regression test before each valid fix, rerun relevant validation, and document rejected findings with evidence.
- [ ] Send the same updated revision/evidence to both reviewers and repeat until each explicitly reports no actionable feedback on that revision.
- [ ] Run final validation again, commit with repository conventions, push `codex/phase-1-durable-local-data`, and create a non-draft PR with the required architecture/privacy/migration/test/review/limitation/roadmap sections.
