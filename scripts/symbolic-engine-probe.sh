#!/usr/bin/env bash
set -euo pipefail

version="8.6.0.37351"
group="org.sonarsource.java"
artifact="sonar-java-symbolic-execution-plugin"
output="target/symbolic-engine-probe"
mkdir -p "$output"

mvn -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.7.1:copy \
    "-Dartifact=$group:$artifact:$version" "-DoutputDirectory=$output"
file="$output/$artifact-$version.jar"
test -s "$file"

unzip -p "$file" META-INF/MANIFEST.MF | tee "$output/manifest.txt"
unzip -Z1 "$file" | grep -E 'org/sonar/l10n/java/rules/javase/(S[0-9]+|Sonar_way_profile)\.json$' \
    | tee "$output/rule-entries.txt"

test "$(grep -Ec '/S[0-9]+\.json$' "$output/rule-entries.txt")" -ge 20
grep -q '/Sonar_way_profile.json$' "$output/rule-entries.txt"
grep -q 'Plugin-Class:' "$output/manifest.txt"

pom="$HOME/.m2/repository/org/sonarsource/java/$artifact/$version/$artifact-$version.pom"
test -s "$pom"
cp "$pom" "$output/dependency-pom.xml"
echo "Symbolic plugin $version exists with standalone metadata and plugin manifest."
