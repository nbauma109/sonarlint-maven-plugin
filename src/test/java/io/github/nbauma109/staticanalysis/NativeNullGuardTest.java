package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

public class NativeNullGuardTest {

    @Test
    public void retainsUnsafeAndUnprovenDereferences() throws Exception {
        Path fixture = Path.of("src/it/null-predicate").toAbsolutePath();
        Path source = fixture.resolve("src/main/java/example/PredicateCases.java");
        JavaSourceFile input = new JavaSourceFile(fixture, source, StandardCharsets.UTF_8);
        JavaSourceFile predicates = new JavaSourceFile(fixture,
                fixture.resolve("src/main/java/example/Predicates.java"), StandardCharsets.UTF_8);
        AnalysisConfiguration configuration = AnalysisConfiguration.builder()
                .setBaseDir(fixture).addInputFiles(input, predicates)
                .putExtraProperty("sonar.java.source", "17").build();
        List<Issue> issues = new ArrayList<>();
        List<String> lines = Files.readAllLines(source);
        for (int index = 0; index < lines.size(); index++) {
            int start = lines.get(index).indexOf("value.length");
            if (start >= 0) {
                int line = index + 1;
                TextRange range = new TextRange(line, start, line, start + "value.length".length());
                issues.add(new Issue("java:S2259", "A \"NullPointerException\" could be thrown; \"value\" is nullable here.", Map.of(), range,
                        input, List.of(), List.of(), Optional.empty()));
            }
        }
        assertEquals(7, issues.size());
        assertEquals(issues.subList(3, 7), NativeNullGuard.retainUnproven(configuration, issues,
                new MavenLogOutput(new SystemStreamLog())));
    }
}
