# Third-party components

Our own code remains GPL-3.0-only with a GPLv3 section 7 linking exception for specified Eclipse JDT dependencies. The components below are not relicensed by this project.

| Component | Selected version or role | Licence | Distribution notes |
| --- | --- | --- | --- |
| SonarLint Analysis Engine and other SonarLint Core modules | 10.13.0.79996 | GNU Lesser General Public License, version 3 | Retain notices; provide corresponding source and required replacement/relinking rights when distributing the combined executable. |
| SonarJava | 8.6.0.37351 | GNU Lesser General Public License, version 3 | Historical LGPL-era analyzer. Preserve upstream notices and distribution rights. Later releases have different terms. |
| Eclipse Java Development Tools Core | 3.47.0 | Eclipse Public License, version 2.0 | Runtime dependency of the embedded cleanup engine; retain upstream notices and review GPL compatibility. |
| Eclipse Java Development Tools Core Manipulation | 1.21.0 | Eclipse Public License, version 2.0 | Provides the genuine cleanup analysis visitors. Review transitive dependencies and redistribution terms before release. |
| refactoring-cli | Planned separate executable | GNU General Public License, version 3 | Our independent tool; its copyright notices and source distribution requirements remain applicable. |

Before publishing a binary, generate and review an actual resolved dependency inventory and the individual third-party license files, including any shaded or transitive packages. This list is a design-time inventory and not a representation that all runtime component notices have been audited.

**Eclipse JDT linking exception:** The Eclipse JDT libraries are licensed under EPL-2.0 and have not been treated as GPL-relicensed libraries. Instead, our own original GPL-3.0-only code has a specifically scoped [additional linking permission under GPLv3 section 7](ECLIPSE-JDT-LINKING-EXCEPTION.md) for JDT Core, JDT Core Manipulation and the EPL-2.0-covered Eclipse Platform dependencies needed by those libraries.

The [Eclipse Foundation](https://blogs.eclipse.org/post/mike-milinkovich/eplgpl-commentary) and the [Free Software Foundation](https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs) identify a copyright-holder-granted GPL linking exception as a path for this combination. This exception does not remove or modify the EPL-2.0 obligations of the JDT dependencies. It does not apply to unrelated GPL code without copyright-holder permission.

**Publication requirements:** Verify copyright authorization from every holder of copyrightable GPL code included in the Maven plugin; audit the complete resolved runtime dependency set, including indirect dependencies and source/notice obligations; retain the exception with source and binary artifacts; and confirm all applicable terms before release. The exception is a substantive legal permission, not a declaration that a scanner has certified the entire binary.

- [SonarLint Core](https://github.com/SonarSource/sonarlint-core)
- [SonarJava](https://github.com/SonarSource/sonar-java/tree/8.6.0.37351)
- [Eclipse Public License, version 2.0](https://www.eclipse.org/legal/epl/epl-v20.html)
- [GNU General Public License, version 3](https://www.gnu.org/licenses/gpl-3.0.html)

## SonarJava symbolic execution plugin (optional reliability profile)

- Artifact: `org.sonarsource.java:sonar-java-symbolic-execution-plugin:8.6.0.37351`
- Upstream: [SonarJava symbolic execution](https://github.com/SonarSource/sonar-java/tree/8.6.0.37351/java-symbolic-execution)
- License: GNU Lesser General Public License, version 3 or later, for this pinned source release
- Usage: loaded together with the matching SonarJava analyzer only for the explicitly selected `reliability` profile
