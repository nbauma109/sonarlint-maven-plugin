package io.github.nbauma109.staticanalysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SourceAnalysisScopeTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void excludesConfiguredOutputButKeepsSimilarlyNamedHandwrittenRoots() {
        Path project = temporaryFolder.getRoot().toPath();
        SourceAnalysisScope scope = new SourceAnalysisScope(project, Path.of("custom-output"));
        assertFalse(scope.includes(Path.of("custom-output/generated/Main.java")));
        assertFalse(scope.includes(project.resolve("custom-output/../custom-output/Test.java")));
        assertTrue(scope.includes(Path.of("src/generated/Main.java")));
        assertTrue(scope.includes(Path.of("custom-output-extra/Main.java")));
        assertEquals(List.of("src/main/java", "src/extra/java"),
                scope.sourceRoots(List.of("src/main/java", "custom-output/generated", "src/extra/java")));
    }

    @Test
    public void excludesNestedBuildOutputEvenWhenSourceRootContainsIt() throws IOException {
        Path project = temporaryFolder.getRoot().toPath();
        Path handwritten = createSource("sources/example/Handwritten.java");
        createSource("sources/output/generated/Generated.java");
        SourceAnalysisScope scope = new SourceAnalysisScope(project, Path.of("sources/output"));
        assertEquals(List.of(handwritten), scope.javaFiles(List.of("sources", "sources/output/generated")));
    }

    private Path createSource(String name) throws IOException {
        Path path = temporaryFolder.getRoot().toPath().resolve(name);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "package example;");
    }
}
