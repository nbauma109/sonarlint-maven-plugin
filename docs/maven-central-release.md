# Releasing the Maven plugin to Maven Central

The published Maven coordinates are
`io.github.nbauma109:static-analysis-maven-plugin`.
The Maven artifact requires Java 17 and Maven 3.9.9 or later. The Gradle
adapter is a separate build and is **not** published by this procedure.

## Existing GitHub publishing secrets

This repository uses the same GitHub Actions secret names as
`nbauma109/jd-util`, `nbauma109/jd-core-v0`, and other Maven Central projects:

| GitHub Actions secret | Usage |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Central Portal publishing token username |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal publishing token password |
| `PGP_PRIVATE_KEY` | Exported ASCII-armored GnuPG private key |
| `PGP_PASSPHRASE` | Passphrase for the signing key |

The publishing workflow generates the Maven server `central` using
`actions/setup-java`. It passes the existing `PGP_PASSPHRASE` secret to
GnuPG as `MAVEN_GPG_PASSPHRASE`. No extra Maven credentials or new secret
names are required, but the secrets must be accessible to this repository
through its GitHub settings or organization-level configuration.

Never commit token values, Maven settings with credentials, private keys,
or passphrases.

## Release prerequisites

1. Confirm that the `io.github.nbauma109` namespace is verified for the
   publishing account at [Central Portal](https://central.sonatype.com/).
2. Confirm all four existing GitHub Actions secrets are available in this
   repository and correspond to a valid Portal user token and signing key.
3. Ensure the signing public key is discoverable by Central's validation.
4. Ensure the `master` checkout is clean and current and has permission to
   push commits and tags. All build and integration workflows must pass.
5. Confirm the candidate version is not already published. Maven Central
   releases cannot be overwritten.

## Validate release artifacts without publishing

```sh
mvn -B -ntp -Pcentral-release -Dgpg.skip=true clean verify
mvn -B -ntp release:prepare -DdryRun=true
mvn -B -ntp release:clean
```

The first command verifies the main Maven plugin, sources, Javadoc, plugin
descriptor, license and third-party notices without requiring secrets.
The other commands check release preparation without pushing a tag.

## Prepare the first release

From a clean and current `master` checkout, after checks pass:

```sh
mvn -B -ntp release:prepare \
    -DreleaseVersion=0.1.0 \
    -DdevelopmentVersion=0.1.1-SNAPSHOT \
    -Dtag=v0.1.0
```

The Maven Release Plugin commits the release version, pushes `v0.1.0`,
and advances `master` to the next development snapshot. Its
`release:perform` goal, when invoked separately, runs `install` rather
than `deploy`, avoiding duplicate publication.

**The pushed version tag triggers
[the publishing workflow](../.github/workflows/publish.yml).** The workflow
verifies the tag matches the non-snapshot project version, loads the four
existing secrets, builds and signs artifacts, and deploys the bundle to
Sonatype Central via the `central-release` profile. The publishing plugin
is configured with `autoPublish=true`, matching the existing repository
convention: a validated bundle proceeds to publication without a separate
manual promotion. Creating a release tag is therefore an intentional,
irreversible publication action. Do not push the tag before the release is
approved.

Watch the GitHub Actions run and the
[Central Portal deployments](https://central.sonatype.com/publishing/deployments)
page. The artifact is usable only after Maven Central indexing and
publication succeed.

After publication, consumers can use:

```xml
<plugin>
    <groupId>io.github.nbauma109</groupId>
    <artifactId>static-analysis-maven-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The `check` goal runs after compilation, so execute
`mvn process-classes` or a later lifecycle phase.

## Validation and failure handling

The project release-validation workflow runs packaging checks without
signing credentials. A successful GitHub build does not verify Sonatype
namespace access or signing credentials. If preparation fails before
tagging, investigate and use `mvn release:rollback` when appropriate.
After pushing a release tag, inspect the publishing workflow and Portal
deployment rather than publishing the same version a second time.

See the [Maven Release Plugin documentation](https://maven.apache.org/maven-release/maven-release-plugin/)
and [Central Publishing Maven Plugin documentation](https://central.sonatype.org/publish/publish-portal-maven/)
for release and Portal behavior.
