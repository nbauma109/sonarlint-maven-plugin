# Static Analysis Maven Plugin

Local Java static analysis for Maven with Checkstyle-like warnings, using the standalone SonarJava engine. Complementary Eclipse cleanups are planned.

## Status

Experimental, unreleased proof of concept. The priority profile activates eight standard Java rules. Rule metadata and Sonar Way membership are discovered from the locally resolved analyzer archive. The expanded integration suite must pass before release. The analyzer is pinned to its LGPL-era release; modern SonarCloud rule parity is not claimed.

## Build and test

Requires Maven 3.9.9 and Java 17 or later to run the plugin. The consuming project may target an older Java release.

```sh
mvn clean install
bash scripts/integration-test.sh
```

The script verifies source-located findings on Java 8 fixtures, offline execution, optional build failure, configurable complexity threshold, the priority and Sonar Way profiles, rule selection and unsupported-rule rejection.

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
| `staticAnalysis.profile` | `priority` | priority, complexity, sonar-way or none |
| `staticAnalysis.enabledRules` | empty | Comma-separated additional rule keys |
| `staticAnalysis.disabledRules` | empty | Comma-separated exclusions, taking precedence |

The plugin analyzes production Java source roots and their resolved compile classpath. It has no server configuration, authentication token or report upload. Normal Maven dependency downloads may occur on the first build. Once dependencies are cached, use Maven offline mode (`-o`).

## Rule profiles

- `priority`: S100, S107, S112, S1128, S1192, S1066, S1481 and S3776.
- `complexity`: S3776 only.
- `sonar-way`: eligible ready standard production rules from the analyzer's embedded Sonar Way profile; experimental and not equivalent to a server profile.
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

Only production Java sources are analyzed. Symbolic execution, test sources, security review reporting, complete rule parameter validation, reactor tests, modern language compatibility, and XML reporting are not yet implemented. The older analyzer and engine require a security and compatibility review before publication.

See [the design](docs/design.md) and [licensing considerations](docs/licensing.md).

## License

Our original project code is licensed under the [GNU General Public License version 3](LICENSE) (GPL-3.0-only). Third-party components retain their respective licenses. This project is independent of SonarSource and is not endorsed by it.

## Project name

The descriptive name **Static Analysis Maven Plugin** refers to this independent project, not an official SonarSource or Eclipse Foundation product. Our rule implementation still uses the pinned SonarJava and SonarLint Core upstream libraries. We use their names only to describe compatibility and dependencies.
