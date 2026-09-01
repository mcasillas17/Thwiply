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
