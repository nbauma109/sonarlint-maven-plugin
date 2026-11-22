#!/usr/bin/env python3
"""Verify versioned Sonar Way membership against our pinned rule catalog."""

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STANDARD = ROOT / "src/main/resources/io/github/nbauma109/staticanalysis/sonar-way-java-8.6.0.37351.rules"
SYMBOLIC = ROOT / "docs/sonar-way-symbolic-java-8.6.0.37351.rules"
CATALOG = ROOT / "docs/rule-catalog.csv"


def load_snapshot(path: Path) -> set[str]:
    entries = path.read_text(encoding="utf-8").splitlines()
    if not entries or any(not entry.startswith("java:S") for entry in entries):
        raise ValueError(f"Invalid Sonar Way rule entries in {path}")
    if len(entries) != len(set(entries)):
        raise ValueError(f"Duplicate Sonar Way entries in {path}")
    return set(entries)


def main() -> None:
    standard_keys = load_snapshot(STANDARD)
    symbolic_keys = load_snapshot(SYMBOLIC)
    if len(standard_keys) != 491 or len(symbolic_keys) != 21:
        raise ValueError("The pinned Sonar Way snapshots have unexpected rule counts")

    with CATALOG.open(encoding="utf-8", newline="") as catalog_file:
        rows = list(csv.DictReader(catalog_file))

    for engine, expected in (("standard", standard_keys), ("symbolic", symbolic_keys)):
        catalog_keys = {
            row["rule_key"]
            for row in rows
            if row["engine"] == engine and row["sonar_way"] == "YES"
        }
        if catalog_keys != expected:
            missing = sorted(expected - catalog_keys)
            unexpected = sorted(catalog_keys - expected)
            raise ValueError(
                f"{engine}: missing catalog entries {missing}; "
                f"unexpected catalog entries {unexpected}"
            )

    for rule in ("java:S818", "java:S881"):
        if rule in standard_keys:
            raise ValueError(f"Style or noisy rule unexpectedly added to Sonar Way: {rule}")

    print(
        f"Verified pinned Sonar Way: {len(standard_keys)} standard rules, "
        f"{len(symbolic_keys)} symbolic rules."
    )
    print("The standalone Java engine activates 392 eligible standard rules.")


if __name__ == "__main__":
    main()
