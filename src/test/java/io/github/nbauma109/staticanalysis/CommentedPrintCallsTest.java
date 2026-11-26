package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class CommentedPrintCallsTest {

    @Test
    public void detectsConsoleCallsInCommentsWithoutConfusingLiteralsOrDocumentation() throws Exception {
        Path fixture = Path.of("src/it/commented-print").toAbsolutePath();
        Path source = fixture.resolve("src/test/java/example/CommentedPrintIssues.java");
        List<SupplementalAnalyzer.Finding> findings = SupplementalAnalyzer.analyze(
                fixture, List.of(source), List.of(), "17", Set.of("java:S125"));
        assertEquals(2, findings.size());
        assertEquals(9L, findings.get(0).line());
        assertEquals(14L, findings.get(1).line());
        assertEquals(List.of(findings.get(1)), SupplementalAnalyzer.withoutNativeDuplicates(findings,
                List.of("src/test/java/example/CommentedPrintIssues.java:8:java:S125")));
    }

    @Test
    public void preservesNativeRuleSelectionAndExplicitDisabling() {
        assertTrue(RuleCatalog.supplementalRules("sonar-way", "", "").contains("java:S125"));
        assertTrue(RuleCatalog.testRules("sonar-way", "", "").contains("java:S125"));
        assertTrue(RuleCatalog.supplementalRules("none", "java:S125", "").contains("java:S125"));
        assertFalse(RuleCatalog.supplementalRules("sonar-way", "", "java:S125").contains("java:S125"));
        assertFalse(RuleCatalog.testRules("sonar-way", "", "java:S125").contains("java:S125"));
    }
}
