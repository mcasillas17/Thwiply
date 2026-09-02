#!/usr/bin/env bash

set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$repo_root/scripts/check-apk-size.sh"
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

truncate -s 9 "$fixture_dir/below.apk"
truncate -s 10 "$fixture_dir/exact.apk"
truncate -s 11 "$fixture_dir/above.apk"
: > "$fixture_dir/empty.apk"

expect_success \
  "accepts an APK below the limit" \
  "$verifier" "$fixture_dir/below.apk" 10
expect_success \
  "accepts an APK exactly at the limit" \
  "$verifier" "$fixture_dir/exact.apk" 10
expect_failure_containing \
  "rejects an APK above the limit" \
  "exceeds maximum" \
  "$verifier" "$fixture_dir/above.apk" 10
expect_failure_containing \
  "rejects a missing APK" \
  "APK does not exist" \
  "$verifier" "$fixture_dir/missing.apk" 10
expect_failure_containing \
  "rejects an empty APK" \
  "APK is empty" \
  "$verifier" "$fixture_dir/empty.apk" 10
expect_failure_containing \
  "rejects a non-numeric limit" \
  "Maximum bytes must be a positive integer" \
  "$verifier" "$fixture_dir/below.apk" ten

printf '%d passed, %d failed\n' "$passed" "$failed"
((failed == 0))
