#!/usr/bin/env python3
"""Validate the signal-first Eclipse profile against the pinned cleanup catalog."""

import csv
from pathlib import Path
import xml.etree.ElementTree as ElementTree

ROOT = Path(__file__).resolve().parent.parent
RECOMMENDED = ROOT / "src/it/broad-complementary/eclipse-cleanups-recommended.xml"
BROAD = ROOT / "src/it/broad-complementary/eclipse-cleanups.xml"
CATALOG = ROOT / "docs/eclipse-cleanup-catalog.csv"
SELECTED = {
    "cleanup.pull_up_assignment",
    "cleanup.reduce_indentation",
    "cleanup.add_all",
    "cleanup.array_with_curly",
    "cleanup.remove_redundant_modifiers",
}


def profile_options(path: Path) -> set[str]:
    root = ElementTree.parse(path).getroot()
    options = [setting.attrib["id"] for setting in root.findall("./profile/setting")
               if setting.attrib["value"] == "true"]
    if len(options) != len(set(options)):
        raise ValueError(f"Duplicate cleanup option in {path}")
    return set(options)


def main() -> None:
    recommended = profile_options(RECOMMENDED)
    broad = profile_options(BROAD)
    if recommended != SELECTED:
        raise ValueError(f"Unexpected recommended cleanup options: {sorted(recommended)}")
    if not recommended.issubset(broad):
        raise ValueError(f"Recommended cleanups missing from the broad profile: {sorted(recommended - broad)}")

    with CATALOG.open(encoding="utf-8", newline="") as catalog_file:
        catalog = {row["eclipse_option"]: row for row in csv.DictReader(catalog_file)}

    for option in sorted(recommended):
        row = catalog.get(option)
        if row is None or row["triage"] != "ECLIPSE_CANDIDATE":
            raise ValueError(f"Unverified or overlapping cleanup selected: {option}")

    print(f"Verified {len(recommended)} signal-first Eclipse cleanup options.")


if __name__ == "__main__":
    main()
