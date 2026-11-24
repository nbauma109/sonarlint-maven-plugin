#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/fixture-maven.sh
: "${RUNNER_TEMP:?An Eclipse runtime directory is required}"

project="$PWD/src/it/field-finality-parity"
profile="$PWD/src/it/modern-production-rules/eclipse-private-fields-final.xml"
output="$PWD/target/eclipse-modern-rule-comparison"
mkdir -p "$output"

fixture_maven -B -ntp -f "$project/pom.xml" compile
fixture_maven -B -ntp -f "$project/pom.xml" \
    io.github.nbauma109:static-analysis-maven-plugin:"$plugin_version":check \
    -DstaticAnalysis.profile=none \
    -DstaticAnalysis.failOnViolation=false \
    -DstaticAnalysis.eclipseEnabled=true \
    "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
    "-DstaticAnalysis.eclipseCleanupProfile=$profile" \
    | tee "$output/maven.log"

cp "$project/target/static-analysis/eclipse/diagnostics.json" "$output/diagnostics.json"

python3 - "$output/diagnostics.json" "$project" <<'PY'
import json
from pathlib import Path
import sys

report = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
project = Path(sys.argv[2])
expected = {"src/main/java/example/FieldFinality.java": {5, 6}}
expected_locations = {(name, line) for name, lines in expected.items() for line in lines}
issues = report["issues"]
actual_locations = set()
print("ECLIPSE_FINAL_ISSUE_COUNT", len(issues))
for issue in issues:
    path = issue["file"]
    line = issue["line"]
    source = (project / path).read_text(encoding="utf-8")
    source_line = source.splitlines()[line - 1]
    print("ECLIPSE_FINAL_ISSUE", json.dumps({
        "file": path,
        "line": line,
        "cleanup": issue["cleanup"],
        "module": issue["module"],
        "edit": source[issue["startOffset"]:issue["endOffset"]],
        "sourceLine": source_line.strip(),
    }, sort_keys=True))
    assert issue["cleanup"] in {"VariableDeclarationCleanUpCore", "cleanup.make_private_fields_final"}, issue
    assert issue["startOffset"] == issue["endOffset"], issue
    actual_locations.add((path, line))
print("EXPECTED_S9438_LOCATIONS", len(expected_locations))
print("MATCHED_S9438_LOCATIONS", len(actual_locations & expected_locations))
print("MISSED_S9438_LOCATIONS", sorted(expected_locations - actual_locations))
print("UNEXPECTED_ECLIPSE_LOCATIONS", sorted(actual_locations - expected_locations))
assert report["formatVersion"] == 1, report
assert len(issues) == len(expected_locations), issues
assert actual_locations == expected_locations, (actual_locations, expected_locations)
PY

fixture_maven -B -ntp -f "$project/pom.xml" \
    io.github.nbauma109:static-analysis-maven-plugin:"$plugin_version":check \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.failOnViolation=false \
    -DstaticAnalysis.eclipseEnabled=true \
    "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
    "-DstaticAnalysis.eclipseCleanupProfile=$profile" \
    | tee "$output/maven-combined.log"

fixture_maven -B -ntp -f "$project/pom.xml" \
    io.github.nbauma109:static-analysis-maven-plugin:"$plugin_version":check \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.failOnViolation=false \
    -DstaticAnalysis.eclipseEnabled=false \
    | tee "$output/maven-local-only.log"

python3 - "$output/maven-combined.log" "$output/maven-local-only.log" <<'PY'
from pathlib import Path
import sys

combined = Path(sys.argv[1]).read_text(encoding="utf-8")
local_only = Path(sys.argv[2]).read_text(encoding="utf-8")

assert "Equivalent private-field findings provided by Eclipse: 2." in combined, combined
assert combined.count("[java:S9438]") == 0, combined
assert combined.count("[eclipse:VariableDeclarationCleanUpCore]") == 2, combined
assert "Eclipse cleanup analysis: 2 violation(s)." in combined, combined
assert local_only.count("[java:S9438]") == 2, local_only
assert "[eclipse:VariableDeclarationCleanUpCore]" not in local_only, local_only
print("ECLIPSE_PREFERRED_FIELD_FINALITY", 2)
print("LOCAL_S9438_FALLBACK", 2)
PY

git diff --exit-code -- src/it/field-finality-parity/src/main/java
