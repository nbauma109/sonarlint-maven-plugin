package io.github.nbauma109.staticanalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaVersionGradlePluginTest {

    @TempDir
    Path projectDirectory;

    @ParameterizedTest
    @ValueSource(ints = {17, 21, 22, 25})
    void activatesSupplementalRulesOnlyAtSupportedTestReleases(int release) throws IOException {
        assumeTrue(Runtime.version().feature() >= release);
        prepare("tasks.named('compileTestJava') { options.release = " + release + " }");
        BuildResult result = run();
        assertTrue(result.getOutput().contains("[java:S8786]"), result.getOutput());
        if (release >= 22) {
            assertTrue(result.getOutput().contains("[java:S7467]"), result.getOutput());
            assertTrue(result.getOutput().contains("Test-source Java analysis: 3 violation(s)."),
                    result.getOutput());
        } else {
            assertFalse(result.getOutput().contains("[java:S7467]"), result.getOutput());
            assertTrue(result.getOutput().contains("Test-source Java analysis: 2 violation(s)."),
                    result.getOutput());
        }
    }

    @Test
    void usesSourceCompatibilityWhenReleaseIsNotConfigured() throws IOException {
        prepare("");
        BuildResult result = run();
        assertFalse(result.getOutput().contains("[java:S7467]"), result.getOutput());
        assertTrue(result.getOutput().contains("[java:S8786]"), result.getOutput());
    }

    private void prepare(String compilerConfiguration) throws IOException {
        Path fixture = Path.of(System.getProperty("staticAnalysis.versionFixtureDirectory"),
                "modern-test-rules/src/test/java/example/ModernTestIssues.java");
        Path source = projectDirectory.resolve("src/test/java/example/ModernTestIssues.java");
        Files.createDirectories(source.getParent());
        Files.copy(fixture, source);
        Files.writeString(projectDirectory.resolve("settings.gradle"),
                "rootProject.name = 'java-version-fixture'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.nbauma109.static-analysis'
                }
                repositories {
                    mavenCentral()
                }
                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                staticAnalysis {
                    profile = 'none'
                    enabledRules = 'java:S7467,java:S8786'
                    failOnViolation = false
                }
                %s
                """.formatted(compilerConfiguration));
    }

    private BuildResult run() {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("staticAnalysisTests", "--stacktrace")
                .withPluginClasspath()
                .build();
    }
}
