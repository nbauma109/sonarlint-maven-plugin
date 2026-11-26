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
S6539 is available as a native opt-in beta rule in `sonar-way-beta` or
through explicit rule selection. It is not enabled by the stable profiles.
An earlier native probe reproduced all three jd-util master findings (113,
23 and 37 dependencies), but that result does not establish general parity
because project-key-based import counting can differ. Upstream promoted the
rule to ready on June 29, 2026; the pinned implementation retains the beta
status and older utility-class handling. No analyzer internals are modified.

## S6541: brain methods

[S6541](https://github.com/SonarSource/sonar-java/blob/98ac462d427aec7ae72f13e038a5280d9685e57c/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/S6541.json)
reports methods that perform too many tasks. Its pinned metadata marks it
as beta. It is available only through opt-in beta or explicit rule selection.
Upstream deprecated it on July 23, 2026. Both versions count variable
declaration nodes, despite the metric rename. The pinned implementation
uses `noavThreshold`, and lacks the newer reporting cap and explicit
post-report state clearing. Results can differ by analysis group or module.
Supported complexity rules, including S3776, are not equivalent to S6541.

## S6548: singleton design pattern

[S6548](https://github.com/SonarSource/sonar-java/blob/98ac462d427aec7ae72f13e038a5280d9685e57c/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/S6548.json)
advises caution when using the singleton design pattern. Its pinned metadata
marks it as beta. It is available only through opt-in beta or explicit rule
selection. Upstream deprecated it on June 29, 2026 without changing the
detector itself. It is an architectural advisory rather than proof of a
defect. No supplemental implementation is provided.

All three beta rules use the pinned native implementations. The beta profile
adds them to stable Sonar Way, retains its supplemental and test-source
coverage, and honors individual rule disabling. Beta findings follow the
existing failure setting; report-only execution is recommended.

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

The comparison retains cloud-only javabugs rules and normalizes the known
S2259 and S6466 aliases without discarding unmatched occurrences.

## S6466: proven array bounds

The cloud engine detects input-dependent and interprocedural array bounds
defects that the pinned standalone symbolic engine cannot report. The local
supplemental detector covers a deliberately conservative subset: integral
constant indices and lengths of directly allocated arrays or final local
references initialized by an array allocation. It does not infer bounds for
unknown allocations, reassigned references, or arbitrary inputs. It is not a
replacement for the cloud engine and does not claim to reproduce the
interprocedural constant-pool finding in jd-util.

## S125: commented-out code classifier version

The native S125 rule remains enabled in the applicable production and test
profiles. The pinned JavaFootprint recognizer can miss source examples that
the newer SonarCloud recognizer reports, including the labelled console-call
example observed in jd-util PR #229. The newer upstream classifier change
[SONARJAVA-6511](https://github.com/SonarSource/sonar-java/commit/3a5ee6acf2115bcdf94849f2d45e9c7fa6a748b2)
is distributed under the Sonar Source-Available License. The plugin does not
copy that implementation or add a console-specific replacement detector.
Retain native S125 findings and record this version difference in parity
reports until a licensing-compatible general improvement is validated.
