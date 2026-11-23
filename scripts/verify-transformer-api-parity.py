#!/usr/bin/env python3
"""Verify source-located local diagnostics against the pinned SonarCloud issue inventory."""

from collections import Counter
from pathlib import Path
import re
import sys

SOURCE = "src/main/java/com/heliosdecompiler/transformerapi/"
TEST = "src/test/java/com/heliosdecompiler/transformerapi/"

EXPECTED = Counter(
    [
        (SOURCE + "StandardTransformers.java", 80, "S1452"),
        (SOURCE + "StandardTransformers.java", 98, "S1452"),
        (SOURCE + "decompilers/fernflower/FernflowerDecompiler.java", 54, "S106"),
        (SOURCE + "decompilers/vineflower/VineflowerDecompiler.java", 51, "S106"),
        (SOURCE + "common/BytecodeSourceLinker.java", 637, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 646, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 706, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 719, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 757, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 770, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 775, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 852, "S3398"),
        (SOURCE + "common/BytecodeSourceLinker.java", 1174, "S3398"),
        (SOURCE + "common/FileLoader.java", 51, "S2259"),
        (SOURCE + "common/SettingsApplicable.java", 25, "S9398"),
        (SOURCE + "decompilers/cfr/CFRDataSource.java", 30, "S9438"),
        (SOURCE + "decompilers/cfr/CFRDataSource.java", 31, "S9438"),
        (SOURCE + "decompilers/cfr/CFRDataSource.java", 32, "S9438"),
        (SOURCE + "decompilers/cfr/CFROutputStreamFactory.java", 32, "S9438"),
        (SOURCE + "decompilers/cfr/CFROutputStreamFactory.java", 33, "S9438"),
        (SOURCE + "decompilers/cfr/CFRSettings.java", 29, "S9438"),
        (SOURCE + "decompilers/fernflower/FernflowerSettings.java", 34, "S9438"),
        (SOURCE + "decompilers/vineflower/VineflowerSettings.java", 35, "S9438"),
        (TEST + "StandardTransformersTest.java", 368, "S8786"),
        (TEST + "StandardTransformersTest.java", 368, "S8786"),
        (TEST + "TestCompact.java", 18, "S7467"),
    ]
)

FINDING_PATTERN = re.compile(
    r"^\[WARNING\] (?P<path>src/(?:main|test)/java/[^:]+\.java):"
    r"(?P<line>\d+) \[(?:java|javabugs):(?P<rule>S\d+)\]"
)


def read_findings(file_path: Path) -> Counter:
    findings = Counter()
    for line in file_path.read_text(encoding="utf-8").splitlines():
        match = FINDING_PATTERN.search(line)
        if match:
            key = (match.group("path"), int(match.group("line")), match.group("rule"))
            findings[key] += 1
    return findings


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: verify-transformer-api-parity.py PRODUCTION_LOG TEST_LOG", file=sys.stderr)
        return 2
    actual = read_findings(Path(sys.argv[1])) + read_findings(Path(sys.argv[2]))
    missing = EXPECTED - actual
    extra = actual - EXPECTED
    print(f"Pinned SonarCloud issues: {EXPECTED.total()}")
    print(f"Local findings: {actual.total()}")
    for category, findings in (("Missing", missing), ("Unexpected", extra)):
        for (path, line, rule), count in sorted(findings.items()):
            print(f"{category}: {path}:{line} [{rule}] x{count}")
    if missing or extra:
        return 1
    print("Every pinned SonarCloud finding matches a local diagnostic by source, line and rule.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
