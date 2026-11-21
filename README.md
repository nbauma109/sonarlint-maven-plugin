# SonarLint Maven Plugin

Local Java static analysis for Maven with Checkstyle-like warnings, using Sonar's standalone analysis engine.

## Status

Experimental, unreleased proof of concept. This initial implementation activates only the real SonarJava cognitive complexity rule (`java:S3776`). Compatibility and integration tests must pass before it is considered usable. The analyzer is pinned to its LGPL-era release; modern SonarCloud rule parity is not claimed.

## Build and test

Requires Maven 3.9.9 and Java 17 or later to run the plugin. The consuming project may target an older Java release.

```sh
mvn clean install
bash scripts/integration-test.sh
```

The script verifies a source-located warning, offline execution after dependency resolution, optional build failure, and a configurable threshold on a Java 8 fixture.

## Usage from a locally installed snapshot

```xml
<plugin>
    <groupId>io.github.nbauma109</groupId>
    <artifactId>sonarlint-maven-plugin</artifactId>
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
mvn compile io.github.nbauma109:sonarlint-maven-plugin:0.1.0-SNAPSHOT:check
```

| Property | Default | Purpose |
| --- | --- | --- |
| `sonarlint.skip` | `false` | Skip analysis |
| `sonarlint.failOnViolation` | `false` | Fail if any violation is reported |
| `sonarlint.cognitiveComplexityThreshold` | `15` | Maximum allowed cognitive complexity |

The plugin analyzes production Java source roots and their resolved compile classpath. It has no server configuration, authentication token or report upload. Normal Maven dependency downloads may occur on the first build. Once dependencies are cached, use Maven offline mode (`-o`).

## Limitations

Only production Java sources and rule S3776 are in the initial scope. Default profile discovery, additional rules, symbolic execution, test sources, exclusions, reactor tests, modern language compatibility, and XML reporting are not yet implemented. The older analyzer and engine require a security and compatibility review before publication.

See [the design](docs/design.md) and [licensing considerations](docs/licensing.md).

## License

Our original project code is licensed under the [MIT License](LICENSE). Third-party components retain their respective licenses. This project is independent of SonarSource and is not endorsed by it.
