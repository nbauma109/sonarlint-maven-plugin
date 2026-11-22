#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
fixture="$PWD/src/it/symbolic-execution"
output="$PWD/target/symbolic-engine-probe"
source_file="$fixture/src/main/java/example/SymbolicFailures.java"
mkdir -p "$output"
original="$(sha256sum "$source_file")"

mvn -B -ntp install

/usr/bin/time -f '%e %M' -o "$output/maven-symbolic-time.txt" \
    mvn -B -ntp -f "$fixture/pom.xml" process-classes \
        -DstaticAnalysis.profile=none \
        -DstaticAnalysis.symbolicEnabled=true \
        -DstaticAnalysis.enabledRules=java:S2259,java:S2095,java:S3655 \
        > "$output/maven-symbolic.log" 2>&1

grep -F '[java:S2259]' "$output/maven-symbolic.log"
grep -F '[java:S2095]' "$output/maven-symbolic.log"
grep -F '[java:S3655]' "$output/maven-symbolic.log"

gradle --no-daemon -p "$fixture" staticAnalysis \
    > "$output/gradle-symbolic.log" 2>&1

grep -F '[java:S2259]' "$output/gradle-symbolic.log"
grep -F '[java:S2095]' "$output/gradle-symbolic.log"
grep -F '[java:S3655]' "$output/gradle-symbolic.log"

if mvn -B -ntp -f "$fixture/pom.xml" process-classes \
        -DstaticAnalysis.profile=none \
        -DstaticAnalysis.symbolicEnabled=false \
        -DstaticAnalysis.enabledRules=java:S2259 \
        > "$output/maven-disabled.log" 2>&1; then
    echo "Symbolic rules unexpectedly ran with the analyzer disabled." >&2
    exit 1
fi
grep -F 'not an eligible standard production Java rule' "$output/maven-disabled.log"

if gradle --no-daemon -p "$fixture" staticAnalysis -PsymbolicEnabled=false \
        > "$output/gradle-disabled.log" 2>&1; then
    echo "Gradle unexpectedly ran symbolic rules with the analyzer disabled." >&2
    exit 1
fi
grep -F 'not an eligible standard production Java rule' "$output/gradle-disabled.log"

mvn -B -ntp -f "$fixture/pom.xml" process-classes \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.symbolicEnabled=false \
    > "$output/maven-standard-way.log" 2>&1

mvn -B -ntp -f "$fixture/pom.xml" process-classes \
    -DstaticAnalysis.profile=sonar-way \
    -DstaticAnalysis.symbolicEnabled=true \
    > "$output/maven-symbolic-way.log" 2>&1

python3 - "$output/maven-standard-way.log" "$output/maven-symbolic-way.log" <<'PY'
from pathlib import Path
import re
import sys

def count(path):
    text = Path(path).read_text(encoding="utf-8")
    match = re.search(r"Activated (\d+) local Java rule\(s\)", text)
    assert match, text
    return int(match.group(1))

standard = count(sys.argv[1])
symbolic = count(sys.argv[2])
print(f"Standard Sonar Way: {standard}, symbolic Sonar Way: {symbolic}")
assert standard == 392, standard
assert standard < symbolic <= standard + 21, (standard, symbolic)
PY

test "$(sha256sum "$source_file")" = "$original"
echo "Both Maven and Gradle detected real symbolic failures without editing source."
