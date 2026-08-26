# GitHub Actions CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pull-request and main-branch CI, then publish validated debug APKs as GitHub prereleases when `v*` tags are pushed.

**Architecture:** Two independent GitHub Actions workflows keep normal CI read-only and grant release permissions only to tag-triggered runs. Both workflows provision JDK 21, validate the Gradle wrapper, run unit tests and Android lint, and assemble the debug APK; the release workflow then stages a tag-qualified asset and publishes it with GitHub CLI.

**Tech Stack:** GitHub Actions, Gradle, Android Gradle Plugin, JDK 21, GitHub CLI

---

## File Structure

- Create `.github/workflows/ci.yml`: Validate pull requests and pushes to
  `main` with read-only permissions.
- Create `.github/workflows/release.yml`: Validate `v*` tags and publish one
  debug APK as a GitHub prerelease.

No application source or Gradle configuration changes are required.

The action revisions in this plan were resolved from their upstream repositories
on 2026-08-26:

- `actions/checkout@v4`:
  `11d5960a326750d5838078e36cf38b85af677262`
- `actions/setup-java@v5`:
  `b6effb05e454b25005698d916606bdc6ffcbf961`
- `gradle/actions/setup-gradle@v4`:
  `ed408507eac070d1f99cc633dbcf757c94c7933a`

### Task 1: Add the Continuous Integration Workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Verify the CI workflow does not already exist**

Run:

```bash
test ! -e .github/workflows/ci.yml
```

Expected: exit code 0. If the file exists, stop and reconcile its behavior with
this plan instead of overwriting it.

- [ ] **Step 2: Create the CI workflow**

Create `.github/workflows/ci.yml` with exactly:

```yaml
name: CI

on:
  pull_request:
    branches:
      - main
  push:
    branches:
      - main

permissions:
  contents: read

concurrency:
  group: ci-${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: Test, lint, and build
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Check out repository
        uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262
        with:
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a

      - name: Run tests, lint, and debug build
        run: ./gradlew test lint assembleDebug --stacktrace --no-daemon
```

- [ ] **Step 3: Verify the CI workflow's security and trigger invariants**

Run:

```bash
python3 - <<'PY'
from pathlib import Path

workflow = Path(".github/workflows/ci.yml").read_text()
required = [
    "pull_request:",
    "push:",
    "contents: read",
    "persist-credentials: false",
    "java-version: \"21\"",
    "gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a",
    "./gradlew test lint assembleDebug --stacktrace --no-daemon",
]
for value in required:
    assert value in workflow, f"missing CI invariant: {value}"
assert "contents: write" not in workflow
assert "secrets." not in workflow
print("CI workflow invariants passed")
PY
```

Expected:

```text
CI workflow invariants passed
```

- [ ] **Step 4: Check formatting and inspect the complete workflow**

Run:

```bash
git diff --check
sed -n '1,220p' .github/workflows/ci.yml
```

Expected: `git diff --check` exits 0, followed by the complete workflow shown
above.

- [ ] **Step 5: Commit the CI workflow**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: validate Android builds"
```

### Task 2: Add the Tagged Prerelease Workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Verify the release workflow does not already exist**

Run:

```bash
test ! -e .github/workflows/release.yml
```

Expected: exit code 0. If the file exists, stop and reconcile its behavior with
this plan instead of overwriting it.

- [ ] **Step 2: Create the tagged release workflow**

Create `.github/workflows/release.yml` with exactly:

```yaml
name: Release debug APK

on:
  push:
    tags:
      - "v*"

permissions:
  contents: write

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

jobs:
  release:
    name: Validate and publish
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Check out tagged commit
        uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262
        with:
          persist-credentials: false

      - name: Set up JDK 21
        uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961
        with:
          distribution: temurin
          java-version: "21"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a

      - name: Run tests, lint, and debug build
        run: ./gradlew test lint assembleDebug --stacktrace --no-daemon

      - name: Stage release asset
        env:
          TAG_NAME: ${{ github.ref_name }}
        run: |
          set -euo pipefail

          if [[ ! "$TAG_NAME" =~ ^v[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
            echo "Unsupported release tag: $TAG_NAME" >&2
            exit 1
          fi

          source_apk="app/build/outputs/apk/debug/app-debug.apk"
          if [[ ! -f "$source_apk" ]]; then
            echo "Expected APK not found: $source_apk" >&2
            exit 1
          fi

          asset_path="thwiply-${TAG_NAME}-debug.apk"
          cp "$source_apk" "$asset_path"
          printf 'ASSET_PATH=%s\n' "$asset_path" >> "$GITHUB_ENV"

      - name: Create GitHub prerelease
        env:
          GH_TOKEN: ${{ github.token }}
          TAG_NAME: ${{ github.ref_name }}
        run: |
          set -euo pipefail

          notes_file="$RUNNER_TEMP/release-notes.md"
          cat > "$notes_file" <<'EOF'
          > [!WARNING]
          > This is an early debug build. Uninstall any previous CI-distributed
          > Thwiply build before installing this APK. Uninstalling removes the
          > app's local data.
          EOF

          gh release create "$TAG_NAME" \
            "$ASSET_PATH#Thwiply ${TAG_NAME} debug APK" \
            --repo "$GITHUB_REPOSITORY" \
            --title "Thwiply ${TAG_NAME}" \
            --verify-tag \
            --prerelease \
            --generate-notes \
            --notes-file "$notes_file"
```

- [ ] **Step 3: Verify the release workflow's security and publishing invariants**

Run:

```bash
python3 - <<'PY'
from pathlib import Path

workflow = Path(".github/workflows/release.yml").read_text()
required = [
    '      - "v*"',
    "contents: write",
    "persist-credentials: false",
    "java-version: \"21\"",
    "./gradlew test lint assembleDebug --stacktrace --no-daemon",
    'source_apk="app/build/outputs/apk/debug/app-debug.apk"',
    'asset_path="thwiply-${TAG_NAME}-debug.apk"',
    "gh release create",
    "--verify-tag",
    "--prerelease",
    "--generate-notes",
]
for value in required:
    assert value in workflow, f"missing release invariant: {value}"
assert "pull_request_target:" not in workflow
assert "--clobber" not in workflow
assert "secrets." not in workflow
print("Release workflow invariants passed")
PY
```

Expected:

```text
Release workflow invariants passed
```

- [ ] **Step 4: Check formatting and inspect the complete workflow**

Run:

```bash
git diff --check
sed -n '1,280p' .github/workflows/release.yml
```

Expected: `git diff --check` exits 0, followed by the complete workflow shown
above.

- [ ] **Step 5: Commit the release workflow**

```bash
git add .github/workflows/release.yml
git commit -m "ci: publish tagged debug APKs"
```

### Task 3: Validate the Integrated Workflows and Android Build

**Files:**
- Verify: `.github/workflows/ci.yml`
- Verify: `.github/workflows/release.yml`
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Confirm the required Java runtime**

Run:

```bash
java -version
```

Expected: Java 21. If Java is missing or another major version is active,
provision JDK 21 and set `JAVA_HOME` before continuing. Do not skip the Gradle
validation.

- [ ] **Step 2: Confirm the Gradle tasks exist**

Run:

```bash
./gradlew tasks --all --console=plain | grep -E '^(assembleDebug|lint|test) '
```

Expected: output includes `assembleDebug`, `lint`, and `test`.

- [ ] **Step 3: Run the complete CI validation**

Run:

```bash
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL` with no failed tests or lint errors.

- [ ] **Step 4: Verify and stage the APK exactly as the release workflow does**

Run:

```bash
set -euo pipefail
TAG_NAME="v1.0.0-plan-check"
source_apk="app/build/outputs/apk/debug/app-debug.apk"
asset_path="thwiply-${TAG_NAME}-debug.apk"
test -s "$source_apk"
cp "$source_apk" "$asset_path"
test -s "$asset_path"
rm "$asset_path"
printf 'APK staging check passed: %s\n' "$source_apk"
```

Expected:

```text
APK staging check passed: app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 5: Re-run both workflow invariant checks**

Run the Python checks from Task 1 Step 3 and Task 2 Step 3 again.

Expected:

```text
CI workflow invariants passed
Release workflow invariants passed
```

- [ ] **Step 6: Verify the final repository state**

Run:

```bash
git diff --check
git status --short --branch
git log -3 --oneline
```

Expected: no unstaged workflow changes, a clean diff check, and separate commits
for CI and tagged releases.

### Task 4: Perform the First End-to-End Prerelease

**Files:**
- Verify remotely: `.github/workflows/release.yml`
- Verify remotely: GitHub Release asset for `v1.0.0-alpha.1`

This task publishes a real Git tag and GitHub prerelease. Do not execute it
without explicit user approval, and run it only after the workflow commits are
available on the repository's default branch.

- [ ] **Step 1: Confirm the test tag is unused**

Run:

```bash
git ls-remote --exit-code --tags origin refs/tags/v1.0.0-alpha.1
```

Expected: exit code 2 and no output, meaning the tag is unused. If the command
finds the tag, stop and choose a new approved prerelease tag; never move or
reuse an existing release tag.

- [ ] **Step 2: Create and push the approved prerelease tag**

Run:

```bash
git tag -a v1.0.0-alpha.1 -m "Thwiply v1.0.0 alpha 1"
git push origin v1.0.0-alpha.1
```

Expected: GitHub accepts the new tag and starts the `Release debug APK`
workflow.

- [ ] **Step 3: Watch the release workflow**

Run:

```bash
gh run watch --exit-status "$(gh run list \
  --workflow release.yml \
  --event push \
  --branch v1.0.0-alpha.1 \
  --limit 1 \
  --json databaseId \
  --jq '.[0].databaseId')"
```

Expected: the workflow completes successfully.

- [ ] **Step 4: Verify the prerelease and asset**

Run:

```bash
gh release view v1.0.0-alpha.1 \
  --json isPrerelease,tagName,assets \
  --jq '{
    tag: .tagName,
    prerelease: .isPrerelease,
    assets: [.assets[].name]
  }'
```

Expected:

```json
{
  "tag": "v1.0.0-alpha.1",
  "prerelease": true,
  "assets": [
    "thwiply-v1.0.0-alpha.1-debug.apk"
  ]
}
```

## Verified References

- Design specification:
  `docs/superpowers/specs/2026-08-26-github-actions-ci-cd-design.md`
- GitHub workflow syntax:
  <https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax>
- `GITHUB_TOKEN` authentication:
  <https://docs.github.com/en/actions/tutorials/authenticate-with-github_token>
- GitHub CLI release creation:
  <https://cli.github.com/manual/gh_release_create>
- Android command-line builds:
  <https://developer.android.com/build/building-cmdline>
