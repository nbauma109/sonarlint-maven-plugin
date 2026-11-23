# Licensing and release compliance

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

## Our licence: GPL version 3 only

Our original project source code is available under the GNU General Public License version 3 only (SPDX: GPL-3.0-only). We retain exact copyright and license notices for dependencies; our choice of GPL does not relicense SonarSource's LGPLv3 libraries, Eclipse's EPLv2 components or other third-party software. The complete GPLv3 license text is in [LICENSE](../LICENSE). Our project copyright and attribution are in [COPYRIGHT.md](../COPYRIGHT.md), with dependencies in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

LGPLv3 components can generally be combined with GPLv3 code, subject to the applicable combined-work and distribution obligations. The historical SonarJava 8.6.0.37351 and SonarLint Core 10.13.0.79996 releases are the versions currently selected. Do not update the analyzers to Sonar Source-Available License versions without a separate permission and compatibility review.

The existing `refactoring-cli` is itself GPLv3, so GPL licensing removes the MIT-versus-GPL issue for original CLI code that we own or are licensed to modify. GPL licensing **does not** make Eclipse Public License 2.0 compatible by itself: the Eclipse Public License secondary GPL licensing option applies only where the appropriate notice and conditions are met. For the embedded JDT Core and Core Manipulation integration, the project instead grants a narrowly scoped [GPLv3 section 7 linking exception](../ECLIPSE-JDT-LINKING-EXCEPTION.md), only for code whose copyright holders authorize that additional permission. The [Eclipse JDT release conditions](eclipse-jdt-licensing.md) must be satisfied before distributing a combined build. The separate external Eclipse runner remains available for other cleanups; distributing it also requires the applicable upstream license and attribution notices.

The project name now avoids SonarSource's product marks. Upstream library names can be used for accurate attribution and description, not as a claim of sponsorship. Do not restore a SonarSource mark to the distributed plugin name without written permission. The language name Java is similarly used descriptively in our technical documentation rather than as the product brand.

Running this GPL-licensed analysis plugin against a separate project does not automatically change the licence of the analyzed project's source code. That differs from incorporating GPL implementation code into the analyzed application.

## Additional references

- [Free Software Foundation GPL compatibility](https://www.gnu.org/licenses/gpl-faq.en.html)
- [Eclipse Public License 2.0 secondary licensing](https://www.eclipse.org/legal/epl/epl-v20.html)
- [SonarSource trademark policy](https://www.sonarsource.com/trademark-use/)
- [Apache Maven third-party plugin naming](https://maven.apache.org/guides/introduction/introduction-to-plugin-prefix-mapping.html)
