#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${RUNNER_TEMP:?The integration requires a temporary runtime directory}"
fixture="$PWD/src/it/real-eclipse"
java_file="$fixture/src/main/java/example/OverlapChecks.java"
profile="$fixture/eclipse-cleanups.xml"
executable="$RUNNER_TEMP/eclipse-check.sh"
output="$PWD/target/real-eclipse-integration"
mkdir -p "$output"

source_hash="$(sha256sum "$java_file")"
common=(
    -DstaticAnalysis.profile=none
    -DstaticAnalysis.eclipseEnabled=true
    "-DstaticAnalysis.eclipseExecutable=$executable"
    "-DstaticAnalysis.eclipseCleanupProfile=$profile"
)
mvn -B -ntp -f "$fixture/pom.xml" process-classes \
    "${common[@]}" \
    -DstaticAnalysis.enabledRules=java:S1128,java:S1161,java:S2093 \
    | tee "$output/combined.log"
cp "$fixture/target/static-analysis/eclipse/diagnostics.json" "$output/combined.json"
cat "$output/combined.json"

python3 - "$output/combined.log" "$output/combined.json" <<'PY'
import json
from pathlib import Path
import re
import sys

log = Path(sys.argv[1]).read_text(encoding="utf-8")
issues = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))["issues"]
assert "Eclipse cleanup analysis: 0 violation(s)." in log, log
assert "Duplicate Eclipse findings suppressed: 3." in log, log
for rule, cleanup in [
    ("S1128", "cleanup.remove_unused_imports"),
    ("S1161", "cleanup.add_missing_override_annotations"),
    ("S2093", "cleanup.try_with_resource"),
]:
    sonar_count = len(re.findall(r"(?m)^\[WARNING\].*\[java:" + rule + r"\]", log))
    eclipse_count = len(re.findall(r"(?m)^\[WARNING\].*\[eclipse:" + re.escape(cleanup) + r"\]", log))
    source_count = sum(issue["cleanup"] == cleanup for issue in issues)
    print(f"{rule}/{cleanup}: sonar={sonar_count}, eclipse={eclipse_count}, eclipse-edits={source_count}")
    assert sonar_count == 1, f"Expected one genuine Sonar issue for {rule}"
    assert source_count >= 1, f"Expected a genuine Eclipse cleanup for {cleanup}"
    assert eclipse_count == 0, f"Duplicate cleanup {cleanup} reached the console"
PY

mvn -B -ntp -f "$fixture/pom.xml" process-classes \
    "${common[@]}" \
    -DstaticAnalysis.enabledRules=java:S1128 \
    | tee "$output/sonar-disabled.log"
python3 - "$output/sonar-disabled.log" <<'PY'
from pathlib import Path
import re
import sys

log = Path(sys.argv[1]).read_text(encoding="utf-8")
for cleanup in ("cleanup.add_missing_override_annotations", "cleanup.try_with_resource"):
    count = len(re.findall(r"(?m)^\[WARNING\].*\[eclipse:" + re.escape(cleanup) + r"\]", log))
    assert count == 1, f"Expected one Eclipse fallback warning for {cleanup}, received {count}"
PY
test "$(sha256sum "$java_file")" = "$source_hash"
echo "Real Sonar and Eclipse overlap integration passed"
