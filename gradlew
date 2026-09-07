#!/usr/bin/env sh
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed or not on PATH." >&2
  echo "Install Gradle 8.10 or newer, then run ./gradlew build again." >&2
  exit 1
fi
exec gradle "$@"
