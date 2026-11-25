package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class SupplementalRuleVersionTest {

    private static final Path FIXTURE = Path.of("src/it/modern-test-rules").toAbsolutePath();
    private static final Map<String, Path> RULE_FIXTURES = Map.of(
            "java:S7467", FIXTURE.resolve("src/test/java/example/ModernTestIssues.java"),
            "java:S9412", FIXTURE.resolve("../collections-sort/src/main/java/example/CollectionsSortIssues.java"),
            "java:S7158", FIXTURE.resolve("../blank-string/src/main/java/example/BlankStringIssues.java"));

    @Test
    public void providesAFixtureForEveryVersionRestrictedSupplementalRule() {
        assertEquals(RuleJavaVersion.minimumReleases().keySet(), RULE_FIXTURES.keySet());
    }

    @Test
    public void respectsEveryRuleBoundarySupportedByTheRuntime() throws Exception {
        for (Map.Entry<String, Integer> requirement : RuleJavaVersion.minimumReleases().entrySet()) {
            String rule = requirement.getKey();
            int minimum = requirement.getValue();
            Path source = RULE_FIXTURES.get(rule);
            if (Runtime.version().feature() >= minimum - 1) {
                assertEquals(0, SupplementalAnalyzer.analyze(FIXTURE, List.of(source), List.of(),
                        Integer.toString(minimum - 1), Set.of(rule)).size());
            }
            if (Runtime.version().feature() >= minimum) {
                assertEquals(1, SupplementalAnalyzer.analyze(FIXTURE, List.of(source), List.of(),
                        Integer.toString(minimum), Set.of(rule)).size());
            }
        }
    }

    @Test
    public void reportsEligibleRulesAtJava25() throws Exception {
        assumeTrue(Runtime.version().feature() >= 25);
        for (Map.Entry<String, Path> fixture : RULE_FIXTURES.entrySet()) {
            int expected = RuleJavaVersion.minimumReleases().get(fixture.getKey()) <= 25 ? 1 : 0;
            assertEquals(expected, SupplementalAnalyzer.analyze(FIXTURE, List.of(fixture.getValue()),
                    List.of(), "25", Set.of(fixture.getKey())).size());
        }
    }
    @Test
    public void acceptsAlreadyUnnamedCatchParameters() throws Exception {
        assumeTrue(Runtime.version().feature() >= 22);
        Path fixture = Path.of("src/it/java-version-rules").toAbsolutePath();
        Path source = fixture.resolve("src/test/java/example/UnnamedCatch.java");
        assertEquals(0, SupplementalAnalyzer.analyze(fixture, List.of(source), List.of(),
                "22", Set.of("java:S7467")).size());
    }
}
