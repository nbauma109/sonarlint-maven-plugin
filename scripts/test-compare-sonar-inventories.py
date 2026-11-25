#!/usr/bin/env python3
"""Independent comparison regressions for duplicated and relocated findings."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("comparison",
    Path(__file__).with_name("compare-sonar-inventories.py"))
comparison = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = comparison
SPEC.loader.exec_module(comparison)
Finding = comparison.Finding


class ComparisonTest(unittest.TestCase):
    def test_preserves_same_line_multiplicity(self):
        item = Finding("Example.java", "java:S8786", 4, 4)
        result = comparison.compare([item, item], [item], Path("."))
        self.assertEqual(0, result["exact_location_matches"])
        self.assertEqual(1, result["same_line_matches"])
        self.assertEqual(1, result["local_only_count"])

    def test_precise_matches_take_precedence_over_line_only_matches(self):
        approximate = Finding("Example.java", "java:S8786", 4, 4)
        precise = Finding("Example.java", "java:S8786", 4, 4, 5, 12)
        distinct = Finding("Example.java", "java:S8786", 4, 4, 15, 25)
        result = comparison.compare([approximate, precise], [precise, distinct], Path("."))
        self.assertEqual(1, result["exact_location_matches"])
        self.assertEqual(1, result["same_line_matches"])
        self.assertEqual(0, result["cloud_only_count"])
        self.assertEqual(15, result["same_line_matches_requiring_review"][0]["cloud"]["start_offset"])

    def test_rejects_incomplete_nested_pagination(self):
        with tempfile.TemporaryDirectory() as directory:
            inventory = Path(directory) / "cloud.json"
            inventory.write_text(json.dumps({"paging": {"total": 2}, "issues": []}),
                                 encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Incomplete inventory"):
                comparison.read_inventory(inventory)

    def test_distinct_columns_are_not_automatically_matched(self):
        left = Finding("Example.java", "java:S8786", 4, 4, 5, 12)
        right = Finding("Example.java", "java:S8786", 4, 4, 15, 25)
        result = comparison.compare([left], [right], Path("."))
        self.assertEqual(0, result["exact_location_matches"])
        self.assertEqual([], result["location_candidates_requiring_review"])

    def test_comment_block_is_review_candidate_not_automatic_match(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "Example.java").write_text("// first\n// second\nint value;\n// separate\n",
                                               encoding="utf-8")
            left = Finding("Example.java", "java:S125", 1, 1)
            same = Finding("Example.java", "java:S125", 2, 2)
            separate = Finding("Example.java", "java:S125", 4, 4)
            result = comparison.compare([left], [same, separate], root)
            self.assertEqual(0, result["exact_location_matches"])
            self.assertEqual(2, result["cloud_only_count"])
            self.assertEqual(1, len(result["location_candidates_requiring_review"]))


if __name__ == "__main__":
    unittest.main()
