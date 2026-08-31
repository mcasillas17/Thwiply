# Dependabot Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Dependabot alerts and security-update pull requests for every detected ecosystem, and add Gradle dependency submission because GitHub reports no Gradle manifest or submitted Gradle snapshot.

**Architecture:** Repository settings enable alerts and automated security fixes. A security-only `dependabot.yml` covers Gradle and GitHub Actions, while a dedicated default-branch workflow resolves and submits Gradle's complete dependency graph with immutable action pins and least-privilege permissions.

**Tech Stack:** GitHub Dependabot, GitHub Actions, Gradle 9.4.1, JDK 21, GitHub REST and GraphQL APIs, Ruby/Psych YAML parser

---

## File structure

- Create `.github/dependabot.yml`: define security-update coverage for the Gradle and GitHub Actions ecosystems while suppressing routine version-update pull requests.
- Create `.github/workflows/dependency-submission.yml`: resolve the Gradle graph on `main` and submit it to GitHub's dependency submission API.
- Preserve `docs/superpowers/specs/2026-08-30-dependabot-design.md`: the approved design and validation contract.

### Task 1: Configure all detected ecosystems

**Files:**
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Verify the configuration does not already exist**

Run:

```bash
test ! -e .github/dependabot.yml
```

Expected: exit 0 because no Dependabot configuration exists.

- [ ] **Step 2: Create the security-only Dependabot configuration**

Create `.github/dependabot.yml` with:

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 0

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 0
```

- [ ] **Step 3: Parse and assert the configuration**

Run:

```bash
ruby -e '
require "yaml"
config = YAML.safe_load_file(".github/dependabot.yml")
abort "wrong schema version" unless config["version"] == 2
updates = config.fetch("updates")
abort "wrong ecosystems" unless updates.map { |entry| entry["package-ecosystem"] }.sort == %w[github-actions gradle]
abort "wrong directory" unless updates.all? { |entry| entry["directory"] == "/" }
abort "routine PRs enabled" unless updates.all? { |entry| entry["open-pull-requests-limit"] == 0 }
puts "dependabot.yml: valid"
'
```

Expected: `dependabot.yml: valid`.

- [ ] **Step 4: Check formatting**

Run:

```bash
git diff --check
```

Expected: exit 0 with no output.

- [ ] **Step 5: Commit the configuration**

```bash
git add .github/dependabot.yml
git commit -m "chore: configure Dependabot ecosystems" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>" \
  -m "Copilot-Session: f886cd42-d3af-4244-92ca-f284082ce74a"
```

### Task 2: Add Gradle dependency submission

**Files:**
- Create: `.github/workflows/dependency-submission.yml`

- [ ] **Step 1: Verify the workflow does not already exist**

Run:

```bash
test ! -e .github/workflows/dependency-submission.yml
```

Expected: exit 0 because no dependency-submission workflow exists.

- [ ] **Step 2: Create the dependency-submission workflow**

Create `.github/workflows/dependency-submission.yml` with:

```yaml
name: Dependency submission

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: dependency-submission-${{ github.ref }}
  cancel-in-progress: true

jobs:
  submit:
    name: Submit Gradle dependency graph
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Check out repository
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
        with:
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961
        with:
          distribution: temurin
          java-version: "21"

      - name: Generate and submit dependency graph
        uses: gradle/actions/dependency-submission@9c971963bec38e04b3d30dcc455b5382be2fdbfb
        with:
          dependency-graph: generate-and-submit
          validate-wrappers: true
          additional-arguments: --stacktrace --no-daemon
```

- [ ] **Step 3: Parse and assert the workflow**

Run:

```bash
ruby -e '
require "yaml"
YAML.parse_file(".github/workflows/dependency-submission.yml")
text = File.read(".github/workflows/dependency-submission.yml")
abort "missing contents permission" unless text.include?("contents: write")
abort "dependency action is not pinned" unless text.match?(%r{gradle/actions/dependency-submission@[0-9a-f]{40}})
abort "submission failures are being ignored" if text.include?("continue-on-error")
puts "dependency-submission.yml: valid"
'
```

Expected: `dependency-submission.yml: valid`.

- [ ] **Step 4: Confirm the action pin supports the configured inputs**

Run:

```bash
gh api \
  --method GET \
  repos/gradle/actions/contents/dependency-submission/action.yml \
  -f ref=9c971963bec38e04b3d30dcc455b5382be2fdbfb \
  --jq '.content' |
ruby -rbase64 -e '
text = Base64.decode64(STDIN.read)
%w[dependency-graph validate-wrappers additional-arguments].each do |input|
  abort "missing input: #{input}" unless text.include?("#{input}:")
end
puts "dependency-submission action inputs: valid"
'
```

Expected: `dependency-submission action inputs: valid`.

- [ ] **Step 5: Check formatting**

Run:

```bash
git diff --check
```

Expected: exit 0 with no output.

- [ ] **Step 6: Commit the workflow**

```bash
git add .github/workflows/dependency-submission.yml
git commit -m "ci: submit Gradle dependency graph" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>" \
  -m "Copilot-Session: f886cd42-d3af-4244-92ca-f284082ce74a"
```

### Task 3: Run repository checks

**Files:**
- Test: `.github/dependabot.yml`
- Test: `.github/workflows/dependency-submission.yml`
- Test: Gradle project

- [ ] **Step 1: Validate both YAML files together**

Run:

```bash
ruby -e '
require "yaml"
ARGV.each do |path|
  YAML.parse_file(path)
  puts "#{path}: valid YAML"
end
' .github/dependabot.yml .github/workflows/dependency-submission.yml
```

Expected:

```text
.github/dependabot.yml: valid YAML
.github/workflows/dependency-submission.yml: valid YAML
```

- [ ] **Step 2: Run the existing project checks**

Run:

```bash
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Confirm the worktree contains only intended changes**

Run:

```bash
git status --short
git diff --check
git log --oneline main..HEAD
```

Expected: a clean worktree, no whitespace errors, and only the design, plan, and Dependabot commits.

### Task 4: Enable repository security settings

**Files:**
- No repository files; changes GitHub repository settings.

- [ ] **Step 1: Record the disabled baseline**

Run:

```bash
gh api repos/mcasillas17/Thwiply \
  --jq '.security_and_analysis.dependabot_security_updates.status'
gh api repos/mcasillas17/Thwiply/vulnerability-alerts --include
```

Expected before enablement: `disabled`, and the alert endpoint does not return `204 No Content`.

- [ ] **Step 2: Enable Dependabot alerts**

Run:

```bash
gh api --method PUT repos/mcasillas17/Thwiply/vulnerability-alerts
```

Expected: exit 0.

- [ ] **Step 3: Enable security-update pull requests**

Run:

```bash
gh api --method PUT repos/mcasillas17/Thwiply/automated-security-fixes
```

Expected: exit 0.

- [ ] **Step 4: Verify both settings**

Run:

```bash
gh api repos/mcasillas17/Thwiply/vulnerability-alerts --include |
  sed -n '1p'
gh api repos/mcasillas17/Thwiply \
  --jq '.security_and_analysis.dependabot_security_updates.status'
gh api repos/mcasillas17/Thwiply/automated-security-fixes \
  --jq '{enabled,paused}'
```

Expected:

```text
HTTP/2.0 204 No Content
enabled
{"enabled":true,"paused":false}
```

### Task 5: Push and open the repository-change pull request

**Files:**
- No additional files.

- [ ] **Step 1: Push the feature branch**

Run:

```bash
git push --set-upstream origin mcasillas17-enable-dependabot
```

Expected: the remote branch is created and tracking is configured.

- [ ] **Step 2: Open the pull request without merging it**

Create a pull request titled `Enable Dependabot security updates` with this body:

```markdown
## Summary

- configure Dependabot security updates for Gradle and GitHub Actions
- add Gradle dependency submission because the complete Gradle graph is not visible
- keep routine version-update pull requests disabled

## Validation

- parsed the Dependabot and workflow YAML
- ran `./gradlew test lint assembleDebug --stacktrace --no-daemon`
- verified Dependabot alerts and security updates are enabled

## Post-merge

The dependency-submission workflow runs on the first push to `main`; this PR is intentionally left open and unmerged.
```

Expected: an open pull request targeting `main`.

- [ ] **Step 3: Verify the pull request remains open**

Run:

```bash
gh pr view --json number,state,mergedAt,url \
  --jq '{number,state,mergedAt,url}'
```

Expected: `state` is `OPEN` and `mergedAt` is `null`.

### Task 6: Report alert severity counts and graph state

**Files:**
- No repository files; reads GitHub security state.

- [ ] **Step 1: Count all open alerts by severity**

Run:

```bash
gh api --method GET --paginate --slurp \
  repos/mcasillas17/Thwiply/dependabot/alerts \
  -f state=open \
  -f per_page=100 \
  --jq '
    [.[][]] |
    reduce .[] as $alert (
      {critical: 0, high: 0, moderate: 0, low: 0};
      .[$alert.security_advisory.severity] += 1
    )
  '
```

Expected: a JSON object containing numeric `critical`, `high`, `moderate`, and `low` counts. An all-zero result is valid.

- [ ] **Step 2: Record dependency-graph visibility**

Run:

```bash
gh api graphql -f query='
query {
  repository(owner: "mcasillas17", name: "Thwiply") {
    dependencyGraphManifests(first: 100) {
      totalCount
      nodes {
        filename
        dependenciesCount
      }
    }
  }
}'
```

Expected before merge: the graph may still omit any Gradle manifest or submitted Gradle snapshot because the new workflow cannot run from the default branch until this unmerged pull request lands. Report that state explicitly as the complete Gradle graph not yet being visible rather than claiming the submitted graph is already visible.

- [ ] **Step 3: Confirm the pull request was not merged**

Run:

```bash
gh pr view --json state,mergedAt --jq '{state,mergedAt}'
```

Expected: the repository-change pull request remains open with `mergedAt: null`. Do not call any Dependabot alert dismissal endpoint during implementation.
