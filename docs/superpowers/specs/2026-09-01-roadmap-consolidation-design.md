# Roadmap Consolidation Design

**Date:** 2026-09-01

## Goal

Make `docs/ROADMAP.md` the only authoritative statement of Thwiply's current
status, delivery order, exit gates, and deferred work.

## Current problem

The repository has one file named as a roadmap, but roadmap status is repeated
in the README as a second checklist. Historical implementation plans also
contain unchecked task boxes after their work has shipped. Readers can mistake
those execution records for current product status.

The canonical roadmap is accurate about Phases 0 and 1 being complete and
Phase 2 being next, but it does not make prerequisite engineering debt or the
next executable work packages prominent enough.

## Considered approaches

### 1. Canonical roadmap with linked historical records

Keep `docs/ROADMAP.md` authoritative, replace the README checklist with a short
status summary and link, and add an execution queue plus documentation rules to
the roadmap. Keep specs and plans as historical records.

This avoids duplicated status while preserving useful design rationale and is
the selected approach.

### 2. Generate every roadmap view from structured data

Store phases in YAML or JSON and generate both README and roadmap output.

This would prevent drift mechanically, but it adds tooling and maintenance for
a small repository without improving the product.

### 3. Archive or delete completed plans

Move or remove old plans so unchecked boxes cannot be mistaken for current
work.

This reduces visual noise but breaks stable links and discards useful context.
Historical records are better retained and clearly labeled.

## Design

### Canonical roadmap

`docs/ROADMAP.md` will contain:

- the product promise and non-promises;
- a concise current-state table;
- a dependency-ordered execution queue;
- phase scope and exit gates;
- cross-cutting privacy, reliability, security, accessibility, and release
  requirements;
- deferred, evidence-led follow-ups; and
- links to supporting historical plans and specs.

The execution queue will separate immediate foundation debt from Phase 2
delivery. It will not mark work complete unless code and tests in the
repository support the claim.

### README

The README will state only the current phase and next phase, then link to the
canonical roadmap. It will not reproduce roadmap checkboxes.

### Historical records

Create `docs/superpowers/README.md` to explain that:

- `specs/` records accepted designs;
- `plans/` records task-level execution instructions;
- unchecked boxes in a historical plan do not define repository status; and
- `docs/ROADMAP.md` is the sole source for current product status.

Existing plans and specs remain in place to preserve links and history.

## Consolidated execution order

The roadmap will order work as follows:

1. Add Android instrumentation tests to CI for the Phase 1 persistence,
   migration, and backup gates.
2. Harden the Android shell and local-model lifecycle before notification
   ingestion increases background and privacy-sensitive work.
3. Define Phase 2 consent, normalization, queue, and service contracts.
4. Implement durable consent and an empty-by-default per-app allowlist.
5. Add notification-access and app-selection UX.
6. Register the bounded notification listener.
7. Normalize and deduplicate bounded notification events.
8. Add the backpressured ingestion queue and local diagnostics.
9. Complete Phase 2 automated and physical-device validation.
10. Implement Phase 3 deterministic rules, structured local inference, strict
    validation, and Needs Review fallback.
11. Implement Phase 4 Now/Later/Needs Review UX, explanations, corrections,
    and editable rules.
12. Run pilot and release-quality gates before enabling deferred capabilities.

## Validation

- Search Markdown files to confirm only `docs/ROADMAP.md` contains a roadmap
  checklist or phase-status source.
- Verify every linked plan, spec, and roadmap path exists.
- Run the repository's existing documentation-adjacent shell checks and
  Android build gates.
- Inspect the final diff for status claims unsupported by current code.
