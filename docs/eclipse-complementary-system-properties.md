# Five complementary Eclipse Java cleanup warnings

This optional Eclipse profile enables five Java Development Tools cleanup choices that are
not mapped to any SonarJava rule in our pinned analyzer's duplicate filter.

| Eclipse cleanup option | Example to modernize |
| --- | --- |
| `cleanup.system_property_file_separator` | `System.getProperty("file.separator")` |
| `cleanup.system_property_path_separator` | `System.getProperty("path.separator")` |
| `cleanup.system_property_line_separator` | `System.getProperty("line.separator")` |
| `cleanup.system_property_file_encoding` | `System.getProperty("file.encoding")` |
| `cleanup.system_property_boolean` | `Boolean.parseBoolean(System.getProperty("com.example.enabled"))` |

These are five **sub-options of the system-property cleanup family**, not five
independent Eclipse cleanup modules. SonarJava may still have related diagnostics
on particular code constructs; the actual reported issues are checked with
our pinned offline sonar-way profile in integration tests.

The ready-to-use profile is [eclipse-cleanups.xml](../src/it/complementary-eclipse/eclipse-cleanups.xml).
It enables the required `cleanup.system_property` parent and all five options.

## Maven usage

Configure our Maven plugin:

```xml
<configuration>
    <profile>sonar-way</profile>
    <eclipseEnabled>true</eclipseEnabled>
    <eclipseExecutable>/path/to/eclipse-with-refactoring-cli</eclipseExecutable>
    <eclipseCleanupProfile>${project.basedir}/eclipse-cleanups.xml</eclipseCleanupProfile>
    <eclipseDuplicatePolicy>prefer-sonar</eclipseDuplicatePolicy>
</configuration>
```

Copy the profile into the consuming project, or use its absolute path.

## Gradle usage

```groovy
staticAnalysis {
    profile = 'sonar-way'
    eclipseEnabled = true
    eclipseExecutable = file('/path/to/eclipse-with-refactoring-cli')
    eclipseCleanupProfile = file('eclipse-cleanups.xml')
    eclipseDuplicatePolicy = 'prefer-sonar'
}
```

The Eclipse application must include the updated
[refactoring-cli](https://github.com/nbauma109/refactoring-cli) with report-only support.
These warnings work in Maven and Gradle through the same importer.

## Semantics and compatibility

This group is **opt-in**. Eclipse warns about potential transformations but the plugin
does not rewrite source files. Replacing a system property lookup with a convenience
constant or method can change behavior when applications change those properties
at runtime. Review each suggestion before applying a cleanup, especially
`file.encoding` and platform separators. Java source levels and available standard
library methods can affect which suggestions Eclipse can offer.

The real Eclipse CI test asserts that all five cleanup opportunities are reported,
that source contents remain byte-for-byte unchanged, and that they remain visible
with the pinned SonarJava `sonar-way` profile enabled. This is verified coverage
for the tested constructs rather than a claim that Sonar never covers related code.
