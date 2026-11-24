#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
java_major="$1"
logs=target/integration-tests
native_fixture=src/it/native-java-version-rules/pom.xml
for compiler_release in 11 17; do
    log="$logs/native-java-release-$compiler_release.log"
    mvn -B -ntp -o -f "$native_fixture" clean process-test-classes \
        -Dmaven.compiler.release="$compiler_release" | tee "$log"
    if [ "$compiler_release" -eq 17 ]; then
        grep -E '\[WARNING\].*NativeVersionIssues\.java:[0-9]+ \[java:S6201\]' "$log"
    elif grep -F '[java:S6201]' "$log"; then
        exit 1
    fi
done

if [ "$java_major" -lt 25 ]; then
    exit 0
fi

fixture=src/it/java-version-rules/pom.xml
logs=target/integration-tests
for goal in check check-tests; do
    log="$logs/mixed-java-releases-$goal.log"
    mvn -B -ntp -o -f "$fixture" clean test-compile \
        io.github.nbauma109:static-analysis-maven-plugin:0.1.3-SNAPSHOT:"$goal" \
        | tee "$log"
    test "$(grep -Ec '\[WARNING\].*ModernCatch\.java:[0-9]+ \[java:S7467\]' "$log")" -eq 1
    if grep -E '\[WARNING\].*(LegacyCatch|UnnamedCatch)\.java:[0-9]+ \[java:S7467\]' "$log"; then
        exit 1
    fi
done
