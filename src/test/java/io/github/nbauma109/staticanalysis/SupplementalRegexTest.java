package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class SupplementalRegexTest {

    @Test
    public void detectsRepeatedWhitespaceClassesWithoutReportingGuardedExpressions() throws Exception {
        Path fixture = Path.of("src/it/modern-test-rules").toAbsolutePath();
        Path source = fixture.resolve("src/test/java/example/WhitespaceRegexIssues.java");
        List<SupplementalAnalyzer.Finding> findings = SupplementalAnalyzer.analyze(
                fixture, List.of(source), List.of(), "17", Set.of("java:S8786"));
        List<Long> lines = new ArrayList<>();
        for (SupplementalAnalyzer.Finding finding : findings) {
            assertEquals("java:S8786", finding.rule());
            lines.add(finding.line());
        }
        assertEquals(List.of(9L, 9L, 13L, 17L), lines);
    }
    @Test
    public void detectsWhitespaceBeforeRequiredEscapedDelimiters() throws Exception {
        Path fixture = Path.of("src/it/regex-delimiters").toAbsolutePath();
        Path source = fixture.resolve("src/test/java/example/DelimiterRegexIssues.java");
        List<SupplementalAnalyzer.Finding> findings = SupplementalAnalyzer.analyze(
                fixture, List.of(source), List.of(), "17", Set.of("java:S8786"));
        assertEquals(2, findings.size());
        assertEquals(5L, findings.get(0).line());
        assertEquals(9L, findings.get(1).line());
    }

    @Test
    public void preservesDistinctOccurrencesOnTheSameLine() {
        SupplementalAnalyzer.Finding first = new SupplementalAnalyzer.Finding(
                "Example.java", 9, "java:S8786", "First occurrence");
        SupplementalAnalyzer.Finding second = new SupplementalAnalyzer.Finding(
                "Example.java", 9, "java:S8786", "Second occurrence");
        List<SupplementalAnalyzer.Finding> findings = List.of(first, second);
        String nativeLocation = "Example.java:9:java:S8786";
        assertEquals(findings, SupplementalAnalyzer.withoutNativeDuplicates(findings, List.of()));
        assertEquals(List.of(second), SupplementalAnalyzer.withoutNativeDuplicates(
                findings, List.of(nativeLocation)));
        assertEquals(List.of(), SupplementalAnalyzer.withoutNativeDuplicates(
                findings, List.of(nativeLocation, nativeLocation)));
    }
}
