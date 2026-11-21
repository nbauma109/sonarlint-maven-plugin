#!/usr/bin/env python3
"""Enrich the pinned rule catalog using local SonarJava analyzer archives."""

import argparse
import csv
import json
from pathlib import Path
from zipfile import ZipFile


BASE_CATALOG = Path(__file__).resolve().parent.parent / "docs" / "rule-catalog.csv"
ANALYZER_VERSION = "8.6.0.37351"


def read_catalog():
    with BASE_CATALOG.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        return reader.fieldnames, list(reader)


def validate_archive(archive, row):
    entry = row["metadata_entry"]
    if entry not in archive.namelist():
        raise ValueError(f"Missing metadata for {row['rule_key']}: {entry}")
    return json.loads(archive.read(entry))


def enrich_row(row, archive):
    metadata = validate_archive(archive, row)
    row["scope"] = metadata["scope"]
    row["rule_type"] = metadata["type"]
    row["upstream_status"] = metadata["status"]
    if row["rule_type"] == "SECURITY_HOTSPOT":
        row["manual_review"] = "YES"
    else:
        row["manual_review"] = "NO"
    if row["classification"] == "LOCAL_STANDARD_CANDIDATE" and row["scope"] == "Tests":
        row["classification"] = "LOCAL_TEST_CANDIDATE"


def enrich(rows, standard, symbolic):
    with ZipFile(standard) as standard_archive, ZipFile(symbolic) as symbolic_archive:
        archives = {"standard": standard_archive, "symbolic": symbolic_archive}
        for row in rows:
            archive = archives.get(row["engine"])
            if archive is not None:
                enrich_row(row, archive)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("standard", type=Path)
    parser.add_argument("symbolic", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    for archive in (arguments.standard, arguments.symbolic):
        if not archive.name.endswith(f"-{ANALYZER_VERSION}.jar"):
            parser.error(f"Expected analyzer version {ANALYZER_VERSION}: {archive}")
    columns, rows = read_catalog()
    enrich(rows, arguments.standard, arguments.symbolic)
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    with arguments.output.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=columns, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} catalog entries to {arguments.output}")


if __name__ == "__main__":
    main()
