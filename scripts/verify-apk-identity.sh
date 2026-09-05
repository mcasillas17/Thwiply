#!/usr/bin/env bash
#
# Assert that a built APK actually carries the version identity and the single
# native ABI it is supposed to carry.
#
# Passing version properties to Gradle states an intention; this reads the
# result back out of the artifact. It also catches an APK packaged with the
# wrong ABI's native libraries, which is possible because both ABI variants of
# this app build to the same output path.

set -euo pipefail

if [[ "$#" -ne 5 ]]; then
  echo "Usage: $0 <aapt2> <apk> <expected-abi> <expected-version-code> <expected-version-name>" >&2
  exit 2
fi

aapt2="$1"
apk="$2"
expected_abi="$3"
expected_code="$4"
expected_name="$5"

if [[ ! -f "$apk" ]]; then
  echo "APK does not exist: $apk" >&2
  exit 2
fi

badging="$("$aapt2" dump badging "$apk")"
field() {
  sed -nE "s/.*$1='([^']*)'.*/\1/p" <<< "$badging" | head -n 1
}

actual_code="$(field versionCode)"
actual_name="$(field versionName)"

status=0
if [[ "$actual_code" != "$expected_code" ]]; then
  echo "$apk: packaged versionCode '$actual_code' != expected '$expected_code'" >&2
  status=1
fi
if [[ "$actual_name" != "$expected_name" ]]; then
  echo "$apk: packaged versionName '$actual_name' != expected '$expected_name'" >&2
  status=1
fi

# Distinguish an unreadable archive from one that simply has no lib/ entries,
# so a truncated APK does not present as "no native libraries".
if ! unzip -Z1 "$apk" > /dev/null 2>&1; then
  echo "$apk: cannot read APK archive (truncated or corrupt?)" >&2
  exit 1
fi

# The zip entries are the ground truth for which ABI shipped. Directory entries
# yield an empty field, so drop those before comparing.
abis="$(
  unzip -Z1 "$apk" 'lib/*' 2>/dev/null |
    cut -d/ -f2 |
    grep -v '^$' |
    sort -u || true
)"

if [[ "$abis" != "$expected_abi" ]]; then
  echo "$apk: expected only '$expected_abi' native libraries, found:" >&2
  printf '  %s\n' "${abis:-<none>}" >&2
  if [[ -z "$abis" ]]; then
    echo "  (no lib/<abi>/ entries at all; check the ABI filter and that the" >&2
    echo "   native dependency ships this ABI)" >&2
  fi
  status=1
fi

((status == 0)) || exit 1

printf '%s: versionCode=%s versionName=%s abi=%s\n' \
  "$(basename "$apk")" "$actual_code" "$actual_name" "$abis"
