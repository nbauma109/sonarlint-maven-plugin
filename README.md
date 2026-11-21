# SonarLint Maven Plugin

An independent, open-source Maven plugin project aiming to provide local Java static analysis with the ergonomics of Maven Checkstyle.

## Status

Early development. No working plugin or Maven Central release is available yet.

## Goals

- Report findings with a source file, line number, rule identifier and message directly in the Maven build.
- Run analysis locally without an analysis server, authentication token or report upload.
- Use real Sonar rules where licensing and analyzer compatibility permit.
- Integrate with the standard Maven lifecycle after production compilation.
- Report warnings by default and support optional build failure.
- Resolve source roots and the compilation classpath from each Maven project.
- Support offline analysis after normal Maven dependency resolution.

## Proposed usage

The following illustrates the intended configuration, not an available release:

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

The proposed default phase is `process-classes`. The first milestone is a verified end-to-end Java analysis that emits a Maven warning for a known cognitive complexity violation, without contacting a Sonar server.

See [the initial design](docs/design.md) and [licensing considerations](docs/licensing.md).

## License

Our original project code is licensed under the [MIT License](LICENSE). Third-party components retain their respective licenses. This project is independent of SonarSource and is not endorsed by it.
