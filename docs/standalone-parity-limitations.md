# Standalone SonarJava parity

The plugin pins SonarJava 8.6.0.37351 and SonarLint Core 10.13.0.79996.
The analysis scope is the registered handwritten source roots outside the
configured build directory, with compiled generated classes still available
for symbol resolution. The selected native rules follow the pinned profile
and rule metadata, not a live SonarCloud profile.

## S6539: excessive class dependencies

The pinned [rule metadata](https://github.com/SonarSource/sonar-java/blob/98ac462d427aec7ae72f13e038a5280d9685e57c/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/S6539.json)
marks S6539 as beta. Its [native detector](https://github.com/SonarSource/sonar-java/blob/98ac462d427aec7ae72f13e038a5280d9685e57c/java-checks/src/main/java/org/sonar/java/checks/design/ClassImportCouplingCheck.java)
also counts imports using the scanner project key.

The pinned standalone engine's
[SonarLintInputProject](https://github.com/SonarSource/sonarlint-core/blob/a9d5d6162d37af95a092b2e92126c1c8e1581d86/backend/analysis-engine/src/main/java/org/sonarsource/sonarlint/core/analysis/container/analysis/filesystem/SonarLintInputProject.java)
returns a fixed project key, and its
[AnalysisConfiguration](https://github.com/SonarSource/sonarlint-core/blob/a9d5d6162d37af95a092b2e92126c1c8e1581d86/backend/analysis-engine/src/main/java/org/sonarsource/sonarlint/core/analysis/api/AnalysisConfiguration.java)
has no supported project-key setter. Setting an extra property cannot change
that implementation. Merely admitting beta rules would not establish parity.
We do not replace the detector, patch analyzer internals, or guess a package
prefix. Revisit when a compatible licensed analyzer and standalone interface
can supply the required context.

## S2259: null-guard verification

The native symbolic analyzer may not infer the result of a resolved static
predicate in another source file. The shared engine verifies a narrow contract:
a single return expression with a null comparison, or a leading null comparison
that short-circuits an AND or OR expression. It proves that the branch containing
the reported receiver excludes null, requires the same local variable or
parameter and exact diagnostic source range, and rejects reassignment within
the branch. Variable-arity methods are excluded because a non-null array of
arguments does not prove that an individual argument is non-null. Verification
requires source availability and successful semantic resolution. All unresolved cases retain the original diagnostic; no rule is
disabled. A verification message identifies a proven receiver in debug output.

## Comparing inventories

Compare the unchanged Java sources at a specific revision, using the same
source release, dependency classpath and issue scope. Record analyzer versions,
profile parameters and resolved/accepted issue states. Preserve occurrence
counts; do not collapse repeated findings on a line. Report exact matches first,
then investigate possible location differences. For example, a comment finding
can move within the same contiguous block when the detector changes. A nearby
line alone is insufficient evidence of a match. Record confirmed location
differences separately from true missing detections and independently valid
local findings.

Use `scripts/compare-sonar-inventories.py` with a complete SonarCloud JSON
inventory and a single local analysis log (or a normalized JSON inventory
containing source ranges). Supply the same analyzed source revision for both
inputs. The script rejects incomplete paginated inventories (including nested
paging totals) and mismatched revisions. It distinguishes exact source-range
matches from tentative same-line matches, preserves occurrence counts, and
reports potential relocated issues for manual review rather than automatically
counting them as matches.

```sh
python3 scripts/compare-sonar-inventories.py \
  --local local.log --cloud cloud.json --source-root . \
  --local-revision "$revision" --cloud-revision "$revision"
```

Run `python3 scripts/test-compare-sonar-inventories.py` to exercise duplicate
locations, distinct columns, and contiguous commented-out blocks.

## Master-branch validation observations

The Visual Diff baseline exposes S7158 for a resolved String trim followed by
length greater than zero. The supplemental check covers that specific pattern
at Java 11 and above. Its message explicitly describes the difference between
trim and Unicode whitespace semantics; it does not rewrite source code.

The comparison retains cloud-only javabugs rules, normalizing only the known
S2259 alias. S6466 is not treated as equivalent to an unrelated local rule.
