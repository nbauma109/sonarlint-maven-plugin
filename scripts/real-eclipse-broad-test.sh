#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${RUNNER_TEMP:?The temporary runner directory is required}"
fixture="$PWD/src/it/broad-complementary"
source_file="$fixture/src/main/java/example/BroadComplementaryCleanups.java"
output="$PWD/target/real-eclipse-broad"
mkdir -p "$output"
original_sha="$(sha256sum "$source_file")"
module_file="$fixture/src/main/java/module-info.java"
module_sha="$(sha256sum "$module_file")"

mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.eclipseEnabled=true \
    "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
    "-DstaticAnalysis.eclipseCleanupProfile=$fixture/eclipse-cleanups.xml" \
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

source = Path(sys.argv[5]).read_text(encoding="utf-8")
findings = {}
for system, log_file, report_file in (
    ("Maven", Path(sys.argv[1]), Path(sys.argv[2])),
    ("Gradle", Path(sys.argv[3]), Path(sys.argv[4])),
):
    log = log_file.read_text(encoding="utf-8")
    report = json.loads(report_file.read_text(encoding="utf-8"))
    assert report["formatVersion"] == 1
    issues = report["issues"]
    for issue in issues:
        assert issue["file"] == "src/main/java/example/BroadComplementaryCleanups.java", issue
        assert 0 <= issue["startOffset"] <= issue["endOffset"] <= len(source), issue
    eclipse = {}
    counts = {}
    for issue in issues:
        eclipse.setdefault(issue["cleanup"], set()).add(issue["line"])
        counts[issue["cleanup"]] = counts.get(issue["cleanup"], 0) + 1
    expected = {
        "cleanup.system_property_javaversion",
        "cleanup.system_property_javaspecversion",
        "cleanup.add_all",
        "cleanup.reduce_indentation",
        "cleanup.module_imports",
        "cleanup.use_string_is_blank",
        "cleanup.extract_increment",
        "cleanup.pull_up_assignment",
    }
    assert set(counts) == expected, (system, counts, issues)
    assert "Duplicate Eclipse findings suppressed: 0." in log, (system, log)
    for cleanup, count in counts.items():
        assert log.count("[eclipse:" + cleanup + "]") == count, (
            system, cleanup, count, log
        )
    sonar = {}
    for line in log.splitlines():
        if "[java:S" not in line or "BroadComplementaryCleanups.java:" not in line:
            continue
        match = re.search(r"BroadComplementaryCleanups\.java:(\d+).*\[(java:S\d+)\]", line)
        if match:
            sonar.setdefault(int(match.group(1)), set()).add(match.group(2))
    overlap = {
        key: {
            "lines": sorted(lines),
            "sonar_on_lines": {
                str(line): sorted(sonar.get(line, set()))
                for line in lines if line in sonar
            },
        }
        for key, lines in sorted(eclipse.items())
    }
    for cleanup, details in overlap.items():
        for source_line, rule_keys in details["sonar_on_lines"].items():
            assert not rule_keys, (system, cleanup, source_line, rule_keys)
    findings[system] = overlap
    print("VERIFIED COMPLEMENTARY ECLIPSE OPTIONS", system, json.dumps(overlap, sort_keys=True))
assert findings["Maven"] == findings["Gradle"], findings
print("Both build adapters report the same eight complementary modernization cleanups")
PY

recommended="$fixture/eclipse-cleanups-recommended.xml"

mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.eclipseEnabled=true \
    "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
    "-DstaticAnalysis.eclipseCleanupProfile=$recommended" \
    | tee "$output/maven-recommended.log"
cp "$fixture/target/static-analysis/eclipse/diagnostics.json" \
    "$output/maven-recommended.json"

gradle --no-daemon -p "$fixture" staticAnalysis \
    -PcleanupProfile=eclipse-cleanups-recommended.xml \
    | tee "$output/gradle-recommended.log"
cp "$fixture/build/static-analysis/eclipse/diagnostics.json" \
    "$output/gradle-recommended.json"

python3 - "$output/maven-recommended.log" "$output/maven-recommended.json" \
    "$output/gradle-recommended.log" "$output/gradle-recommended.json" <<'PY'
import json
from pathlib import Path
import sys

expected = {
    "cleanup.pull_up_assignment",
    "cleanup.reduce_indentation",
    "cleanup.add_all",
}
for system, log_path, report_path in (
    ("Maven", Path(sys.argv[1]), Path(sys.argv[2])),
    ("Gradle", Path(sys.argv[3]), Path(sys.argv[4])),
):
    log = log_path.read_text(encoding="utf-8")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["formatVersion"] == 1, (system, report)
    issues = report["issues"]
    actual = [issue["cleanup"] for issue in issues]
    assert len(actual) == 3 and set(actual) == expected, (system, issues)
    assert "Duplicate Eclipse findings suppressed: 0." in log, (system, log)
    assert "Eclipse cleanup analysis: 3 violation(s)." in log, (system, log)
    for option in expected:
        assert log.count("[eclipse:" + option + "]") == 1, (system, option)
    print(f"{system}: three distinct maintainability Eclipse findings, no duplicates")
PY

aesthetics="$fixture/eclipse-cleanups-aesthetics.xml"

mvn -B -ntp -f "$fixture/pom.xml" process-test-classes \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.eclipseEnabled=true \
    "-DstaticAnalysis.eclipseExecutable=$RUNNER_TEMP/eclipse-check.sh" \
    "-DstaticAnalysis.eclipseCleanupProfile=$aesthetics" \
    | tee "$output/maven-aesthetics.log"
cp "$fixture/target/static-analysis/eclipse/diagnostics.json" \
    "$output/maven-aesthetics.json"

gradle --no-daemon -p "$fixture" staticAnalysis \
    -PcleanupProfile=eclipse-cleanups-aesthetics.xml \
    | tee "$output/gradle-aesthetics.log"
cp "$fixture/build/static-analysis/eclipse/diagnostics.json" \
    "$output/gradle-aesthetics.json"

python3 - "$output/maven-aesthetics.log" "$output/maven-aesthetics.json" \
    "$output/gradle-aesthetics.log" "$output/gradle-aesthetics.json" <<'PY'
import json
from pathlib import Path
import re
import sys

expected = {
    "cleanup.array_with_curly",
    "cleanup.remove_redundant_modifiers",
}
locations = {}
for system, log_path, report_path in (
    ("Maven", Path(sys.argv[1]), Path(sys.argv[2])),
    ("Gradle", Path(sys.argv[3]), Path(sys.argv[4])),
):
    log = log_path.read_text(encoding="utf-8")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["formatVersion"] == 1, (system, report)
    issues = report["issues"]
    actual = [issue["cleanup"] for issue in issues]
    assert len(actual) == 2 and set(actual) == expected, (system, issues)
    assert "Duplicate Eclipse findings suppressed: 0." in log, (system, log)
    assert "Eclipse cleanup analysis: 2 violation(s)." in log, (system, log)
    for option in expected:
        assert log.count("[eclipse:" + option + "]") == 1, (system, option)
    array = next(issue for issue in issues
                 if issue["cleanup"] == "cleanup.array_with_curly")
    sonar = re.findall(
        r"(?m)^.*BroadComplementaryCleanups\.java:"
        + str(array["line"]) + r".*\[java:S1488\].*$", log)
    assert len(sonar) == 1, (system, array, sonar, log)
    locations[system] = {issue["cleanup"]: issue["line"] for issue in issues}
    print(f"{system}: two opt-in aesthetic findings, no suppressed duplicates")
assert locations["Maven"] == locations["Gradle"], locations
PY

test "$(sha256sum "$source_file")" = "$original_sha"
test "$(sha256sum "$module_file")" = "$module_sha"
echo "Broad complementary candidate check completed without source modifications"
