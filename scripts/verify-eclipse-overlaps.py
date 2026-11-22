#!/usr/bin/env python3
"""Validate verified duplicate mappings and Eclipse inventory consistency."""

import csv
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "docs/eclipse-cleanup-catalog.csv"
FILTER = ROOT / "src/main/java/io/github/nbauma109/staticanalysis/EclipseDuplicateFilter.java"
AUDIT = ROOT / "docs/eclipse-overlap-audit.md"
SUPPLEMENTAL = ROOT / "src/main/java/io/github/nbauma109/staticanalysis/CheckMojo.java"

EXPECTED = {
    "cleanup.remove_unused_imports": "S1128",
    "cleanup.remove_unnecessary_casts": "S1905",
    "cleanup.remove_unused_local_variables": "S1481",
    "cleanup.add_missing_override_annotations": "S1161",
    "cleanup.try_with_resource": "S2093",
    "cleanup.no_string_creation": "S2129",
    "cleanup.remove_redundant_type_arguments": "S2293",
    "cleanup.remove_unnecessary_array_creation": "S3878",
    "cleanup.useless_return": "S3626",
    "cleanup.static_inner_class": "S2694",
    "cleanup.remove_unused_private_fields": "S1068",
    "cleanup.remove_unused_private_methods": "S1144",
}
EXPECTED_CATEGORIES = {
    "SONAR_OVERLAP": 35,
    "ECLIPSE_CANDIDATE": 14,
    "ECLIPSE_AESTHETIC": 2,
    "REVIEW": 78,
    "STYLE_POLICY": 18,
}


def main() -> None:
    with CATALOG.open(encoding="utf-8", newline="") as catalog_file:
        rows = list(csv.DictReader(catalog_file))
    by_id = {row["eclipse_option"]: row for row in rows}
    if len(by_id) != len(rows):
        raise ValueError("Duplicate Eclipse catalog identifiers")

    counts = {category: sum(row["triage"] == category for row in rows)
              for category in EXPECTED_CATEGORIES}
    if counts != EXPECTED_CATEGORIES:
        raise ValueError(f"Eclipse classification changed: {counts}")

    source = FILTER.read_text(encoding="utf-8")
    mappings = dict(re.findall(
        r'"(cleanup\.[a-z_]+)",\s*"java:(S\d+)"', source
    ))
    if mappings != EXPECTED:
        raise ValueError(f"Verified overlap mappings changed: {mappings}")
    for cleanup, rule in mappings.items():
        row = by_id[cleanup]
        if row["triage"] != "SONAR_OVERLAP" or row["sonar_candidates"] != rule:
            raise ValueError(f"Incorrect overlap classification: {cleanup}, {rule}")

    supplemental = SUPPLEMENTAL.read_text(encoding="utf-8")
    if ('"cleanup.make_private_fields_final"' not in supplemental
            or '"VariableDeclarationCleanUpCore"' not in supplemental
            or '"java:S9438"' not in supplemental):
        raise ValueError("Verified Eclipse private-field fallback is missing")

    audit = AUDIT.read_text(encoding="utf-8")
    pending = counts["SONAR_OVERLAP"] - len(mappings) - 1
    for marker in (f"**{len(mappings)}", f"**{pending} candidate overlap",
                   f"**{counts['REVIEW']} options"):
        if marker not in audit:
            raise ValueError(f"Overlap audit missing correct count: {marker}")
    print(f"Verified {len(mappings)} duplicate mappings and {pending} pending overlaps.")


if __name__ == "__main__":
    main()
