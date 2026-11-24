#!/usr/bin/env python3
"""Compare complete Java issue inventories without discarding repeated occurrences."""

import argparse
from collections import Counter
from dataclasses import asdict, dataclass
import json
from pathlib import Path
import re

WARNING = re.compile(r"\[WARNING\]\s+(.+?\.java):(\d+)\s+\[(java:S\d+)\]")
RULE = re.compile(r"java:S\d+")


@dataclass(frozen=True)
class Finding:
    path: str
    rule: str
    start: int
    end: int
    start_offset: int | None = None
    end_offset: int | None = None

    @property
    def identity(self):
        return self.path, self.rule


def read_log(path):
    findings = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = WARNING.search(line)
        if match:
            file, number, rule = match.groups()
            findings.append(Finding(file.replace("\\", "/"), rule, int(number), int(number)))
    return findings


def read_inventory(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    issues = data["issues"] if isinstance(data, dict) else data
    findings = []
    for issue in issues:
        rule = issue["rule"]
        if not RULE.fullmatch(rule):
            continue
        if issue.get("resolution") or issue.get("status") in {"RESOLVED", "CLOSED", "ACCEPTED", "FALSE_POSITIVE"}:
            continue
        file = issue.get("path") or issue["component"].split(":", 1)[-1]
        location = issue.get("textRange") or {}
        start = location.get("startLine", issue.get("line", 0))
        end = location.get("endLine", start)
        findings.append(Finding(file, rule, start, end,
                                location.get("startOffset"), location.get("endOffset")))
    if isinstance(data, dict) and data.get("total", len(issues)) > len(issues):
        raise ValueError("Incomplete inventory: retrieve all issue pages before comparing.")
    return findings


def exact(left, right):
    if left.identity != right.identity or left.start != right.start:
        return False
    if left.start_offset is None or right.start_offset is None:
        return True
    return (left.end, left.start_offset, left.end_offset) == (
        right.end, right.start_offset, right.end_offset)


def overlaps(left, right):
    if left.identity != right.identity or not left.start or not right.start:
        return False
    if left.end < right.start or right.end < left.start:
        return False
    if left.start_offset is None or right.start_offset is None:
        return True
    if left.end == right.start and left.end_offset <= right.start_offset:
        return False
    if right.end == left.start and right.end_offset <= left.start_offset:
        return False
    return True


def same_comment_block(left, right, root):
    if left.identity != right.identity or left.rule != "java:S125":
        return False
    if not left.start or not right.start:
        return False
    file = (root / left.path).resolve()
    if not file.is_relative_to(root.resolve()) or not file.is_file():
        return False
    lines = file.read_text(encoding="utf-8").splitlines()
    first, last = sorted((left.start, right.start))
    return last <= len(lines) and all(
        line.lstrip().startswith("//") for line in lines[first - 1:last])


def pair_exact(local, cloud):
    remaining = list(cloud)
    matches = []
    unmatched = []
    # Match fully located diagnostics first; line-only logs cannot distinguish columns.
    for finding in sorted(local, key=lambda item: item.start_offset is None):
        for index, candidate in enumerate(remaining):
            if exact(finding, candidate):
                matches.append((finding, remaining.pop(index)))
                break
        else:
            unmatched.append(finding)
    return matches, unmatched, remaining


def compare(local, cloud, root):
    matches, local_only, cloud_only = pair_exact(local, cloud)
    candidates = []
    for left in local_only:
        for right in cloud_only:
            if overlaps(left, right) or same_comment_block(left, right, root):
                candidates.append({"local": asdict(left), "cloud": asdict(right)})
    return {
        "local_count": len(local),
        "cloud_count": len(cloud),
        "exact_location_matches": len(matches),
        "local_only_count": len(local_only),
        "cloud_only_count": len(cloud_only),
        "local_only_by_rule": dict(Counter(item.rule for item in local_only)),
        "cloud_only_by_rule": dict(Counter(item.rule for item in cloud_only)),
        "local_only": [asdict(item) for item in local_only],
        "cloud_only": [asdict(item) for item in cloud_only],
        "location_candidates_requiring_review": candidates,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--local", type=Path, required=True)
    parser.add_argument("--local-format", choices=("log", "json"), default="log")
    parser.add_argument("--cloud", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--local-revision", required=True)
    parser.add_argument("--cloud-revision", required=True)
    arguments = parser.parse_args()
    if arguments.local_revision != arguments.cloud_revision:
        parser.error("Local and SonarCloud source revisions must match.")
    local = read_log(arguments.local) if arguments.local_format == "log" else read_inventory(arguments.local)
    cloud = read_inventory(arguments.cloud)
    report = compare(local, cloud, arguments.source_root)
    report["source_revision"] = arguments.local_revision
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
