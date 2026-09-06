# Minified alpha smoke runbook

This is the smoke-validation slice of [FND-13](ROADMAP.md), not release
authorization. Run it on **both a host-native emulator and a representative
physical arm64 device**. An ARM64 emulator is not physical-device evidence.
Use synthetic tasks/prompts and a dedicated, owner-approved test installation.

Keep three evidence classes separate: unsigned local packaging observations,
preliminary `TEST-KEY` smoke, and final **pinned persistent-key** candidate smoke.
A `candidate-UNVERIFIED` artifact is identity-bootstrap evidence only. Never
promote preliminary results to final results by changing their label.

## 1. Inventory and approval

Use Bash and the [existing JDK 21 / Android SDK setup](../README.md#android-instrumentation).
Required tools: Git, GitHub CLI with repository access, Python 3, `adb`, emulator,
SDK platform 36, Build Tools (including `aapt2` and `apksigner`), `unzip`, and
`sha256sum` on Linux or `shasum` on macOS. No signing private key is needed locally.
Set `JAVA_HOME` to the installed JDK 21 first. Set these variables for the entire
session; adapt the SDK location on Linux:

```bash
set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel)"
GH_REPO=mcasillas17/Thwiply
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
EVIDENCE="$(mktemp -d "$HOME/thwiply-smoke.XXXXXX")"
printf 'Evidence directory: %s\n' "$EVIDENCE"
java -version
./gradlew --version
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --list_installed
"$ANDROID_HOME/emulator/emulator" -version
"$ANDROID_HOME/emulator/emulator" -accel-check
"$ANDROID_HOME/emulator/emulator" -list-avds
adb devices -l
```

Choose a host-native API 31+ emulator image (API 36 is the current CI baseline).
Use Android Studio Device Manager to create a separate test AVD if needed;
do not repurpose or wipe another project's running emulator. For the physical
device, obtain its owner's approval, enable USB debugging and accept the USB
authorization prompt. Inventory without installing anything:

```bash
read -r -p 'Approved target adb serial: ' SERIAL
adb -s "$SERIAL" get-state
{
  adb -s "$SERIAL" shell getprop ro.product.manufacturer
  adb -s "$SERIAL" shell getprop ro.product.model
  adb -s "$SERIAL" shell getprop ro.build.version.sdk
  adb -s "$SERIAL" shell getprop ro.product.cpu.abilist
  adb -s "$SERIAL" shell getprop ro.kernel.qemu
  adb -s "$SERIAL" shell df -h /data
} > "$EVIDENCE/device.txt"
adb -s "$SERIAL" shell pm list packages thwiply.elopenmike.com
```

Record physical/emulator explicitly, not inferred solely from `ro.kernel.qemu`.
Record RAM, free storage and thermal/power conditions from the test device's
settings. Allow at least 1,597,931,520 bytes for weights **plus** APK, runtime,
partial-download and filesystem headroom. Arrange unmetered networking and
permission for the approximately 1.49 GiB download; do not silently consume a
tester’s mobile-data allowance.

That storage estimate is for Qwen only. Nano may need a separate Android-managed
shared download, with device-dependent storage/RAM use; do not assume it is
preinstalled or free of download/memory cost. Arrange approval for that download
separately and satisfy the [Nano policy gates](RELEASING.md#gemini-nano-policy-and-device-gates).

Recheck main, the next unused alpha version, and signing configuration:

```bash
git fetch origin main
git rev-parse origin/main
gh release list --repo "$GH_REPO" --limit 20
git ls-remote --tags origin
gh api "repos/$GH_REPO/environments" \
  --jq '{environments:[.environments[]|{name,protection_rules,deployment_branch_policy}]}'
gh secret list --repo "$GH_REPO" --env alpha-signing
gh variable list --repo "$GH_REPO" --json name
gh run list --repo "$GH_REPO" --workflow release-preflight.yml --limit 10
```

These commands inspect secret **names**, never values. A 403/404 is not proof
of secret absence; distinguish inaccessible metadata from a successful empty
listing. The owner must configure required review and selected deployment
branches/tags (`main`, `v*`) on `alpha-signing`, provision the four documented
secrets, and approve the public certificate pin as described in
[signing provisioning](RELEASING.md#signing-identity-and-provisioning).
An environment name or main-ancestry check alone is not authorization.
Verify the actual selected-branch/tag policies in the environment settings.

## 2. Obtain the candidate, not a local replacement

**Get explicit approval before any signing dispatch, including `test-key`.**
Never approve a protected environment on the owner's behalf. Do not provision,
rotate, export or print signing secrets as part of this procedure.

Only after approval, choose the next unused version and the approved identity:

```bash
VERSION=v1.0.0-alpha.4   # Recheck availability; this does not reserve the version.
SIGNING_KEY=test-key    # Use persistent-key for final pinned candidate evidence.
gh workflow run "Alpha release preflight" --repo "$GH_REPO" --ref main \
  -f candidate_version="$VERSION" -f signing_key="$SIGNING_KEY"
```

Record the returned run URL. If necessary, use the run list to identify that
specific dispatch, not just the latest successful run. `--ref main` selects
the revision for that run; main can advance afterward. Wait for owner approval
and successful completion. The run must contain this runbook's R8 retention
steps; an older candidate without matching diagnostics needs a new approved run.

```bash
read -r -p 'That preflight run ID: ' RUN_ID
[[ "$RUN_ID" =~ ^[0-9]+$ ]]
gh run view "$RUN_ID" --repo "$GH_REPO" \
  --json url,headSha,attempt,event,status,conclusion,workflowName \
  > "$EVIDENCE/run.json"
gh api "repos/$GH_REPO/actions/runs/$RUN_ID/artifacts" > "$EVIDENCE/artifacts.json"
gh api "repos/$GH_REPO/actions/runs/$RUN_ID/artifacts" \
  --jq '.artifacts[] | [.id, .name, .expired] | @tsv'
```

Inspect `run.json`: require `Alpha release preflight`, `workflow_dispatch`,
`completed` and `success`. Record its attempt and the selected artifact IDs.
Select the two artifacts from the same successful attempt and matching label:
`thwiply-candidate-<label>-<run_id>` and `thwiply-r8-<label>-<run_id>`.
Do not combine files from different runs or attempts.

```bash
read -r -p 'Exact candidate artifact name: ' CANDIDATE_ARTIFACT
read -r -p 'Exact R8 artifact name: ' R8_ARTIFACT
gh run download "$RUN_ID" --repo "$GH_REPO" \
  --name "$CANDIDATE_ARTIFACT" --dir "$EVIDENCE/candidate"
gh run download "$RUN_ID" --repo "$GH_REPO" \
  --name "$R8_ARTIFACT" --dir "$EVIDENCE/r8"
CANDIDATE="$EVIDENCE/candidate"
R8="$EVIDENCE/r8"
SOURCE_SHA="$(gh run view "$RUN_ID" --repo "$GH_REPO" --json headSha --jq .headSha)"
git fetch origin main
git merge-base --is-ancestor "$SOURCE_SHA" origin/main
git show "$SOURCE_SHA:app/src/main/java/thwiply/elopenmike/com/llm/model/ModelPreset.kt" \
  > "$EVIDENCE/ModelPreset.kt"
```

Both artifacts expire after 14 days and are not private distribution in this
public repository. Preserve approved evidence before expiry. Read `CANDIDATE.txt`
as data: **do not source it or execute its printed tagging command**.
Compare its repository, run URL, source SHA, candidate version, signing identity,
fingerprint and pin status with the selected run and owner's approved identity.

## 3. Verify the downloaded bytes

Run these in the same Bash session; stop on any failure. Do not regenerate a
checksum file to make mismatched bytes pass.

```bash
verify_sums() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c SHA256SUMS
  else
    shasum -a 256 -c SHA256SUMS
  fi
}
(cd "$CANDIDATE" && verify_sums)
(cd "$R8" && verify_sums)
cmp "$CANDIDATE/CANDIDATE.txt" "$R8/CANDIDATE.txt"
cmp "$CANDIDATE/SHA256SUMS" "$R8/APK-SHA256SUMS"
grep -Fx "Source commit:       $SOURCE_SHA" "$CANDIDATE/CANDIDATE.txt"
read -r -p 'Candidate version for this run (vX.Y.Z-alpha.N): ' VERSION
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-alpha\.[0-9]+$ ]]
grep -Fx "Candidate version:   $VERSION" "$CANDIDATE/CANDIDATE.txt"
VERSION_CODE="$(git rev-list --count "$SOURCE_SHA")"
VERSION_NAME="${VERSION#v}"
LABEL="$VERSION-candidate"   # Final only; use "$VERSION-TEST-KEY" for preliminary.
ARM64="$CANDIDATE/thwiply-$LABEL-arm64-v8a.apk"
X86="$CANDIDATE/thwiply-$LABEL-x86_64-emulator.apk"
for apk in "$ARM64" "$X86"; do
  "$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$apk"
done > "$EVIDENCE/apksigner.txt"
bash "$REPO_ROOT/scripts/verify-apk-identity.sh" "$BUILD_TOOLS/aapt2" \
  "$ARM64" arm64-v8a "$VERSION_CODE" "$VERSION_NAME"
bash "$REPO_ROOT/scripts/verify-apk-identity.sh" "$BUILD_TOOLS/aapt2" \
  "$X86" x86_64 "$VERSION_CODE" "$VERSION_NAME"
bash "$REPO_ROOT/scripts/check-apk-size.sh" "$ARM64" 33554432
```

For **final** evidence, require manifest identity `persistent-key` and status
`pinned and verified`. Obtain the expected public fingerprint from the
owner-approved record (the repository variable must match that record), never
from the observed APK as a new trust anchor:

```bash
EXPECTED_PIN="$(gh variable get ALPHA_SIGNING_CERT_SHA256 --repo "$GH_REPO")"
bash "$REPO_ROOT/scripts/verify-apk-certificate.sh" \
  "$EVIDENCE/apksigner.txt" "$EXPECTED_PIN" > "$EVIDENCE/certificate-sha256.txt"
```

For **preliminary test-key** evidence only, omit the expected pin argument and
record the observed certificate as untrusted/disposable. This is a different
mode, **not a fallback after a pin mismatch**. An unverified persistent-key run
is only for the owner's offline identity comparison, not final smoke sign-off.

Save verifier output with the report. Inspect `aapt2 dump badging` for application
ID `thwiply.elopenmike.com`, minimum API 31 and target API 36; do not use Settings'
hardcoded version as packaged-version evidence. Inspect `unzip -l "$ARM64" 'lib/*'`
and the matching x86 APK: require their own ABI's `liblitertlm_jni.so` and LiteRT
libraries, and no bundled model. Inspect the source build configuration and
successful `minifyAlphaWithR8`/resource-shrinking run logs, not the filename alone.

The R8 artifact contains `configuration.txt`, `mapping.txt`, `seeds.txt` and
`usage.txt` under each ABI. The merged configuration identifies dependency
consumer rules; seeds/mapping show retained JNI names. Review these **before**
adding a keep rule. No app rule is justified merely because
`app/proguard-rules.pro` is empty of active rules. For a demonstrated shrinker
failure, retain the exact crash, mapping and affected symbol, make the smallest
rule change, and obtain new candidate bytes and fresh smoke evidence.

## 4. Install safely and exercise the actual native path

Obtain explicit permission to install on the selected target. Prefer a fresh
dedicated AVD and an owner-approved physical test device with no existing app.
This guard refuses any matching installed package; it does not uninstall or
clear data:

```bash
test "$(adb -s "$SERIAL" get-state)" = device
test -z "$(adb -s "$SERIAL" shell pm list packages thwiply.elopenmike.com)"
APK="$ARM64"   # Choose "$X86" only for an x86_64 emulator.
adb -s "$SERIAL" install "$APK"
adb -s "$SERIAL" shell am force-stop thwiply.elopenmike.com
adb -s "$SERIAL" shell am start -W -n thwiply.elopenmike.com/.MainActivity \
  > "$EVIDENCE/cold-launch.txt"
```

If the guard fails, **stop**. An approved in-place update with `adb install -r`
preserves data but requires matching signing identity and compatible version
code; it cannot establish the fresh/no-model baseline. Alpha.3 and older
debug-signed builds, or another disposable test-key build, may require an
uninstall before this candidate can install. Uninstalling removes local app data
and app-managed Qwen weights; Android manages the shared Nano model independently.
Explain this and obtain separate explicit consent; never
automate uninstall, `pm clear`, a device wipe, or downgrade/verification bypass.

Run each scenario on both target classes and record `PASS`, `FAIL`, or
`NOT RUN (reason)` separately. A partial output stream is not completed inference.

| Scenario | Procedure and required observation |
|---|---|
| Cold/no-model launch | On a fresh installation, Today works without model setup. The unset provider choice defaults to Qwen; in Lab, see `No model available for Lab` and a disabled `Thwip Test`. If weights or a saved Nano choice exist, do not claim this baseline. SDK network/telemetry activity is separate from manual-feature availability. |
| Manual Today | Add `Smoke: buy oat milk`, toggle completion, cold-launch again and confirm persistence. Delete only the synthetic task you created, then confirm its removal. |
| Settings without weights | Open Settings and the `Delete notification data and rules` confirmation, then **Cancel**. Controls must remain reachable without weights; this is not evidence that deleting real notification data was exercised. |
| Optional setup | Enter `Model setup` from Settings, exit with Back/`Return to app` and remain on Settings. Repeat from Lab and remain on Lab. No download should be required to return. |
| Approved download and verification | On the fresh installation, select the pinned Qwen model and `Download or resume model`. Wait beyond 100% for `Model installed. Open Lab to initialize local inference.` Record duration and any error. Only then return explicitly. |
| Real initialization | Open Lab. Record initialization start/end or failure, then require `Model ready. Tap Thwip Test to run inference on this device.` This must be the real minified APK and LiteRT-LM, not an instrumentation fake. |
| Completed generation | Uncheck `Extract JSON Task`; enter `Reply with one short sentence about a blue kite.` Tap `Thwip Test`. Save the synthetic prompt and final nonempty response. Require `Generating...` to finish, the button to become available again, and no generation error/crash. Record wall-clock duration, not claimed tokens/second. |
| Post-inference shell | Return to Today and Settings; confirm both remain usable. Cold-launch and repeat initialization/generation if testing restart separately; this does not prove restart digest revalidation. |

### Gemini Nano scenarios

Run these on a supported, owner-approved **physical** device with the same exact
candidate. Determine support through the SDK, not the device name alone. Do not
enable developer-preview enrollment; this integration uses the default stable
configuration. On an emulator, mark real Nano cases `NOT RUN` unless actual
service availability/inference is demonstrated, and never relabel emulator
evidence as physical-device evidence.

| Scenario | Procedure and required observation |
|---|---|
| Explicit selection, not download | Open setup, select **Gemini Nano**, and review the adult-use/metrics notice. Cancel leaves the choice unchanged; confirming saves Nano but is not download consent. Record the SDK-reported state separately. |
| Unavailable/unready AICore | If unavailable or failed, record the exact safe UI state. Today, Settings and Qwen selection remain accessible; no automatic Qwen download or provider switch occurs. AICore configuration/system updates may be needed; no permanent-incompatibility or automatic-recovery promise is made. Do not reset AICore or reinstall the app without separate approval. |
| Approved Nano preparation | Only when offered, choose **Prepare or download Gemini Nano**, review the download consent and confirm on the approved network. Require a subsequent available state; no fictitious percentage. If already ready, mark download `NOT RUN (already available)`, not a successful fresh download. |
| Real foreground generation | Keep Thwiply top foreground. In Lab verify **Selected provider: Gemini Nano**, disable JSON extraction, and use the short synthetic blue-kite prompt above. Require a nonempty completed response with no failure/limit/Stopped indicator. Record the actual response and wall-clock duration; an enabled button alone is not completed inference. |
| Stop and foreground loss | Start a synthetic stream and tap **Stop**; separately repeat and background the app or make another split-screen app top-resumed. Require cancellation/Stopped behavior, no new chunks after unwind, recoverable controls and a later explicit successful request. A foreground service is not an exception. Do not claim instantaneous cancellation of blocking cleanup. |
| Navigation and switching | Leave an active stream for Today/Settings/setup. Select Qwen explicitly; require old Nano output/metrics to clear and no implicit Qwen download. Installed Qwen weights and synthetic manual tasks survive both provider changes. |
| Restart and ownership | Leave Nano selected, cold-launch and confirm the selection survives even if Nano becomes unavailable. Settings distinguishes private Qwen files from Android's shared Nano model and never offers to delete the shared model. |

The pinned prompt beta2 SDK caps output at **256 tokens**. MAX_TOKENS, the app's
display limit, timeouts, safety rejection and empty output are failures/incomplete
results, not completed generation. Observe quota/busy errors if encountered; do
not deliberately exhaust a device's quota or loop retries to manufacture evidence.
Deterministic fake-client tests cover failure cases that cannot safely be induced.
The app currently cancels all setup/Lab work on foreground loss; Nano additionally
has the platform-level restriction. No notification ingestion or content retention
for later Nano processing is part of these scenarios.

### Qwen artifact identity

The approved Qwen identity at the preparation baseline is below. Recheck against
`ModelPreset.kt` from **the candidate SHA**, not a later checkout:

```text
id: qwen-2.5-1.5b
upstream revision: 19edb84c69a0212f29a6ef17ba0d6f278b6a1614
URL: https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true
installed filename: qwen-2.5-1.5b-q8-ekv4096.litertlm
bytes: 1597931520
SHA-256: faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9
runtime dependency: com.google.ai.edge.litertlm:litertlm-android:0.12.0
```

Fresh-download success passes through ModelManager's exact-size and SHA-256
activation checks. It is not an independent privileged readback of private model
storage. At this baseline restart adoption checks only length (FND-08); a
preexisting installed model or the UI's 100% progress alone is not fresh digest
verification evidence. Do not enable debugging/root access or replace weights
with a tiny fixture to work around the non-debuggable candidate.

## 5. Evidence and failure handoff

After launch, capture only the app process while performing synthetic scenarios.
In a second terminal with the same variables, run the following and stop it
with Ctrl-C after the scenarios. Reacquire the PID after each cold launch:

```bash
APP_PID="$(adb -s "$SERIAL" shell pidof -s thwiply.elopenmike.com | tr -d '\r')"
[[ "$APP_PID" =~ ^[0-9]+$ ]]
adb -s "$SERIAL" logcat --pid="$APP_PID" -T 1 -v threadtime \
  > "$EVIDENCE/app-logcat.private.txt"
```

If the app dies before a PID can be obtained, record failed launch and inspect
the bounded crash buffer (`adb -s "$SERIAL" logcat -b crash -d -t 200`) **only on
the approved dedicated target**. System/native crash entries may not appear in
PID-filtered logs. Do not take broad personal-device bugreports or dump databases.
For a useful synthetic-only screen, capture authentic pixels with
`adb -s "$SERIAL" exec-out screencap -p > "$EVIDENCE/screen.private.png"`.
Review for personal data, serials, local paths and signed download URLs before
sharing. Keep raw files local; share only deliberately redacted logs or
screenshots with redactions disclosed. Screenshots alone never prove inference.

Create an evidence report outside the repository, one record per candidate APK
and device. Include all fields below; never fill an unexecuted field with an
expected result:

| Field | Required record |
|---|---|
| Classification | Local unsigned / preliminary TEST-KEY / unverified identity bootstrap / final pinned persistent-key |
| Provenance | Run URL, run ID/attempt, artifact names/IDs, actual source SHA, download/test UTC time |
| Exact bytes | APK filename, SHA-256 from verified SHA256SUMS, size, packaged application ID/versionName/versionCode, native ABI |
| Signing | Observed certificate SHA-256, expected pin source and owner approval, signature result, pin verification state |
| Build diagnostics | Matching R8 artifact/checksums, ABI-specific mapping/configuration/seeds/usage, minification/shrinking log |
| Target | Physical or emulator, device model, API, ABI, RAM/free storage, power/thermal/network conditions; redact serial |
| Model/provider | Qwen: candidate's preset ID/revision/URL/file/byte count/digest and fresh-download verification or existing weights. Nano: packaged SDK version, default-stable configuration, observed SDK availability, Android/AICore version and whether preparation actually ran; do not invent a file URL/digest for the shared model. |
| Scenarios | Each row above: outcome, time, expected vs actual, synthetic prompt and completed response, redacted evidence filenames |
| Limitations | Unrun scenarios, failures, missing approvals/hardware, roadmap owner and precise recovery action |

The current Lab reports Unicode characters and chars/second, not token counts.
Older candidates with `Tokens`/`t/s` labels counted stream emissions; those labels
are not valid token metrics. Record the candidate's actual UI, not a later build.
Do not count debug Room/backup/navigation tests, fake engines, build success,
or a different/rebuilt candidate as native inference evidence.

| Failure/blocker | Next action; keep the gate closed |
|---|---|
| Signing environment, required review, secrets or approved pin unavailable | Owner completes provisioning/approval; no substitute private key or self-approved deployment. |
| Checksum, certificate, version, ABI or size mismatch | Stop installation; compare run and manifest; reuse the existing verifier diagnostics. Never relax a verifier or trust the observed certificate automatically. |
| Missing R8 report/artifact | Inspect the failed retention step and build log; do not substitute another ABI/run's mapping. New build means new candidate evidence. |
| Wrong ABI / signature conflict / older version code | Reconfirm target and candidate. Use an approved fresh target, not an automatic uninstall or downgrade. |
| Download failure, truncation or stuck cancellation | Record exact symptom; retry only through setup after the prior request unwinds. Broader download hardening belongs to FND-10. |
| JNI/serialization crash in minified build | Use that APK's ABI-specific mapping and consumer rules. Add only a rule supported by a reproduced failure, then repeat artifact-specific smoke. |
| Initialization, cancellation or generation failure | Record visible state and redacted native/app logs; engine lifecycle belongs to FND-09 and Lab behavior/metrics to FND-11. |
| No representative physical hardware | Emulator results stay emulator-only; physical scenarios remain NOT RUN. |
| Missing license or hardcoded Settings version | Report unresolved FND-14; use packaged version for evidence, not a silent metadata fix in this slice. |

FND-13 remains blocked while FND-08, FND-09, FND-10 or FND-11 is open, even after
a successful smoke. Main CI must pass for the intended source revision,
including **Android instrumentation**, independently of this procedure.
Notification ingestion is not implemented and is not tested here.

Do not push a tag, publish a release or merge a PR as part of smoke validation.
The publishing workflow **rebuilds** from source instead of promoting these
candidate bytes. Candidate proof is not proof of a published deployment;
see the separate [owner-controlled release gates](RELEASING.md#publish).
