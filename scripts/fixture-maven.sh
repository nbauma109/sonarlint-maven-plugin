#!/usr/bin/env bash

plugin_version="$(python3 - "${BASH_SOURCE[0]%/*}/../pom.xml" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

pom = ET.parse(Path(sys.argv[1]))
version = pom.findtext("{http://maven.apache.org/POM/4.0.0}version")
if not version or not version.strip():
    raise SystemExit("The plugin project must declare its version")
print(version.strip())
PY
)"

fixture_maven() {
    mvn "-DstaticAnalysis.pluginVersion=$plugin_version" "$@"
}
