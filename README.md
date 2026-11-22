# Static Analysis Maven Plugin

Local Java static analysis for Maven with Checkstyle-like warnings, using the standalone SonarJava engine. Optional Eclipse cleanup analysis runs in a separate, report-only process.

## Status

Experimental, unreleased proof of concept. The priority profile activates eight standard Java rules. Rule metadata and Sonar Way membership are discovered from the locally resolved analyzer archive. The expanded integration suite must pass before release. The analyzer is pinned to its LGPL-era release; modern SonarCloud rule parity is not claimed.

## Build and test

Requires Maven 3.9.9 and Java 17 or later to run the plugin. The consuming project may target an older Java release.

```sh
mvn clean install
bash scripts/integration-test.sh
```

The script verifies source-located findings on Java 8 fixtures, offline execution, optional build failure, configurable complexity threshold, the priority and Sonar Way profiles, rule selection and unsupported-rule rejection.

## Maven Central release preparation

The Maven artifact is prepared for a signed release through the Maven Release
Plugin and Sonatype's Central Publishing Portal. See the
[release procedure](docs/maven-central-release.md) for validation, credentials,
signing, tagging, uploading and promotion. Until the first release is visible on
Maven Central, use the locally installed snapshot below. The separate Gradle
adapter is not published by this Maven release procedure.

## Usage from a locally installed snapshot

```xml
<plugin>
    <groupId>io.github.nbauma109</groupId>
    <artifactId>static-analysis-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Run `mvn process-classes`, `mvn test` or a later lifecycle phase. The goal runs after compilation. For a direct invocation:

```sh
mvn compile io.github.nbauma109:static-analysis-maven-plugin:0.1.0-SNAPSHOT:check
```

| Property | Default | Purpose |
| --- | --- | --- |
| `staticAnalysis.skip` | `false` | Skip analysis |
| `staticAnalysis.failOnViolation` | `false` | Fail if any violation is reported |
| `staticAnalysis.cognitiveComplexityThreshold` | `15` | Maximum allowed cognitive complexity |
| `staticAnalysis.profile` | `priority` | priority, complexity, sonar-way, sonar-way-standard, reliability, sonar-way-reliability or none |
| `staticAnalysis.enabledRules` | empty | Comma-separated additional rule keys |
| `staticAnalysis.disabledRules` | empty | Comma-separated exclusions, taking precedence |
| `staticAnalysis.eclipseEnabled` | `false` | Enable a separate Eclipse cleanup process |
| `staticAnalysis.eclipseExecutable` | empty | Eclipse executable or launcher wrapper |
| `staticAnalysis.eclipseCleanupProfile` | empty | Exported Eclipse cleanup profile |
| `staticAnalysis.eclipseDuplicatePolicy` | `prefer-sonar` | Suppress verified overlapping Sonar findings, or `none` |
| `staticAnalysis.eclipseTimeoutSeconds` | `600` | Eclipse process timeout in seconds |

The plugin analyzes production Java source roots and their resolved compile classpath. It has no server configuration, authentication token or report upload. Normal Maven dependency downloads may occur on the first build. Once dependencies are cached, use Maven offline mode (`-o`).

## Pinned Sonar Way membership

The Java Sonar Way selection is versioned in
[`sonar-way-java-8.6.0.37351.rules`](src/main/resources/io/github/nbauma109/staticanalysis/sonar-way-java-8.6.0.37351.rules).
It contains 491 upstream Java rule identifiers; 392 qualify for our local
standalone production analyzer after scope, status, security, and template
restrictions. The analyzer checks the resource against the actual pinned Java
plugin archive and fails if its membership or eligible rule count changes.
Maven and Gradle use the same snapshot.

**Profile migration:** previously, `sonar-way` selected 392 standard rules. It now selects
395 including the three verified symbolic checks. To retain the exact previous rule
selection, use `sonar-way-standard`. The default `priority` profile is unchanged.

The separate [21 symbolic Sonar Way rules](docs/sonar-way-symbolic-java-8.6.0.37351.rules)
are recorded independently. The opt-in `sonar-way` profile includes the 392 eligible
standard rules **plus three reviewed symbolic correctness checks** for resource leaks,
null dereferences and unsafe `Optional` access (395 total). The `sonar-way-standard`
profile retains the original 392-rule baseline, and `reliability` runs just those
three symbolic checks. The other 18 remain unimplemented locally. We do not automatically
promote cosmetic rules such as uppercase literal suffixes or wholesale increment
rewriting. See [the rule selection policy](docs/sonar-way-versioning-and-value.md).

## Rule profiles

- `priority`: S100, S107, S112, S1128, S1192, S1066, S1481 and S3776.
- `complexity`: S3776 only.
- `sonar-way`: the 392 eligible standard Java rules **plus** three verified symbolic correctness checks (395 total); this opt-in profile is not equivalent to a server profile.
- `sonar-way-standard`: 392 eligible standard Java rules, without the symbolic analyzer, for comparisons and explicit opt-out.
- `reliability`: opt-in symbolic execution checks for unclosed resources (`S2095`), null dereferences (`S2259`) and unsafe `Optional.get()` (`S3655`); requires the separately packaged matching symbolic analyzer.
- `sonar-way-reliability`: backward-compatible alias for the 395-rule `sonar-way` profile.
- `none`: select rules explicitly.

Example:

```sh
mvn process-classes -DstaticAnalysis.profile=none -DstaticAnalysis.enabledRules=java:S100,java:S3776
```

Per-rule properties can also be supplied in the plugin configuration:

```xml
<configuration>
    <profile>none</profile>
    <enabledRules>java:S3776</enabledRules>
    <ruleParameters>
        <rule>
            <key>java:S3776</key>
            <properties>
                <Threshold>20</Threshold>
            </properties>
        </rule>
    </ruleParameters>
</configuration>
```

Property names are case-sensitive. Explicit property overrides take precedence over the dedicated complexity threshold. Unknown, test-only, template, symbolic and server-only rules cannot be explicitly enabled. Security hotspot and vulnerability reporting is deliberately deferred. Metadata alone does not establish that a rule will emit findings; see the [capability catalog](docs/rule-catalog.csv).

## Limitations

Only production Java sources are analyzed. Three reviewed symbolic execution rules are available through `sonar-way`, `reliability` and the compatibility alias `sonar-way-reliability`. Test-source analysis, security review reporting, complete rule parameter validation, reactor tests, modern language compatibility, and XML reporting are not yet implemented. The older analyzer and engine require a security and compatibility review before publication.

See [the design](docs/design.md) and [licensing considerations](docs/licensing.md).

## Gradle support (experimental)

A separate Gradle binary plugin is maintained in [gradle-plugin](gradle-plugin/README.md),
using the same offline SonarJava rule catalog and Eclipse cleanup report importer as this Maven
plugin. Its identifier is `io.github.nbauma109.static-analysis` and it provides the
`staticAnalysis` task, also attached to the Java `check` lifecycle. The Gradle adapter
is not yet released to the Gradle Plugin Portal; see its README for how to build and
publish a local snapshot with Gradle 9.9.2.

## License

Our original project code is licensed under the [GNU General Public License version 3](LICENSE) (GPL-3.0-only). Third-party components retain their respective licenses. This project is independent of SonarSource and is not endorsed by it.

## Project name

The descriptive name **Static Analysis Maven Plugin** refers to this independent project, not an official SonarSource or Eclipse Foundation product. Our rule implementation still uses the pinned SonarJava and SonarLint Core upstream libraries. We use their names only to describe compatibility and dependencies.

## Eclipse cleanup warnings (experimental)

An opt-in second analysis engine reports Eclipse Java Development Tools cleanup opportunities **without changing source files**. This requires an Eclipse installation with a version of [refactoring-cli](https://github.com/nbauma109/refactoring-cli) supporting `--report` and `--source-root`; the previously released 2.0.0 CLI cannot produce these reports. No automatic runtime downloads occur inside the goal.

```xml
<configuration>
    <eclipseEnabled>true</eclipseEnabled>
    <eclipseExecutable>/path/to/eclipse/eclipse</eclipseExecutable>
    <eclipseCleanupProfile>${project.basedir}/eclipse-cleanups.xml</eclipseCleanupProfile>
    <eclipseDuplicatePolicy>prefer-sonar</eclipseDuplicatePolicy>
</configuration>
```

On Linux build agents without a graphical display, configure `eclipseExecutable` to an executable wrapper that starts Eclipse through `xvfb-run` and forwards all arguments. The Eclipse runtime and its refactoring CLI plugin must be installed separately. Eclipse analysis runs in a separate process, and the application writes structured diagnostics under `target/static-analysis/eclipse`. A nonzero exit code, missing profile, missing executable or malformed report fails analysis instead of silently succeeding.

Duplicate suppression is conservative and source-range aware. Five mappings are enabled for exact Eclipse cleanup identifiers: unused imports (`S1128`), unnecessary casts (`S1905`), unused local variables (`S1481`), missing `@Override` (`S1161`) and try-with-resources (`S2093`). Real Eclipse and Sonar integration tests verify the latter two using a concrete overridden method and a `java.io.Closeable` resource. The Eclipse CLI groups related refactoring text edits so one cleanup opportunity produces one warning. We retain Eclipse findings when no matching Sonar issue is reported, and ambiguous cleanup modules remain visible. Other potential overlaps require rule-by-rule verification.

The Eclipse report-only CLI currently rejects profiles enabling its custom `cleanup.instanceof` fallback; create a reporting profile with that option disabled until a safe preview mechanism is available. The separate Java 25 Eclipse process is not covered by the Maven plugin's Java 17 minimum runtime. See the [integration design](docs/eclipse-cleanup-integration.md).

## Five complementary Eclipse warnings

We also provide an opt-in profile for five system-property modernization cleanups
that are not mapped to Sonar rule duplicates. The same profile works with Maven and
Gradle without source modifications. See [the complementary Eclipse rule guide](docs/eclipse-complementary-system-properties.md)
and [the example profile](src/it/complementary-eclipse/eclipse-cleanups.xml).

## Broader complementary Eclipse cleanup profile

A [Java 25 complementary profile](docs/eclipse-complementary-broad.md) enables 13 Eclipse
cleanup options (eight added in the latest batch, plus five system-property cleanups).
Both Maven and Gradle can use the [XML profile](src/it/broad-complementary/eclipse-cleanups.xml)
with `eclipseEnabled` enabled and the configured external Eclipse runtime. These options
remain opt-in because cleanup suggestions may have compatibility implications.

## Overlap review and signal quality

Our [Eclipse–Sonar overlap audit](docs/eclipse-overlap-audit.md) distinguishes the five
verified exact-duplicate mappings from the many catalog entries still requiring
confirmation. We prioritize correctness and maintainability over cosmetic rewrites.
A change between `i++` and `++i` by itself is not a useful build warning.
The `reliability` and `sonar-way-reliability` profiles use real symbolic execution,
and [CI records a representative runtime comparison](docs/symbolic-performance.md).

## Signal-first Eclipse recommendation

For low-noise Eclipse maintainability warnings, use
[`eclipse-cleanups-recommended.xml`](src/it/broad-complementary/eclipse-cleanups-recommended.xml):
it enables assignment extraction, needless nesting reduction, and collection `addAll` conversion.
For cosmetic preferences, explicitly select the separate
[`eclipse-cleanups-aesthetics.xml`](src/it/broad-complementary/eclipse-cleanups-aesthetics.xml).
It enables array-initializer simplification and redundant-modifier cleanup only when selected;
additional `final`, type-inference, parentheses and formatting preferences are present as
individually switchable settings, initially `false`. No profile is loaded automatically:
`staticAnalysis.eclipseEnabled` defaults to `false`.
The broader 13-option modernization profile stays available without aesthetic warnings.
Sonar Way still contains its own versioned rules, including `java:S1124` and
`java:S1155`; those are not duplicated by Eclipse's aesthetic profile.
Our [rule-value review](docs/eclipse-rule-value-review.md) ranks the current picks,
the next eight substantive candidates and the trivial/style-only options we would
usually omit. Neither profile changes source code automatically.

## Opt-in high-signal Eclipse cleanup

For a substantive Eclipse diagnostic not included in the versioned Sonar Way
profile, see [non-static nested-class review](docs/eclipse-high-signal-comparison.md)
and the [high-signal Eclipse XML profile](src/it/high-signal-eclipse/eclipse-cleanups-high-signal.xml).
It reports an inner class that needlessly retains its enclosing instance.
When Sonar `S2694` is explicitly enabled, the same class declaration is
reported only once after exact-range matching. Regex precompilation remains
an experimental option until auxiliary edits can be attributed precisely.
