#!/usr/bin/env bash
set -euo pipefail
JAR="build/libs/java-debugging-system-all.jar"
[[ -f "$JAR" ]] || ./scripts/build.sh
java -jar "$JAR"   --launch com.example.sample.ExampleApp   --bp config/breakpoints.json   --log debug-events.jsonl
