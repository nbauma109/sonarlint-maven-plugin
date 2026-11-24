#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/fixture-maven.sh
mkdir -p target/integration-tests
fixture=src/it/cognitive-complexity/pom.xml
logs=target/integration-tests
python3 scripts/verify-sonar-way-snapshot.py
python3 scripts/verify-eclipse-recommendations.py
python3 scripts/verify-eclipse-overlaps.py
python3 scripts/verify-eclipse-review-queue.py
mvn -B -ntp clean install
mkdir -p "$logs"

generated_fixture=src/it/generated-sources
generated_output="$generated_fixture/target/custom-output"
rm -rf "$generated_output"
mkdir -p "$generated_output/generated-sources/main" "$generated_output/generated-test-sources/test"
cp -R "$generated_fixture/inputs/main/." "$generated_output/generated-sources/main/"
cp -R "$generated_fixture/inputs/test/." "$generated_output/generated-test-sources/test/"
fixture_maven -B -ntp -f "$generated_fixture/pom.xml" process-test-classes | tee "$logs/generated-sources.log"
test -f "$generated_output/classes/example/GeneratedMain.class"
test -f "$generated_output/test-classes/example/GeneratedTest.class"
for name in HandwrittenMain AdditionalMain; do
    grep -E "\\[WARNING\\].*${name}\\.java:[0-9]+ \\[java:S106\\]" "$logs/generated-sources.log"
    grep -E "\\[WARNING\\].*${name}\\.java:[0-9]+ \\[java:S9438\\]" "$logs/generated-sources.log"
done
grep -E '\[WARNING\].*HandwrittenTest\.java:[0-9]+ \[java:S8786\]' "$logs/generated-sources.log"
grep -F 'Test-source Java analysis: 1 violation(s).' "$logs/generated-sources.log"
if grep -E '\[WARNING\].*Generated(Main|Test)\.java:[0-9]+ \[java:' "$logs/generated-sources.log"; then
    echo "Generated sources must not produce analysis findings." >&2
    exit 1
fi

fixture_maven -B -ntp -f "$fixture" process-test-classes -DstaticAnalysis.profile=complexity | tee "$logs/warnings.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/warnings.log"
grep -F 'Sonar analysis: 1 violation(s).' "$logs/warnings.log"

fixture_maven -B -ntp -o -f "$fixture" process-test-classes -DstaticAnalysis.profile=complexity | tee "$logs/offline.log"
grep -E '\[WARNING\].*ComplexMethod\.java:[0-9]+.*\[java:S3776\]' "$logs/offline.log"

if fixture_maven -B -ntp -o -f "$fixture" process-test-classes -DstaticAnalysis.profile=complexity -DstaticAnalysis.failOnViolation=true > "$logs/failure.log" 2>&1; then
    cat "$logs/failure.log"
    echo "Expected the build to fail for a cognitive complexity violation." >&2
    exit 1
fi
cat "$logs/failure.log"
grep -F 'Sonar found 1 violation(s).' "$logs/failure.log"

fixture_maven -B -ntp -o -f "$fixture" process-test-classes -DstaticAnalysis.profile=complexity -DstaticAnalysis.cognitiveComplexityThreshold=100 | tee "$logs/clean.log"
grep -F 'Sonar analysis: 0 violation(s).' "$logs/clean.log"

logger_fixture=src/it/logger-categories/pom.xml
if fixture_maven -B -ntp -f "$logger_fixture" package > "$logs/logger-category-strict.log" 2>&1; then
    cat "$logs/logger-category-strict.log"
    echo "Expected the normal Maven build to fail at process-test-classes for an incorrect logger category." >&2
    exit 1
fi
grep -F 'Activated 395 local Java rule(s).' "$logs/logger-category-strict.log"
grep -E '\[WARNING\].*LoggerCategories\.java:[0-9]+ \[java:S3416\] Update this logger to use "WrongCategory.class".' \
    "$logs/logger-category-strict.log"
logger_issues="$(grep -Ec '\[WARNING\].*\[java:S3416\]' "$logs/logger-category-strict.log" || true)"
if [ "$logger_issues" -ne 1 ]; then
    cat "$logs/logger-category-strict.log"
    echo "Expected one S3416 finding for the wrong logger and none for the matching logger." >&2
    exit 1
fi
grep -F 'Sonar found 1 violation(s).' "$logs/logger-category-strict.log"

fixture_maven -B -ntp -o -f "$logger_fixture" process-test-classes \
    -DstaticAnalysis.disabledRules=java:S3416 | tee "$logs/logger-category-disabled.log"
grep -F 'Activated 394 local Java rule(s).' "$logs/logger-category-disabled.log"
grep -F 'Sonar analysis: 0 violation(s).' "$logs/logger-category-disabled.log"

embedded_fixture=src/it/embedded-eclipse/pom.xml
if fixture_maven -B -ntp -f "$embedded_fixture" process-test-classes > "$logs/embedded-eclipse-strict.log" 2>&1; then
    cat "$logs/embedded-eclipse-strict.log"
    echo "Expected bundled JDT Core finality cleanup to fail the Maven build." >&2
    exit 1
fi
grep -F 'Running embedded Eclipse JDT cleanup analysis without an IDE.' \
    "$logs/embedded-eclipse-strict.log"
grep -E '\[WARNING\].*EmbeddedJdtFinality\.java:[0-9]+:[0-9]+ \[eclipse:VariableDeclarationCleanUpCore\]' \
    "$logs/embedded-eclipse-strict.log"
grep -F 'Eclipse cleanup analysis: 1 violation(s).' "$logs/embedded-eclipse-strict.log"
grep -F 'Static analysis found 1 violation(s).' "$logs/embedded-eclipse-strict.log"

embedded_profile="$(pwd)/src/it/embedded-eclipse/eclipse-private-fields-final.xml"
fixture_maven -B -ntp -f "$embedded_fixture" process-test-classes \
    -DstaticAnalysis.failOnViolation=false \
    "-DstaticAnalysis.eclipseCleanupProfile=$embedded_profile" \
    | tee "$logs/embedded-eclipse-profile.log"
grep -F 'Eclipse cleanup analysis: 1 violation(s).' "$logs/embedded-eclipse-profile.log"

unsupported_profile="$(pwd)/src/it/embedded-eclipse/eclipse-unsupported-cleanup.xml"
if fixture_maven -B -ntp -f "$embedded_fixture" process-test-classes \
    "-DstaticAnalysis.eclipseCleanupProfile=$unsupported_profile" \
    > "$logs/embedded-eclipse-unsupported.log" 2>&1; then
    cat "$logs/embedded-eclipse-unsupported.log"
    echo "Expected unsupported embedded cleanup option to fail." >&2
    exit 1
fi
grep -F 'The embedded Eclipse engine does not support cleanup: cleanup.make_parameters_final' \
    "$logs/embedded-eclipse-unsupported.log"

fixture_maven -B -ntp -o -f "$embedded_fixture" process-test-classes \
    -DstaticAnalysis.eclipseEnabled=false | tee "$logs/embedded-eclipse-disabled.log"
grep -F 'Sonar analysis: 0 violation(s).' "$logs/embedded-eclipse-disabled.log"

production_fixture=src/it/modern-production-rules/pom.xml
if fixture_maven -B -ntp -o -f "$production_fixture" process-test-classes \
    > "$logs/modern-production-strict.log" 2>&1; then
    cat "$logs/modern-production-strict.log"
    echo "Expected supplemental production checks to fail the build." >&2
    exit 1
fi
for rule in S3398 S9398 S9438; do
    grep -E "\[WARNING\].*ModernProductionIssues\.java:[0-9]+ \[java:$rule\]" \
        "$logs/modern-production-strict.log"
done
grep -F 'Sonar found 4 violation(s).' "$logs/modern-production-strict.log"
test "$(grep -Ec '\[WARNING\].*\[java:S9438\]' "$logs/modern-production-strict.log")" -eq 2
fixture_maven -B -ntp -o -f "$production_fixture" process-test-classes \
    -DstaticAnalysis.disabledRules=java:S3398,java:S9398,java:S9438 \
    | tee "$logs/modern-production-disabled.log"

null_fixture=src/it/null-safety/pom.xml
if fixture_maven -B -ntp -o -f "$null_fixture" process-test-classes > "$logs/null-safety-strict.log" 2>&1; then
    cat "$logs/null-safety-strict.log"
    echo "Expected unguarded null dereference to fail the build." >&2
    exit 1
fi
grep -E '\[WARNING\].*NullSafetyIssues\.java:[0-9]+ \[java:S2259\]' \
    "$logs/null-safety-strict.log"
test "$(grep -Ec '\[WARNING\].*\[java:S2259\]' "$logs/null-safety-strict.log")" -eq 1
grep -F 'Sonar found 1 violation(s).' "$logs/null-safety-strict.log"
fixture_maven -B -ntp -o -f "$null_fixture" process-test-classes -DstaticAnalysis.disabledRules=java:S2259 \
    | tee "$logs/null-safety-disabled.log"
grep -F 'Sonar analysis: 0 violation(s).' "$logs/null-safety-disabled.log"

test_fixture=src/it/modern-test-rules/pom.xml
java_major="$(java -XshowSettings:properties -version 2>&1 \
    | sed -n 's/^ *java.specification.version = \([0-9]*\).*$/\1/p' | head -1)"
for compiler_release in 17 21 22 25; do
    if [ "$compiler_release" -gt "$java_major" ]; then
        continue
    fi
    expected_test_issues=6
    if [ "$compiler_release" -ge 22 ]; then
        expected_test_issues=7
    fi
    log="$logs/modern-test-java-$compiler_release.log"
    if fixture_maven -B -ntp -o -f "$test_fixture" clean test -Dmaven.compiler.release="$compiler_release" \
        > "$log" 2>&1; then
        cat "$log"
        echo "Expected supplemental test checks to fail the build." >&2
        exit 1
    fi
    grep -F "Test-source analysis found $expected_test_issues violation(s)." "$log"
    test "$(grep -Ec '\[WARNING\].*\[java:S8786\]' "$log")" -eq 6
    test "$(grep -Ec '\[WARNING\].*WhitespaceRegexIssues\.java:[0-9]+ \[java:S8786\]' "$log")" -eq 4
    if [ "$compiler_release" -ge 22 ]; then
        grep -E '\[WARNING\].*ModernTestIssues\.java:[0-9]+ \[java:S7467\]' "$log"
    elif grep -F '[java:S7467]' "$log"; then
        exit 1
    fi
    fixture_maven -B -ntp -o -f "$test_fixture" test -Dmaven.compiler.release="$compiler_release" \
        -DstaticAnalysis.disabledRules=java:S8786,java:S7467 \
        | tee "$logs/modern-test-disabled-java-$compiler_release.log"
done

native_test_fixture=src/it/native-test-rules/pom.xml
for goal in check check-tests; do
    log="$logs/native-test-rules-$goal.log"
    fixture_maven -B -ntp -f "$native_test_fixture" test-compile \
        io.github.nbauma109:static-analysis-maven-plugin:"$plugin_version":"$goal" | tee "$log"
    for rule in S1130 S5961; do
        test "$(grep -Ec "\\[WARNING\\].*NativeTestIssues\\.java:[0-9]+ \\[java:$rule\\]" "$log")" -eq 1
    done
done

bash scripts/java-version-test.sh "$java_major"

priority_fixture=src/it/priority-rules/pom.xml
fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes | tee "$logs/priority.log"
for rule in S100 S107 S112 S1128 S1192 S1066 S1481 S3776; do
    grep -E "\\[WARNING\\].*PriorityViolations\\.java:[0-9]+.*\\[java:$rule\\]" "$logs/priority.log"
done

fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
    -DstaticAnalysis.profile=none -DstaticAnalysis.enabledRules=java:S100,java:S107 \
    -DstaticAnalysis.disabledRules=java:S107 | tee "$logs/selection.log"
grep -F '[java:S100]' "$logs/selection.log"
grep -F 'Sonar analysis: 1 violation(s).' "$logs/selection.log"
if grep -F '[java:S107]' "$logs/selection.log"; then
    exit 1
fi

if fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
    -DstaticAnalysis.profile=none -DstaticAnalysis.enabledRules=javasecurity:S3649 > "$logs/unsupported.log" 2>&1; then
    echo "Expected unsupported rule selection to fail." >&2
    exit 1
fi
grep -F 'Rule javasecurity:S3649 is not an eligible standard production Java rule' "$logs/unsupported.log"

fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
    -DstaticAnalysis.profile=sonar-way | tee "$logs/sonar-way.log"
grep -F 'Activated 395 local Java rule(s).' "$logs/sonar-way.log"
grep -F '[java:S3776]' "$logs/sonar-way.log"
for rule in S1124 S1155; do
    grep -E "\\[WARNING\\].*AestheticPreferences\\.java:[0-9]+.*\\[java:$rule\\]" \
        "$logs/sonar-way.log"
done

aesthetic_source=src/it/priority-rules/src/main/java/example/AestheticPreferences.java
aesthetic_checksum="$(sha256sum "$aesthetic_source")"
fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
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

fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
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

fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
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

fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.enabledRules=java:S1905 \
    -DstaticAnalysis.disabledRules=java:S1905,java:S1481 | tee "$logs/eclipse-mapped-disabled.log"
grep -F 'Remove unnecessary cast' "$logs/eclipse-mapped-disabled.log"
grep -F 'Remove unused local variable' "$logs/eclipse-mapped-disabled.log"
grep -F 'Duplicate Eclipse findings suppressed: 1.' "$logs/eclipse-mapped-disabled.log"

fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.eclipseDuplicatePolicy=none | tee "$logs/eclipse-without-deduplication.log"
grep -F '[eclipse:cleanup.remove_unused_imports]' "$logs/eclipse-without-deduplication.log"
grep -F 'Duplicate Eclipse findings suppressed: 0.' "$logs/eclipse-without-deduplication.log"

fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.disabledRules=java:S1128 | tee "$logs/eclipse-without-sonar-import.log"
grep -F '[eclipse:cleanup.remove_unused_imports]' "$logs/eclipse-without-sonar-import.log"
grep -F 'Eclipse cleanup analysis: 4 violation(s).' "$logs/eclipse-without-sonar-import.log"
grep -F 'Duplicate Eclipse findings suppressed: 1.' "$logs/eclipse-without-sonar-import.log"

if fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
    -DstaticAnalysis.eclipseEnabled=true \
    -DstaticAnalysis.eclipseExecutable="$eclipse_executable" \
    -DstaticAnalysis.eclipseCleanupProfile="$eclipse_profile" \
    -DstaticAnalysis.failOnViolation=true > "$logs/eclipse-fail.log" 2>&1; then
    echo "Expected combined analysis to fail the build." >&2
    exit 1
fi
grep -F 'Static analysis found' "$logs/eclipse-fail.log"

if fixture_maven -B -ntp -o -f "$priority_fixture" process-test-classes \
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

fixture_maven -B -ntp -o -f "$reliability_fixture" process-test-classes \
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

fixture_maven -B -ntp -o -f "$reliability_fixture" process-test-classes \
    -DstaticAnalysis.profile=reliability \
    -DstaticAnalysis.disabledRules=java:S2259 | tee "$logs/reliability-disabled.log"
grep -F 'Sonar analysis: 2 violation(s).' "$logs/reliability-disabled.log"
if grep -F '[java:S2259]' "$logs/reliability-disabled.log"; then
    echo "Disabled null-dereference rule was unexpectedly reported." >&2
    exit 1
fi
test "$(sha256sum "$reliability_source")" = "$reliability_checksum"

/usr/bin/time -f '%e %M' -o "$logs/standard-sonar-way-timing.txt" \
    mvn "-DstaticAnalysis.pluginVersion=$plugin_version" -B -ntp -o -f "$reliability_fixture" process-test-classes \
        -DstaticAnalysis.profile=sonar-way-standard > "$logs/standard-sonar-way.log" 2>&1
grep -F 'Activated 392 local Java rule(s).' "$logs/standard-sonar-way.log"

/usr/bin/time -f '%e %M' -o "$logs/sonar-way-reliability-timing.txt" \
    mvn "-DstaticAnalysis.pluginVersion=$plugin_version" -B -ntp -o -f "$reliability_fixture" process-test-classes \
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
