#!/usr/bin/env bash
set -euo pipefail

if [[ -x "./gradlew" ]]; then
  ./gradlew --no-daemon clean test shadowJar
elif command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon clean test shadowJar
else
  echo "Gradle not found. Using Docker build as fallback..."
  docker build -t java-debugging-system .
fi

echo "Built: build/libs/java-debugging-system-all.jar (if Gradle path was used)"
