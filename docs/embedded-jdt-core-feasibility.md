# Embedded Eclipse JDT Core Manipulation feasibility

The Maven plugin currently requires a separately installed Eclipse runtime and a compatible
`refactoring-cli` to invoke Eclipse cleanup rules. This experiment checks whether
the existing Eclipse **core implementation** of the private-field finality cleanup
can run directly on Java compiler sources without an IDE, an Eclipse application,
a graphical display, or a preinstalled `refactoring-cli`.

## Component provenance

The Eclipse JDT Core Manipulation bundle includes
`VariableDeclarationFixCore`, `VariableDeclarationCleanUpCore` and the
`VariableDeclarationFinder`/ `WrittenNamesFinder` visitors. The probe
uses the **actual Eclipse visitors**, rather than duplicating their finality
logic. Those classes are internal APIs, so future version changes require
a pinned integration regression.

The Java 17 runtime baseline uses
`org.eclipse.jdt:org.eclipse.jdt.core:3.45.0` and
`org.eclipse.jdt:org.eclipse.jdt.core.manipulation:1.21.300`, both
published to Maven Central. Core Manipulation is not two independent
Java archives: its OSGi metadata also lists core runtime/resources,
refactoring, text, launching and related dependencies. Maven should resolve
those transitive library dependencies; the goal is to eliminate the
separate **Eclipse installation**, not to deny the transitive dependency graph.

This experiment intentionally does not register a new Maven plugin goal or
silently change the existing `sonar-way` profile. It verifies one conservative
standalone execution path before touching the production integration.
The proposed production adapter can reuse these core visitors, but must
handle source roots, diagnostics, classpath fidelity, and cleanup selection
under strict Maven failure semantics.

## Execution

```sh
mvn -B -ntp -f src/it/embedded-jdt-core-cleanups/pom.xml compile exec:java
```

The source fixture contains two fields that can be `final` (one initialized
at declaration, one through delegated constructors) and one deliberately
mutable field. The check passes on Java 17, Java 21 and Java 25, reporting
`EMBEDDED_JDT_CORE_SUCCESS` with exactly the two eligible field names.
The Java 25 job additionally verifies parsing of a flexible constructor
body that places statements before `super(...)`.

For real-project parity, the Java 25 job compiles the exact
[`transformer-api` source revision](https://github.com/nbauma109/transformer-api/commit/fdc55663e6c9d5836db57e6369fd08d6bc4651cd)
and passes its resolved Maven classpath to the same JDT Core Manipulation
visitors. This reproduced **all eight pinned `S9438` field findings at
the exact source lines, with no extra findings**, without an installed
Eclipse application or `refactoring-cli`. The
[successful workflow run](https://github.com/nbauma109/static-analysis-maven-plugin/actions/runs/45081615043)
verifies this behavior end to end.

The standalone probe also checks the resolved dependency tree for
Eclipse user-interface and application artifacts, in addition to the
compiler and Core Manipulation runtime dependencies.

## Scope and release blockers

- **Binding and classpath fidelity:** The standalone probe now parses the
  actual pinned `transformer-api` sources and dependencies, and all eight
  `S9438` findings match. A future Maven adapter still needs to handle
  arbitrary source roots, module paths and unresolved source safely.
- **Source ranges:** The probe checks the Eclipse visitor's selected
  declarations. Production diagnostics need stable field ranges and must
  remain strictly report-only.
- **Runtime compatibility:** Core Manipulation 1.21.300 targets Java 17; the 2026-09 Core
  Manipulation bundle requires Java 25. Core 3.45.0 accepts Java 25 syntax
  while keeping a Java 17 runtime requirement, but combining these
  artifacts from different Eclipse releases is not a supported release
  pairing until end-to-end validation passes. Protect the Java 17 plugin
  minimum and the Java 25 consuming-source scope independently.
- **Licensing:** Bundling Eclipse EPL-2.0 libraries alongside the GPLv3
  Maven plugin requires an explicit license and distribution review,
  including secondary-license notices where applicable.
- **No scope expansion:** This experiment covers the field-finality
  visitor only. Other Eclipse cleanups may need workspace/refactoring
  services that cannot operate in this minimal environment.
