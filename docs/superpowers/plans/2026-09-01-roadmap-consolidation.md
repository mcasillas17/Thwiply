# Roadmap Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `docs/ROADMAP.md` the repository's sole authoritative roadmap while preserving historical implementation records.

**Architecture:** Product status, ordering, exit gates, and deferred work live only in `docs/ROADMAP.md`. The README exposes a concise current/next summary and link. `docs/superpowers/README.md` distinguishes historical specs and plans from live roadmap status.

**Tech Stack:** Markdown, Git, ripgrep, existing Gradle and shell validation.

---

### Task 1: Consolidate roadmap status and execution order

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Add a current execution queue after the current-state table**

Add a `## Current execution queue` section that:

- labels foundation debt separately from Phase 2 delivery;
- orders instrumentation CI, Android shell/model hardening, Phase 2 design,
  consent, listener, normalization, queue, validation, Phase 3, Phase 4, and
  pilot/release gates;
- links each group to its owning roadmap phase; and
- states that deferred follow-ups remain excluded until evidence justifies
  them.

- [ ] **Step 2: Add missing cross-cutting requirements**

Extend the roadmap with:

```markdown
### Android quality

- State collection is lifecycle-aware.
- Target SDK 36 edge-to-edge and window-inset behavior is tested.
- Interactive controls expose semantics and meet accessible touch targets.
- User-facing strings are Android resources.
- Supported phone, tablet, foldable, and window-size layouts remain usable.

### Delivery

- JVM, lint, build, workflow, and Android instrumentation gates run in CI.
- Release artifacts remain signed, per-ABI, checksummed, and size-bounded.
- Roadmap completion claims cite code and fresh automated or device evidence.
```

- [ ] **Step 3: Add supporting-record links and clarify authority**

Replace the final roadmap-maintenance sentence with a section that links:

- `docs/superpowers/specs/`
- `docs/superpowers/plans/`
- `docs/RELEASING.md`

State explicitly that those are supporting records and never override current
status in `docs/ROADMAP.md`.

- [ ] **Step 4: Verify roadmap structure**

Run:

```bash
rg -n '^## |^### |\\*\\*Status:\\*\\*' docs/ROADMAP.md
```

Expected: one current-state source, one execution queue, Phases 0–4, evidence-led
follow-ups, cross-cutting requirements, and supporting records.

- [ ] **Step 5: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: consolidate roadmap execution order"
```

### Task 2: Remove the duplicate README roadmap

**Files:**
- Modify: `README.md:107-129`

- [ ] **Step 1: Replace duplicate checklists with a canonical summary**

Replace the roadmap checklists with:

```markdown
## 🗺️ Roadmap

The canonical product roadmap, current status, dependency-ordered execution
queue, launch gates, privacy requirements, and non-goals live in
[`docs/ROADMAP.md`](docs/ROADMAP.md).

**Current:** The secure alpha and durable local data foundation are complete.

**Next:** Phase 2 adds explicit notification-access consent, an empty-by-default
per-app allowlist, and bounded notification ingestion. Structured local triage
and the trustworthy Now / Later / Needs Review experience follow in Phases 3
and 4.
```

- [ ] **Step 2: Verify the README no longer owns task status**

Run:

```bash
if rg -n '\\[[ x]\\].*(Notification|triage|Roadmap|Listener|consent)' README.md; then
  exit 1
fi
```

Expected: exit 0 with no matches.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: point README to canonical roadmap"
```

### Task 3: Distinguish historical execution records

**Files:**
- Create: `docs/superpowers/README.md`

- [ ] **Step 1: Create the documentation index**

Create:

```markdown
# Design and Implementation Records

This directory preserves the decisions and task instructions used to deliver
Thwiply changes.

- [`specs/`](specs/) contains accepted point-in-time designs.
- [`plans/`](plans/) contains point-in-time implementation instructions.
- [`../ROADMAP.md`](../ROADMAP.md) is the sole source of truth for current
  product status, ordering, exit gates, and deferred work.

Plans are historical records, not live trackers. An unchecked box can describe
an execution step that was completed in a later commit or pull request. Do not
infer current repository status from plan checkboxes; verify the canonical
roadmap and implementation instead.
```

- [ ] **Step 2: Verify every documentation link**

Run:

```bash
test -f docs/ROADMAP.md
test -f docs/RELEASING.md
test -d docs/superpowers/specs
test -d docs/superpowers/plans
```

Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/README.md
git commit -m "docs: label implementation plans as historical"
```

### Task 4: Validate and open the pull request

**Files:**
- Verify: `README.md`
- Verify: `docs/ROADMAP.md`
- Verify: `docs/superpowers/README.md`
- Verify: `docs/superpowers/specs/2026-09-01-roadmap-consolidation-design.md`
- Verify: `docs/superpowers/plans/2026-09-01-roadmap-consolidation.md`

- [ ] **Step 1: Confirm there is one live roadmap source**

Run:

```bash
test "$(find . -iname '*roadmap*' -type f \
  ! -path './.git/*' \
  ! -path './docs/superpowers/specs/*' \
  ! -path './docs/superpowers/plans/*' | sort)" = "./docs/ROADMAP.md"
rg -n 'sole source of truth|canonical product roadmap' \
  README.md docs/ROADMAP.md docs/superpowers/README.md
```

Expected: `docs/ROADMAP.md` is the only live roadmap file; authority references
all point to it.

- [ ] **Step 2: Run repository validation**

Run:

```bash
./gradlew verifyBuildscriptBouncyCastle test lint assembleDebug --no-daemon
bash scripts/test-check-apk-size.sh
bash scripts/test-release-workflows.sh
```

Expected: Gradle exits 0, APK-size checks report `6 passed, 0 failed`, and
release-workflow checks report `20 passed, 0 failed`.

- [ ] **Step 3: Review the final diff**

Run:

```bash
git diff origin/main...HEAD --check
git diff origin/main...HEAD -- README.md docs/
```

Expected: no whitespace errors; only the consolidation design, plan, roadmap,
README, and documentation index changed.

- [ ] **Step 4: Push and create the pull request**

```bash
git push -u origin HEAD
```

Open a non-draft PR titled `docs: consolidate the Thwiply roadmap` describing:

- `docs/ROADMAP.md` as the single authority;
- removal of README checklist duplication;
- historical-plan labeling;
- the new dependency-ordered execution queue; and
- validation evidence.
