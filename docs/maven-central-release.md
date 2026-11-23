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
6. Review the [Eclipse JDT licensing requirements](eclipse-jdt-licensing.md),
   including copyright-holder authorization and third-party obligations.
   Release workflows do not require a separate licensing approval variable. They independently run the Eclipse dependency-license audit and verify the packaged GPLv3 linking exception and notices before publishing.

## Validate release artifacts without publishing

```sh
mvn -B -ntp -Pcentral-release -Dgpg.skip=true clean verify
mvn -B -ntp release:prepare -DdryRun=true
mvn -B -ntp release:clean
```

The same validation now also checks the GPLv3 section 7
[Eclipse JDT linking exception](../ECLIPSE-JDT-LINKING-EXCEPTION.md)
in the Maven artifact and resolves an inventory of Eclipse JDT and
Eclipse Platform runtime dependencies with their declared license
metadata. Review the uploaded `eclipse-jdt-runtime-license-inventory`
artifact and [license evidence](eclipse-jdt-licensing.md) before release.
An exception cannot authorize original code owned by unrelated copyright
holders; verify project contributor authorization before publication.

The first command verifies the main Maven plugin, sources, Javadoc, plugin
descriptor, license and third-party notices without requiring secrets.
The other commands check release preparation without pushing a tag.

## Prepare the first release

Run the manual [Maven Release workflow](../.github/workflows/maven.yml)
on `master` after all tests pass. This follows the same workflow layout as
`jd-util` and `jd-core-v0`. It requires the existing `SSH_PRIVATE_KEY` and
`KNOWN_HOSTS` secrets to push release commits and tags through SSH.

The workflow runs:

```sh
mvn --no-transfer-progress -B -Dusername=git \
    -Darguments="-Dgpg.skip=true" \
    release:clean release:prepare release:perform
```

The Maven Release Plugin selects the release version from
`0.1.0-SNAPSHOT`, commits the release version, pushes the plain
**`0.1.0` tag** without a `v` prefix, and advances the development
version. `release:perform` builds and installs the released project,
while the tag-triggered workflows handle external publishing.

The tag triggers two independent workflows:

- [`release.yml`](../.github/workflows/release.yml) builds the release
  artifacts and creates the GitHub Release with the main, sources and Javadoc
  archives.
- [`publish.yml`](../.github/workflows/publish.yml) verifies the tag and
  project version match, loads `CENTRAL_TOKEN_USERNAME`,
  `CENTRAL_TOKEN_PASSWORD`, `PGP_PRIVATE_KEY` and `PGP_PASSPHRASE`,
  signs the artifacts and publishes them to Sonatype Central.

The Central publishing plugin uses `autoPublish=true`, matching the other
repositories. The Maven workflow pushes the tag using SSH rather than the
automatically supplied GitHub Actions token so that the tag push triggers
both downstream workflows. Creating a release tag is therefore an intentional,
irreversible publication action.

Watch the [GitHub Actions](https://github.com/nbauma109/static-analysis-maven-plugin/actions)
runs and [Central Portal deployments](https://central.sonatype.com/publishing/deployments).
The artifact is usable only after Maven Central indexing and publication
succeed.

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

The release-validation workflow runs packaging checks without
signing credentials. A successful GitHub build does not verify Sonatype
namespace access or signing credentials. If preparation fails before
tagging, investigate and use `mvn release:rollback` when appropriate.
After pushing a release tag, inspect the GitHub Release and Central publishing
workflows and the Portal deployment rather than publishing the same version twice.

See the [Maven Release Plugin documentation](https://maven.apache.org/maven-release/maven-release-plugin/)
and [Central Publishing Maven Plugin documentation](https://central.sonatype.org/publish/publish-portal-maven/)
for release and Portal behavior.
