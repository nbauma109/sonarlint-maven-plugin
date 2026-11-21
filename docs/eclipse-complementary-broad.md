# Broad complementary Eclipse cleanup profile

This opt-in profile combines **15 complementary Eclipse cleanup options**. Ten options
are newly packaged in this batch; five existing system-property options are retained.
All are supported by the same report-only importer in Maven and Gradle.

## Ten options in the expanded batch

| Exact cleanup identifier | Java modernization | Verification boundary |
| --- | --- | --- |
| `cleanup.add_all` | Replace a loop of collection additions with an `addAll` operation | Distinct Eclipse warning on our Java 25 fixture |
| `cleanup.array_with_curly` | Simplify array initializers to curly braces where appropriate | Sonar's nearby `S1488` addresses a temporary return variable, not the initializer |
| `cleanup.reduce_indentation` | Reduce unnecessary nesting after terminating branches | Distinct Eclipse warning |
| `cleanup.remove_redundant_modifiers` | Remove redundant interface member modifiers | The fixture exercises a single redundant modifier and requires one warning |
| `cleanup.system_property_javaversion` | Modernize a Java runtime-version property lookup | Distinct Eclipse warning |
| `cleanup.system_property_javaspecversion` | Modernize a Java specification-version property lookup | Distinct Eclipse warning |
| `cleanup.module_imports` | Consolidate ordinary Java imports with Java 25 module imports | Requires Java 25 and module-aware compilation |
| `cleanup.pull_up_assignment` | Move an assignment out of an `if` condition | Distinct Eclipse warning on the Java 25 test fixture |
| `cleanup.extract_increment` | Extract an increment or decrement from an expression | Distinct Eclipse warning on the Java 25 test fixture |
| `cleanup.use_string_is_blank` | Replace `strip().isEmpty()` with `isBlank()` | Java 11 or later; distinct tested Eclipse warning |

The existing five system-property options remain included:
`cleanup.system_property_file_separator`,
`cleanup.system_property_path_separator`,
`cleanup.system_property_line_separator`,
`cleanup.system_property_file_encoding` and
`cleanup.system_property_boolean`.

Use [the ready-to-copy XML profile](../src/it/broad-complementary/eclipse-cleanups.xml) for
Java 25 projects with the Eclipse cleanup CLI installed. Both Maven and Gradle accept it
via their existing `eclipseCleanupProfile` configuration property.

This is not a claim that SonarJava has no related rules in any version or configuration.
Our integration suite compares real Eclipse findings against the pinned SonarJava
`sonar-way` profile on a reproducible Java 25 fixture. The package remains opt-in
because refactorings may not preserve behavior in every application context, especially
when runtime system properties are overridden.

## Test strategy

The real-engine workflow compiles the Java 25 fixture with a module declaration, runs
the pinned Sonar analyzer and Eclipse cleanup importer in both build systems, and
requires exact Eclipse rule identifiers. For the fixture it verifies ten new
cleanup identifiers, confirms redundant modifiers produce one warning, checks that
there are no suppressed diagnostics, compares Maven and Gradle results and ensures
the original source files remain unchanged. Existing coverage verifies the five
previous system-property options separately.

[Candidate profile](../src/it/broad-complementary/eclipse-cleanups-candidates.xml)
contains additional cleanup options being screened. It is not intended as the
production profile: several candidates already overlap Sonar rules or lack verified
fixture coverage.
