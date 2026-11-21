# Analyzer licensing

This is a release-blocking design concern, not legal advice.

SonarSource states that analyzers released after November 29, 2024, including patch releases of earlier versions, use its Sonar Source-Available License (SSAL). The current SSAL also limits competitive uses and products offering substantially similar functionality. The LGPL license of SonarLint Core does not automatically extend to the Java analyzer.

Before integrating or distributing a recent analyzer, review the precise license of the chosen artifact and seek clarification or permission from SonarSource where necessary. Merely downloading an analyzer at build time instead of bundling it does not necessarily resolve restrictions on use.

A candidate for an openly licensed proof of concept is SonarJava `8.6.0.37351`, published November 15, 2024. Verify its exact artifact license and a compatible standalone engine before adding the dependency. Its older Java language support and missing later rule fixes must be disclosed. Do not assume that a later patch release has the same license.

The project should prefer the newest analyzer permitted for our intended open-source distribution, not merely the newest version available in Maven Central. If a compatible, openly licensed combination is not satisfactory, seek permission for a current analyzer rather than representing the project as fully compatible with current Sonar rules.

## Sources

- [SonarJava repository and license statement](https://github.com/SonarSource/sonar-java)
- [SonarSource licensing overview](https://www.sonarsource.com/license/)
- [Sonar Source-Available License](https://www.sonarsource.com/license/ssal/)
- [SonarJava 8.6.0.37351 artifact](https://central.sonatype.com/artifact/org.sonarsource.java/sonar-java-plugin/8.6.0.37351)
