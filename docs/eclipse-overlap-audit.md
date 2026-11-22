# Eclipse and Sonar overlap review

The goal is to report **additional, useful Eclipse warnings not already
reported by the pinned SonarJava analyzer**, rather than to maximize
the number of enabled cleanup options. Neither a shared source line
nor similar rule descriptions proves an actual duplicate.

## Current inventory

The catalog contains **147 Eclipse options**. One historical classification
was corrected: `cleanup.boolean_literal` converts `Boolean.TRUE` and
`Boolean.FALSE` to primitive literals; it is not equivalent to Sonar
`S1125` and must not be suppressed under that identifier.

| Classification | Options | Verification status |
| --- | ---: | --- |
| Potential Sonar overlap | 34 | Real combined-engine comparison still needed for most |
| Eclipse candidate | 14 | Distinct on selected fixtures, not a universal exclusivity guarantee |
| Review | 79 | Actual overlap, usefulness or transformation scope not settled |
| Aesthetics | 2 | Available only through the separate opt-in aesthetics profile |
| Style policy | 18 | Preferences disabled unless selected explicitly |
| **Total** | **147** | |

**12 exact mappings** have passed full real Sonar plus Eclipse deduplication
regression in both Maven and Gradle: `S1128` (unused imports), `S1905`
(redundant casts), `S1481` (unused local variables), `S1161` (missing
`@Override`), `S2093` (try-with-resources), `S2129` (unnecessary
`String` construction), `S2293` (redundant generic type arguments),
`S3878` (unnecessary array creation), `S3626` (redundant returns), and
`S2694` (non-static nested classes, only when explicitly enabled),
`S1068` (unused private fields), and `S1144` (unused private methods).
Each is suppressed only when a matching active Sonar finding overlaps the
original source edit. Integration includes disabling deduplication and
removing corresponding Sonar rules to verify the negative controls.

Some other potential overlaps are supported by individual fixtures
but not a combined exact-once guarantee. The `S818` numeric-suffix
and `S1125` boolean-comparison candidates are still isolated from
the production implementation; `cleanup.boolean_literal` is
explicitly **not** the boolean-comparison cleanup.

## Quality-first selection policy

- Prefer correctness, resource safety, meaningful API modernization
  and substantive maintainability improvements.
- Do not promote a rule solely for `++i` versus `i++`,
  optional `final` declarations, whitespace, member ordering
  or equivalent formatting. Such options may be supported by an
  explicitly selected style profile, but should not pollute the
  recommended analysis.
- Treat `cleanup.extract_increment` as optional: extracting a
  side effect from a complex expression can improve readability,
  but simple increment notation is not a bug.
- Suppress an Eclipse warning only if the cleanup identifier and
  Sonar rule are an established pair **and** both engines report
  overlapping source ranges for that specific problem.
- Revalidate overlaps under the symbolic-enabled `sonar-way` profile.
  Resource and null-state bugs may overlap Eclipse cleanup findings that
  previously looked complementary under the standard-only rule set.
- Preserve dissimilar warnings occurring on the same source line.

## Profile-dependent overlaps to test next

The pinned Sonar Way profile does **not** activate two existing SonarJava
rules that resemble high-value Eclipse refactorings:

- `cleanup.precompile_regex` may overlap Sonar `S4248`, which flags
  needlessly created regular-expression patterns. The match is not universal:
  Eclipse's cleanup considers repeated uses of a regular expression within
  one method. Require matching source ranges and positive/negative real-engine
  examples before filtering.
- `cleanup.static_inner_class` is now mapped to `S2694` **only** when the
  same class declaration is flagged by the explicitly enabled Sonar rule.
  The Eclipse-only warning remains with pinned Sonar Way, which excludes
  `S2694`. Review reflection, serialization and framework constraints.
- `cleanup.remove_unnecessary_suppress_warnings` is distinct from `S1309`,
  which restricts use of `@SuppressWarnings` rather than proving an
  individual suppression unnecessary. Compiler diagnostics must establish
  that a suppression is stale.

Both `S4248` and `S2694` are available as explicitly enabled standard
rules in the pinned analyzer but are not in its versioned Sonar Way snapshot.
A finding is therefore complementary **relative to the selected profile**,
not necessarily to the complete SonarJava rule inventory.

## Prioritized validation queue

All unresolved candidates have now been assigned a **review priority and test
type**, without enabling or suppressing anything. The
[104-option review queue](eclipse-pending-review-queue.csv) and
[validation guide](eclipse-review-queue.md) are enforced by the integration
suite. Priority is a screening decision, not a statement that a cleanup
is safe or a Sonar duplicate.

## What remains

**22 candidate overlap classifications** lack a fully verified
combined-engine deduplication mapping. An additional **79 options**
remain under review. These are review work items, not confirmed duplicates.

Our opt-in Eclipse profiles separately provide **3 maintainability**, **13 broad
modernization**, and **2 aesthetic** cleanups, plus 14 additional disabled
style preferences. The broad profile includes the 3 maintainability options;
these are not additive counts of unique options. The aesthetic options are
enabled only when their separate profile is explicitly selected. We should
expand it only when a cleanup passes both positive and negative
tests against the selected versioned Sonar profile.

[Detailed catalog](eclipse-cleanup-catalog.csv)
