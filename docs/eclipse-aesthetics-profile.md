# Opt-in Eclipse aesthetics profile

This profile is deliberately separate from our maintainability and Java modernization
profiles. **Aesthetic warnings are off by default.** The Maven and Gradle plugin
default `eclipseEnabled=false` remains unchanged, and neither the recommended nor
the broader Eclipse profile enables the two aesthetic cleanups.

The [aesthetics XML profile](../src/it/broad-complementary/eclipse-cleanups-aesthetics.xml)
enables only two cleanups already verified with the real Eclipse engine:

| Enabled cleanup | Intent |
| --- | --- |
| `cleanup.array_with_curly` | Favor concise array initializers |
| `cleanup.remove_redundant_modifiers` | Avoid compiler-redundant modifiers |

The same file documents other personal preferences with `value="false"`.
They can be activated individually after reviewing language-level and semantic
constraints: explicit `final`, type inference, parentheses choices, member ordering,
literal suffixes, and formatting. None is hard-forbidden by our verifier. In particular,
`cleanup.number_suffix` overlaps Sonar `S818` if that rule is active; duplicate
suppression must remain rule- and source-range-specific. Choosing opposite
parentheses preferences together is not meaningful.

## Maven configuration

Set `eclipseEnabled` to `true` and use
`eclipseCleanupProfile` to point at
`src/it/broad-complementary/eclipse-cleanups-aesthetics.xml` or a copy of the
profile in the consuming repository. The external Eclipse CLI is still required.

## Gradle configuration

Enable `eclipseEnabled`, configure the Eclipse executable, then use:

```groovy
eclipseCleanupProfile = file('eclipse-cleanups-aesthetics.xml')
```

Only one Eclipse XML cleanup profile is selected per plugin invocation.
To request maintainability and aesthetics together, export a combined Eclipse
profile containing the desired settings; neither category is enabled implicitly.

## Testing

The real Java 25 integration runs Maven and Gradle with the broad, recommended,
and aesthetics profiles separately. It verifies exact source-located warnings,
that aesthetic findings appear only under the aesthetic profile, and that no
source files are modified. A lightweight validation script checks enabled settings
and that disabled style options remain opt-in.

Sonar Way is versioned independently and retains its own standard Java rules:
the aesthetics XML does not silently modify Sonar's rule set.
