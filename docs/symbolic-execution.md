# Symbolic execution compatibility gate

The offline Java analyzer uses the LGPL-era SonarJava `8.6.0.37351` and
SonarLint analysis engine `10.13.0.79996`.

The separate Java symbolic execution engine must be loaded as a second
SonarLint-compatible plugin, not misclassified as rules in the standard
`sonar-java-plugin`. We are evaluating the *matching*
`org.sonarsource.java:sonar-java-symbolic-execution-plugin:8.6.0.37351`
release before allowing the Maven and Gradle adapters to distribute or execute it.

The symbolic Java Sonar Way profile contains 21 keys, versioned in
`sonar-way-symbolic-java-8.6.0.37351.rules`. The actual matching analyzer
archive has passed the manifest and LGPLv3 license gate in GitHub Actions.
Runtime loading and positive fixture checks for `java:S2259`, `java:S2095`
and `java:S3655` are required before enabling it by default.

Both build adapters support an **experimental opt-in** flag:
`staticAnalysis.symbolicEnabled=true` in Maven, or
`staticAnalysis { symbolicEnabled = true }` in Gradle. When enabled,
the pinned symbolic rules are added to `sonar-way` and may be selected
explicitly using `enabledRules`. When disabled, existing analysis is
unchanged and the Gradle symbolic dependency is not resolved.

The extra symbolic engine can incur substantial path exploration on
complicated methods; measure wall-clock runtime and memory usage on
representative projects before making it the default.

## Acceptance criteria

- The artifact resolves from Maven Central with a plugin manifest, actual
  JavaSE metadata and Sonar Way rule definitions.
- Verify the exact artifact license and historical release date before
  redistribution. Do not substitute a newer SSALv1 release.
- The SonarLint loader recognizes both the Java and JavaSE plugins and
  reports genuine bugs, rather than only registering rule identifiers.
- With symbolic analysis disabled, established standalone Maven and Gradle
  analyses behave exactly as before.
- With symbolic analysis enabled, representative Java 17 and 21 fixtures
  detect the intended bug paths and clean fixtures remain clean.
- The symbolic analyzer and standard analyzer are version-pinned together.
- A CI runtime comparison measures cost on the same representative fixture
  before symbolic execution becomes the default.

Source-available Sonar analyzers released after November 29, 2024 are
under a different license. We do not bundle those into our GPL-3.0-only
project without a separate compatibility and legal review.
