#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
java_major="$1"
if [ "$java_major" -lt 25 ]; then
    exit 0
fi

fixture=src/it/java-version-rules/pom.xml
logs=target/integration-tests
for goal in check check-tests; do
    log="$logs/mixed-java-releases-$goal.log"
    mvn -B -ntp -o -f "$fixture" clean test-compile \
        io.github.nbauma109:static-analysis-maven-plugin:0.1.3-SNAPSHOT:"$goal" \
        | tee "$log"
    test "$(grep -Ec '\[WARNING\].*ModernCatch\.java:[0-9]+ \[java:S7467\]' "$log")" -eq 1
    if grep -E '\[WARNING\].*LegacyCatch\.java:[0-9]+ \[java:S7467\]' "$log"; then
        exit 1
    fi
done
