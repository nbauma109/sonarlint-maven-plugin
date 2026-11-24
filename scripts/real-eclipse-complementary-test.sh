#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
source scripts/fixture-maven.sh
: "${RUNNER_TEMP:?A temporary runtime directory is required}"
fixture="$PWD/src/it/complementary-eclipse"
source_file="$fixture/src/main/java/example/ComplementarySystemProperties.java"
profile="$fixture/eclipse-cleanups.xml"
output="$PWD/target/real-eclipse-complementary"
mkdir -p "$output"

source_hash="$(sha256sum "$source_file")"
fixture_maven -B -ntp -f "$fixture/pom.xml" process-test-classes \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.eclipseEnabled=true \
    "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
    "-DstaticAnalysis.eclipseCleanupProfile=$profile" \
    | tee "$output/maven.log"
cp "$fixture/target/static-analysis/eclipse/diagnostics.json" "$output/maven.json"

gradle --no-daemon -p "$fixture" staticAnalysis | tee "$output/gradle.log"
cp "$fixture/build/static-analysis/eclipse/diagnostics.json" "$output/gradle.json"

python3 - "$output/maven.log" "$output/maven.json" \
    "$output/gradle.log" "$output/gradle.json" "$source_file" <<'PY'
import json
from pathlib import Path
import re
import sys

expected = {
    "cleanup.system_property_file_separator",
    "cleanup.system_property_path_separator",
    "cleanup.system_property_line_separator",
    "cleanup.system_property_file_encoding",
    "cleanup.system_property_boolean",
}
source = Path(sys.argv[5]).read_text(encoding="utf-8")
target_lines = {
    number
    for number, line in enumerate(source.splitlines(), 1)
    if 'System.getProperty(' in line
}
assert len(target_lines) == 5, target_lines

for label, log_file, report_file in (
    ("Maven", Path(sys.argv[1]), Path(sys.argv[2])),
    ("Gradle", Path(sys.argv[3]), Path(sys.argv[4])),
):
    log = log_file.read_text(encoding="utf-8")
    report = json.loads(report_file.read_text(encoding="utf-8"))
    issues = report["issues"]
    actual = {issue["cleanup"] for issue in issues}
    assert report["formatVersion"] == 1, (label, report)
    assert actual == expected, (label, actual, issues)
    assert len(issues) == 5, (label, issues)
    assert "Eclipse cleanup analysis: 5 violation(s)." in log, (label, log)
    assert "Duplicate Eclipse findings suppressed: 0." in log, (label, log)
    for cleanup in expected:
        assert log.count("[eclipse:" + cleanup + "]") == 1, (label, cleanup, log)
    for match in re.finditer(r"(?m)^.*\[java:S[0-9]+\].*$", log):
        line = match.group(0)
        source_line = re.search(r"ComplementarySystemProperties\.java:(\d+)", line)
        if source_line is not None:
            assert int(source_line.group(1)) not in target_lines, (label, line)
    print(f"{label}: five distinct complementary Eclipse warnings; no duplicates")
PY

test "$(sha256sum "$source_file")" = "$source_hash"
echo "Both build adapters preserved five complementary Eclipse warnings and source bytes"
