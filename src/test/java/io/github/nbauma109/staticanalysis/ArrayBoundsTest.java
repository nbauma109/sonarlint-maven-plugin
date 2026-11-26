package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ArrayBoundsTest {

    @Test
    public void detectsProvenOutOfBoundsAccessesOnly() throws Exception {
        Path fixture = Path.of("src/it/array-bounds").toAbsolutePath();
        Path source = fixture.resolve("src/main/java/example/ArrayBoundsIssues.java");
        List<SupplementalAnalyzer.Finding> findings = SupplementalAnalyzer.analyze(
                fixture, List.of(source), List.of(), "17", Set.of("java:S6466"));
        List<Long> lines = new ArrayList<>();
        for (SupplementalAnalyzer.Finding finding : findings) {
            assertEquals("java:S6466", finding.rule());
            lines.add(finding.line());
        }
        assertEquals(List.of(7L, 12L, 16L, 22L), lines);
    }
}
