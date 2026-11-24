package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class RuleJavaVersionTest {

    @Test
    public void testsEveryDeclaredRuleAtItsReleaseBoundary() {
        for (Map.Entry<String, Integer> requirement : RuleJavaVersion.minimumReleases().entrySet()) {
            String rule = requirement.getKey();
            int minimum = requirement.getValue();
            assertFalse(RuleJavaVersion.eligibleRules(Set.of(rule), minimum - 1).contains(rule));
            assertTrue(RuleJavaVersion.eligibleRules(Set.of(rule), minimum).contains(rule));
            assertTrue(RuleJavaVersion.eligibleRules(Set.of(rule), minimum + 1).contains(rule));
        }
    }

    @Test
    public void retainsUnrestrictedRulesAndDoesNotActivateUnselectedRules() {
        assertEquals(Set.of("java:S8786"),
                RuleJavaVersion.eligibleRules(Set.of("java:S8786"), 25));
        assertEquals(Set.of(), RuleJavaVersion.eligibleRules(Set.of(), 25));
    }

    @Test
    public void normalizesLegacyJavaVersions() {
        assertEquals(8, RuleJavaVersion.parse("1.8"));
        assertEquals(8, RuleJavaVersion.parse("8"));
        assertEquals(25, RuleJavaVersion.parse("25"));
    }
}
