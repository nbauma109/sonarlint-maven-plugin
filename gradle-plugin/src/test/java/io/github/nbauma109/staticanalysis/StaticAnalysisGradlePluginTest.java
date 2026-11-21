package io.github.nbauma109.staticanalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticAnalysisGradlePluginTest {

    @TempDir
    Path projectDirectory;

    @Test
    void reportsCognitiveComplexityWithoutChangingSources() throws IOException {
        prepare("complexity", false, 15);
        Path source = projectDirectory.resolve("src/main/java/example/ComplexMethod.java");
        String before = Files.readString(source, StandardCharsets.UTF_8);
        BuildResult result = run("staticAnalysis");
        assertTrue(result.getOutput().contains("[java:S3776]"), result.getOutput());
        assertTrue(result.getOutput().contains("Sonar analysis: 1 violation(s)."), result.getOutput());
        assertTrue(before.equals(Files.readString(source, StandardCharsets.UTF_8)));
    }

    @Test
    void respectsThresholdAndBuildFailurePolicy() throws IOException {
        prepare("complexity", true, 100);
        BuildResult clean = run("staticAnalysis");
        assertTrue(clean.getOutput().contains("Sonar analysis: 0 violation(s)."), clean.getOutput());

        Files.writeString(projectDirectory.resolve("build.gradle"), configuration("complexity", true, 15),
                StandardCharsets.UTF_8);
        BuildResult failed = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("staticAnalysis", "--offline", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail();
        assertTrue(failed.getOutput().contains("Static analysis found 1 violation(s)."),
                failed.getOutput());
    }

    @Test
    void rejectsServerOnlyRules() throws IOException {
        prepare("none", false, 15);
        Files.writeString(projectDirectory.resolve("build.gradle"), configuration("none", false, 15)
                + "\nstaticAnalysis.enabledRules = 'javasecurity:S3649'\n", StandardCharsets.UTF_8);
        BuildResult failed = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("staticAnalysis", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail();
        assertTrue(failed.getOutput().contains("not an eligible standard production Java rule"),
                failed.getOutput());
    }

    private void prepare(String profile, boolean failOnViolation, int threshold) throws IOException {
        Path original = Path.of(System.getProperty("staticAnalysis.fixtureDirectory"),
                "example", "ComplexMethod.java");
        Path source = projectDirectory.resolve("src/main/java/example/ComplexMethod.java");
        Files.createDirectories(source.getParent());
        Files.copy(original, source);
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'fixture'\n",
                StandardCharsets.UTF_8);
        Files.writeString(projectDirectory.resolve("build.gradle"),
                configuration(profile, failOnViolation, threshold), StandardCharsets.UTF_8);
    }

    private static String configuration(String profile, boolean failOnViolation, int threshold) {
        return """
                plugins {
                    id 'java'
                    id 'io.github.nbauma109.static-analysis'
                }
                repositories {
                    mavenCentral()
                }
                java {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                }
                staticAnalysis {
                    profile = '%s'
                    failOnViolation = %s
                    cognitiveComplexityThreshold = %d
                }
                """.formatted(profile, failOnViolation, threshold);
    }

    private BuildResult run(String task) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments(task, "--stacktrace")
                .withPluginClasspath()
                .build();
    }
}
