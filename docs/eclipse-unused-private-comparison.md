# Unused private fields and methods: real Sonar–Eclipse comparison

Two common maintainability rules already exist in pinned SonarJava:

| Eclipse cleanup | SonarJava rule | Tested problem |
| --- | --- | --- |
| `cleanup.remove_unused_private_fields` | `S1068` | An unread private field |
| `cleanup.remove_unused_private_methods` | `S1144` | An uncalled private method |

This is **screening**, not new duplicate suppression. Eclipse's parent
`cleanup.remove_unused_private_members` must be enabled alongside each child
cleanup. Each option is tested separately against the real Eclipse Java runtime
and SonarJava in both Maven and Gradle. The test verifies one source-located
finding per unused declaration, excludes genuinely used members, confirms
Maven–Gradle agreement, and leaves source files byte-for-byte unchanged.

The refactoring CLI version used by the workflow comes from the matching
[real-Eclipse screening branch](https://github.com/nbauma109/refactoring-cli/pull/7)
until its source is merged and a stable commit is pinned.

Only after a green source-range comparison should the duplicate filter consider
these exact option/rule pairs. A future negative control must prove that
Eclipse remains visible whenever the corresponding Sonar rule is disabled.
Do not treat parent cleanup options as identical to all child findings.
