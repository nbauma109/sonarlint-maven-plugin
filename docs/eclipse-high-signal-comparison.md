# High-signal Eclipse and Sonar comparison

This integration tests one **conditional, source-qualified duplicate mapping**
and one still-experimental overlap hypothesis. Two real Eclipse cleanup options
are compared with explicitly enabled SonarJava rules under Java 25:

| Eclipse cleanup | Candidate Sonar rule | Verification target |
| --- | --- | --- |
| `cleanup.precompile_regex` | `S4248` | Distinguish actual repeated pattern compilation from auxiliary import edits |
| `cleanup.static_inner_class` | `S2694` | Detect a stateless inner class and compare matching declaration ranges |

Neither `S4248` nor `S2694` belongs to the pinned Sonar Way snapshot,
but teams can select them individually. A corresponding Sonar warning is
therefore **profile-dependent**, not always available. The test deliberately
enables both Sonar rules and requires real positive findings. It retains
separate regex findings, but suppresses the matched nested-class warning only
when `S2694` is explicitly active and covers the same class declaration.

The [real-engine script](../scripts/real-eclipse-high-signal-comparison.sh)
runs Maven and Gradle against the same compiled Java 25 source using each
Eclipse cleanup option in isolation. It compares exact reported source
ranges, records matching Sonar warning counts, and asserts source bytes
remain unchanged. The regex comparison does **not** prove semantic equivalence; the nested-class
mapping additionally requires explicit negative controls and exact class-name
source matching.

The standalone [Eclipse candidate screening](https://github.com/nbauma109/refactoring-cli/pull/6)
previously observed two `precompile_regex` reported edits and one
`static_inner_class` finding. The combined-engine test determines whether
the two build adapters preserve that information consistently.

No existing recommended, broad or aesthetics profile is changed. A new
standalone high-signal profile is available only by explicit selection.

The fixture includes literal-pattern and dynamically supplied-pattern calls:
the former is eligible for the pinned `S4248` detector, while the latter
must not be treated as an equivalent static-constant opportunity. This
helps expose different detection scopes before considering deduplication.

## S2694 exact-once conditional overlap

The real Maven and Gradle integration now verifies an explicit rule-key match between
`cleanup.static_inner_class` and `S2694` on the same class declaration. The
filter recognizes the zero-length insertion of `static` immediately before the
`class` keyword, but only when the active Sonar warning covers that declaration.
Disabling duplicate suppression exposes both warnings; disabling `S2694` preserves
the Eclipse finding. This is a **profile-dependent** mapping: `S2694` is not part
of our pinned Sonar Way profile and is not implicitly enabled.

The regex cleanup is deliberately **not mapped** to `S4248`: its observed
source edits are associated with creating a reusable pattern and importing
support, whereas Sonar flags repeated literal `String.matches` calls at
another source location. These distinct findings must remain visible.

## Tested complementary use with pinned Sonar Way

For projects using our opt-in `sonar-way` profile, the separate
[high-signal Eclipse profile](../src/it/high-signal-eclipse/eclipse-cleanups-high-signal.xml)
provides `cleanup.static_inner_class`. It is absent from pinned Sonar Way
unless `S2694` is selected explicitly. The real combined-engine test now
requires this Eclipse warning to survive with all 395 selected Sonar Way checks
and verifies it in Maven and Gradle. Changing an inner class to `static`
can affect framework and serialized representations; the plugin reports a
warning and never changes source code.
