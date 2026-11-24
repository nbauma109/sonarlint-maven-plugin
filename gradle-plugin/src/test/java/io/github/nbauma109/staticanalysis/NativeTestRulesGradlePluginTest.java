package io.github.nbauma109.staticanalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeTestRulesGradlePluginTest {

    @TempDir
    Path projectDirectory;

    @Test
    void executesNativeTestRules() throws IOException {
        Path fixture = Path.of(System.getProperty("staticAnalysis.versionFixtureDirectory"),
                "native-test-rules");
        for (String name : new String[] {"src/main/java/example/Value.java",
                "src/test/java/example/NativeTestIssues.java"}) {
            Path target = projectDirectory.resolve(name);
            Files.createDirectories(target.getParent());
            Files.copy(fixture.resolve(name), target);
        }
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'native-test-rules'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'io.github.nbauma109.static-analysis'
                }
                repositories { mavenCentral() }
                dependencies { testImplementation 'junit:junit:4.13.2' }
                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                staticAnalysis {
                    profile = 'none'
                    enabledRules = 'java:S1130,java:S5961'
                }
                """);
        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments("staticAnalysisTests", "--stacktrace")
                .withPluginClasspath()
                .build();
        String output = result.getOutput();
        assertTrue(output.contains("[java:S1130]"), output);
        assertTrue(output.contains("[java:S5961]"), output);
        assertTrue(output.contains("Native test-source Java analysis: 2 violation(s)."), output);
    }
}
