#!/usr/bin/env python3
"""Verify that the Maven artifact preserves the Eclipse JDT linking permission."""

from pathlib import Path
from xml.etree import ElementTree
from zipfile import ZipFile
import sys

ROOT = Path(__file__).resolve().parent.parent
EXCEPTION = "ECLIPSE-JDT-LINKING-EXCEPTION.md"
REQUIRED_ENTRIES = {
    "META-INF/LICENSE",
    "META-INF/COPYRIGHT.md",
    "META-INF/THIRD_PARTY_NOTICES.md",
    "META-INF/" + EXCEPTION,
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def verify_project_metadata() -> None:
    license_text = (ROOT / "LICENSE").read_text(encoding="utf-8")
    permission = (ROOT / EXCEPTION).read_text(encoding="utf-8")
    require("GNU GENERAL PUBLIC LICENSE" in license_text,
            "The unchanged GNU General Public License must remain available")
    require("section 7" in permission and "Eclipse JDT Core Manipulation" in permission,
            "The project must retain the named Eclipse linking permission")
    require("EPL-2.0" in permission and "GPL-3.0-only" in permission,
            "The permission must preserve both third-party and project licenses")

    root = ElementTree.parse(ROOT / "pom.xml").getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    licenses = root.findall("./m:licenses/m:license/m:name", namespace)
    require(any("Eclipse JDT linking exception" in (license_entry.text or "")
                for license_entry in licenses),
            "Maven project license metadata must identify the linking permission")
    resources = [
        entry.text
        for entry in root.findall(".//m:resources/m:resource/m:includes/m:include", namespace)
    ]
    require(EXCEPTION in resources,
            "The linking exception must be included in distributed Maven artifacts")

    for source in (
        "src/main/java/io/github/nbauma109/staticanalysis/CheckMojo.java",
        "src/main/java/io/github/nbauma109/staticanalysis/EmbeddedEclipseCleanupRunner.java",
    ):
        text = (ROOT / source).read_text(encoding="utf-8")
        require(EXCEPTION in text, f"Missing source notice in {source}")


def verify_binary_artifact() -> None:
    artifacts = sorted(
        item for item in (ROOT / "target").glob("static-analysis-maven-plugin-*.jar")
        if not item.name.endswith("-sources.jar")
        and not item.name.endswith("-javadoc.jar")
    )
    require(len(artifacts) == 1, "Exactly one compiled Maven plugin artifact is expected")
    with ZipFile(artifacts[0]) as archive:
        entries = set(archive.namelist())
        missing = REQUIRED_ENTRIES - entries
        require(not missing, f"Missing legal notices in the plugin artifact: {sorted(missing)}")
        embedded = archive.read("META-INF/" + EXCEPTION).decode("utf-8")
        require(embedded == (ROOT / EXCEPTION).read_text(encoding="utf-8"),
                "Distributed linking permission differs from the source text")
    print(f"Verified Eclipse JDT linking permission and notices in {artifacts[0]}")


def main() -> int:
    try:
        verify_project_metadata()
        verify_binary_artifact()
    except (ValueError, OSError, ElementTree.ParseError) as failure:
        print(str(failure), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
