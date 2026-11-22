#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${RUNNER_TEMP:?An Eclipse runtime directory is required}"

project="$PWD/transformer-api-eclipse-source"
profile="$PWD/src/it/modern-production-rules/eclipse-private-fields-final.xml"
output="$PWD/target/eclipse-modern-rule-comparison"
mkdir -p "$output"

mvn -B -ntp -f "$project/pom.xml" compile
mvn -B -ntp -f "$project/pom.xml" \
    io.github.nbauma109:static-analysis-maven-plugin:0.1.1-SNAPSHOT:check \
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
expected = {
    "src/main/java/com/heliosdecompiler/transformerapi/decompilers/cfr/CFRDataSource.java": {30, 31, 32},
    "src/main/java/com/heliosdecompiler/transformerapi/decompilers/cfr/CFROutputStreamFactory.java": {32, 33},
    "src/main/java/com/heliosdecompiler/transformerapi/decompilers/cfr/CFRSettings.java": {29},
    "src/main/java/com/heliosdecompiler/transformerapi/decompilers/fernflower/FernflowerSettings.java": {34},
    "src/main/java/com/heliosdecompiler/transformerapi/decompilers/vineflower/VineflowerSettings.java": {35},
}
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
    actual_locations.add((path, line))
print("EXPECTED_S9438_LOCATIONS", len(expected_locations))
print("MATCHED_S9438_LOCATIONS", len(actual_locations & expected_locations))
print("MISSED_S9438_LOCATIONS", sorted(expected_locations - actual_locations))
print("UNEXPECTED_ECLIPSE_LOCATIONS", sorted(actual_locations - expected_locations))
PY

git -C "$project" diff --exit-code -- src/main/java
