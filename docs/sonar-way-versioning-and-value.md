# Versioned Sonar Way and complementary rule value policy

## Authoritative source

The SonarJava artifact is pinned to `8.6.0.37351` and the SonarLint analysis
engine to `10.13.0.79996`. Our [versioned standard Sonar Way
snapshot](../src/main/resources/io/github/nbauma109/staticanalysis/sonar-way-java-8.6.0.37351.rules)
contains **491** rule identifiers copied from the
[matching upstream profile](https://github.com/SonarSource/sonar-java/blob/8.6.0.37351/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/Sonar_way_profile.json).
The runtime must find exactly the same set in the analyzer archive.

The standalone engine selects **392 standard** rules according to pinned
metadata: only ready, applicable production rules, excluding rule templates,
security hotspots and vulnerabilities that do not have a corresponding
supported review workflow. Both Maven and Gradle enforce this count. The opt-in `sonar-way` profile also
includes three verified symbolic rules for **395 total**, while
`sonar-way-standard` retains the 392-rule baseline for comparison. The
snapshot is reproducible, **not a claim of parity with a current SonarQube
or SonarCloud server's live profile**. Selecting `priority` remains the default.

Run `python3 scripts/verify-sonar-way-snapshot.py` to compare standard and
symbolic snapshot membership with [the catalog](rule-catalog.csv).
A deliberate analyzer upgrade must review and update the versioned resources,
eligibility count, tests and rule-value policy together.

## Evidence for logger-category rule `S3416`

The pinned Sonar Way profile contains `java:S3416`, and our
`sonar-way` selection activates it. The investigation of
[`transformer-api` pull request #244](https://github.com/nbauma109/transformer-api/pull/244)
confirmed that this was **not** a missing local rule:

1. The first build detected `S106` for `System.out` and `S1452` for
   generic wildcard return types. The mismatched logger declarations did
   not exist in that version of the code.
2. The logging replacement introduced two logger declarations whose
   categories referred to different classes. The [next Maven analysis
   run](https://github.com/nbauma109/transformer-api/actions/runs/45068291586)
   reported **two `S3416` violations and failed at `process-classes`**.
3. After correcting both categories, the [following Maven run](https://github.com/nbauma109/transformer-api/actions/runs/45068364110)
   reported **395 active rules, zero violations, and 80 passing tests**.

The integration fixture at `src/it/logger-categories` now tests this
behavior from a normal Maven build: a mismatched SLF4J logger category
must fail with exactly one `S3416` finding, whereas the matching
category is not reported. Disabling that rule must make the same
fixture pass. This guards against future profile-selection and
analysis-execution regressions, including local builds.

**This is not a guarantee of SonarCloud parity.** The local profile is
pinned to SonarJava `8.6.0.37351`, whereas SonarCloud uses its own
evolving analyzer and project-specific quality profile. Known
capability gaps include:

- **99 standard entries** from the pinned 491-rule Sonar Way snapshot
  excluded by the local eligibility and reporting policy; this count
  is not proof that all 99 could emit valid local findings.
- **18 symbolic rules** from the pinned 21-rule symbolic snapshot
  that have not been reviewed or enabled locally.
- **Test-source rules**, because the current Maven goal analyzes
  production sources only.
- **Server-dependent security analysis**, and newly added or changed
  rules in SonarCloud that are not implemented by the pinned analyzer.

To investigate a *real* missed finding, preserve its SonarCloud rule
identifier, file, source range, code version and effective quality
profile. First check whether the identifier is supported and selected,
then reproduce the same source with a compiled dependency classpath
under `process-classes`, checking both a positive case and a
non-triggering control. Do not treat a count of activated rule keys
as evidence that every rule will produce identical findings across
engines or analyzer versions.

## Missing local coverage worth pursuing

The pinned release also contains [21 symbolic Sonar Way rules](sonar-way-symbolic-java-8.6.0.37351.rules)
from a **separate symbolic-execution analyzer**. We now make three high-value checks
available through the opt-in `reliability` profile (confirmed in compiled
Maven and Gradle fixtures), while the remaining 18 require individual review.
The selected rules are:

| Rule | Reason to prioritize | Current status |
| --- | --- | --- |
| `java:S2095` | Resource leaks when closeable resources are not closed | Opt-in `reliability` profile; genuine source-located finding tested |
| `java:S2259` | Potential null-pointer dereferences | Opt-in `reliability` profile; genuine source-located finding tested |
| `java:S3655` | Accessing an empty `Optional` | Opt-in `reliability` profile; genuine source-located finding tested |

These are correctness and reliability checks, not cosmetic refactorings.
The `reliability` profile loads the compatible pinned symbolic analyzer
alongside SonarJava and activates exactly these three rules. The `sonar-way`
profile now combines these three with the 392 pinned standard rules for
**395 local checks**. The previous `sonar-way-reliability` selector remains
as a compatibility alias; `sonar-way-standard` explicitly excludes symbolic
analysis. Both profiles check an
unclosed resource, null dereference, and unsafe `Optional.get()`. Safe controls
and disabled-rule precedence are part of the integration fixtures. The other
18 symbolic entries remain catalogued but unavailable; their identifiers are
not sufficient to claim execution coverage.

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
