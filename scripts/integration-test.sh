#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p target/integration-tests
fixture=src/it/cognitive-complexity/pom.xml
logs=target/integration-tests
python3 scripts/verify-sonar-way-snapshot.py
python3 scripts/verify-eclipse-recommendations.py
python3 scripts/verify-eclipse-overlaps.py
python3 scripts/verify-eclipse-review-queue.py
mvn -B -ntp clean install
mkdir -p "$logs"

mvn -B -ntp -f "$fixture" process-classes -DstaticAnalysis.profile=complexity | tee "$logs/warnings.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/warnings.log"
grep -F 'Sonar analysis: 1 violation(s).' "$logs/warnings.log"

mvn -B -ntp -o -f "$fixture" process-classes -DstaticAnalysis.profile=complexity | tee "$logs/offline.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/offline.log"

if mvn -B -ntp -o -f "$fixture" process-classes -DstaticAnalysis.profile=complexity -DstaticAnalysis.failOnViolation=true > "$logs/failure.log" 2>&1; then
    cat "$logs/failure.log"
    echo "Expected the build to fail for a cognitive complexity violation." >&2
    exit 1
fi
cat "$logs/failure.log"
grep -F 'Sonar found 1 violation(s).' "$logs/failure.log"

mvn -B -ntp -o -f "$fixture" process-classes -DstaticAnalysis.profile=complexity -DstaticAnalysis.cognitiveComplexityThreshold=100 | tee "$logs/clean.log"
grep -F 'Sonar analysis: 0 violation(s).' "$logs/clean.log"

priority_fixture=src/it/priority-rules/pom.xml
mvn -B -ntp -o -f "$priority_fixture" process-classes | tee "$logs/priority.log"
for rule in S100 S107 S112 S1128 S1192 S1066 S1481 S3776; do
    grep -E "\\[WARNING\\].*PriorityViolations\\.java:[0-9]+.*\\[java:$rule\\]" "$logs/priority.log"
done

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.profile=none -DstaticAnalysis.enabledRules=java:S100,java:S107 \
    -DstaticAnalysis.disabledRules=java:S107 | tee "$logs/selection.log"
grep -F '[java:S100]' "$logs/selection.log"
grep -F 'Sonar analysis: 1 violation(s).' "$logs/selection.log"
if grep -F '[java:S107]' "$logs/selection.log"; then
    exit 1
fi

if mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.profile=none -DstaticAnalysis.enabledRules=javasecurity:S3649 > "$logs/unsupported.log" 2>&1; then
    echo "Expected unsupported rule selection to fail." >&2
    exit 1
fi
grep -F 'Rule javasecurity:S3649 is not an eligible standard production Java rule' "$logs/unsupported.log"

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.profile=sonar-way | tee "$logs/sonar-way.log"
grep -F 'Activated 395 local Java rule(s).' "$logs/sonar-way.log"
grep -F '[java:S3776]' "$logs/sonar-way.log"
for rule in S1124 S1155; do
    grep -E "\\[WARNING\\].*AestheticPreferences\\.java:[0-9]+.*\\[java:$rule\\]" \
        "$logs/sonar-way.log"
done

aesthetic_source=src/it/priority-rules/src/main/java/example/AestheticPreferences.java
aesthetic_checksum="$(sha256sum "$aesthetic_source")"
mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.profile=none \
    -DstaticAnalysis.enabledRules=java:S1124,java:S1155 \
    | tee "$logs/aesthetic-conventions.log"
grep -F 'Sonar analysis: 2 violation(s).' "$logs/aesthetic-conventions.log"
for rule in S1124 S1155; do
    count="$(grep -Ec "\\[WARNING\\].*AestheticPreferences\\.java:[0-9]+.*\\[java:$rule\\]" "$logs/aesthetic-conventions.log" || true)"
    if [ "$count" -ne 1 ]; then
        echo "Expected exactly one source-located aesthetic warning for $rule, got $count." >&2
        exit 1
    fi
done
test "$(sha256sum "$aesthetic_source")" = "$aesthetic_checksum"


eclipse_executable="$(pwd)/scripts/mock-eclipse.sh"
eclipse_profile="$(pwd)/src/it/priority-rules/eclipse-cleanup-profile.xml"
source_file="src/it/priority-rules/src/main/java/example/PriorityViolations.java"
source_checksum="$(sha256sum "$source_file")"

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" | tee "$logs/eclipse.log"
grep -F '[eclipse:cleanup.pull_up_assignment]' "$logs/eclipse.log"
grep -F 'Eclipse cleanup analysis: 3 violation(s).' "$logs/eclipse.log"
grep -F 'Duplicate Eclipse findings suppressed: 2.' "$logs/eclipse.log"
if grep -F '[eclipse:cleanup.remove_unused_imports]' "$logs/eclipse.log"; then
    echo "The duplicate unused import warning was not filtered." >&2
    exit 1
fi

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.enabledRules=java:S1905 | tee "$logs/eclipse-mapped.log"
grep -F '[java:S1905]' "$logs/eclipse-mapped.log"
grep -F 'Eclipse cleanup analysis: 2 violation(s).' "$logs/eclipse-mapped.log"
grep -F 'Duplicate Eclipse findings suppressed: 3.' "$logs/eclipse-mapped.log"
grep -F 'Unrelated edit on the cast line' "$logs/eclipse-mapped.log"
grep -F '[eclipse:cleanup.pull_up_assignment]' "$logs/eclipse-mapped.log"
if grep -F 'Remove unnecessary cast' "$logs/eclipse-mapped.log"         || grep -F 'Remove unused local variable' "$logs/eclipse-mapped.log"; then
    echo "A matching Eclipse warning was not suppressed." >&2
    exit 1
fi

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.enabledRules=java:S1905 \
    -DstaticAnalysis.disabledRules=java:S1905,java:S1481 | tee "$logs/eclipse-mapped-disabled.log"
grep -F 'Remove unnecessary cast' "$logs/eclipse-mapped-disabled.log"
grep -F 'Remove unused local variable' "$logs/eclipse-mapped-disabled.log"
grep -F 'Duplicate Eclipse findings suppressed: 1.' "$logs/eclipse-mapped-disabled.log"

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.eclipseDuplicatePolicy=none | tee "$logs/eclipse-without-deduplication.log"
grep -F '[eclipse:cleanup.remove_unused_imports]' "$logs/eclipse-without-deduplication.log"
grep -F 'Duplicate Eclipse findings suppressed: 0.' "$logs/eclipse-without-deduplication.log"

mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.disabledRules=java:S1128 | tee "$logs/eclipse-without-sonar-import.log"
grep -F '[eclipse:cleanup.remove_unused_imports]' "$logs/eclipse-without-sonar-import.log"
grep -F 'Eclipse cleanup analysis: 4 violation(s).' "$logs/eclipse-without-sonar-import.log"
grep -F 'Duplicate Eclipse findings suppressed: 1.' "$logs/eclipse-without-sonar-import.log"

if mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.failOnViolation=true > "$logs/eclipse-fail.log" 2>&1; then
    echo "Expected combined analysis to fail the build." >&2
    exit 1
fi
grep -F 'Static analysis found' "$logs/eclipse-fail.log"

if mvn -B -ntp -o -f "$priority_fixture" process-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$(pwd)/src/it/priority-rules/missing-profile.xml" \
    > "$logs/eclipse-missing-profile.log" 2>&1; then
    echo "Expected a missing Eclipse profile to fail analysis." >&2
    exit 1
fi
grep -F 'The Eclipse cleanup profile does not exist' "$logs/eclipse-missing-profile.log"

test "$(sha256sum "$source_file")" = "$source_checksum"

reliability_fixture=src/it/reliability/pom.xml
reliability_source=src/it/reliability/src/main/java/example/ReliabilityIssues.java
reliability_checksum="$(sha256sum "$reliability_source")"

mvn -B -ntp -o -f "$reliability_fixture" process-classes \
    -DstaticAnalysis.profile=reliability | tee "$logs/reliability.log"
grep -F 'Activated 3 local Java rule(s).' "$logs/reliability.log"
grep -F 'Sonar analysis: 3 violation(s).' "$logs/reliability.log"
for rule in S2095 S2259 S3655; do
    count="$(grep -Ec "\\[WARNING\\].*ReliabilityIssues\\.java:[0-9]+.*\\[java:$rule\\]" "$logs/reliability.log" || true)"
    if [ "$count" -ne 1 ]; then
        echo "Expected one source-located symbolic reliability finding for $rule, got $count." >&2
        exit 1
    fi
done

mvn -B -ntp -o -f "$reliability_fixture" process-classes \
    -DstaticAnalysis.profile=reliability \
    -DstaticAnalysis.disabledRules=java:S2259 | tee "$logs/reliability-disabled.log"
grep -F 'Sonar analysis: 2 violation(s).' "$logs/reliability-disabled.log"
if grep -F '[java:S2259]' "$logs/reliability-disabled.log"; then
    echo "Disabled null-dereference rule was unexpectedly reported." >&2
    exit 1
fi
test "$(sha256sum "$reliability_source")" = "$reliability_checksum"

/usr/bin/time -f '%e %M' -o "$logs/standard-sonar-way-timing.txt" \
    mvn -B -ntp -o -f "$reliability_fixture" process-classes \
        -DstaticAnalysis.profile=sonar-way-standard > "$logs/standard-sonar-way.log" 2>&1
grep -F 'Activated 392 local Java rule(s).' "$logs/standard-sonar-way.log"

/usr/bin/time -f '%e %M' -o "$logs/sonar-way-reliability-timing.txt" \
    mvn -B -ntp -o -f "$reliability_fixture" process-classes \
        -DstaticAnalysis.profile=sonar-way | tee "$logs/sonar-way-reliability.log"
grep -F 'Activated 395 local Java rule(s).' "$logs/sonar-way-reliability.log"
for rule in S2095 S2259 S3655; do
    grep -E "\\[WARNING\\].*ReliabilityIssues\\.java:[0-9]+.*\\[java:$rule\\]" \
        "$logs/sonar-way-reliability.log"
done
test "$(sha256sum "$reliability_source")" = "$reliability_checksum"


python3 - "$logs/standard-sonar-way-timing.txt" \
    "$logs/sonar-way-reliability-timing.txt" <<'PY'
from pathlib import Path
import sys

def measurements(path):
    elapsed, peak_memory = Path(path).read_text(encoding="utf-8").strip().split()
    return float(elapsed), int(peak_memory)

standard = measurements(sys.argv[1])
reliability = measurements(sys.argv[2])
ratio = reliability[0] / standard[0]
print(f"Standard Sonar Way: {standard[0]:.2f} seconds, {standard[1]} KiB peak memory")
print(f"Sonar Way plus symbolic reliability: {reliability[0]:.2f} seconds, "
      f"{reliability[1]} KiB peak memory")
print(f"Observed runtime ratio: {ratio:.2f}x")
PY
