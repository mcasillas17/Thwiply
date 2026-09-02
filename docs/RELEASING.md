# Releasing Thwiply Alphas

Thwiply alpha tags publish minified, persistently signed APKs for one Android
ABI at a time. The workflow never publishes a universal APK.

## Published artifacts

For tag `v1.0.0-alpha.N`, the release contains:

- `thwiply-v1.0.0-alpha.N-arm64-v8a.apk` for physical devices and ARM64
  emulators;
- `thwiply-v1.0.0-alpha.N-x86_64-emulator.apk` for x86_64 emulators; and
- `SHA256SUMS` covering both APKs.

The arm64-v8a APK must not exceed 33,554,432 bytes (32 MiB). Pull-request CI
builds the same minified arm64 variant and enforces the same limit.

## Signing secrets

The release workflow requires one long-lived PKCS12 signing key. Configure these
GitHub Actions repository secrets before pushing the first optimized alpha tag:

- `ALPHA_KEYSTORE_BASE64`;
- `ALPHA_KEYSTORE_PASSWORD`;
- `ALPHA_KEY_ALIAS`; and
- `ALPHA_KEY_PASSWORD`.

Generate the key outside the repository. Use strong, independently generated
passwords and keep an encrypted offline backup; losing the private key prevents
future APKs from updating existing persistent-key installations.

One example provisioning flow is:

```bash
keytool -genkeypair \
  -storetype PKCS12 \
  -keystore thwiply-alpha-signing.p12 \
  -alias thwiply-alpha \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000

openssl base64 -A -in thwiply-alpha-signing.p12 |
  gh secret set ALPHA_KEYSTORE_BASE64
printf '%s' "$ALPHA_KEYSTORE_PASSWORD" |
  gh secret set ALPHA_KEYSTORE_PASSWORD
printf '%s' "$ALPHA_KEY_ALIAS" |
  gh secret set ALPHA_KEY_ALIAS
printf '%s' "$ALPHA_KEY_PASSWORD" |
  gh secret set ALPHA_KEY_PASSWORD
```

Do not place the keystore, passwords, or their base64 representation in the
repository, build logs, release assets, or unencrypted backups.

## Version identity

The tag must match:

```text
v<major>.<minor>.<patch>-alpha.<number>
```

The workflow requires the tagged commit to be contained in `origin/main`.
`versionName` is the tag without the leading `v`; `versionCode` is the full
commit count at the tagged commit. Both ABI artifacts therefore have identical,
deterministic Android version metadata.

Releases through `v1.0.0-alpha.3` used ephemeral debug signing. Testers must
uninstall one of those builds once before installing the first persistently
signed alpha. Later alphas signed by the persistent key can update in place when
their version code increases.

## Local dry run

Set the Android SDK and Java locations for the local environment, then build
each ABI separately because both variants use the same output path:

```bash
./gradlew clean :app:assembleAlpha \
  -Pthwiply.abi=arm64-v8a \
  -Pthwiply.versionCode=100 \
  -Pthwiply.versionName=1.0.0-alpha.test
cp app/build/outputs/apk/alpha/app-alpha-unsigned.apk /tmp/thwiply-arm64.apk

./gradlew clean :app:assembleAlpha \
  -Pthwiply.abi=x86_64 \
  -Pthwiply.versionCode=100 \
  -Pthwiply.versionName=1.0.0-alpha.test
cp app/build/outputs/apk/alpha/app-alpha-unsigned.apk /tmp/thwiply-x86_64.apk
```

Run the repository checks before creating a tag:

```bash
bash scripts/test-check-apk-size.sh
bash scripts/test-release-workflows.sh
bash scripts/check-apk-size.sh /tmp/thwiply-arm64.apk 33554432
./gradlew verifyBuildscriptBouncyCastle test lint assembleDebug
```

Locally sign with a disposable test key and verify with the installed Android
Build Tools `apksigner`. Never use the persistent alpha private key for ad hoc
local builds.

## Publish

After the optimized release workflow is merged and all four signing secrets are
configured, tag the intended `origin/main` commit and push only that tag:

```bash
git fetch origin main --tags
git tag -a v1.0.0-alpha.N origin/main -m "Thwiply v1.0.0 alpha N"
git push origin refs/tags/v1.0.0-alpha.N
```

The workflow validates lineage and tag syntax, runs tests and lint, builds both
ABIs, signs and verifies both APKs, enforces the arm64 size budget, generates
checksums, and creates the GitHub prerelease.
