# Optimized Alpha Distribution Design

## Goal

Replace the universal, unminified debug prerelease with installable, signed,
device-specific alpha APKs whose size and identity are enforced before GitHub
publishes them.

## Current problem

`v1.0.0-alpha.3` is a 113.49 MiB debug APK. Measured contents are:

- 64.38 MiB of unminified DEX, including 13.69 MB of Material extended icons;
- 26.92 MiB of x86_64 native libraries;
- 21.45 MiB of arm64-v8a native libraries; and
- 0.53 MiB of packaged resources.

The workflow publishes `assembleDebug`, bundles both phone and emulator native
libraries, uses a per-run debug signing identity, and does not enforce a size
budget. The model weights are downloaded after installation and are not part of
the APK.

## Build architecture

Add an `alpha` build type initialized from `release` with:

- `isDebuggable = false`;
- R8 minification enabled;
- resource shrinking enabled;
- the optimized default ProGuard rules; and
- no Gradle signing configuration.

Gradle accepts three explicit project properties:

- `thwiply.abi`: exactly `arm64-v8a` or `x86_64`;
- `thwiply.versionCode`: a positive Android version code; and
- `thwiply.versionName`: the release tag without its leading `v`.

Normal local debug builds remain universal and retain their existing defaults.
Alpha CI and releases always pass one supported ABI, so each build contains only
one LiteRT-LM native implementation.

## Release workflow

Tags matching `v<major>.<minor>.<patch>-alpha.<number>` trigger the workflow.
The tagged commit must be contained in `origin/main`.

The workflow derives:

- `versionName` from the tag; and
- `versionCode` from `git rev-list --count HEAD`, making it deterministic for the
  tagged commit and increasing as main advances.

It runs tests and lint once, builds arm64-v8a and x86_64 alpha APKs separately,
and copies each unsigned output outside the Gradle build directory before the
next clean build. It then:

1. decodes the alpha PKCS12 keystore from GitHub Actions secrets;
2. signs both APKs with Android `apksigner`;
3. verifies each signature and signer certificate;
4. enforces the arm64-v8a size budget;
5. generates SHA-256 checksums; and
6. publishes both APKs plus the checksum file in one GitHub prerelease.

The required repository secrets are:

- `ALPHA_KEYSTORE_BASE64`;
- `ALPHA_KEYSTORE_PASSWORD`;
- `ALPHA_KEY_ALIAS`; and
- `ALPHA_KEY_PASSWORD`.

The private key is never committed or uploaded as a workflow artifact. Temporary
keystore material is removed by an exit trap.

## Artifact policy

The primary tester artifact is:

`thwiply-<tag>-arm64-v8a.apk`

The optional emulator artifact is:

`thwiply-<tag>-x86_64-emulator.apk`

The workflow does not publish a universal APK. The arm64-v8a maximum is 32 MiB (33,554,432 bytes), leaving about 28%
headroom over the measured 26,148,249-byte signed artifact. A checked-in shell
verifier rejects missing files,
non-numeric limits, empty APKs, and APKs above the limit with explicit errors.

The first persistently signed alpha cannot update an APK signed by a prior
ephemeral debug key. Release notes therefore require one final uninstall for
older builds and state that subsequent persistently signed alpha builds can
update in place when their version code increases.

## CI and validation

Pull-request CI keeps the existing unit tests, lint, and debug assembly. It also
builds an unsigned minified arm64-v8a alpha APK with deterministic test version
properties and applies the same 32 MiB gate.

Local validation covers:

- red/green shell tests for the size verifier;
- invalid ABI and invalid version property failures;
- unit tests, lint, debug assembly, and minified alpha assembly;
- APK ABI contents and absence of the opposite ABI;
- manifest version code/name;
- local ephemeral signing and `apksigner verify`;
- arm64 APK size and DEX/package composition; and
- Android launch smoke testing when a device is available.

The validated alpha artifacts use Android version code `100` and version name
`1.0.0-alpha.test` for the local dry run. The signed arm64-v8a artifact is
26,148,249 bytes and the signed x86_64 artifact is 31,886,733 bytes. Each
contains only its requested ABI. Android `apksigner` verifies both with the same
test certificate, and the arm64-v8a artifact cold-launches successfully on an
API 36.1 ARM64 emulator.

## Documentation

README installation and release documentation will distinguish phone and
emulator assets, explain the model download separately from APK size, document
the one-time signing transition, and state the enforced size budget.
