#!/usr/bin/env bash
#
# Read an apksigner "verify --print-certs" report, require that it describes
# exactly one signer certificate, and print that certificate's SHA-256 digest.
# When an expected fingerprint is given, also require the digest to match it.
#
# The expected fingerprint is public certificate metadata, not a secret: it is
# recoverable from any published APK. It is pinned so that a wrong, rotated, or
# substituted signing key cannot silently produce a release candidate.
#
# Only the bare fingerprint goes to stdout, so callers can capture it directly.

set -euo pipefail

if [[ "$#" -lt 1 || "$#" -gt 2 ]]; then
  echo "Usage: $0 <apksigner-report-path> [expected-sha256-fingerprint]" >&2
  exit 2
fi

report_path="$1"
expected="${2-}"

if [[ ! -f "$report_path" ]]; then
  echo "apksigner report does not exist: $report_path" >&2
  exit 2
fi

# Accept the colon-separated and bare hex spellings operators copy from
# apksigner and keytool, and compare case-insensitively.
normalize() {
  printf '%s' "$1" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]'
}

if [[ "$#" -eq 2 ]]; then
  expected_normalized="$(normalize "$expected")"
  if [[ ! "$expected_normalized" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Expected fingerprint must be a SHA-256 hex digest: $expected" >&2
    exit 2
  fi
fi

# Distinct signer certificates, one per line. Keep this portable to the
# bash 3.2 that ships with macOS, like the other scripts in this directory.
observed="$(
  grep -oE 'certificate SHA-256 digest: *[0-9a-fA-F:]{64,}' "$report_path" |
    sed -E 's/.*digest: *//' |
    tr '[:upper:]' '[:lower:]' |
    tr -d ':' |
    grep -xE '[0-9a-f]{64}' |
    sort -u || true
)"

if [[ -z "$observed" ]]; then
  echo "No certificate SHA-256 digest found in $report_path" >&2
  exit 1
fi

observed_count="$(printf '%s\n' "$observed" | grep -c '^')"
if [[ "$observed_count" -ne 1 ]]; then
  echo "Expected exactly one signer certificate, found $observed_count:" >&2
  printf '  %s\n' "$observed" >&2
  exit 1
fi

actual="$observed"

if [[ "$#" -eq 2 ]]; then
  if [[ "$actual" != "$expected_normalized" ]]; then
    echo "Signing certificate does not match the pinned identity." >&2
    echo "  expected: $expected_normalized" >&2
    echo "  actual:   $actual" >&2
    exit 1
  fi
  echo "Signing certificate matches the pinned identity: $actual" >&2
else
  echo "Signing certificate is not pinned; observed identity: $actual" >&2
fi

printf '%s\n' "$actual"
