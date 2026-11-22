# Eclipse cleanup recommendations: signal before quantity

## Decision

The default Maven and Gradle profiles remain unchanged. Eclipse cleanup analysis
remains opt-in and runs without modifying sources. For users who want fewer,
more actionable suggestions, we recommend the
[five-option maintainability and aesthetics profile](../src/it/broad-complementary/eclipse-cleanups-recommended.xml)
rather than enabling all 15 broad cleanup options.

This is a **maintainability** recommendation, not a new set of bug detectors.
The five selected options produced distinct Eclipse findings in the real Java 25
integration fixtures against the pinned SonarJava configuration. Their usefulness
and behavioral safety still depend on source context. We must not treat a shared
source line or superficially similar rule title as proof of duplication.

## Current picks

| Cleanup or rule | Decision | Rationale |
| --- | --- | --- |
| Sonar `S2095`, `S2259`, `S3655` | **Keep** in opt-in Sonar Way | Actual resource leak, null dereference, and unsafe `Optional` access findings are higher-value than stylistic cleanup. |
| `cleanup.pull_up_assignment` | **Recommend**, opt-in Eclipse | Makes assignments embedded in conditions explicit; helps review side effects. |
| `cleanup.reduce_indentation` | **Recommend**, opt-in Eclipse | Simplifies nested control flow after terminating branches. |
| `cleanup.add_all` | **Recommend**, opt-in Eclipse | Makes collection bulk addition intent clearer; check custom collection semantics before applying. |
| `cleanup.extract_increment` | **Context only** in broad profile | Extracting a side effect from a larger expression can improve readability, but `i++` versus `++i` alone is not a problem. |
| `cleanup.use_string_is_blank` | **Convenience** in broad profile | An understandable Java 11 modernization; too small to warrant a core build warning. |
| `cleanup.module_imports` | **Java 25 migration** in broad profile | Only useful in an explicit module/import modernization effort. |
| `cleanup.system_property_* ` | **Compatibility review** in broad profile | Seven distinct options are available; replacing a property lookup can change the effect of runtime overrides. |
| `cleanup.array_with_curly` | **Keep for aesthetics**, opt-in Eclipse | Shorter array-initializer syntax, explicitly requested as an aesthetic convention. |
| `cleanup.remove_redundant_modifiers` | **Keep for aesthetics**, opt-in Eclipse | Removes redundant interface and other modifiers; useful for consistency. |
| Sonar `S1124` (modifier ordering) | **Keep**, pinned Sonar Way | Checks Java Language Specification modifier ordering; no separate Eclipse member-sort rule is needed. |
| Sonar `S1155` (`isEmpty()` instead of `size() == 0`) | **Keep**, pinned Sonar Way | Prefer an explicit collection emptiness check; uses Sonar's existing detector. |

The 15-option
[broad profile](../src/it/broad-complementary/eclipse-cleanups.xml)
is kept intact; the five selected recommendations include the two consciously
retained aesthetic conventions. Modifier ordering and collection `isEmpty()` checks
come from the pinned Sonar Way profile rather than duplicate Eclipse diagnostics. A cleanup
that reports correctly is not automatically worth enabling by default.

## Conventions retained using Sonar Way

Two preferred conventions are already implemented by **active SonarJava checks**
in the pinned versioned Sonar Way profile:

- `java:S1124` detects misplaced Java declaration modifiers such as
  `static public` instead of `public static`. This is *modifier ordering*,
  not `cleanup.sort_members`, which rearranges field/method declarations
  and may change initialization order.
- `java:S1155` detects collection emptiness comparisons such as
  `values.size() == 0` and recommends `values.isEmpty()`.

Both are exercised on real source by the Maven and Gradle integration tests.
We do not add duplicate Eclipse cleanup options or change the default
`priority` profile. Select `sonar-way`, or explicitly enable
`java:S1124,java:S1155` when using a narrower rule profile.

The five-option Eclipse profile and these two Sonar checks are preferences
we consciously keep, without treating their findings as bugs.

## Next candidates to investigate (not enabled)

| Priority | Eclipse option | Potential value | Required review |
| --- | --- | --- | --- |
| 1 | `cleanup.remove_unnecessary_suppress_warnings` | Reveal obsolete or overly broad suppressions | Trigger with an actually stale suppression; verify no duplicate of active Sonar rules and no unsafe removal. |
| 2 | `cleanup.precompile_regex` | Avoid recompiling an invariant regular expression in a hot path | Establish loop-invariance and safety; compare with Sonar regular-expression checks, false positives and cost. |
| 3 | `cleanup.replace_deprecated_calls` | Support intentional migration away from deprecated APIs | Require known safe replacements, target Java version and behavioral equivalence. |
| 4 | `cleanup.static_inner_class` | Avoid retaining a surrounding instance unnecessarily | Test an inner class with no outer reference; compare Sonar and framework/serialization constraints. |
| 5 | `cleanup.objects_equals` | Potentially simplify null-safe equality | Check overloads, equality direction, side effects and Sonar null-safety findings. |
| 6 | `cleanup.arrays_fill` | Clarify repeated array initialization | Check original iteration and array semantics; compare current Sonar findings. |
| 7 | `cleanup.primitive_parsing` | Remove unnecessary wrapper construction | Measure allocation and verify parsing/null/exception semantics. |
| 8 | `cleanup.convert_to_switch_expressions` | Modernize genuinely branching control flow | Compare equivalent switch rules and guard language level, exhaustiveness and behavior. |

These are *screening priorities*, not endorsed automatic transformations. A new
rule needs real Eclipse positive and negative fixtures, a selected Sonar Way
comparison, a non-mutating report, and a reason the diagnostic merits attention.

## Trivial or preference-only issues for review

| Candidate | Recommended policy | Reason |
| --- | --- | --- |
| `i++` versus `++i` | **Do not report** | Prefix and postfix have different values when used in expressions; blanket rewriting is not safe. |
| `java:S818` / `cleanup.number_suffix` | **Style-only** | Lowercase `l` versus uppercase `L`; a clarity convention, outside our pinned Sonar Way. |
| `cleanup.make_local_variable_final`, `cleanup.make_parameters_final`, `cleanup.make_variable_declarations_final` | **Explicitly exclude from both Eclipse profiles** | Do not require `final` on parameters or local variables. |
| `cleanup.use_var`, `cleanup.insert_inferred_type_arguments` | **Explicitly exclude from both Eclipse profiles** | Do not enforce `var` instead of explicit types or the reverse. |
| `cleanup.always_use_parentheses_in_expressions`, `cleanup.never_use_parentheses_in_expressions` | **Exclude** | Contradictory parenthesis conventions; leave to formatter/style policy. |
| `cleanup.sort_members`, `cleanup.sort_members_all` | **Exclude** | Declaration ordering is a preference. |
| `cleanup.correct_indentation`, `cleanup.remove_trailing_whitespaces`, `cleanup.format_source_code` | **Formatter only** | Not independent analysis warnings. |
| `cleanup.use_anonymous_class_creation`, `cleanup.use_lambda` | **Do not recommend automatically** | Expression form preference; the latter overlaps active Sonar `S1604` on tested shapes. |
| `cleanup.always_use_this_for_non_static_field_access` | **Exclude** | Repeated qualification adds noise without stronger semantics. |
| `cleanup.remove_unnecessary_nls_tags`, `cleanup.add_missing_nls_tags` | **Project-specific** | Localization tagging policy, not an unconditional maintainability improvement. |
| `cleanup.array_with_curly`, `cleanup.remove_redundant_modifiers` | **Keep for aesthetics** | Explicitly included in the recommended profile, not mistaken for correctness bugs. |

## Guardrails

- Suppress a duplicate only when an established Eclipse option maps to an
  actual active Sonar rule and **the edits overlap the same source construct**.
- A Sonar rule that is disabled or unavailable must not hide an Eclipse warning.
- Keep symbolic reliability checks; the performance probe showed tolerable cost
  on the tested fixture, not a universal guarantee.
- Review semantic changes separately: system-property runtime overrides,
  source-version gating, mutation order, and framework compatibility.
- Use formatter or Checkstyle configuration for whitespace, naming,
  parentheses and other preferences instead of issuing duplicate diagnostics.

[Full overlap audit](eclipse-overlap-audit.md) ·
[Rule catalog](eclipse-cleanup-catalog.csv) ·
[Versioned Sonar Way policy](sonar-way-versioning-and-value.md)
