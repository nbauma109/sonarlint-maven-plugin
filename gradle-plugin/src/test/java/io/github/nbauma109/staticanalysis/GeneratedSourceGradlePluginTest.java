package io.github.nbauma109.staticanalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedSourceGradlePluginTest {

    @TempDir
    Path projectDirectory;

    @Test
    void excludesGeneratedMainAndTestSourcesInCustomBuildDirectory() throws IOException {
        copy("src/main/java/example/HandwrittenMain.java", "src/main/java/example/HandwrittenMain.java");
        copy("src/extra/java/example/AdditionalMain.java", "src/extra/java/example/AdditionalMain.java");
        copy("src/test/java/example/HandwrittenTest.java", "src/test/java/example/HandwrittenTest.java");
        copy("inputs/main/example/GeneratedMain.java", "custom-output/generated/main/example/GeneratedMain.java");
        copy("inputs/test/example/GeneratedTest.java", "custom-output/generated/test/example/GeneratedTest.java");
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'generated-fixture'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.nbauma109.static-analysis'
                }
                repositories { mavenCentral() }
                layout.buildDirectory = file('custom-output')
                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                sourceSets {
                    main.java.srcDirs += ['src/extra/java', 'custom-output/generated/main']
                    test.java.srcDir 'custom-output/generated/test'
                }
                staticAnalysis {
                    profile = 'none'
                    enabledRules = 'java:S106,java:S9438,java:S8786'
                    failOnViolation = false
                }
                """);

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("staticAnalysis", "staticAnalysisTests", "--stacktrace")
                .withPluginClasspath()
                .build();

        String output = result.getOutput();
        assertTrue(Files.isRegularFile(projectDirectory.resolve("custom-output/classes/java/main/example/GeneratedMain.class")));
        assertTrue(Files.isRegularFile(projectDirectory.resolve("custom-output/classes/java/test/example/GeneratedTest.class")));
        assertTrue(output.contains("HandwrittenMain.java:"), output);
        assertTrue(output.contains("AdditionalMain.java:"), output);
        assertTrue(output.contains("HandwrittenTest.java:"), output);
        assertTrue(output.contains("[java:S106]"), output);
        assertTrue(output.contains("[java:S9438]"), output);
        assertTrue(output.contains("Test-source Java analysis: 1 violation(s)."), output);
        assertFalse(output.contains("GeneratedMain.java:"), output);
        assertFalse(output.contains("GeneratedTest.java:"), output);
    }

    private void copy(String input, String destination) throws IOException {
        Path original = Path.of(System.getProperty("staticAnalysis.versionFixtureDirectory"),
                "generated-sources", input);
        Path target = projectDirectory.resolve(destination);
        Files.createDirectories(target.getParent());
        Files.copy(original, target);
    }
}
