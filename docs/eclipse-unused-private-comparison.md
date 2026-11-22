# Unused private fields and methods: real Sonar–Eclipse comparison

Two common maintainability rules already exist in pinned SonarJava:

| Eclipse cleanup | SonarJava rule | Tested problem |
| --- | --- | --- |
| `cleanup.remove_unused_private_fields` | `S1068` | An unread private field |
| `cleanup.remove_unused_private_methods` | `S1144` | An uncalled private method |

This comparison includes verified, source-qualified duplicate suppression. Eclipse's parent
`cleanup.remove_unused_private_members` must be enabled alongside each child
cleanup. Each option is tested separately against the real Eclipse Java runtime
and SonarJava in both Maven and Gradle. The test verifies one source-located
finding per unused declaration, excludes genuinely used members, confirms
Maven–Gradle agreement, and leaves source files byte-for-byte unchanged.

The workflow uses the merged and independently tested
[refactoring-cli pull request #7](https://github.com/nbauma109/refactoring-cli/pull/7),
pinned to commit `39b6a885`. Its source tree matches the successful standalone
Eclipse test.

The production duplicate filter maps only these two child options, and both
negative controls are covered. Eclipse remains visible when the corresponding
Sonar rule is disabled. The parent option is not mapped.

## Exact-once duplicate filtering

Maven and Gradle now exercise both production mappings with four controls for
each member kind: real Sonar and Eclipse positives on the same declaration,
only one combined warning when deduplication is active, both warnings when
suppression is disabled, and the Eclipse warning preserved when its Sonar rule
is not active. Only the child option for the specific field or method is mapped;
`cleanup.remove_unused_private_members` remains an unmapped parent because its
scope may include other kinds of private members. Used members are never
reported in the tested source. Neither build modifies source files.
