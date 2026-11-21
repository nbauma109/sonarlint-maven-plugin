#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${RUNNER_TEMP:?The integration requires a temporary runtime directory}"
fixture="$PWD/src/it/real-eclipse"
source_file="$fixture/src/main/java/example/OverlapChecks.java"
output="$PWD/target/real-eclipse-integration"
mkdir -p "$output"

original_hash="$(sha256sum "$source_file")"
gradle --no-daemon -p "$fixture" staticAnalysis \
    | tee "$output/gradle-combined.log"
cp "$fixture/build/static-analysis/eclipse/diagnostics.json" "$output/gradle-combined.json"

gradle --no-daemon -p "$fixture" staticAnalysis -PduplicatePolicy=none \
    | tee "$output/gradle-deduplication-disabled.log"

gradle --no-daemon -p "$fixture" staticAnalysis -PenabledRules=java:S1128 \
    | tee "$output/gradle-sonar-disabled.log"

python3 - "$output/gradle-combined.log" "$output/gradle-deduplication-disabled.log" \
    "$output/gradle-sonar-disabled.log" "$output/gradle-combined.json" <<'PY'
import json
from pathlib import Path
import re
import sys

combined, unfiltered, fallback, report_file = (
    Path(path).read_text(encoding="utf-8") for path in sys.argv[1:]
)
issues = json.loads(report_file)["issues"]
rules = [
    ("S1128", "cleanup.remove_unused_imports"),
    ("S1161", "cleanup.add_missing_override_annotations"),
    ("S2093", "cleanup.try_with_resource"),
]

def count(text, identifier):
    return len(re.findall(r"(?m)^[^\n]*\[" + re.escape(identifier) + r"\]", text))

assert "Eclipse cleanup analysis: 0 violation(s)." in combined, combined
assert "Duplicate Eclipse findings suppressed: 3." in combined, combined
assert "Eclipse cleanup analysis: 3 violation(s)." in unfiltered, unfiltered
assert "Duplicate Eclipse findings suppressed: 0." in unfiltered, unfiltered
for rule, cleanup in rules:
    sonar = "java:" + rule
    eclipse = "eclipse:" + cleanup
    assert sum(issue["cleanup"] == cleanup for issue in issues) >= 1, cleanup
    assert count(combined, sonar) == 1, (rule, combined)
    assert count(combined, eclipse) == 0, (cleanup, combined)
    assert count(unfiltered, sonar) == 1, (rule, unfiltered)
    assert count(unfiltered, eclipse) == 1, (cleanup, unfiltered)
    expected_fallback = 0 if rule == "S1128" else 1
    assert count(fallback, eclipse) == expected_fallback, (cleanup, fallback)
print("Gradle reports each duplicated real Sonar/Eclipse issue exactly once.")
PY

test "$(sha256sum "$source_file")" = "$original_hash"
echo "Real Gradle Sonar and Eclipse integration passed without modifying sources"
