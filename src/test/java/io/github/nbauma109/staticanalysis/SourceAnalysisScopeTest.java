package io.github.nbauma109.staticanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAnalysisScopeTest {

    @TempDir
    Path project;

    @Test
    void excludesConfiguredOutputButKeepsSimilarlyNamedHandwrittenRoots() {
        SourceAnalysisScope scope = new SourceAnalysisScope(project, Path.of("custom-output"));
        assertFalse(scope.includes(Path.of("custom-output/generated/Main.java")));
        assertFalse(scope.includes(project.resolve("custom-output/../custom-output/Test.java")));
        assertTrue(scope.includes(Path.of("src/generated/Main.java")));
        assertTrue(scope.includes(Path.of("custom-output-extra/Main.java")));
        assertEquals(List.of("src/main/java", "src/extra/java"),
                scope.sourceRoots(List.of("src/main/java", "custom-output/generated", "src/extra/java")));
    }

    @Test
    void excludesNestedBuildOutputEvenWhenSourceRootContainsIt() throws IOException {
        Path handwritten = createSource("sources/example/Handwritten.java");
        createSource("sources/output/generated/Generated.java");
        SourceAnalysisScope scope = new SourceAnalysisScope(project, Path.of("sources/output"));
        assertEquals(List.of(handwritten), scope.javaFiles(List.of("sources", "sources/output/generated")));
    }

    private Path createSource(String name) throws IOException {
        Path path = project.resolve(name);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "package example;");
    }
}
