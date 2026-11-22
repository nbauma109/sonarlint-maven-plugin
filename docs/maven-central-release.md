# Releasing the Maven plugin to Maven Central

The published Maven coordinates are
`io.github.nbauma109:static-analysis-maven-plugin`.
The Maven artifact requires Java 17 and Maven 3.9.9 or later. The Gradle
adapter is a separate build and is **not** published by this procedure.

## Release prerequisites

1. Confirm that the `io.github.nbauma109` namespace is verified for the
   publishing account at [Central Portal](https://central.sonatype.com/).
2. Generate a Central Portal user token and configure a Maven `server` entry
   named `central` in the local Maven settings file. Use the token username
   and token password, not the account password.
3. Import a signing key into GnuPG and publish its public key to a key server.
   Confirm the signing identity with `gpg --list-secret-keys --keyid-format LONG`.
   Arrange an unlocked GnuPG agent for non-interactive signing.
4. Ensure the Git checkout is clean, the `master` branch is current, and the
   Git credentials can push commits and tags. All project workflows must pass.
5. Confirm the candidate version is not already published. Central releases
   cannot be overwritten.

The `central` entry in `~/.m2/settings.xml` uses this structure:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
    <servers>
        <server>
            <id>central</id>
            <username>${env.CENTRAL_TOKEN_USERNAME}</username>
            <password>${env.CENTRAL_TOKEN_PASSWORD}</password>
        </server>
    </servers>
</settings>
```

Set both environment variables privately in the release environment. Never
commit Maven settings, access tokens, signing keys, or signing passphrases.

## Validate the release build without publishing

```sh
mvn -B -ntp -Pcentral-release -Dgpg.skip=true clean verify
mvn -B -ntp release:prepare -DdryRun=true
mvn -B -ntp release:clean
```

The first command builds the main plugin, sources, and Javadoc artifacts
without signing or uploading. Verify the plugin descriptor is present in the
main archive and inspect the resulting files in `target/`.

The dry run checks release preparation without committing or tagging.
Do not use `-Dgpg.skip=true` for an actual release.

## Prepare and upload the first release

From the clean, current `master` checkout with the prerequisites configured:

```sh
mvn -B -ntp release:prepare \
    -DreleaseVersion=0.1.0 \
    -DdevelopmentVersion=0.1.1-SNAPSHOT \
    -Dtag=v0.1.0
mvn -B -ntp release:perform
```

The release plugin commits the release version, creates and pushes the tag,
and advances the working branch to the next development snapshot.
`release:perform` checks out that tag and runs `deploy` with the
`central-release` profile. That profile builds the sources and Javadoc
archives, signs the Maven artifacts and POM with GnuPG, and uploads the
release bundle using the Central Publishing Maven Plugin. It waits until
Sonatype validates the bundle, **not** until it is publicly available.

Publishing is deliberately manual (`autoPublish=false`). Review the
validated deployment in
[Central Portal deployments](https://central.sonatype.com/publishing/deployments)
and explicitly publish it. Wait for it to be visible in Central before
advertising the released version.

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

The project release-validation workflow runs the packaging checks without
signing credentials. A successful GitHub build is not proof that the Sonatype
account has namespace access or that GnuPG can sign on the release machine.
If preparation fails before tagging, investigate and use
`mvn release:rollback` when appropriate. Never rerun a publication against
an already-published version; diagnose the deployment in Central Portal.

See the [Maven Release Plugin documentation](https://maven.apache.org/maven-release/maven-release-plugin/)
and [Central Publishing Maven Plugin documentation](https://central.sonatype.org/publish/publish-portal-maven/)
for release and Portal behavior.
