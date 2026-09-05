#!/usr/bin/env bash

set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$repo_root/scripts/verify-apk-identity.sh"
fixture_dir="$(mktemp -d)"
trap 'rm -rf "$fixture_dir"' EXIT

passed=0
failed=0

record_pass() {
  printf 'PASS: %s\n' "$1"
  passed=$((passed + 1))
}

record_fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failed=$((failed + 1))
}

expect_success() {
  local name="$1"
  shift
  local output
  if output="$("$@" 2>&1)"; then
    record_pass "$name"
  else
    record_fail "$name (unexpected failure: $output)"
  fi
}

expect_failure_containing() {
  local name="$1"
  local expected="$2"
  shift 2
  local output
  if output="$("$@" 2>&1)"; then
    record_fail "$name (unexpected success)"
  elif [[ "$output" == *"$expected"* ]]; then
    record_pass "$name"
  else
    record_fail "$name (missing '$expected' in: $output)"
  fi
}

# A stand-in for aapt2 that prints a real `dump badging` first line. The Android
# SDK is not available here, so the tool is injected rather than assumed.
make_aapt2() {
  local path="$fixture_dir/aapt2-$1"
  cat > "$path" <<AAPT
#!/usr/bin/env bash
echo "package: name='thwiply.elopenmike.com' versionCode='$2' versionName='$3' platformBuildVersionName='16'"
echo "sdkVersion:'31'"
echo "application-label:'Thwiply'"
AAPT
  chmod +x "$path"
  printf '%s' "$path"
}

# Build real zip archives so the ABI probe runs against genuine zip entries.
make_apk() {
  local name="$1"
  shift
  local dir="$fixture_dir/build-$name"
  rm -rf "$dir"
  mkdir -p "$dir"
  for entry in "$@"; do
    mkdir -p "$dir/$(dirname "$entry")"
    printf 'x' > "$dir/$entry"
  done
  mkdir -p "$dir/META-INF"
  printf 'manifest' > "$dir/AndroidManifest.xml"
  (cd "$dir" && zip -q -r "$fixture_dir/$name.apk" .)
  printf '%s' "$fixture_dir/$name.apk"
}

good_aapt2="$(make_aapt2 good 1234 1.0.0-alpha.4)"
wrong_code_aapt2="$(make_aapt2 wrongcode 1 1.0.0-alpha.4)"
wrong_name_aapt2="$(make_aapt2 wrongname 1234 1.0)"

arm_apk="$(make_apk arm lib/arm64-v8a/libfoo.so lib/arm64-v8a/libbar.so)"
x86_apk="$(make_apk x86 lib/x86_64/libfoo.so)"
mixed_apk="$(make_apk mixed lib/arm64-v8a/libfoo.so lib/x86_64/libfoo.so)"
nolibs_apk="$(make_apk nolibs classes.dex)"
truncated_apk="$fixture_dir/truncated.apk"
head -c 20 "$arm_apk" > "$truncated_apk"

expect_success \
  "accepts a matching arm64 APK" \
  "$verifier" "$good_aapt2" "$arm_apk" arm64-v8a 1234 1.0.0-alpha.4
expect_success \
  "accepts a matching x86_64 APK" \
  "$verifier" "$good_aapt2" "$x86_apk" x86_64 1234 1.0.0-alpha.4

expect_failure_containing \
  "rejects a Gradle versionCode fallback" \
  "packaged versionCode '1' != expected '1234'" \
  "$verifier" "$wrong_code_aapt2" "$arm_apk" arm64-v8a 1234 1.0.0-alpha.4
expect_failure_containing \
  "rejects a versionName mismatch" \
  "packaged versionName '1.0' != expected '1.0.0-alpha.4'" \
  "$verifier" "$wrong_name_aapt2" "$arm_apk" arm64-v8a 1234 1.0.0-alpha.4
expect_failure_containing \
  "rejects arm64 libraries in the x86_64 artifact" \
  "expected only 'x86_64' native libraries" \
  "$verifier" "$good_aapt2" "$arm_apk" x86_64 1234 1.0.0-alpha.4
expect_failure_containing \
  "rejects an APK carrying both ABIs" \
  "expected only 'arm64-v8a' native libraries" \
  "$verifier" "$good_aapt2" "$mixed_apk" arm64-v8a 1234 1.0.0-alpha.4
expect_failure_containing \
  "rejects an APK with no native libraries" \
  "no lib/<abi>/ entries at all" \
  "$verifier" "$good_aapt2" "$nolibs_apk" arm64-v8a 1234 1.0.0-alpha.4
expect_failure_containing \
  "distinguishes a truncated APK from one lacking libraries" \
  "cannot read APK archive" \
  "$verifier" "$good_aapt2" "$truncated_apk" arm64-v8a 1234 1.0.0-alpha.4
expect_failure_containing \
  "rejects a missing APK" \
  "does not exist" \
  "$verifier" "$good_aapt2" "$fixture_dir/absent.apk" arm64-v8a 1234 1.0.0-alpha.4
expect_failure_containing \
  "rejects wrong argument counts" \
  "Usage:" \
  "$verifier" "$good_aapt2" "$arm_apk" arm64-v8a 1234

# A bare lib/ directory entry must not be mistaken for an extra ABI.
dir_apk_build="$fixture_dir/build-dirent"
mkdir -p "$dir_apk_build/lib/arm64-v8a"
printf 'x' > "$dir_apk_build/lib/arm64-v8a/libfoo.so"
(cd "$dir_apk_build" && zip -q -r -D "$fixture_dir/dirent.apk" . && \
  zip -q -r "$fixture_dir/dirent.apk" lib) 2>/dev/null
expect_success \
  "ignores bare directory entries when reading ABIs" \
  "$verifier" "$good_aapt2" "$fixture_dir/dirent.apk" arm64-v8a 1234 1.0.0-alpha.4

printf '%d passed, %d failed\n' "$passed" "$failed"
((failed == 0))
