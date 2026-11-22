# Eclipse review queue — pending validation, not automatic enablement

The [machine-checkable queue](eclipse-pending-review-queue.csv) tracks all
**101 unresolved Eclipse options**, without implying that they have been tested
or are safe to enable. It is generated from our pinned 147-option catalog and
the twelve real-engine-verified duplicate mappings.

| Backlog | Count | Current policy |
| --- | ---: | --- |
| Potential overlaps without exact-once verification | 22 | Do not add a duplicate mapping yet |
| Unclassified Eclipse options | 79 | Do not enable as recommended warnings yet |
| **Total needing evidence** | **101** | Preserve current profiles |

## Review sequencing

| Priority | Count | Meaning |
| --- | ---: | --- |
| P1 | 14 | Highest-value correctness, lifecycle or substantive replacement candidates |
| P2 | 34 | Useful modernization, performance or maintainability candidates |
| P3 | 25 | Needs scope research or is lower impact |
| P4 | 28 | Aesthetics, parent toggles and convention-sensitive overlaps |

The queue contains **25**
convention-sensitive items. This is *not* a hard exclusion: those options
remain available for the independently opt-in aesthetics profile after their
actual Eclipse behavior and conflicts are verified.

## Real-engine evidence and next tests

The [refactoring-cli high-signal screening](https://github.com/nbauma109/refactoring-cli/pull/6)
found an isolated Eclipse opportunity for
`cleanup.static_inner_class`, now covered by the exact-once integration;
`cleanup.precompile_regex` produced
two reported edits that still need separating into substantive and auxiliary
changes. Its current stale-`@SuppressWarnings` fixture produced no Eclipse
finding. The new `S2694` regression is a separately qualified equivalence; other
observations do not establish equivalence to Sonar rules,
or validates that a warning is safe for the recommended profile.

The pinned Sonar Way snapshot lists candidate rules for
**16** of the unresolved entries
(all are potential overlaps), but being listed does **not** guarantee a rule
is executable or active in the selected local profile. 8
unresolved options have candidate rule identifiers absent from the pinned
snapshot. Actual activation must be verified at runtime.

The next high-value combined-engine tests should examine `cleanup.precompile_regex`
against explicitly enabled `S4248` and the remaining unused-parameter checks against
active Sonar rules. Unused private fields and methods now have exact-once integration evidence. Negative controls must verify both disabling duplicate filtering
and disabling each candidate Sonar rule. For supplementary changes on the
same line, compare precise text ranges rather than discarding all warnings.

## Status semantics

`catalog_only` means a catalog hypothesis with no new test evidence.
`isolated_eclipse_one_finding` and `isolated_eclipse_two_edits`
are narrow observations from the independent Eclipse Java 25 fixture.
Every queue row has `review_decision=pending`: no row automatically adds,
removes, enables or suppresses a rule. Additional verification is required
for every recommendation.

The queue is checked in the Maven integration suite by
`scripts/verify-eclipse-review-queue.py`. Any change to the exact pending
catalog entries, Sonar candidate identifiers, or pinned Sonar Way snapshot
requires the review queue to be updated deliberately.

See also the [overlap audit](eclipse-overlap-audit.md),
[aesthetics policy](eclipse-aesthetics-profile.md), and
[rule-value review](eclipse-rule-value-review.md).
