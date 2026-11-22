#!/usr/bin/env python3
"""Verify our pending Eclipse review queue against the live catalog and pinned snapshot."""

import csv
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "docs/eclipse-cleanup-catalog.csv"
QUEUE = ROOT / "docs/eclipse-pending-review-queue.csv"
FILTER = ROOT / "src/main/java/io/github/nbauma109/staticanalysis/EclipseDuplicateFilter.java"
SNAPSHOT = (
    ROOT / "src/main/resources/io/github/nbauma109/staticanalysis/"
    "sonar-way-java-8.6.0.37351.rules"
)
REVIEW_CATEGORIES = {"SONAR_OVERLAP", "REVIEW"}
REVIEW_PRIORITIES = {"P1", "P2", "P3", "P4"}
EVIDENCE_STATES = {
    "catalog_only",
    "isolated_eclipse_two_edits",
    "isolated_eclipse_one_finding",
}
SNAPSHOT_STATES = {
    "no_rule_candidate",
    "none_listed",
    "some_listed",
    "all_listed",
}


def read_csv(path):
    with path.open(encoding="utf-8", newline="") as file:
        return list(csv.DictReader(file))


def verified_mappings():
    source = FILTER.read_text(encoding="utf-8")
    return set(re.findall(r'"(cleanup\.[a-z_]+)",\s*"java:S\d+"', source))


def snapshot_membership(sonar_candidates, snapshot):
    if not sonar_candidates:
        return "no_rule_candidate"
    rules = ["java:" + rule for rule in sonar_candidates.split(";")]
    count = sum(rule in snapshot for rule in rules)
    if count == 0:
        return "none_listed"
    if count == len(rules):
        return "all_listed"
    return "some_listed"


def validate_queue(catalog, queue):
    verified = verified_mappings()
    catalog_by_option = {row["eclipse_option"]: row for row in catalog}
    if len(catalog_by_option) != len(catalog):
        raise ValueError("Duplicate identifiers in Eclipse cleanup catalog")

    pending = {
        row["eclipse_option"]
        for row in catalog
        if row["triage"] == "REVIEW"
        or (row["triage"] == "SONAR_OVERLAP"
            and row["eclipse_option"] not in verified)
    }
    options = [row["eclipse_option"] for row in queue]
    if len(options) != len(set(options)) or set(options) != pending:
        raise ValueError("Review queue must contain precisely the unresolved catalog entries")

    snapshot = set(SNAPSHOT.read_text(encoding="utf-8").splitlines())
    counts = {category: 0 for category in REVIEW_CATEGORIES}
    for row in queue:
        reference = catalog_by_option[row["eclipse_option"]]
        category = reference["triage"]
        counts[category] += 1
        if (row["catalog_state"] != category
                or row["sonar_candidates"] != reference["sonar_candidates"]):
            raise ValueError("Catalog and review queue disagree: " + row["eclipse_option"])
        if row["priority"] not in REVIEW_PRIORITIES:
            raise ValueError("Unknown review priority: " + row["eclipse_option"])
        if row["existing_evidence"] not in EVIDENCE_STATES:
            raise ValueError("Unknown evidence status: " + row["eclipse_option"])
        if row["review_decision"] != "pending":
            raise ValueError("An unverified option cannot be auto-approved: " + row["eclipse_option"])
        if not row["review_lane"] or not row["next_test"] or not row["review_rationale"]:
            raise ValueError("Missing review instructions: " + row["eclipse_option"])
        expected = snapshot_membership(row["sonar_candidates"], snapshot)
        if row["snapshot_membership"] not in SNAPSHOT_STATES:
            raise ValueError("Unknown snapshot membership: " + row["eclipse_option"])
        if row["snapshot_membership"] != expected:
            raise ValueError("Pinned Sonar Way membership changed: " + row["eclipse_option"])
    if counts != {"SONAR_OVERLAP": 22, "REVIEW": 80}:
        raise ValueError(f"Unexpected pending classification totals: {counts}")
    return counts


def main():
    totals = validate_queue(read_csv(CATALOG), read_csv(QUEUE))
    print("Verified review-only queue: "
          f"{totals['SONAR_OVERLAP']} overlaps and {totals['REVIEW']} Eclipse options.")


if __name__ == "__main__":
    main()
