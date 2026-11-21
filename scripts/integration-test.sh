#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p target/integration-tests
fixture=src/it/cognitive-complexity/pom.xml
logs=target/integration-tests
mvn -B -ntp clean install
mkdir -p "$logs"

mvn -B -ntp -f "$fixture" process-classes | tee "$logs/warnings.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/warnings.log"
grep -F 'Sonar analysis: 1 violation(s).' "$logs/warnings.log"

mvn -B -ntp -o -f "$fixture" process-classes | tee "$logs/offline.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/offline.log"

if mvn -B -ntp -o -f "$fixture" process-classes -Dsonarlint.failOnViolation=true > "$logs/failure.log" 2>&1; then
    cat "$logs/failure.log"
    echo "Expected the build to fail for a cognitive complexity violation." >&2
    exit 1
fi
cat "$logs/failure.log"
grep -F 'Sonar found 1 violation(s).' "$logs/failure.log"

mvn -B -ntp -o -f "$fixture" process-classes -Dsonarlint.cognitiveComplexityThreshold=100 | tee "$logs/clean.log"
grep -F 'Sonar analysis: 0 violation(s).' "$logs/clean.log"
