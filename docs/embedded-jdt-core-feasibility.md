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

The Java 17 baseline uses
`org.eclipse.jdt:org.eclipse.jdt.core:3.40.0` and
`org.eclipse.jdt:org.eclipse.jdt.core.manipulation:1.21.300`, both
published to Maven Central. Core Manipulation is not two independent
Java archives: its OSGi metadata also lists core runtime/resources,
refactoring, text, launching and related dependencies. Maven should resolve
those transitive library dependencies; the goal is to eliminate the
separate **Eclipse installation**, not to deny the transitive dependency graph.

This experiment intentionally does not register a new Maven plugin goal or
silently change the existing `sonar-way` profile. It verifies one conservative
standalone execution path before touching the production integration.

## Execution

```sh
mvn -B -ntp -f src/it/embedded-jdt-core-cleanups/pom.xml compile exec:java
```

The source fixture contains two fields that can be `final` (one initialized
at declaration, one through delegated constructors) and one deliberately
mutable field. The expected output is
`EMBEDDED_JDT_CORE_SUCCESS`, with exactly the two eligible field names.

## Scope and release blockers

- **Binding and classpath fidelity:** Production usage must parse the
  project's actual source files and dependencies, not an inline fixture,
  and verify all eight pinned `S9438` findings and non-triggering controls.
- **Source ranges:** The probe checks the Eclipse visitor's selected
  declarations. Production diagnostics need stable field ranges and must
  remain strictly report-only.
- **Runtime compatibility:** Core Manipulation 1.21.300 targets Java 17;
  the 2026-09 Core Manipulation bundle requires Java 25. A supported
  approach for Java 25 source must be chosen without raising the Maven
  plugin's Java 17 minimum.
- **Licensing:** Bundling Eclipse EPL-2.0 libraries alongside the GPLv3
  Maven plugin requires an explicit license and distribution review,
  including secondary-license notices where applicable.
- **No scope expansion:** This experiment covers the field-finality
  visitor only. Other Eclipse cleanups may need workspace/refactoring
  services that cannot operate in this minimal environment.
