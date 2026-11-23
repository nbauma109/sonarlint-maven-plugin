#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${RUNNER_TEMP:?The runner temporary directory is required}"
fixture="$PWD/src/it/private-overlaps"
source_file="$fixture/src/main/java/example/PrivateMemberChecks.java"
output="$PWD/target/private-overlap-comparison"
mkdir -p "$output"
original_hash="$(sha256sum "$source_file")"

for option in remove_unused_private_fields remove_unused_private_methods; do
    profile="$fixture/eclipse-$option.xml"

    mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
        -DstaticAnalysis.profile=none \
        -DstaticAnalysis.enabledRules=java:S1068,java:S1144 \
        -DstaticAnalysis.eclipseEnabled=true \
        "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
        "-DstaticAnalysis.eclipseCleanupProfile=$profile" \
        | tee "$output/maven-$option.log"
    cp "$fixture/target/static-analysis/eclipse/diagnostics.json" \
        "$output/maven-$option.json"

    gradle --no-daemon -p "$fixture" staticAnalysis \
        -PenabledRules=java:S1068,java:S1144 \
        "-PcleanupProfile=eclipse-$option.xml" \
        | tee "$output/gradle-$option.log"
    cp "$fixture/build/static-analysis/eclipse/diagnostics.json" \
        "$output/gradle-$option.json"

    fallback_rule="java:S1068"
    if [ "$option" = "remove_unused_private_fields" ]; then
        fallback_rule="java:S1144"
    fi

    mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
        -DstaticAnalysis.profile=none \
        -DstaticAnalysis.enabledRules=java:S1068,java:S1144 \
        -DstaticAnalysis.eclipseEnabled=true \
        "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
        "-DstaticAnalysis.eclipseCleanupProfile=$profile" \
        -DstaticAnalysis.eclipseDuplicatePolicy=none \
        | tee "$output/maven-$option-unfiltered.log"

    gradle --no-daemon -p "$fixture" staticAnalysis \
        -PenabledRules=java:S1068,java:S1144 \
        "-PcleanupProfile=eclipse-$option.xml" -PduplicatePolicy=none \
        | tee "$output/gradle-$option-unfiltered.log"

    mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
        -DstaticAnalysis.profile=none \
        "-DstaticAnalysis.enabledRules=$fallback_rule" \
        -DstaticAnalysis.eclipseEnabled=true \
        "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
        "-DstaticAnalysis.eclipseCleanupProfile=$profile" \
        | tee "$output/maven-$option-fallback.log"

    gradle --no-daemon -p "$fixture" staticAnalysis \
        "-PenabledRules=$fallback_rule" \
        "-PcleanupProfile=eclipse-$option.xml" \
        | tee "$output/gradle-$option-fallback.log"

    python3 - "$option" "$source_file" \
        "$output/maven-$option.log" "$output/maven-$option.json" \
        "$output/gradle-$option.log" "$output/gradle-$option.json" \
        "$output/maven-$option-unfiltered.log" "$output/gradle-$option-unfiltered.log" \
        "$output/maven-$option-fallback.log" "$output/gradle-$option-fallback.log" <<'PY'
import json
from pathlib import Path
import re
import sys

option = sys.argv[1]
key = "cleanup." + option
source = Path(sys.argv[2]).read_text(encoding="utf-8")
target_name = "unusedCounter" if option.endswith("_fields") else "unusedDescription"
expected_sonar = "java:S1068" if option.endswith("_fields") else "java:S1144"
results = {}

for system, log_path, report_path in (
    ("Maven", Path(sys.argv[3]), Path(sys.argv[4])),
    ("Gradle", Path(sys.argv[5]), Path(sys.argv[6])),
):
    log = log_path.read_text(encoding="utf-8")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    issues = report["issues"]
    assert report["formatVersion"] == 1, report
    assert len(issues) == 1, (system, option, issues)
    issue = issues[0]
    assert issue["cleanup"] == key, (system, issue)
    assert issue["file"] == "src/main/java/example/PrivateMemberChecks.java", issue
    assert 0 <= issue["startOffset"] < issue["endOffset"] <= len(source), issue
    changed = source[issue["startOffset"]:issue["endOffset"]]
    assert target_name in changed, (system, option, changed)
    assert not re.search(r"\b(activeCounter|usedDescription)\b", changed), issue
    assert log.count("[" + expected_sonar + "]") == 1, (system, expected_sonar, log)
    assert log.count("[eclipse:" + key + "]") == 0, (system, key, log)
    assert "Eclipse cleanup analysis: 0 violation(s)." in log, (system, log)
    assert "Duplicate Eclipse findings suppressed: 1." in log, (system, log)
    sonar_lines = {
        int(match)
        for match in re.findall(
            r"PrivateMemberChecks\.java:(\d+).*\[" + expected_sonar + r"\]",
            log,
        )
    }
    assert issue["line"] in sonar_lines, (system, issue, sonar_lines)
    results[system] = (issue["line"], issue["startOffset"], issue["endOffset"])
    print(system, option, "Eclipse and Sonar independently flag the same declaration")
assert results["Maven"] == results["Gradle"], results

for system, unfiltered_path, fallback_path in (
    ("Maven", Path(sys.argv[7]), Path(sys.argv[9])),
    ("Gradle", Path(sys.argv[8]), Path(sys.argv[10])),
):
    unfiltered = unfiltered_path.read_text(encoding="utf-8")
    fallback = fallback_path.read_text(encoding="utf-8")
    eclipse_key = "[eclipse:" + key + "]"
    assert unfiltered.count(eclipse_key) == 1, (system, key, unfiltered)
    assert fallback.count(eclipse_key) == 1, (system, key, fallback)
    assert unfiltered.count("[" + expected_sonar + "]") == 1, unfiltered
    assert "[" + expected_sonar + "]" not in fallback, fallback
    assert "Duplicate Eclipse findings suppressed: 0." in unfiltered, unfiltered
    assert "Duplicate Eclipse findings suppressed: 0." in fallback, fallback
print("Both engines report exactly one warning for matched private members.")
PY

    test "$(sha256sum "$source_file")" = "$original_hash"
done

echo "Unused private member overlap screening preserved original Java source"
