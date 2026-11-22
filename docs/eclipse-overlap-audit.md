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
| Potential Sonar overlap | 33 | Real combined-engine comparison still needed for most |
| Eclipse candidate | 16 | Distinct on selected fixtures, not a universal exclusivity guarantee |
| Review | 80 | Actual overlap, usefulness or transformation scope not settled |
| Style policy | 18 | Not a recommended standalone warning |
| **Total** | **147** | |

Only **five exact mappings** have passed the full real Sonar plus
real Eclipse deduplication regression: `S1128` for unused imports,
`S1905` for redundant casts, `S1481` for unused locals,
`S1161` for missing `@Override`, and `S2093` for
try-with-resources.

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

## What remains

**28 candidate overlap classifications** lack a fully verified
combined-engine deduplication mapping. An additional 80 options
remain under review. These are work items, not 108 confirmed duplicates.

Our existing 15-option opt-in Eclipse profile supplies verified
complementary warnings on representative Java fixtures. We should
expand it only when a cleanup passes both positive and negative
tests against the selected versioned Sonar profile.

[Detailed catalog](eclipse-cleanup-catalog.csv)
