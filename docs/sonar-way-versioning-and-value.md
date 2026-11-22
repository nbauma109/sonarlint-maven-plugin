# Versioned Sonar Way and complementary rule value policy

## Authoritative source

The SonarJava artifact is pinned to `8.6.0.37351` and the SonarLint analysis
engine to `10.13.0.79996`. Our [versioned standard Sonar Way
snapshot](../src/main/resources/io/github/nbauma109/staticanalysis/sonar-way-java-8.6.0.37351.rules)
contains **491** rule identifiers copied from the
[matching upstream profile](https://github.com/SonarSource/sonar-java/blob/8.6.0.37351/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/Sonar_way_profile.json).
The runtime must find exactly the same set in the analyzer archive.

The standalone engine selects **392** of these rules according to pinned
metadata: only ready, applicable production rules, excluding rule templates,
security hotspots and vulnerabilities that do not have a corresponding
supported review workflow. Both Maven and Gradle enforce this count; the
snapshot is a reproducible baseline, **not a claim of parity with a current
SonarQube or SonarCloud server's live profile**. Selecting `priority` remains
the default; `sonar-way` must be explicitly chosen.

Run `python3 scripts/verify-sonar-way-snapshot.py` to compare standard and
symbolic snapshot membership with [the catalog](rule-catalog.csv).
A deliberate analyzer upgrade must review and update the versioned resources,
eligibility count, tests and rule-value policy together.

## Missing local coverage worth pursuing

The pinned release also contains [21 symbolic Sonar Way rules](sonar-way-symbolic-java-8.6.0.37351.rules)
from a **separate symbolic-execution analyzer**, all present in our catalog
but not yet executed locally. We should first investigate:

| Rule | Reason to prioritize | Current status |
| --- | --- | --- |
| `java:S2095` | Resource leaks when closeable resources are not closed | Not enabled; requires symbolic-engine integration |
| `java:S2259` | Potential null-pointer dereferences | Not enabled; requires symbolic-engine integration |
| `java:S3655` | Accessing an empty `Optional` | Not enabled; requires symbolic-engine integration |

These are correctness and reliability checks, not cosmetic refactorings.
Adding their names to `enabledRules` cannot make them work: support requires
a compatible licensed symbolic analyzer, explicit registration, and real
positive/negative fixture tests under both Maven and Gradle.

There are **no missing standard Sonar Way rule identifiers** in the existing
catalog. Its 491 entries match the upstream membership exactly. The 99
not activated by our standalone selector are **policy/eligibility exclusions**,
not evidence that those analyzers can be safely enabled.

## Selection standard for further Eclipse warnings

We prioritize source-located, reproducible warnings that materially improve
correctness, avoid resource misuse, remove confusing control flow, or offer
clear maintainability benefits unavailable from active Sonar findings.

We do not auto-enable suggestions whose only justification is a taste for a
different spelling or form. In particular:

- `i++` versus `++i` is **not** a diagnostic by itself. The expressions
  return different values when their result is used. Replacing one with the
  other is not generally behavior-preserving.
- `java:S818` (uppercase literal suffixes) is not in our pinned Sonar Way;
  it remains a cosmetic, optional candidate, not an automatically enabled rule.
- `java:S881` concerns increments embedded in larger expressions, not simply
  prefix versus postfix style. It is also outside the pinned Sonar Way and
  must undergo false-positive and usefulness review before activation.
- Eclipse's `cleanup.extract_increment` is about separating a side effect
  from an expression. It should not be treated as a rule demanding `++i`
  wherever `i++` appears.

The [broader Eclipse cleanup profile](../src/it/broad-complementary/eclipse-cleanups.xml)
remains **opt-in**. Its confirmed distinct findings do not imply every cleanup
is valuable in every project. We should promote new rules only after a real
Eclipse positive fixture, a non-triggering fixture, a Sonar comparison and a
behavioral safety assessment.

The overlapping-rules pull request covering `S818` and `S1125` remains
separate; the snapshot work does not approve those candidates or modify
their filtering.
