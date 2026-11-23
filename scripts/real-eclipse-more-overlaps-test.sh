#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${RUNNER_TEMP:?A temporary runner directory is required}"
fixture="$PWD/src/it/verified-overlaps"
file="$fixture/src/main/java/example/MoreOverlapChecks.java"
profile="$fixture/eclipse-cleanups.xml"
output="$PWD/target/verified-overlaps"
mkdir -p "$output"
original_hash="$(sha256sum "$file")"

common=(
    -DstaticAnalysis.profile=none
    -DstaticAnalysis.eclipseEnabled=true
    "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh"
    "-DstaticAnalysis.eclipseCleanupProfile=$profile"
)
all_rules="java:S2129,java:S2293,java:S3878,java:S3626"

mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
    "${common[@]}" "-DstaticAnalysis.enabledRules=$all_rules" \
    | tee "$output/maven-combined.log"
cp "$fixture/target/static-analysis/eclipse/diagnostics.json" "$output/maven.json"

mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
    "${common[@]}" "-DstaticAnalysis.enabledRules=$all_rules" \
    -DstaticAnalysis.eclipseDuplicatePolicy=none \
    | tee "$output/maven-unfiltered.log"

mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
    "${common[@]}" -DstaticAnalysis.enabledRules=java:S2129 \
    | tee "$output/maven-fallback.log"

gradle --no-daemon -p "$fixture" staticAnalysis \
    | tee "$output/gradle-combined.log"
cp "$fixture/build/static-analysis/eclipse/diagnostics.json" "$output/gradle.json"

gradle --no-daemon -p "$fixture" staticAnalysis -PduplicatePolicy=none \
    | tee "$output/gradle-unfiltered.log"

gradle --no-daemon -p "$fixture" staticAnalysis -PenabledRules=java:S2129 \
    | tee "$output/gradle-fallback.log"

python3 - "$output" <<'PY'
import json
from pathlib import Path
import re
import sys

output = Path(sys.argv[1])
rules = [
    ("S2129", "cleanup.no_string_creation"),
    ("S2293", "cleanup.remove_redundant_type_arguments"),
    ("S3878", "cleanup.remove_unnecessary_array_creation"),
    ("S3626", "cleanup.useless_return"),
]

def count(log, key):
    return len(re.findall(r"(?m)^.*\[" + re.escape(key) + r"\]", log))

for system in ("maven", "gradle"):
    combined = (output / f"{system}-combined.log").read_text(encoding="utf-8")
    unfiltered = (output / f"{system}-unfiltered.log").read_text(encoding="utf-8")
    fallback = (output / f"{system}-fallback.log").read_text(encoding="utf-8")
    report = json.loads((output / f"{system}.json").read_text(encoding="utf-8"))
    issues = report["issues"]
    assert report["formatVersion"] == 1, (system, report)
    assert len(issues) == len(rules), (system, issues)
    assert {issue["cleanup"] for issue in issues} == {
        option for _, option in rules
    }, (system, issues)
    assert "Eclipse cleanup analysis: 0 violation(s)." in combined, combined
    assert "Duplicate Eclipse findings suppressed: 4." in combined, combined
    assert "Eclipse cleanup analysis: 4 violation(s)." in unfiltered, unfiltered
    assert "Duplicate Eclipse findings suppressed: 0." in unfiltered, unfiltered
    for rule, option in rules:
        sonar_key = "java:" + rule
        eclipse_key = "eclipse:" + option
        assert count(combined, sonar_key) == 1, (system, sonar_key, combined)
        assert count(combined, eclipse_key) == 0, (system, eclipse_key, combined)
        assert count(unfiltered, sonar_key) == 1, (system, sonar_key, unfiltered)
        assert count(unfiltered, eclipse_key) == 1, (system, eclipse_key, unfiltered)
        assert count(fallback, eclipse_key) == (0 if rule == "S2129" else 1), (
            system, eclipse_key, fallback
        )
    print(f"{system}: four exact-once real Sonar and Eclipse overlaps verified")
PY

test "$(sha256sum "$file")" = "$original_hash"
echo "Four additional overlap mappings passed without modifying source"
