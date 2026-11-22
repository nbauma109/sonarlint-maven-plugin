# Symbolic execution performance and rollout policy

Maven and Gradle run the pinned LGPLv3 Java symbolic analyzer only
when explicitly selected through the `reliability` or
`sonar-way-reliability` profiles.

The reviewed symbolic set includes three real-engine-tested
checks: resource leaks (`S2095`), null dereferences (`S2259`)
and unsafe `Optional` access (`S3655`).

The [Maven integration script](../scripts/integration-test.sh) compares
`sonar-way` (392 executable checks) to
`sonar-way-reliability` (395 executable checks) on the same
compiled Java fixture. It logs elapsed wall-clock seconds and peak
resident memory as `target/integration-tests/*-timing.txt`.
The [build workflow](../.github/workflows/build.yml) runs this
on Java 17 and 21.

These measurements include Maven startup and are only an initial
indicator. They are **not** a reliable prediction for a large
multi-module project with deep branch exploration or complex
dependencies. Do not impose an arbitrary performance threshold
or enable symbolic analysis globally without broader benchmarking.

The default `priority` profile remains unchanged. The reviewed
symbolic profiles are opt-in; newer source-available analyzer releases
are excluded pending separate license review.
