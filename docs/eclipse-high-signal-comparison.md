# High-signal Eclipse and Sonar comparison

This is a **screening test**, not a new production duplicate mapping.
Two real Eclipse cleanup options are compared with explicitly enabled
SonarJava rules under Java 25:

| Eclipse cleanup | Candidate Sonar rule | Verification target |
| --- | --- | --- |
| `cleanup.precompile_regex` | `S4248` | Distinguish actual repeated pattern compilation from auxiliary import edits |
| `cleanup.static_inner_class` | `S2694` | Detect a stateless inner class and compare matching declaration ranges |

Neither `S4248` nor `S2694` belongs to the pinned Sonar Way snapshot,
but teams can select them individually. A corresponding Sonar warning is
therefore **profile-dependent**, not always available. The test deliberately
enables both Sonar rules, while retaining Eclipse findings because no
verified duplicate-suppression mapping exists.

The [real-engine script](../scripts/real-eclipse-high-signal-comparison.sh)
runs Maven and Gradle against the same compiled Java 25 source using each
Eclipse cleanup option in isolation. It compares exact reported source
ranges, records matching Sonar warning counts, and asserts source bytes
remain unchanged. This test must not be interpreted as proving the two
engines' findings equivalent.

The standalone [Eclipse candidate screening](https://github.com/nbauma109/refactoring-cli/pull/6)
previously observed two `precompile_regex` reported edits and one
`static_inner_class` finding. The combined-engine test determines whether
the two build adapters preserve that information consistently.

No recommended, broad or aesthetics profile is changed by this test.
