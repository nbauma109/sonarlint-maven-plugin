#!/usr/bin/env python3
"""Validate isolated maintainability and aesthetics profiles against the cleanup catalog."""

import csv
from pathlib import Path
import xml.etree.ElementTree as ElementTree

ROOT = Path(__file__).resolve().parent.parent
PROFILES = ROOT / "src/it/broad-complementary"
CATALOG = ROOT / "docs/eclipse-cleanup-catalog.csv"
RECOMMENDED = {
    "cleanup.pull_up_assignment",
    "cleanup.reduce_indentation",
    "cleanup.add_all",
}
AESTHETICS = {
    "cleanup.array_with_curly",
    "cleanup.remove_redundant_modifiers",
}
OPTIONAL_AESTHETICS = {
    "cleanup.make_variable_declarations_final",
    "cleanup.make_local_variable_final",
    "cleanup.make_parameters_final",
    "cleanup.use_var",
    "cleanup.insert_inferred_type_arguments",
    "cleanup.use_parentheses_in_expressions",
    "cleanup.always_use_parentheses_in_expressions",
    "cleanup.never_use_parentheses_in_expressions",
    "cleanup.sort_members",
    "cleanup.sort_members_all",
    "cleanup.number_suffix",
    "cleanup.correct_indentation",
    "cleanup.remove_trailing_whitespaces",
    "cleanup.format_source_code",
}


def read_profile(name: str) -> tuple[set[str], set[str]]:
    path = PROFILES / name
    root = ElementTree.parse(path).getroot()
    settings = root.findall("./profile/setting")
    options = [setting.attrib["id"] for setting in settings]
    if len(options) != len(set(options)):
        raise ValueError(f"Duplicate cleanup option in {path}")
    if any(setting.attrib["value"] not in {"true", "false"} for setting in settings):
        raise ValueError(f"Unknown cleanup option value in {path}")
    enabled = {setting.attrib["id"] for setting in settings if setting.attrib["value"] == "true"}
    disabled = set(options) - enabled
    return enabled, disabled


def main() -> None:
    recommended, _ = read_profile("eclipse-cleanups-recommended.xml")
    broad, _ = read_profile("eclipse-cleanups.xml")
    aesthetics, disabled = read_profile("eclipse-cleanups-aesthetics.xml")

    if recommended != RECOMMENDED:
        raise ValueError(f"Unexpected recommended rules: {sorted(recommended)}")
    if aesthetics != AESTHETICS:
        raise ValueError(f"Unexpected active aesthetic rules: {sorted(aesthetics)}")
    if not OPTIONAL_AESTHETICS.issubset(disabled):
        raise ValueError(f"Aesthetic choices not explicitly available but disabled: "
                         f"{sorted(OPTIONAL_AESTHETICS - disabled)}")
    if not recommended.issubset(broad):
        raise ValueError(f"Recommended rules absent from broad profile: "
                         f"{sorted(recommended - broad)}")
    if (recommended | broad) & (aesthetics | OPTIONAL_AESTHETICS):
        raise ValueError("Aesthetic choices leaked into maintainability profiles")

    with CATALOG.open(encoding="utf-8", newline="") as catalog_file:
        catalog = {row["eclipse_option"]: row for row in csv.DictReader(catalog_file)}
    for option in sorted(recommended | broad | aesthetics | OPTIONAL_AESTHETICS):
        if option not in catalog:
            raise ValueError(f"Unknown cleanup option: {option}")
    for option in sorted(recommended | aesthetics):
        if catalog[option]["triage"] != "ECLIPSE_CANDIDATE":
            raise ValueError(f"Unverified or overlapping rule enabled: {option}")

    print(f"Verified {len(recommended)} maintainability, {len(broad)} broad, "
          f"and {len(aesthetics)} opt-in aesthetic cleanup options.")


if __name__ == "__main__":
    main()
