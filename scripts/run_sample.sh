#!/usr/bin/env bash
set -euo pipefail
CP="build/libs/java-debugging-system-all.jar"
[[ -f "$CP" ]] || ./scripts/build.sh
java -cp "$CP" com.example.sample.ExampleApp
