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

class NativeNullGuardGradlePluginTest {

    @TempDir
    Path projectDirectory;

    @Test
    void retainsUnsafeDereferencesButNotProvenGuards() throws IOException {
        Path fixture = Path.of(System.getProperty("staticAnalysis.versionFixtureDirectory"),
                "null-predicate", "src/main/java/example");
        Path sources = projectDirectory.resolve("src/main/java/example");
        Files.createDirectories(sources);
        for (String name : new String[] {"Predicates.java", "PredicateCases.java"}) {
            Files.copy(fixture.resolve(name), sources.resolve(name));
        }
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'null-predicate'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.nbauma109.static-analysis'
                }
                repositories { mavenCentral() }
                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                staticAnalysis {
                    profile = 'reliability'
                }
                """);
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("staticAnalysis", "--stacktrace")
                .withPluginClasspath()
                .build();
        String output = result.getOutput();
        assertTrue(output.contains("PredicateCases.java:42 [java:S2259]"), output);
        assertTrue(output.contains("PredicateCases.java:57 [java:S2259]"), output);
        for (int line : new int[] {18, 26, 36}) {
            assertFalse(output.contains("PredicateCases.java:" + line + " [java:S2259]"), output);
        }
    }
}
