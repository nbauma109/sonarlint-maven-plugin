# Initial design

## Build integration

The Maven plugin will expose a `check` goal with `process-classes` as its default phase and compile-scope dependency resolution. Running `mvn compile` alone does not reach this phase; `mvn process-classes`, `mvn test` and later phases do. Direct invocation should either require compiled classes or fail with a useful diagnostic.

Maven coordinates: `io.github.nbauma109:static-analysis-maven-plugin`. Initial development version: `0.1.0-SNAPSHOT`.

## Component boundaries

1. Maven adapter: select compile source roots, encoding, source level, binary output and resolved compilation classpath for the current Maven module.
2. Analysis adapter: initialize a compatible standalone analysis engine with licensed analyzers; configure rules and parameters; collect findings and analysis failures; release all resources.
3. Reporting and policy: emit deterministic, source-located Maven warnings, summarize findings, and optionally fail the build. Analysis failures must not be presented as successful clean analysis.

Keep the core adapter independent of Maven where practical. Do not upload source, use a remote quality profile, require a Sonar token, or silently replace unavailable Sonar rules with different rules.

A rule configuration should distinguish default activation, explicit inclusion and exclusion, and rule parameters. Exclusions should take precedence. Unknown explicitly configured rules must produce an actionable diagnostic.

## First milestone acceptance tests

- Analyze a small compiled Java fixture containing a genuine `java:S3776` violation.
- Verify the reported rule identifier, source location and message.
- Verify that the default warning mode succeeds and that failure-on-violation mode fails.
- Verify a clean fixture succeeds.
- Verify invalid analysis configuration and analysis errors do not report a false clean result.
- Repeat analysis with Maven offline after warming the dependency cache.
- Verify classpath resolution using a fixture with a Maven dependency.
- Verify packaging-only modules with no Java sources are skipped.

Initial scope is production Java sources. Test sources, reports, profile import, forked analyzer runtime and reactor integration tests can follow the standalone proof of concept.

## References

- [SonarLint Core](https://github.com/SonarSource/sonarlint-core)
- [SonarJava](https://github.com/SonarSource/sonar-java)
- [Remal Gradle SonarLint](https://github.com/remal-gradle-plugins/sonarlint)
- [Apache Maven Checkstyle Plugin](https://github.com/apache/maven-checkstyle-plugin)

Remal's MIT-licensed implementation demonstrates the standalone analysis architecture. Prefer a small adapter over copying its Gradle-specific infrastructure. Preserve upstream notices for any reused code.
