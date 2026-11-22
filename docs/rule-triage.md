# Java rule capability triage

Baseline: SonarJava 8.6.0.37351 and SonarLint Analysis Engine 10.13.0.79996.

The [rule catalog](rule-catalog.csv) inventories 692 entries: 644 numbered standard analyzer rules, the legacy NoSonar rule, 23 symbolic execution rules, and 24 separately documented Java security engine rules. The upstream standard and symbolic Sonar Way profiles contain 491 and 21 entries respectively. These are profile membership counts, not claims of executable coverage.

The catalog does not enumerate the entire current SonarCloud catalog. Its commercial bug detection, architecture, newer analyzer rules, custom rules and organization-specific profiles must be compared separately. Missing keys are unknown, not automatically classified as cloud-only.

## Capability model

| Classification | Meaning |
| --- | --- |
| LOCAL_SYNTAX | Local production rule with an observed offline positive result. |
| LOCAL_SYNTAX_CANDIDATE | A source-oriented check proposed for standalone execution. |
| LOCAL_SEMANTIC_CANDIDATE | A local check expected to need type or classpath information. |
| LOCAL_STANDARD_CANDIDATE | Present in the standard analyzer metadata; exact execution requirements or eligibility remain unverified. |
| LOCAL_SYMBOLIC_CANDIDATE | Present in the separately packaged symbolic execution analyzer; enable only after verifying the matching analyzer artifact, registration, and local fixtures. |
| LOCAL_TEST_CANDIDATE | Requires test source classification and the test dependency classpath. |
| RULE_TEMPLATE | A rule definition used to create configured instances; not a normal rule to activate directly. |
| SERVER_REQUIRED | Documented external Java security analysis engine; not executable by our selected standalone analyzer. |

Scope (Main, Tests, All), upstream status, default profile membership, rule type, manual review and verification are independent dimensions. Unknown metadata is left blank or UNKNOWN. A local security hotspot is a review candidate, not proof of vulnerability. A rule listed in an analyzer is not necessarily usable in the selected runtime: it can require unavailable services, project-wide context, a different runtime, or a parameterized instance.

The symbolic execution resource directory is named javase, but the actual repository key is java, as confirmed by JavaSECheckRegistrar. For example, java:S2259, java:S2095 and java:S3655 must not be mistaken for server-only checks. Conversely, the external javasecurity repository is a different engine. Some current commercial dataflow rules can be executed in certain connected IDE environments; we do not load that engine and cannot claim their availability.

## Initial activation strategy

1. Discover rule definitions from the actual standalone engine, respecting the analyzer version, rule scope, status, templates, and the user-selected configuration.
2. Enable eligible standard Sonar Way rules in production source analysis; exclude unsupported and unverified execution paths until tests pass.
3. Provide adequate Maven source level, target JDK, compiled classes, dependency classpath and module context. Test sources must be identified separately.
4. The compatible LGPLv3 symbolic execution artifact at version 8.6.0.37351 is now bundled for the opt-in `reliability` and `sonar-way-reliability` profiles. Real Maven and Gradle fixture tests verify null dereferences (S2259), resource leaks (S2095) and unsafe `Optional.get()` access (S3655). Further symbolic rules require positive and negative validation before inclusion.
5. Emit security hotspots separately as review warnings. Retain the server for taint, commercial analysis, architecture, quality gates, coverage/duplication metrics and review history.
6. Validate configured but unavailable rule keys explicitly. Never silently discard an unsupported rule or imply that a local clean result is a complete SonarCloud analysis.

## Verification status

The [first integration run](https://github.com/nbauma109/static-analysis-maven-plugin/actions/runs/44886653239) compiled the plugin and produced the real java:S3776 finding (complexity 21 versus 15) in both the normal and Maven offline invocations on Java 17. The failure-on-violation case also failed as expected. The threshold override case was unsuccessful because SonarJava's rule property is case-sensitive (Threshold); the original implementation used threshold. The corrective commit changes the key; the corrected test requires a new run. No other rules have been exercised in our plugin.

UPSTREAM_STANDALONE_TEST indicates a positive upstream SonarLint fixture, not a successful test on our Maven adapter. Upstream SonarLintTest confirms S106, S1481 and S2925 as examples. All other candidates require local fixtures. Do not report aggregate offline coverage from these counts.

## Reproducing the metadata

The CSV preserves exact keys and Sonar Way membership from the upstream tree and profiles. To fill remaining scope, type, status and review fields locally, use the standard-library-only metadata script with the matching analyzer JARs:

```sh
python3 scripts/enrich-rule-catalog.py \
  /path/to/sonar-java-plugin-8.6.0.37351.jar \
  /path/to/sonar-java-symbolic-execution-plugin-8.6.0.37351.jar \
  --output target/rule-catalog.csv
```

This is a development-time inventory operation; it reads only local archives and does not change the plugin's runtime dependencies. It does not establish execution compatibility. A build-time rule capability test must check for actual findings, not merely metadata presence.

## Sources

- [Standard Java rules](https://github.com/SonarSource/sonar-java/tree/8.6.0.37351/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java)
- [Symbolic execution rules](https://github.com/SonarSource/sonar-java/tree/8.6.0.37351/java-symbolic-execution/java-symbolic-execution-plugin/src/main/resources/org/sonar/l10n/java/rules/javase)
- [Standard rule definitions and templates](https://github.com/SonarSource/sonar-java/blob/8.6.0.37351/sonar-java-plugin/src/main/java/org/sonar/plugins/java/JavaRulesDefinition.java)
- [Symbolic execution registration and repository key](https://github.com/SonarSource/sonar-java/blob/8.6.0.37351/java-symbolic-execution/java-symbolic-execution-plugin/src/main/java/org/sonar/java/se/plugin/JavaSECheckRegistrar.java)
- [Symbolic analyzer packaging](https://github.com/SonarSource/sonar-java/blob/8.6.0.37351/java-symbolic-execution/java-symbolic-execution-plugin/pom.xml)
- [Check list generator and rule source scopes](https://github.com/SonarSource/sonar-java/blob/8.6.0.37351/check-list/src/main/java/org/sonar/java/CheckListGenerator.java)
- [Upstream standalone integration tests](https://github.com/SonarSource/sonar-java/blob/8.6.0.37351/its/plugin/tests/src/test/java/com/sonar/it/java/suite/SonarLintTest.java)
- [Current standalone rule limitations](https://docs.sonarsource.com/sonarqube-for-intellij/using/rules)
- [External security engine rules and repository](https://docs.sonarsource.com/sonarqube-server/2025.1/analyzing-source-code/security-engine-custom-configuration)
- [Security hotspot review](https://docs.sonarsource.com/sonarqube-cloud/standards/managing-rules/security-hotspots)
