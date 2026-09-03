# Roadmap Task Breakdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the high-level Thwiply execution queue with a stable,
dependency-ordered, implementation-ready product backlog.

**Architecture:** `docs/ROADMAP.md` remains the only live source for status,
ordering, gates, and deferrals. This point-in-time plan and its companion
design preserve the research and editing method without overriding the
canonical roadmap.

**Tech Stack:** GitHub-flavored Markdown, Android/Kotlin repository evidence,
Git and ripgrep verification

---

### Task 1: Correct current status and define task semantics

**Files:**
- Modify: `docs/ROADMAP.md:1-45`
- Reference: `docs/superpowers/specs/2026-09-03-roadmap-task-breakdown-design.md`

- [ ] **Step 1: Update roadmap metadata**

Set the last-updated date to 2026-09-03 and state that Phase 0 and Phase 1
delivered scope is complete while hardening evidence remains open.

- [ ] **Step 2: Correct current-state claims**

Qualify model restoration, Lab metrics/lifecycle, effective categories,
correction/rule application, retention maintenance, minified-alpha proof, and
Android quality against the exact repository evidence.

- [ ] **Step 3: Define task status and dependency rules**

Document `Complete`, `Ready`, `Blocked`, and `Deferred`, and require a task to
cite fresh automated or physical-device evidence before becoming complete.

- [ ] **Step 4: Verify the status block**

Run:

```bash
test "$(sed -n '1,10p' docs/ROADMAP.md \
  | grep -Ec '^[*][*]Status:[*][*] ')" = "1"
test "$(sed -n '1,10p' docs/ROADMAP.md \
  | grep -Ec '^[*][*]Last updated:[*][*] 2026-09-03$')" = "1"
test "$(grep -Ec '^## Current state$' docs/ROADMAP.md)" = "1"
test "$(grep -Ec '^## Task model$' docs/ROADMAP.md)" = "1"
```

Expected: all four assertions exit with status 0.

### Task 2: Replace the ordinal queue with stable foundation tasks

**Files:**
- Modify: `docs/ROADMAP.md`
- Reference: `.github/workflows/ci.yml`
- Reference: `app/src/main/AndroidManifest.xml`
- Reference: `app/src/main/java/thwiply/elopenmike/com/MainActivity.kt`
- Reference: `app/src/main/java/thwiply/elopenmike/com/llm/`
- Reference: `app/src/main/java/thwiply/elopenmike/com/ui/`

- [ ] **Step 1: Add the immediate execution view**

List the parallel-ready foundation tracks and the gate that prevents Phase 2
code from beginning before its contracts are accepted.

- [ ] **Step 2: Add stable `FND` tasks**

Cover instrumentation CI, model-optional navigation, lifecycle-aware state,
edge-to-edge, resources, accessibility, preferences and dead-state cleanup,
model integrity, engine cancellation/release, download hardening, honest Lab
metrics, retention maintenance, minified-alpha release proof, and
license/version/toolchain truth.

- [ ] **Step 3: Give every task an executable contract**

For each `FND` row, include outcome, likely components, dependencies, and
observable completion evidence. Leave exact dependency versions and measured
constants to the owning implementation plan.

- [ ] **Step 4: Verify foundation coverage**

Run:

```bash
test "$(grep -Ec '^[|] FND-[0-9]{2} [|]' docs/ROADMAP.md)" = "14"
```

Expected: every foundation task has one stable row and no duplicate ID.

### Task 3: Add Phase 2, Phase 3, and Phase 4 implementation backlogs

**Files:**
- Modify: `docs/ROADMAP.md`
- Reference: `app/src/main/java/thwiply/elopenmike/com/domain/triage/TriageModels.kt`
- Reference: `app/src/main/java/thwiply/elopenmike/com/data/local/entity/TriageEntities.kt`
- Reference: `app/src/main/java/thwiply/elopenmike/com/data/local/dao/TriageDao.kt`

- [ ] **Step 1: Add the Phase 2 design gate and task table**

Cover consent state, the empty allowlist, app discovery, listener registration,
normalization, typed queue outcomes, idempotency/update handling, recovery,
content-free diagnostics, and automated plus physical-device evidence.

- [ ] **Step 2: Add the Phase 3 design gate and task table**

Cover privacy-minimal provenance, base decisions separated from append-only
corrections, effective-category projection, versioned source-update
reprocessing, deterministic filters and rules,
prompt boundaries, strict one-pass schema validation, single-engine
coordination, Needs Review fallback, atomic persistence, and the evaluation
corpus.

- [ ] **Step 3: Add the Phase 4 task table**

Cover Now/Later/Needs Review, explanations, item-only and explicit
apply-to-future correction actions, learned rules, rule management, sample
onboarding, truthful Settings, diagnostic
export, accessibility, adaptive layouts, and source-notification invariants.

- [ ] **Step 4: Verify task fields and IDs**

Run:

```bash
for expected in P2:13 P3:11 P4:11; do
  prefix="${expected%%:*}"
  count="${expected##*:}"
  test "$(grep -Ec "^[|] ${prefix}-[0-9]{2} [|]" docs/ROADMAP.md)" = "$count"
done

awk -F'|' '
/^[|] (FND|P2|P3|P4|PILOT)-[0-9][0-9] / {
  id=$2
  deps=$5
  gsub(/^ +| +$/, "", id)
  gsub(/^ +| +$/, "", deps)
  order[id]=NR
  deptext[id]=deps
}
END {
  failed=0
  for (id in deptext) {
    text=deptext[id]
    while (match(text, /(FND|P2|P3|P4|PILOT)-[0-9][0-9]/)) {
      dep=substr(text, RSTART, RLENGTH)
      if (!(dep in order) || order[dep] >= order[id]) {
        print id " has an invalid or forward dependency: " dep
        failed=1
      }
      text=substr(text, RSTART + RLENGTH)
    }
  }
  exit failed
}' docs/ROADMAP.md
```

Expected: unique, dependency-ordered rows for all three phases.

### Task 4: Add pilot gates and preserve explicit deferrals

**Files:**
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Add pilot tasks**

Cover local-only aggregate measurement, device protocols, empirical threshold
selection, thermal/battery safety, privacy/security review, pilot feedback,
and a signed alpha release gate.

- [ ] **Step 2: Give deferred capabilities stable IDs**

Keep classifier routing, sender affinity, device-tier models, productized
battery scheduling, screenshot intake, notification snoozing, production Play
distribution, cloud/accounts/analytics, shade mutation, and arbitrary models
deferred.

- [ ] **Step 3: Retain cross-cutting invariants**

Ensure privacy, reliability, security, Android quality, delivery, and roadmap
maintenance requirements still apply to every task.

- [ ] **Step 4: Verify no speculative thresholds were accepted**

Run:

```bash
if grep -En '6 GB|2500ms|85%|95%|capacity = 64|5-minute TTL' docs/ROADMAP.md; then
  exit 1
fi
```

Expected: no output and exit status 0.

### Task 5: Verify and commit the roadmap update

**Files:**
- Verify: `docs/ROADMAP.md`
- Verify: `README.md`
- Verify: `docs/superpowers/README.md`
- Verify: `docs/superpowers/specs/2026-09-03-roadmap-task-breakdown-design.md`
- Verify: `docs/superpowers/plans/2026-09-03-roadmap-task-breakdown.md`

- [ ] **Step 1: Confirm canonical authority**

Run:

```bash
test -f docs/ROADMAP.md
grep -En 'sole source of truth|canonical product roadmap' \
  README.md docs/ROADMAP.md docs/superpowers/README.md
```

Expected: all status references point to `docs/ROADMAP.md`.

- [ ] **Step 2: Check IDs, links, placeholders, and whitespace**

Run:

```bash
test "$(awk -F'|' \
  '/^[|] (FND|P2|P3|P4|PILOT|DEF)-[0-9][0-9] / {
    gsub(/^ +| +$/, "", $2)
    print $2
  }' docs/ROADMAP.md | sort | uniq -d | wc -l | tr -d ' ')" = "0"
if grep -En 'TBD|implement later|fill in details' docs/ROADMAP.md; then
  exit 1
fi
git diff --check
```

Expected: no duplicate task IDs, no placeholders, and no whitespace errors.

- [ ] **Step 3: Stage and inspect the final diff**

Run:

```bash
git add \
  docs/ROADMAP.md \
  docs/superpowers/specs/2026-09-03-roadmap-task-breakdown-design.md \
  docs/superpowers/plans/2026-09-03-roadmap-task-breakdown.md
git diff --cached --check
git --no-pager diff --cached -- \
  docs/ROADMAP.md \
  docs/superpowers/specs/2026-09-03-roadmap-task-breakdown-design.md \
  docs/superpowers/plans/2026-09-03-roadmap-task-breakdown.md
```

Expected: only the accepted roadmap expansion and its historical records.

- [ ] **Step 4: Commit**

```bash
git commit -m "docs: expand the Thwiply implementation roadmap"
```
