# Dependabot Enablement Design

## Goal

Enable Dependabot alerts and security-update pull requests for Thwiply, configure every detected package ecosystem, and submit the resolved Gradle dependency graph because GitHub currently exposes no Gradle manifest or submitted Gradle snapshot.

## Detected dependency surfaces

- Gradle at the repository root, including the Android application module and version catalog.
- GitHub Actions in `.github/workflows`.

No other supported package manifests were detected.

## Approaches considered

### 1. Security updates only, with Gradle dependency submission

Configure both ecosystems in `.github/dependabot.yml`, set `open-pull-requests-limit: 0` to suppress routine version-update pull requests, enable alerts and security updates in repository settings, and add a default-branch Gradle dependency-submission workflow.

This is the selected approach because it matches the requested security scope without introducing unrelated routine-update churn, while still giving GitHub the resolved transitive dependency graph.

### 2. Weekly version and security updates

Use the same ecosystem coverage and dependency submission, but permit routine weekly version-update pull requests. This provides broader maintenance automation but creates pull requests beyond the requested security updates.

### 3. Security updates without dependency submission

Enable alerts, security updates, and ecosystem configuration but rely only on static manifest analysis. This is simpler, but it leaves Gradle's complete resolved graph unavailable and does not satisfy the conditional dependency-submission requirement.

## Repository configuration

Create `.github/dependabot.yml` using schema version 2 with root-directory entries for:

- `gradle`
- `github-actions`

Each entry uses a weekly schedule because the field is required. `open-pull-requests-limit: 0` disables routine version-update pull requests while preserving security-update pull requests.

Enable repository-level Dependabot alerts and Dependabot security updates through GitHub's REST API. Do not dismiss alerts, close generated security pull requests, or merge any pull request.

## Dependency submission

Add a dedicated workflow that runs on pushes to `main` and through manual dispatch. It will:

1. Check out the repository without persisting credentials.
2. Set up the same JDK version used by CI.
3. Run the Gradle dependency-submission action with `contents: write`, the permission required by the dependency submission API.

The workflow will use immutable action commit pins, a timeout, and concurrency control. Dependency generation or submission failures will fail the job rather than being silently ignored.

## Data flow

On a default-branch push, the workflow resolves the Gradle build graph and submits a snapshot to GitHub. GitHub combines that snapshot with statically detected manifests and advisories. Dependabot alerts represent vulnerable dependencies, and enabled security updates can create remediation pull requests for supported vulnerable dependencies.

The `github-actions` Dependabot entry covers actions referenced by workflow files. The `gradle` entry covers the root build, included modules, wrapper, plugins, and version catalog.

## Error handling

- GitHub API failures must stop the setup rather than being interpreted as success.
- Dependency submission must fail visibly if Gradle resolution or API submission fails.
- If GitHub has not processed a newly submitted snapshot immediately, report the workflow and graph state without claiming graph completeness.
- Alert counts must be derived from open alerts returned by GitHub and grouped by GitHub's critical, high, moderate, and low severities.

## Validation

- Parse `.github/dependabot.yml` and workflow YAML.
- Run the existing Gradle test, lint, and debug assembly command.
- Confirm the repository reports Dependabot alerts and security updates as enabled.
- Push the branch and open a pull request containing repository-file changes.
- Run the dependency-submission workflow on the branch only if GitHub permits a safe manual run; otherwise rely on its default-branch trigger after merge and state that limitation.
- Query open Dependabot alerts and report counts for critical, high, moderate, and low severities.
- Verify the pull request remains open and unmerged.
