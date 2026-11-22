# Third-party components

Our own code is licensed GPL-3.0-only. The components below are not relicensed by this project.

| Component | Selected version or role | Licence | Distribution notes |
| --- | --- | --- | --- |
| SonarLint Analysis Engine and other SonarLint Core modules | 10.13.0.79996 | GNU Lesser General Public License, version 3 | Retain notices; provide corresponding source and required replacement/relinking rights when distributing the combined executable. |
| SonarJava | 8.6.0.37351 | GNU Lesser General Public License, version 3 | Historical LGPL-era analyzer. Preserve upstream notices and distribution rights. Later releases have different terms. |
| Eclipse JDT Core Manipulation and Maven-resolved Eclipse Platform dependencies | 1.18.0 (embedded private-field cleanup) | Eclipse Public License, version 2.0, and component-specific transitive licences | Bundled for embedded cleanup detection in the development Maven plugin. Resolve and audit the full transitive dependency licence inventory before any release. |\n| Eclipse IDE and refactoring-cli integration | Optional legacy executable | Eclipse Public License, version 2.0, and tool-specific licences | Not bundled. The separate launcher is retained only for broader external cleanup profiles. |
| refactoring-cli | Planned separate executable | GNU General Public License, version 3 | Our independent tool; its copyright notices and source distribution requirements remain applicable. |

Before publishing a binary, generate and review an actual resolved dependency inventory and the individual third-party license files, including any shaded or transitive packages. This list is a design-time inventory and not a representation that all runtime component notices have been audited.

- [SonarLint Core](https://github.com/SonarSource/sonarlint-core)
- [SonarJava](https://github.com/SonarSource/sonar-java/tree/8.6.0.37351)
- [Eclipse Public License, version 2.0](https://www.eclipse.org/legal/epl/epl-v20.html)
- [GNU General Public License, version 3](https://www.gnu.org/licenses/gpl-3.0.html)

## SonarJava symbolic execution plugin (optional reliability profile)

- Artifact: `org.sonarsource.java:sonar-java-symbolic-execution-plugin:8.6.0.37351`
- Upstream: [SonarJava symbolic execution](https://github.com/SonarSource/sonar-java/tree/8.6.0.37351/java-symbolic-execution)
- License: GNU Lesser General Public License, version 3 or later, for this pinned source release
- Usage: loaded together with the matching SonarJava analyzer only for the explicitly selected `reliability` profile
