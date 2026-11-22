#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${RUNNER_TEMP:?The runner temporary directory is required}"
fixture="$PWD/src/it/high-signal-eclipse"
source_file="$fixture/src/main/java/example/HighSignalCleanupCandidates.java"
output="$PWD/target/high-signal-comparison"
mkdir -p "$output"
original_checksum="$(sha256sum "$source_file")"

for option in precompile_regex static_inner_class; do
    profile="$fixture/eclipse-$option.xml"
    mvn -B -ntp -f "$fixture/pom.xml" process-classes \
        -DstaticAnalysis.profile=none \
        -DstaticAnalysis.enabledRules=java:S4248,java:S2694 \
        -DstaticAnalysis.eclipseEnabled=true \
        "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
        "-DstaticAnalysis.eclipseCleanupProfile=$profile" \
        | tee "$output/maven-$option.log"
    cp "$fixture/target/static-analysis/eclipse/diagnostics.json" \
        "$output/maven-$option.json"

    gradle --no-daemon -p "$fixture" staticAnalysis \
        -PenabledRules=java:S4248,java:S2694 \
        "-PcleanupProfile=eclipse-$option.xml" \
        | tee "$output/gradle-$option.log"
    cp "$fixture/build/static-analysis/eclipse/diagnostics.json" \
        "$output/gradle-$option.json"

    python3 - "$option" "$output/maven-$option.log" "$output/maven-$option.json" \
        "$output/gradle-$option.log" "$output/gradle-$option.json" <<'PY'
import json
from pathlib import Path
import re
import sys

cleanup = "cleanup." + sys.argv[1]
expected_sonar = "java:S4248" if cleanup.endswith("precompile_regex") else "java:S2694"
reports = {}
for build, log_path, report_path in (
    ("Maven", Path(sys.argv[2]), Path(sys.argv[3])),
    ("Gradle", Path(sys.argv[4]), Path(sys.argv[5])),
):
    log = log_path.read_text(encoding="utf-8")
    document = json.loads(report_path.read_text(encoding="utf-8"))
    assert document["formatVersion"] == 1, document
    issues = document["issues"]
    assert issues, (build, cleanup, document)
    assert all(issue["cleanup"] == cleanup for issue in issues), (build, issues)
    assert all(issue["file"] == "src/main/java/example/HighSignalCleanupCandidates.java"
               for issue in issues), (build, issues)
    assert "Duplicate Eclipse findings suppressed: 0." in log, (build, log)
    matches = re.findall(r"\[" + re.escape(expected_sonar) + r"\]", log)
    findings = sorted(
        (issue["line"], issue["startOffset"], issue["endOffset"])
        for issue in issues
    )
    reports[build] = (findings, len(matches))
    print(
        f"{build}: {cleanup}: {len(issues)} Eclipse source edits, "
        f"{len(matches)} matching Sonar findings"
    )
assert reports["Maven"] == reports["Gradle"], reports
PY

    test "$(sha256sum "$source_file")" = "$original_checksum"
done
echo "High-signal Sonar and Eclipse comparison completed without source changes"
