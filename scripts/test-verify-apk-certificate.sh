#!/usr/bin/env bash

set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$repo_root/scripts/verify-apk-certificate.sh"
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

pinned="6d1f7a3c9b0e42d58a17c4f0b93e2d6a815c7fe4093b2a6d5c81ef7043a9b2c8"
other="0000000000000000000000000000000000000000000000000000000000000000"

report() {
  cat <<REPORT
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
Signer #1 certificate DN: CN=Thwiply Alpha, OU=Alpha, O=Thwiply
Signer #1 certificate SHA-256 digest: $1
Signer #1 certificate SHA-1 digest: 1f2e3d4c5b6a798807162534435261708f9e0d1c
Signer #1 certificate MD5 digest: 0123456789abcdef0123456789abcdef
REPORT
}

report "$pinned" > "$fixture_dir/match.txt"
report "$other" > "$fixture_dir/mismatch.txt"
: > "$fixture_dir/empty.txt"
printf 'Verifies\nSigner #1 certificate SHA-256 digest: abc123\n' \
  > "$fixture_dir/malformed.txt"
{
  report "$pinned"
  printf 'Signer #2 certificate SHA-256 digest: %s\n' "$other"
} > "$fixture_dir/two-signers.txt"
{
  report "$pinned"
  printf 'Signer #2 certificate SHA-256 digest: %s\n' "$pinned"
} > "$fixture_dir/same-cert-twice.txt"

# apksigner and keytool print the digest in different spellings; both must pin.
report "$(printf '%s' "$pinned" | tr '[:lower:]' '[:upper:]')" \
  > "$fixture_dir/uppercase.txt"
report "$(printf '%s' "$pinned" | sed -E 's/(..)/\1:/g; s/:$//')" \
  > "$fixture_dir/colons.txt"

expect_success \
  "accepts the pinned fingerprint" \
  "$verifier" "$fixture_dir/match.txt" "$pinned"
expect_success \
  "accepts an uppercase apksigner digest" \
  "$verifier" "$fixture_dir/uppercase.txt" "$pinned"
expect_success \
  "accepts a colon-separated digest in the report" \
  "$verifier" "$fixture_dir/colons.txt" "$pinned"
expect_success \
  "accepts a colon-separated expected fingerprint" \
  "$verifier" "$fixture_dir/match.txt" \
  "6D:1F:7A:3C:9B:0E:42:D5:8A:17:C4:F0:B9:3E:2D:6A:81:5C:7F:E4:09:3B:2A:6D:5C:81:EF:70:43:A9:B2:C8"
expect_success \
  "accepts one certificate reported for several signature schemes" \
  "$verifier" "$fixture_dir/same-cert-twice.txt" "$pinned"

expect_failure_containing \
  "rejects a different signing certificate" \
  "does not match the pinned identity" \
  "$verifier" "$fixture_dir/mismatch.txt" "$pinned"
expect_failure_containing \
  "rejects a report with no certificate digest" \
  "No certificate SHA-256 digest found" \
  "$verifier" "$fixture_dir/empty.txt" "$pinned"
expect_failure_containing \
  "rejects a truncated certificate digest" \
  "No certificate SHA-256 digest found" \
  "$verifier" "$fixture_dir/malformed.txt" "$pinned"
expect_failure_containing \
  "rejects multiple distinct signer certificates" \
  "Expected exactly one signer certificate" \
  "$verifier" "$fixture_dir/two-signers.txt" "$pinned"
expect_failure_containing \
  "rejects a missing report" \
  "does not exist" \
  "$verifier" "$fixture_dir/absent.txt" "$pinned"
expect_failure_containing \
  "rejects a non-SHA-256 expected fingerprint" \
  "must be a SHA-256 hex digest" \
  "$verifier" "$fixture_dir/match.txt" "not-a-fingerprint"
expect_failure_containing \
  "rejects an empty expected fingerprint" \
  "must be a SHA-256 hex digest" \
  "$verifier" "$fixture_dir/match.txt" ""
expect_failure_containing \
  "rejects wrong argument counts" \
  "Usage:" \
  "$verifier" "$fixture_dir/match.txt" "$pinned" "extra"
expect_failure_containing \
  "rejects no arguments" \
  "Usage:" \
  "$verifier"

# Unpinned mode reports the observed identity, which is how an operator first
# learns the fingerprint to pin. It must still reject an ambiguous report.
expect_success \
  "reports an unpinned fingerprint" \
  "$verifier" "$fixture_dir/match.txt"
expect_failure_containing \
  "rejects multiple certificates even when unpinned" \
  "Expected exactly one signer certificate" \
  "$verifier" "$fixture_dir/two-signers.txt"
expect_failure_containing \
  "rejects an empty report even when unpinned" \
  "No certificate SHA-256 digest found" \
  "$verifier" "$fixture_dir/empty.txt"

# stdout carries the bare fingerprint only, so callers can capture it directly.
for mode in unpinned pinned; do
  if [[ "$mode" == pinned ]]; then
    captured="$("$verifier" "$fixture_dir/match.txt" "$pinned" 2>/dev/null)"
  else
    captured="$("$verifier" "$fixture_dir/match.txt" 2>/dev/null)"
  fi
  if [[ "$captured" == "$pinned" ]]; then
    record_pass "stdout is the bare fingerprint ($mode)"
  else
    record_fail "stdout carried extra output in $mode mode: '$captured'"
  fi
done

printf '%d passed, %d failed\n' "$passed" "$failed"
((failed == 0))
