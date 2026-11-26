package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class CommentedJavaStatementsTest {

    @Test
    public void recognizesGeneralJavaStatementsWithoutTreatingProseAsCode() throws Exception {
        Path fixture = Path.of("src/it/commented-java").toAbsolutePath();
        Path source = fixture.resolve("src/test/java/example/CommentedJavaIssues.java");
        List<SupplementalAnalyzer.Finding> findings = SupplementalAnalyzer.analyze(
                fixture, List.of(source), List.of(), "17", Set.of("java:S125"));
        assertEquals(List.of(8L, 14L, 18L, 22L), findings.stream()
                .map(SupplementalAnalyzer.Finding::line).toList());
        assertEquals(findings.subList(1, 4), SupplementalAnalyzer.withoutNativeDuplicates(findings,
                List.of("src/test/java/example/CommentedJavaIssues.java:9:java:S125")));
    }

    @Test
    public void respectsProfileAndExplicitRuleSelection() {
        assertTrue(RuleCatalog.supplementalRules("sonar-way", "", "").contains("java:S125"));
        assertTrue(RuleCatalog.testRules("sonar-way", "", "").contains("java:S125"));
        assertTrue(RuleCatalog.supplementalRules("none", "java:S125", "").contains("java:S125"));
        assertFalse(RuleCatalog.supplementalRules("sonar-way", "", "java:S125").contains("java:S125"));
        assertFalse(RuleCatalog.testRules("sonar-way", "", "java:S125").contains("java:S125"));
    }
}
