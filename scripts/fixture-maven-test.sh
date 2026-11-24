#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT
mkdir -p "$temporary/scripts"
cp scripts/fixture-maven.sh "$temporary/scripts/"

mvn() {
    test "$1" = "-DstaticAnalysis.pluginVersion=$expected_version"
    shift
    test "$*" = "-B -ntp -o verify"
}

for expected_version in 1.2.3-SNAPSHOT 1.2.3 1.2.4-SNAPSHOT; do
    cat > "$temporary/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>plugin</artifactId>
    <version>$expected_version</version>
</project>
XML
    source "$temporary/scripts/fixture-maven.sh"
    test "$plugin_version" = "$expected_version"
    fixture_maven -B -ntp -o verify
done
