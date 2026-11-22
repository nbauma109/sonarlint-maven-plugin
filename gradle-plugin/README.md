# Static Analysis Gradle Plugin

The Gradle adapter uses the same Java rule discovery, pinned offline SonarJava analyzer and
Eclipse cleanup diagnostic importer as the Maven plugin. Its own Gradle plugin identifier is
`io.github.nbauma109.static-analysis`. The implementation shares the neutral Java analyzer
sources with the Maven artifact; it does not invoke Maven or start a SonarQube server.

## Development

Requires Gradle 9.9.2 and Java 17 or newer:

```sh
gradle -p gradle-plugin build
gradle -p gradle-plugin publishToMavenLocal
```

The separate Gradle plugin artifact is
`io.github.nbauma109:static-analysis-gradle-plugin:0.1.0-SNAPSHOT`.

Use the local plugin in a Java project's `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

Then configure `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'io.github.nbauma109.static-analysis' version '0.1.0-SNAPSHOT'
}

repositories {
    mavenCentral()
}

staticAnalysis {
    profile = 'priority'
    failOnViolation = false
    cognitiveComplexityThreshold = 15
    enabledRules = ''
    disabledRules = ''
}
```

Run `gradle staticAnalysis`. The task is also attached to the Java project's `check`
lifecycle and runs after `classes`. For additional analysis, set
`profile = 'sonar-way'` (395 standard and reviewed symbolic checks). To use only
the standard 392-rule baseline, select `profile = 'sonar-way-standard'`. For explicit
rules, set `profile = 'none'` and
`enabledRules = 'java:S3776,java:S100'`. The `ruleParameters` property accepts
a nested map of case-sensitive upstream parameter names.

Eclipse cleanup support is also opt-in:

```groovy
staticAnalysis {
    eclipseEnabled = true
    eclipseExecutable = file('/path/to/eclipse/eclipse')
    eclipseCleanupProfile = file('eclipse-cleanups.xml')
    eclipseDuplicatePolicy = 'prefer-sonar'
    eclipseTimeoutSeconds = 600
}
```

The Eclipse runtime must contain the updated separate `refactoring-cli` plugin. On headless
Linux, provide a launcher executable wrapping Eclipse with `xvfb-run`.

This is an experimental plugin built for Java projects; it is not published to the Gradle
Plugin Portal yet. The shared analysis code is currently compiled into both plugin
artifacts, allowing a minimal change to the existing Maven build. The long-term
publishing layout can extract these files into a common library without changing the
analysis engine or its configuration contract.

All original source code is GPL-3.0-only; see the root [LICENSE](../LICENSE) and
[third-party notices](../THIRD_PARTY_NOTICES.md).
