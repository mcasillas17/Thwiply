# Bouncy Castle Build Classpath Remediation Design

## Objective

Remediate Dependabot alert #35 (GHSA-574f-3g2m-x479 / CVE-2025-14813) by ensuring the Android build-tool classpath resolves `org.bouncycastle:bcprov-jdk18on`, `bcpkix-jdk18on`, and `bcutil-jdk18on` to the minimum patched version, `1.80.2`.

The vulnerable dependencies are transitive build tooling from Android Gradle Plugin (AGP) 9.2.1. They are not application runtime dependencies. The app dependency graph and unrelated source code must remain unchanged.

## Baseline

- The branch starts at `main` commit `e09f2e1`.
- `./gradlew buildEnvironment` resolves all three Bouncy Castle modules to `1.79`.
- `:app:debugRuntimeClasspath` contains no `org.bouncycastle` modules.
- AGP 9.3.2 is not a remediation because its related tooling still declares Bouncy Castle 1.79.

## Considered Approaches

### 1. Constrain the root buildscript classpath

Add strict dependency constraints for the three Bouncy Castle modules to the root buildscript classpath. This is the preferred approach because it expresses the security requirement narrowly while allowing Gradle's normal conflict resolution to align the transitive plugin dependencies.

This approach is acceptable only if `buildEnvironment` proves that the plugin classpath resolves all three modules to 1.80.2.

### 2. Force versions on the root buildscript classpath

Apply a targeted resolution rule for the three module names. This can affect plugin classpath resolution where ordinary constraints do not, but it is more imperative and can mask future dependency intent. Use it only if constraints do not affect the plugins DSL classpath.

### 3. Upgrade AGP or broadly substitute Bouncy Castle

An AGP upgrade is larger in scope and AGP 9.3.2 remains vulnerable through the same declared version. A blanket substitution or a jump to a newer Bouncy Castle release changes more than required. These approaches are rejected unless the narrowly scoped classpath mechanisms cannot produce a patched graph.

## Design

Define the patched Bouncy Castle version once and constrain exactly these root buildscript classpath modules:

- `org.bouncycastle:bcprov-jdk18on`
- `org.bouncycastle:bcpkix-jdk18on`
- `org.bouncycastle:bcutil-jdk18on`

Each constraint will require version 1.80.2 and state the security rationale. No app module dependency will be added.

## Test Strategy

Add a typed root Gradle verification task that fails unless all three expected modules are present at exactly 1.80.2. A lazy provider maps the resolved buildscript classpath into a `MapProperty`; the task action reads only serializable `Property`, `SetProperty`, and `MapProperty` inputs so unrelated tasks do not realize verification data and configuration cache entries can be reused. Test-first evidence must show:

1. Before remediation, the assertion fails and reports the vulnerable 1.79 resolution.
2. After remediation, the assertion passes for all three modules.
3. `buildEnvironment` reports each module resolving to 1.80.2.
4. `:app:debugRuntimeClasspath` remains free of Bouncy Castle.
5. The verification task stores and then reuses the Gradle configuration cache.
6. An unrelated offline `help` invocation remains configuration-cache compatible without running the verification task.
7. The existing test, lint, and debug assembly suite succeeds.

The verification task is deterministic and guards against a future plugin update reintroducing a vulnerable version.

## Scope and Failure Handling

Only the root Gradle build configuration, the dependency assertion, and the accompanying design and implementation plan are in scope. If classpath constraints are ineffective, replace them with the smallest targeted Gradle-native rule that demonstrably changes the plugin classpath; do not layer multiple redundant mechanisms.

The Dependabot alert must not be dismissed or described as closed. After merge and refreshed dependency submission, GitHub should re-evaluate the alert against the patched graph.
