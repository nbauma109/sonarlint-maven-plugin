# Eclipse and Sonar overlap audit

Our objective is to report **useful Eclipse findings that SonarJava does not
already report**, rather than maximizing the number of enabled cleanups.
The catalog covers **147 Eclipse options** and the Java analyzer is pinned to
SonarJava `8.6.0.37351`. Classification is based on this fixed engine, the
versioned standalone Sonar Way profile and explicit test fixtures.

## Current triage

The catalog originally classified 34 options as potential Sonar overlaps.
One classification was incorrect: `cleanup.boolean_literal` converts
`Boolean.TRUE` or `Boolean.FALSE` to primitive literals, and is not the
same transformation as `java:S1125`. It is now back under review.

| Classification | Options | Meaning |
| --- | ---: | --- |
| Potential Sonar overlap | 33 | Similarity requires actual source-level comparison |
| Eclipse candidate | 16 | Distinct on tested constructs, not necessarily universally distinct |
| Review | 80 | Mapping or usefulness has not been established |
| Style policy | 18 | No standalone warning by default |
| **Total** | **147** | |

Five exact-key mappings have **passed real, combined Eclipse/Sonar deduplication**:
`S1128` (unused imports), `S1905` (unnecessary casts),
`S1481` (unused locals), `S1161` (missing `@Override`) and
`S2093` (try-with-resources).

Other overlaps can be known from individual fixtures without being safe
for automatic suppression. We suppress only an exact cleanup identity with
a corresponding *actual* Sonar finding whose source range overlaps the
Eclipse edit; nearby but different issues are both retained.

## Quality-first rules

1. Prefer correctness, resource safety, meaningful API modernization and
   maintainability over cosmetics.
2. Do not add or recommend a cleanup solely for `i++` versus `++i`,
   whitespace, member ordering, redundant modifier style or equivalent
   boolean notation. A cleanup such as `cleanup.extract_increment` may
   improve readability in complex expressions, but stays opt-in rather
   than a recommended core rule.
3. Reject broad “equivalent” mappings unless real Eclipse and Sonar engines
   report the *same* problem on a compiled fixture. Different warnings
   on the same line are not duplicates.
4. Recheck mappings when changing the pinned Java, symbolic execution or
   Eclipse runtime versions. A symbolic dataflow finding may add coverage
   not present in the standard-only comparison.
5. Document observable positives and negatives, rule keys, source ranges,
   Java releases and build performance for each new mapping.

## Work remaining

The remaining 28 potential-overlap classifications do not yet have a full
combined-engine deduplication guarantee. Another 80 options remain under
review. Some may prove complementary; some are cosmetic; some are
partial Sonar overlaps.

The current test suite covers five deduplication mappings and a 15-option
opt-in complementary Eclipse profile. Those are **verified on representative
constructs**, not an exhaustive assertion that every overlapping Java
pattern or Sonar Way rule has been accounted for.

The experimental `S818` / `S1125` candidates are kept in a separate
unmerged pull request until the real combined-engine comparison passes.
`cleanup.boolean_literal` must never be mapped to `S1125`.
