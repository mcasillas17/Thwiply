#!/usr/bin/env bash

set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ci_workflow="$repo_root/.github/workflows/ci.yml"
release_workflow="$repo_root/.github/workflows/release.yml"
passed=0
failed=0

require_text() {
  local name="$1"
  local file="$2"
  local text="$3"
  if grep -Fq -- "$text" "$file"; then
    printf 'PASS: %s\n' "$name"
    passed=$((passed + 1))
  else
    printf "FAIL: %s (missing '%s')\n" "$name" "$text" >&2
    failed=$((failed + 1))
  fi
}

reject_text() {
  local name="$1"
  local file="$2"
  local text="$3"
  if grep -Fq -- "$text" "$file"; then
    printf "FAIL: %s (found '%s')\n" "$name" "$text" >&2
    failed=$((failed + 1))
  else
    printf 'PASS: %s\n' "$name"
    passed=$((passed + 1))
  fi
}

require_text "CI validates build-tool security constraints" "$ci_workflow" \
  "verifyBuildscriptBouncyCastle"
require_text "CI builds the arm64 alpha" "$ci_workflow" \
  "-Pthwiply.abi=arm64-v8a"
require_text "CI enforces the APK size budget" "$ci_workflow" \
  "scripts/check-apk-size.sh"
require_text "CI uses the 32 MiB arm64 budget" "$ci_workflow" \
  "33554432"
reject_text "CI rejects the obsolete 80 MiB budget" "$ci_workflow" \
  "83886080"

require_text "CI has a stable instrumentation check" "$ci_workflow" \
  "name: Android instrumentation"
require_text "CI runs managed instrumentation, not just assembly" "$ci_workflow" \
  ":app:pixel2api36DebugAndroidTest"
require_text "CI forces fresh instrumentation" "$ci_workflow" \
  "--rerun-tasks --no-build-cache"
require_text "CI checks actual instrumentation results" "$ci_workflow" \
  "python3 scripts/check-instrumentation-results.py"
require_text "CI retains failure diagnostics" "$ci_workflow" \
  "if: always()"
require_text "CI runs workflow regression checks" "$ci_workflow" \
  "bash scripts/test-release-workflows.sh"
reject_text "CI cannot ignore failures" "$ci_workflow" "continue-on-error"
reject_text "CI cannot use privileged PR triggers" "$ci_workflow" "pull_request_target"
reject_text "CI cannot use signing secrets" "$ci_workflow" 'secrets.'
require_text "CI provisions the emulator explicitly" "$ci_workflow" \
  '--install "emulator" "platform-tools"'
require_text "CI provisions the matching system image" "$ci_workflow" \
  '"system-images;android-36;google_apis;x86_64"'
require_text "CI does not reuse managed-device caches" "$ci_workflow" \
  "cache-disabled: true"
require_text "CI fails on missing diagnostic artifacts" "$ci_workflow" \
  "if-no-files-found: error"

if python3 - "$repo_root/scripts/check-instrumentation-results.py" "$ci_workflow" <<'PY'
import pathlib
import re
import subprocess
import sys
import tempfile

checker = sys.argv[1]
workflow = pathlib.Path(sys.argv[2]).read_text()
device_job = workflow.split("\n  instrumentation:\n", 1)[1]
assert not re.search(r"^    (needs|if):", device_job, re.MULTILINE), "device job must be independent"
assert "permissions:\n  contents: read" in workflow, "read-only token required"
assert "persist-credentials: false" in device_job, "checkout must not persist credentials"
assert "timeout-minutes: 10" in device_job, "SDK install must be bounded"
assert "timeout-minutes: 30" in device_job, "device execution must be bounded"
assert device_job.index('--install "emulator"') < device_job.index('emulator" -version')
assert all(
    re.fullmatch(r"[\w./-]+@[0-9a-f]{40}", action)
    for action in re.findall(r"uses: (\S+)", workflow)
), "all actions must be SHA pinned"
classes = [
    "thwiply.elopenmike.com.BackupConfigurationTest",
    "thwiply.elopenmike.com.data.local.ThwiplyDatabaseTest",
    "thwiply.elopenmike.com.data.local.ThwiplyMigrationTest",
]

def report(names=classes, child="", attributes="", extra_cases=""):
    cases = "".join(
        f'<testcase classname="{name}" name="test">{child}</testcase>'
        for name in names
    )
    total = len(names) + bool(extra_cases)
    return f'<testsuite tests="{total}" {attributes}>{cases}{extra_cases}</testsuite>'

def aggregate(attributes='tests="3"'):
    suites = "".join(report([name]) for name in classes)
    return f'<testsuites {attributes}>{suites}</testsuites>'

fixtures = [
    ("all required classes", report(), True),
    ("missing reports", None, False),
    ("empty suite", report([]), False),
    ("missing class", report(classes[:2]), False),
    ("skipped tests", report(child="<skipped/>"), False),
    ("assertion failure", report(child="<failure/>"), False),
    ("test error", report(child="<error/>"), False),
    ("suite failure", report(attributes='failures="1"'), False),
    ("suite skips", report(attributes='skipped="1"'), False),
    ("suite errors", report(attributes='errors="1"'), False),
    ("inconsistent count", report().replace('tests="3"', 'tests="4"'), False),
    ("duplicate tests", report(classes + classes), False),
    ("unrelated failing test", report(extra_cases=
     '<testcase classname="FutureServiceTest" name="test"><failure/></testcase>'), False),
    ("real multi-suite shape", aggregate(), True),
    ("aggregate failures", aggregate('tests="3" failures="1"'), False),
    ("aggregate errors", aggregate('tests="3" errors="1"'), False),
    ("aggregate skips", aggregate('tests="3" skipped="1"'), False),
    ("aggregate count mismatch", aggregate('tests="4"'), False),
    ("missing test identity", report().replace('name="test"', 'name=""'), False),
    ("malformed XML", "<testsuite", False),
]
for name, xml, expected in fixtures:
    with tempfile.TemporaryDirectory() as directory:
        if xml is not None:
            pathlib.Path(directory, "TEST-fixture.xml").write_text(xml)
        result = subprocess.run(
            [sys.executable, checker, directory], capture_output=True, text=True
        )
        if (result.returncode == 0) != expected:
            sys.exit(f"FAIL: {name}: {result.stdout}{result.stderr}")
        print(f"PASS: instrumentation results: {name}")
PY
then
  passed=$((passed + 1))
else
  failed=$((failed + 1))
fi

require_text "release validates alpha tags" "$release_workflow" \
  "alpha tag"
require_text "release validates main ancestry" "$release_workflow" \
  "merge-base --is-ancestor"
require_text "release builds arm64" "$release_workflow" \
  "-Pthwiply.abi=arm64-v8a"
require_text "release builds x86_64" "$release_workflow" \
  "-Pthwiply.abi=x86_64"
require_text "release reads the keystore secret" "$release_workflow" \
  "ALPHA_KEYSTORE_BASE64"
require_text "release signs with protected password inputs" "$release_workflow" \
  "--ks-pass env:ALPHA_KEYSTORE_PASSWORD"
require_text "release verifies APK signatures" "$release_workflow" \
  "verify --verbose --print-certs"
require_text "release publishes checksums" "$release_workflow" \
  "SHA256SUMS"
require_text "release enforces the APK size budget" "$release_workflow" \
  "scripts/check-apk-size.sh"
require_text "release pins the ephemeral-signing cutoff" "$release_workflow" \
  "v1.0.0-alpha.3 or earlier"
reject_text "release does not claim every prior alpha used ephemeral signing" "$release_workflow" \
  'Builds before ${TAG_NAME} used ephemeral debug'
require_text "release uses the 32 MiB arm64 budget" "$release_workflow" \
  'MAX_ARM64_APK_BYTES: "33554432"'
reject_text "release rejects the obsolete 80 MiB budget" "$release_workflow" \
  "83886080"
reject_text "release no longer assembles debug" "$release_workflow" \
  "assembleDebug"
reject_text "release no longer publishes debug APK names" "$release_workflow" \
  "debug.apk"

printf '%d passed, %d failed\n' "$passed" "$failed"
((failed == 0))
