#!/usr/bin/env python3
"""Audit the resolved Eclipse JDT and Eclipse Platform Maven dependency licenses."""

from pathlib import Path
import re
import sys
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
DEPENDENCIES = ROOT / "target" / "eclipse-jdt-runtime-dependencies.txt"
INVENTORY = ROOT / "target" / "eclipse-jdt-license-inventory.txt"
MAVEN_REPOSITORY = Path.home() / ".m2" / "repository"
ECLIPSE_COMPONENT = re.compile(
    r"(org\.eclipse\.(?:jdt|platform)):"
    r"([^:]+):jar:([^:]+):(compile|runtime)"
)
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


def pom_path(group: str, artifact: str, version: str) -> Path:
    return (MAVEN_REPOSITORY / group.replace(".", "/") / artifact
            / version / f"{artifact}-{version}.pom")


def text_of(element: ElementTree.Element, path: str) -> str:
    value = element.find(path, NAMESPACE)
    return (value.text or "").strip() if value is not None else ""


def declared_licenses(group: str, artifact: str, version: str,
                      visited: set[tuple[str, str, str]]) -> tuple[str, ...]:
    identifier = (group, artifact, version)
    if identifier in visited:
        raise ValueError(f"Cyclic license metadata inheritance: {identifier}")
    visited.add(identifier)

    path = pom_path(*identifier)
    if not path.is_file():
        raise ValueError(f"Missing resolved Maven dependency metadata: {path}")
    root = ElementTree.parse(path).getroot()
    names = tuple(
        name for license_entry in root.findall("./m:licenses/m:license", NAMESPACE)
        if (name := text_of(license_entry, "m:name"))
    )
    if names:
        return names

    parent = root.find("m:parent", NAMESPACE)
    if parent is None:
        raise ValueError(f"No declared or inherited license for {identifier}")
    return declared_licenses(
        text_of(parent, "m:groupId"),
        text_of(parent, "m:artifactId"),
        text_of(parent, "m:version"),
        visited,
    )


def permitted_license(name: str) -> bool:
    normalized = " ".join(name.lower().replace("-", " ").replace("_", " ").split())
    return any(
        expected in normalized
        for expected in (
            "eclipse public license 2.0",
            "eclipse public license v2.0",
            "eclipse public license v 2.0",
            "eclipse public license version 2.0",
            "epl 2.0",
            "epl v2.0",
            "epl v 2.0",
            "eclipse distribution license",
            "apache license version 2.0",
            "apache software license 2.0",
            "mit license",
            "bsd license",
        )
    )


def main() -> int:
    if not DEPENDENCIES.is_file():
        print(f"Resolved Maven dependency tree missing: {DEPENDENCIES}", file=sys.stderr)
        return 1

    found: set[tuple[str, str, str]] = set()
    for line in DEPENDENCIES.read_text(encoding="utf-8").splitlines():
        match = ECLIPSE_COMPONENT.search(line)
        if match:
            found.add((match.group(1), match.group(2), match.group(3)))

    if not found:
        print("No Eclipse JDT or Eclipse Platform dependencies were resolved", file=sys.stderr)
        return 1

    results = []
    failures = []
    for group, artifact, version in sorted(found):
        try:
            licenses = declared_licenses(group, artifact, version, set())
        except (OSError, ValueError, ElementTree.ParseError) as failure:
            failures.append(f"{group}:{artifact}:{version}: {failure}")
            continue

        result = f"{group}:{artifact}:{version} | {', '.join(licenses)}"
        results.append(result)
        if not all(permitted_license(name) for name in licenses):
            failures.append(f"Unreviewed license in {result}")

    INVENTORY.parent.mkdir(parents=True, exist_ok=True)
    INVENTORY.write_text("\n".join(results) + "\n", encoding="utf-8")
    for result in results:
        print(result)
    for failure in failures:
        print(failure, file=sys.stderr)

    print(f"Reviewed {len(results)} Eclipse JDT and Platform dependencies")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
