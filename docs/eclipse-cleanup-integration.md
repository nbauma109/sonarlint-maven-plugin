# Complementary Eclipse cleanup warnings

## Goal

Provide both standalone Sonar warnings and nonredundant Eclipse Java Development Tools (JDT) cleanup warnings in one Maven build. Check mode must not modify sources, create commits, require a network connection for analysis, or open pull requests.

The existing projects are [refactoring-cleanup-action](https://github.com/nbauma109/refactoring-cleanup-action) and [refactoring-cli](https://github.com/nbauma109/refactoring-cli). The action currently uses an Eclipse installation and a Java 25 runtime in Docker, with a virtual display on Linux. The CLI uses workspace projects, linked sources, JDT cleanup registry, refactoring validation and TextEdit objects. Reuse the cleanup engine rather than recreating Eclipse transformations. A direct JDT Core dependency alone does not provide the full JDT UI cleanup registry.

## Initial option inventory

[eclipse-cleanup-catalog.csv](eclipse-cleanup-catalog.csv) inventories 147 input options declared in the action. The example profile is the existing [jd-core-v0 workflow](https://github.com/nbauma109/jd-core-v0/blob/master/.github/workflows/refactor.yml), with the actual true/false settings when present. This is an initial triage, not an exhaustive equivalence test. Categories: SONAR_OVERLAP means a known candidate for duplicate or partial coverage; ECLIPSE_CANDIDATE means potentially complementary, not proven unique; STYLE_POLICY means defer to the configured formatter/Checkstyle or an explicit style profile; REVIEW means we have not established coverage. Options can be parent switches, subordinate choices or mutually exclusive settings, not one warning each.

Sonar candidates are compared with the pinned SonarJava 8.6.0.37351 metadata, not an assumed current cloud profile. Rule IDs in the mapping are deliberately separate from runtime activation. Do not suppress Eclipse findings simply because a Sonar rule exists.

## Architecture

1. Run Sonar locally once with the selected rules, collecting rule IDs and source locations before rendering warnings.
2. Load existing Eclipse exported cleanup XML with the exact Maven source level, source roots, encoding and compilation classpath. Derive complementary settings without modifying the original profile.
3. Run the Eclipse cleanup engine in a separate process with a compatible Eclipse distribution, Java runtime and isolated workspace. Make the Eclipse engine opt-in until runtime provisioning and cross-platform behavior are validated. Pin versions and verify downloaded components; downloads are setup/dependency resolution rather than analysis.
4. Add a report-only mode to the CLI: after creating and validating each Change, inspect its effective TextEdits against original source. Record cleanup option/module, source path, original line/column or range, description and proposed edit. Do not invoke change.perform or write through linked resources. Audit any fallbacks (including the instanceof fallback) for write operations. Use copies for any transformations that cannot safely be previewed. Failed or skipped cleanups must be diagnosable, not silently reported as a clean result.
5. Merge warnings using stable identifiers and deterministic sorting. Keep the existing Maven failure policy explicit. No GitHub action, Docker daemon or external server is required by the eventual user-facing Maven goal; the current Eclipse distribution may still need a platform-specific runtime and display until a truly headless route is verified.

## Duplicate policy

Prefer Sonar for a proven equivalent finding when its rule is active, supports the relevant source scope, and reports the same underlying problem. If equivalence is verified for the full cleanup, it may be pruned at configuration time for performance. Otherwise compare findings using normalized original source file, overlapping edit/report ranges (including multiple related ranges where available) and the explicit rule mapping. For example S1128 is the proposed equivalent for unused imports; S1905 for redundant casts; S1604 for anonymous-class-to-lambda. A matching rule key alone or a shared line alone is insufficient for partial overlaps. Preserve Eclipse coverage when an overlapping Sonar rule is disabled, unavailable, fails to report or covers a different case. Do not enable a known server-only rule solely for deduplication. Never hide unrelated findings.

Group related text edits into one issue per cleanup opportunity rather than one warning per edit. Keep the cleanup option ID distinct from a cleanup module that may serve multiple options. When needed, isolate option groups with prerequisite flags or produce a conservative module-level diagnostic until the option can be attributed. Do not claim per-option coverage without tests. Evaluate against original source rather than sequentially rewritten files.

## Proposed configuration (not implemented)

```xml
<configuration>
    <profile>sonar-way</profile>
    <failOnViolation>false</failOnViolation>
    <eclipseEnabled>true</eclipseEnabled>
    <eclipseCleanupProfile>${project.basedir}/eclipse-cleanups.xml</eclipseCleanupProfile>
    <eclipseDuplicatePolicy>prefer-sonar</eclipseDuplicatePolicy>
</configuration>
```

The existing goal remains unchanged until implementation. Keep warning output distinct, for example `[java:S1128]` and `[eclipse:cleanup.pull_up_assignment]`, with original file and line. Consider a neutral artifact name only after the combined feature works.

## Acceptance tests

- Both engines report a genuine overlapping unused import; only the Sonar warning reaches the console.
- Disable S1128 and verify the Eclipse warning remains.
- Verify a source-located Eclipse-only cleanup against a fixture and confirm it is not flagged by the chosen Sonar profile.
- Test partial overlaps and unrelated warnings on the same line.
- Assert exact source bytes and repository status are unchanged after check mode, including fallback transformations.
- Validate profile parent/subordinate options, source level, Maven dependency classpath and separate analyzer runtime.
- Validate no false clean output on engine failure, invalid profile or unresolved classpath.
- Verify repeated Maven offline runs with all required artifacts and runtime cached; measure execution cost.
- Keep the existing Sonar-only tests green. Add representative positive and negative cleanup fixtures before replacing the GitHub pull-request workflow.

## Implementation order

First extend refactoring-cli with a tested, structured report-only mode. Then integrate its pinned runtime through a Maven adapter and implement the overlap policy. Next validate the option catalog and select the complementary default profile. Keep the current pull-request action intact until the new path has passed tests.

## References

- [Action inputs](https://github.com/nbauma109/refactoring-cleanup-action/blob/master/action.yml)
- [Action runtime](https://github.com/nbauma109/refactoring-cleanup-action/blob/master/Dockerfile)
- [Existing CleanupRunner](https://github.com/nbauma109/refactoring-cli/blob/master/src/main/java/io/github/nbauma109/refactoring/cli/CleanupRunner.java)
- [Pinned Sonar rule metadata](https://github.com/SonarSource/sonar-java/tree/8.6.0.37351/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java)
