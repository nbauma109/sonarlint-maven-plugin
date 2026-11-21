#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p target/integration-tests
fixture=src/it/cognitive-complexity/pom.xml
logs=target/integration-tests
mvn -B -ntp clean install
mkdir -p "$logs"

mvn -B -ntp -f "$fixture" process-classes -Dsonarlint.profile=complexity | tee "$logs/warnings.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/warnings.log"
grep -F 'Sonar analysis: 1 violation(s).' "$logs/warnings.log"

mvn -B -ntp -o -f "$fixture" process-classes -Dsonarlint.profile=complexity | tee "$logs/offline.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/offline.log"

if mvn -B -ntp -o -f "$fixture" process-classes -Dsonarlint.profile=complexity -Dsonarlint.failOnViolation=true > "$logs/failure.log" 2>&1; then
    cat "$logs/failure.log"
    echo "Expected the build to fail for a cognitive complexity violation." >&2
    exit 1
fi
cat "$logs/failure.log"
grep -F 'Sonar found 1 violation(s).' "$logs/failure.log"

mvn -B -ntp -o -f "$fixture" process-classes -Dsonarlint.profile=complexity -Dsonarlint.cognitiveComplexityThreshold=100 | tee "$logs/clean.log"
grep -F 'Sonar analysis: 0 violation(s).' "$logs/clean.log"

priority_fixture=src/it/priority-rules/pom.xml
mvn -B -ntp -o -f "$priority_fixture" process-classes | tee "$logs/priority.log"
for rule in S100 S107 S112 S1128 S1192 S1066 S1481 S3776; do
    grep -E "\\[WARNING\\].*PriorityViolations\\.java:[0-9]+.*\\[java:$rule\\]" "$logs/priority.log"
done

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -Dsonarlint.profile=none -Dsonarlint.enabledRules=java:S100,java:S107 \
    -Dsonarlint.disabledRules=java:S107 | tee "$logs/selection.log"
grep -F '[java:S100]' "$logs/selection.log"
grep -F 'Sonar analysis: 1 violation(s).' "$logs/selection.log"
if grep -F '[java:S107]' "$logs/selection.log"; then
    exit 1
fi

if mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -Dsonarlint.profile=none -Dsonarlint.enabledRules=javasecurity:S3649 > "$logs/unsupported.log" 2>&1; then
    echo "Expected unsupported rule selection to fail." >&2
    exit 1
fi
grep -F 'Rule javasecurity:S3649 is not an eligible standard production Java rule' "$logs/unsupported.log"

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -Dsonarlint.profile=sonar-way | tee "$logs/sonar-way.log"
grep -F '[java:S3776]' "$logs/sonar-way.log"
