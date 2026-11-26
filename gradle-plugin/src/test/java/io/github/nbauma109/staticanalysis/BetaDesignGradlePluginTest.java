package io.github.nbauma109.staticanalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BetaDesignGradlePluginTest {

    @TempDir
    Path projectDirectory;

    @Test
    void enablesNativeBetaRulesOnlyOnRequest() throws IOException {
        Path fixture = Path.of(System.getProperty("staticAnalysis.versionFixtureDirectory"), "beta-design");
        Path source = projectDirectory.resolve("src/main/java/example");
        Files.createDirectories(source);
        for (String name : new String[] {"CoupledClass.java", "BrainExample.java", "SingletonExample.java"}) {
            Files.copy(fixture.resolve("src/main/java/example").resolve(name), source.resolve(name));
        }
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'beta-design'\n");
        String stable = run("sonar-way", "");
        String beta = run("sonar-way-beta", "");
        String disabled = run("sonar-way-beta", "java:S6548");
        for (String key : new String[] {"S6539", "S6541", "S6548"}) {
            assertFalse(stable.contains("[java:" + key + "]"), stable);
            assertTrue(beta.contains("[java:" + key + "]"), beta);
        }
        assertFalse(disabled.contains("[java:S6548]"), disabled);
    }

    private String run(String profile, String disabledRules) throws IOException {
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
                    profile = '%s'
                    disabledRules = '%s'
                    failOnViolation = false
                }
                """.formatted(profile, disabledRules));
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("staticAnalysis", "--stacktrace", "--rerun-tasks")
                .withPluginClasspath()
                .build().getOutput();
    }
}
