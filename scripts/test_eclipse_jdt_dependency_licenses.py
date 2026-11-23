#!/usr/bin/env python3
"""Regression tests for Eclipse JDT dependency license validation."""

from pathlib import Path
import runpy
import unittest

VALIDATOR = runpy.run_path(
    str(Path(__file__).with_name("verify-eclipse-jdt-dependency-licenses.py"))
)
permitted_license = VALIDATOR["permitted_license"]


class EclipseJdtDependencyLicenseTests(unittest.TestCase):
    def test_supported_eclipse_license_declarations(self) -> None:
        for license_name in ("EPL-2.0", "Eclipse Public License - v 2.0"):
            with self.subTest(license_name=license_name):
                self.assertTrue(permitted_license(license_name))

    def test_unreviewed_or_ambiguous_declarations_fail(self) -> None:
        for license_name in (
            "",
            "EPL-1.0",
            "EPL-2.0 with additional proprietary terms",
            "Eclipse Public License 2.0 or unknown commercial license",
            "Unknown license",
        ):
            with self.subTest(license_name=license_name):
                self.assertFalse(permitted_license(license_name))


if __name__ == "__main__":
    unittest.main()
