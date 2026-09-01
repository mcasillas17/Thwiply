# Optimized Alpha Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish signed, minified, per-ABI alpha APKs with deterministic versions, checksums, and an enforced arm64 size budget.

**Architecture:** Gradle produces unsigned minified alpha APKs for one requested ABI at a time. GitHub Actions signs and verifies the outputs with a persistent secret key, applies a reusable size verifier, and publishes phone and emulator artifacts separately.

**Tech Stack:** Android Gradle Plugin 9.2.1, Kotlin DSL, R8, Bash, GitHub Actions, Android `apksigner`

---

### Task 1: Add the APK size verifier

**Files:**
- Create: `scripts/check-apk-size.sh`
- Create: `scripts/test-check-apk-size.sh`

- [ ] **Step 1: Write failing shell tests**

Cover a file below the limit, a file exactly at the limit, an oversized file,
a missing file, an empty file, and a non-numeric limit. Each failure must assert
the verifier's nonzero status and diagnostic text.

- [ ] **Step 2: Run the tests and confirm the verifier is missing**

Run: `bash scripts/test-check-apk-size.sh`

Expected: nonzero exit because `scripts/check-apk-size.sh` does not exist.

- [ ] **Step 3: Implement the verifier**

The interface is:

```bash
scripts/check-apk-size.sh <apk-path> <maximum-bytes>
```

It must use `wc -c`, reject invalid arguments before measuring, print measured
and maximum bytes, and fail only when the artifact is invalid or over budget.

- [ ] **Step 4: Run the shell tests**

Run: `bash scripts/test-check-apk-size.sh`

Expected: `6 passed, 0 failed`.

### Task 2: Add deterministic alpha build inputs

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add failing configuration checks**

Run:

```bash
./gradlew :app:assembleAlpha -Pthwiply.abi=mips
./gradlew :app:assembleAlpha -Pthwiply.versionCode=zero
```

Expected before implementation: `assembleAlpha` is unknown.

- [ ] **Step 2: Add validated project properties**

Read `thwiply.abi`, `thwiply.versionCode`, and `thwiply.versionName` through
Gradle providers. Accept only `arm64-v8a` and `x86_64`; require a positive
integer version code and a nonblank version name when supplied.

- [ ] **Step 3: Add the alpha build type**

Create `alpha` from `release`, set it non-debuggable, enable minification and
resource shrinking, retain the optimized default rules, and use the release
fallback for dependencies. Do not configure signing in Gradle.

- [ ] **Step 4: Confirm invalid inputs fail clearly**

Re-run both invalid commands and assert their messages name the invalid
property. Run a valid arm64 assembly with:

```bash
./gradlew :app:assembleAlpha \
  -Pthwiply.abi=arm64-v8a \
  -Pthwiply.versionCode=100 \
  -Pthwiply.versionName=1.0.0-alpha.test
```

Expected: `app/build/outputs/apk/alpha/app-alpha-unsigned.apk`.

### Task 3: Enforce optimized alpha builds in CI

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Preserve existing validation**

Keep tests, lint, and debug assembly. Add `verifyBuildscriptBouncyCastle` so CI
continues enforcing the build-tool dependency constraint.

- [ ] **Step 2: Build and budget the arm64 alpha**

Build `assembleAlpha` with arm64, version code `1`, and version name
`ci-alpha`. Run:

```bash
bash scripts/check-apk-size.sh \
  app/build/outputs/apk/alpha/app-alpha-unsigned.apk \
  33554432
```

- [ ] **Step 3: Validate workflow syntax and local equivalent**

Run the exact Gradle and size commands locally. Expected: all commands exit 0.

### Task 4: Publish signed per-ABI prereleases

**Files:**
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Validate tag and main ancestry**

Use a full checkout. Require an alpha SemVer tag and require the tagged commit
to be an ancestor of `origin/main`.

- [ ] **Step 2: Derive deterministic version metadata**

Set `VERSION_NAME=${TAG_NAME#v}` and
`VERSION_CODE=$(git rev-list --count HEAD)`. Reject non-positive codes.

- [ ] **Step 3: Build one ABI at a time**

Run tests/lint with the arm64 build, stage its unsigned APK in `$RUNNER_TEMP`,
clean, build x86_64 with identical version metadata, and stage it separately.

- [ ] **Step 4: Sign without exposing key material**

Decode `ALPHA_KEYSTORE_BASE64` into `$RUNNER_TEMP`, locate the newest Android
build-tools `apksigner`, sign each APK using environment-provided passwords,
verify both signatures with `--print-certs`, and remove the keystore with a
trap.

- [ ] **Step 5: Enforce size and publish checksums**

Apply the 33,554,432-byte limit to the signed arm64 APK, run `sha256sum` for
both APKs into `SHA256SUMS`, and publish all three assets in one prerelease.

- [ ] **Step 6: Correct release notes**

Explain the arm64 phone asset, x86_64 emulator asset, external model download,
one-time uninstall from pre-persistent-key alphas, and future in-place updates.

### Task 5: Validate artifacts and signing locally

**Files:**
- No committed files

- [ ] **Step 1: Build both alpha ABIs**

Build arm64 and x86_64 separately with the same test version metadata and copy
each output before cleaning.

- [ ] **Step 2: Inspect ABI and manifest contents**

Use `unzip -Z1` to assert each APK contains its requested ABI and not the other.
Use `aapt dump badging` to assert version code `100` and version name
`1.0.0-alpha.test`.

- [ ] **Step 3: Exercise signing**

Generate a temporary PKCS12 key, sign both staged APKs with `apksigner`, verify
their signatures, and delete the key. No private key enters the repository.

- [ ] **Step 4: Run regression validation**

Run:

```bash
./gradlew verifyBuildscriptBouncyCastle test lint assembleDebug \
  --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

### Task 6: Converge independent reviews

**Files:**
- Modify only files implicated by actionable findings

- [ ] **Step 1: Dispatch three reviewers in parallel**

Use Claude Opus 4.8, Gemini 3.7 Flash, and Grok 4.6. Give each the complete
requirements, actual diff, measurements, and validation evidence. Require code
gap, correctness, security, and performance review.

- [ ] **Step 2: Fix actionable findings**

Verify each finding against the repository, add or update a failing regression
check, implement the smallest complete fix, and rerun affected validation.

- [ ] **Step 3: Re-review with the same agents**

Send the updated diff and evidence to all three reviewers. Repeat until every
reviewer explicitly reports no remaining actionable feedback.

### Task 7: Update release documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-01-optimized-alpha-distribution-design.md`

- [ ] **Step 1: Document artifact selection and installation**

Explain arm64 phone versus x86_64 emulator APKs, checksum verification, and the
external model download.

- [ ] **Step 2: Document signing transition and size policy**

State the one-time uninstall boundary, future update behavior, and enforced
32 MiB arm64 budget using the measured final artifact size.

- [ ] **Step 3: Verify documentation against outputs**

Compare every documented filename, command, byte limit, and measured size to
the final local artifacts and workflow.

### Task 8: Commit and open the pull request

**Files:**
- All files above

- [ ] **Step 1: Run final validation**

Run shell tests, Gradle regression validation, both alpha builds, APK inspection,
signature verification, `git diff --check`, and a secret-pattern scan.

- [ ] **Step 2: Commit and push**

Commit the reviewed implementation with the required Copilot trailers and push
the session branch without force.

- [ ] **Step 3: Open the pull request**

Create a PR against `main` summarizing measured before/after size, artifacts,
signing and version behavior, checksums, validation, and three-reviewer
convergence.
