# GitHub Actions CI/CD Design

## Goal

Add continuous integration for Thwiply and publish installable debug APKs as
GitHub prereleases from version tags.

The first version is intended for early testers, not production distribution.
Google Play publishing and production signing are explicitly deferred.

## Current Project Context

Thwiply is a single-module Android application built with Gradle, Kotlin, and
Jetpack Compose. The repository currently has:

- Unit and instrumented test source sets.
- Android lint through the Android Gradle plugin.
- Debug and release build types.
- `compileSdk` and `targetSdk` 36.
- A Gradle daemon toolchain configured for Java 21.
- No GitHub Actions workflows, release signing configuration, or publishing
  automation.

The local development environment used while designing this work does not have
a Java runtime. The implementation must therefore confirm the available Gradle
task names after provisioning JDK 21.

## Decisions

- Use two independent workflows: CI and tagged release.
- Run CI for pull requests targeting `main` and pushes to `main`.
- Require unit tests, Android lint, and a debug APK build.
- Publish when a tag matching `v*` is pushed.
- Publish only the auto-signed debug APK.
- Mark automated GitHub Releases as prereleases.
- Accept uninstall/reinstall between tagged builds because hosted runners do
  not preserve a stable debug signing key.
- Do not run emulator-based instrumented tests in this first version.

## Workflow Architecture

### Continuous Integration

Create `.github/workflows/ci.yml`.

The workflow runs for:

- Pull requests targeting `main`.
- Pushes to `main`.

It has one build job on a GitHub-hosted Ubuntu runner:

1. Check out the triggering commit.
2. Provision JDK 21.
3. Configure Gradle dependency caching.
4. Run unit tests.
5. Run Android lint.
6. Assemble the debug APK.

The workflow receives read-only repository content permission. A failing test,
lint violation, or compilation/build error fails the job. Concurrency should
cancel an older run for the same pull request or branch when a newer commit
arrives.

### Tagged Release

Create `.github/workflows/release.yml`.

The workflow runs only when a pushed tag matches `v*`. It checks out that exact
tag and repeats the same unit test, lint, and debug assembly gates used by CI.
After a successful build, it:

1. Confirms `app/build/outputs/apk/debug/app-debug.apk` exists.
2. Copies it to a tag-qualified name such as
   `thwiply-v1.0.0-debug.apk`.
3. Creates a GitHub prerelease for the pushed tag.
4. Generates release notes.
5. Uploads the renamed APK as a release asset.

The release command must verify that the tag already exists so automation
cannot silently create a release from the wrong default-branch commit.

## Permissions and Secrets

The CI workflow uses:

```yaml
permissions:
  contents: read
```

The release workflow uses:

```yaml
permissions:
  contents: write
```

Release creation authenticates with the workflow-provided `GITHUB_TOKEN`. No
custom repository secrets, keystores, or signing passwords are required.

Referenced actions should be pinned to immutable full commit SHAs. Each job
should define a timeout so a stalled Gradle process cannot consume runner time
indefinitely.

## Debug Signing Limitation

Android debug APKs are signed automatically with a local debug key. GitHub-hosted
jobs run on new virtual machines, so separate release runs do not reliably share
the same debug key.

As a result, a tagged APK may not install as an update over an APK from a prior
tag. Release notes must tell testers to uninstall the previous CI build before
installing the new one. Uninstalling removes the app's local data.

This trade-off is accepted for early testing. A future production design must
replace it with a stable protected signing key and a release APK or app bundle.

## Failure Handling

- Tests, lint, and assembly are hard gates; publishing never runs after a
  validation failure.
- The release job checks for the expected APK path before calling GitHub.
- The release command fails when the tag does not exist.
- An existing release or asset is not silently overwritten. Reusing a tag is a
  release-process error and should require an explicit operator decision.
- GitHub API or asset-upload failures fail the workflow and remain visible in
  the Actions run.
- No broad retry or success-shaped fallback is added.

## Validation Strategy

Implementation validation must cover:

1. Confirm the repository's Gradle wrapper exposes the selected unit test, lint,
   and debug assembly tasks under JDK 21.
2. Run those tasks successfully in a JDK 21 environment.
3. Validate both workflow files as YAML and inspect their effective triggers and
   permissions.
4. Confirm CI does not receive write permission.
5. Confirm release publishing cannot run for a branch push or a non-matching
   tag.
6. Confirm the release asset path and tag-qualified filename match the actual
   Gradle output.

The first real tagged release is the end-to-end publishing test. It should use a
deliberate prerelease tag after the workflows merge.

## Out of Scope

- Google Play Console publishing.
- Production release signing.
- Android App Bundle generation.
- Emulator-based instrumented tests.
- Automatic `versionCode` or `versionName` mutation from tags.
- Stable in-place upgrades between CI-distributed APKs.
- Deployment environments or manual approval gates.

## Acceptance Criteria

- Pull requests to `main` and pushes to `main` run unit tests, lint, and debug
  assembly.
- A failure in any required check fails CI.
- A pushed `v*` tag runs the same checks before publishing.
- A successful tagged run creates a GitHub prerelease with generated notes and
  one tag-qualified debug APK.
- Normal CI has read-only repository permission; only release has
  `contents: write`.
- No custom secret is required.
- The prerelease warns testers about uninstall/reinstall and local data loss.

## Verified Platform References

- GitHub workflow triggers and permissions:
  <https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax>
- `GITHUB_TOKEN` authentication and least-privilege permissions:
  <https://docs.github.com/en/actions/tutorials/authenticate-with-github_token>
- GitHub-hosted runner lifecycle:
  <https://docs.github.com/en/actions/concepts/runners/github-hosted-runners>
- GitHub CLI release creation and asset upload:
  <https://cli.github.com/manual/gh_release_create>
- Android command-line debug APK builds:
  <https://developer.android.com/build/building-cmdline>
- Android app and debug signing:
  <https://developer.android.com/studio/publish/app-signing>
