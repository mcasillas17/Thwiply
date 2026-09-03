# Roadmap Task Breakdown Design

**Date:** 2026-09-03
**Status:** Accepted for implementation
**Authority:** Point-in-time design record; `docs/ROADMAP.md` remains the only
authoritative source for current status and ordering.

## Goal

Turn the canonical roadmap from a high-level phase sequence into a
dependency-ordered, implementation-ready backlog without turning it into a
collection of vendor-specific mini-plans.

## Research process

Four reviewers independently inspected the repository, current roadmap,
historical plans, Android manifest, Gradle configuration, workflows,
production code, tests, and recent history:

- Grok 4.6
- Gemini 3.7 Flash
- Claude Opus 4.8
- the primary integrator

After the independent pass, all reviewers received the same evidence packet
and debated fourteen architecture and roadmap decisions. The final roadmap
uses the consensus where one existed and records conservative resolutions
where reviewers disagreed.

## Evidence that changes the roadmap

- Android instrumentation tests exist but do not run in CI.
- manual tasks and Settings are inaccessible until a model is installed;
- theme and capture-placeholder state are in memory only;
- Compose state collection is not lifecycle-aware;
- most user-facing text is hardcoded, and known controls miss accessible touch
  targets or state semantics;
- model restoration validates file length but not its digest;
- native model initialization can run on the main thread;
- generation cannot be stopped from the UI, and stream emissions are labeled
  as tokens without a verified token count;
- notification consent, allowlisting, listener registration, normalization,
  queueing, deduplication, and recovery are not implemented;
- the durable category is not mapped into Today presentation;
- base decisions, corrections, and rules have storage contracts but no
  end-to-end effective-decision or correction transaction;
- the shipped alpha variant is minified but has no recorded on-device
  LiteRT-LM smoke gate;
- the README declares an MIT license and links `LICENSE`, but the file is
  absent;
- notification retention cleanup can hide manual records when purge fails and
  has no best-effort inactive-app maintenance path.

## Accepted decisions

1. Use stable task IDs grouped as `FND`, `P2`, `P3`, `P4`, `PILOT`, and
   `DEF`. Every live task states an outcome, implementation boundary,
   prerequisites, and completion evidence.
2. Keep Phase 0 and Phase 1 delivered scope complete, but make open hardening
   and continuous device evidence explicit in current status.
3. Manual tasks and Settings must remain usable when the model is absent,
   downloading, invalid, removed, or failed.
4. Raw notification fields remain in a bounded memory-only queue. Enqueue
   returns a typed accepted, duplicate, overflow, or unavailable result.
   Overflow is never represented as success, and no durable raw recovery queue
   is permitted.
5. A hash of the platform notification key is the durable identity. Repeated
   delivery is idempotent; same-key updates coalesce latest-wins. Any revision
   fingerprint remains memory-only.
6. User corrections never overwrite the rule or model decision. Corrections
   are append-only, and a repository/database projection derives the effective
   category. A same-key source update may replace the base decision only
   through a versioned reprocessing transaction that preserves correction
   history and keeps the latest correction effective. Recording a correction
   and any user-approved rule is atomic.
7. Durable provenance is limited to bounded package, app label, channel ID,
   stable key hash, source time, decision origin, matched rule or
   model/schema version, and concise explanation. Sender and raw notification
   bodies are not persisted.
8. Model output gets one strict, exact closed-schema parse. Unknown keys,
   wrappers, malformed data, or out-of-range values are rejected. There is no
   regex salvage and no inference retry; invalid output becomes Needs Review.
9. One tap changes the effective category. Applying the correction to future
   notifications is a separate, explicit correction action that records the
   correction and rule together, is channel-scoped by default when a channel
   exists, and is promoted to package-wide scope only deliberately.
10. Retention cleanup runs at active lifecycle boundaries and in at most one
    best-effort periodic local cleanup per day. It performs no network or model
    work, and purge failure never hides manual data.
11. Release readiness adds license and runtime version truth plus a minified
    alpha device smoke gate. Consumer rules are inspected before speculative
    keep rules are added. Toolchain changes require incompatibility evidence.
12. Thermal, battery, quality, latency, memory, and hardware thresholds are
    not accepted before measurement. A fail-open severe-thermal state is
    designed before pilot use.
13. The Lab remains an experimental alpha surface with honest metrics,
    stop/busy behavior, and single-engine arbitration. Its pilot placement is
    decided from evidence.
14. The roadmap owns stable ordering, dependencies, gates, and non-goals.
    Per-phase specs and plans own exact constants, schemas, fixtures, vendor
    choices, and file-by-file steps.

## Dissent resolved

- Gemini and Opus favored dropping the oldest queued event while incrementing
  a counter. The design rejects that behavior because the producer can observe
  success while an older accepted event disappears. Capacity remains bounded,
  but overflow returns an explicit failure outcome.
- Grok and Opus would allow one structural output cleanup or retry. The design
  keeps the existing strict no-repair gate and bounds model cost to one pass.
- Reviewers split between immediate rule creation, repeated-correction
  suggestions, and a separate confirmation. The design requires an explicit
  user-visible rule action and a narrow default scope.
- Grok considered periodic cleanup optional. It is accepted only as
  best-effort local maintenance bounded to at most one run per day.
- Reviewers split on making the Lab developer-only. The alpha keeps it
  experimental; pilot placement remains an explicit gate.

## Roadmap structure

`docs/ROADMAP.md` will retain the product promise, non-promises, delivered phase
history, cross-cutting requirements, and evidence-led deferrals. It will add:

- precise current-state qualifications;
- task status definitions and dependency rules;
- a short immediate execution view;
- detailed foundation, Phase 2, Phase 3, Phase 4, and pilot task tables;
- stable IDs for deferred capabilities;
- explicit phase design gates and evidence requirements.

## Non-goals

- Do not implement any product task in this documentation change.
- Do not choose queue capacities, deduplication windows, quality thresholds,
  device minimums, or other constants without measurement.
- Do not promote cloud services, accounts, screenshot intake, notification
  mutation, arbitrary models, or production Play distribution.
- Do not duplicate file-by-file implementation plans in the roadmap.

## Verification

- `docs/ROADMAP.md` remains the only live roadmap source.
- Every accepted feature maps to a stable task ID or a deferred ID.
- Every live task has implementation detail, dependencies, and observable
  completion evidence.
- Current-state claims match repository symbols and workflows.
- Links resolve, Markdown headings are coherent, and whitespace checks pass.
