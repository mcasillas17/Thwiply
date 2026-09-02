#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <apk-path> <maximum-bytes>" >&2
  exit 2
fi

apk_path="$1"
maximum_bytes="$2"

if [[ ! "$maximum_bytes" =~ ^[1-9][0-9]*$ ]]; then
  echo "Maximum bytes must be a positive integer: $maximum_bytes" >&2
  exit 2
fi

if [[ ! -f "$apk_path" ]]; then
  echo "APK does not exist: $apk_path" >&2
  exit 2
fi

actual_bytes="$(wc -c < "$apk_path" | tr -d '[:space:]')"
if [[ "$actual_bytes" -eq 0 ]]; then
  echo "APK is empty: $apk_path" >&2
  exit 2
fi

printf 'APK size: %s bytes (maximum: %s bytes): %s\n' \
  "$actual_bytes" \
  "$maximum_bytes" \
  "$apk_path"

if [[ "$actual_bytes" -gt "$maximum_bytes" ]]; then
  echo "APK exceeds maximum by $((actual_bytes - maximum_bytes)) bytes" >&2
  exit 1
fi
