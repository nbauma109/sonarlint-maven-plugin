package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.SourceSet;

final class GradleSupplementalAnalysis {

    private GradleSupplementalAnalysis() {
    }

    static List<SupplementalAnalyzer.Finding> analyze(Project project, SourceSet sourceSet,
            Path directory, List<Path> sources, Set<String> rules) throws IOException {
        if (sources.isEmpty() || rules.isEmpty()) {
            return List.of();
        }
        List<String> classpath = new ArrayList<>();
        for (File file : sourceSet.getCompileClasspath().getFiles()) {
            classpath.add(file.getAbsolutePath());
        }
        for (File file : sourceSet.getOutput().getClassesDirs().getFiles()) {
            classpath.add(file.getAbsolutePath());
        }
        return SupplementalAnalyzer.analyze(directory, sources, classpath,
                GradleSourceVersion.release(project, sourceSet), rules);
    }

    static void report(Logger logger, List<SupplementalAnalyzer.Finding> findings) {
        List<SupplementalAnalyzer.Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparing(SupplementalAnalyzer.Finding::file)
                .thenComparingLong(SupplementalAnalyzer.Finding::line)
                .thenComparing(SupplementalAnalyzer.Finding::rule));
        for (SupplementalAnalyzer.Finding finding : sorted) {
            logger.warn(finding.file() + ":" + finding.line() + " [" + finding.rule()
                    + "] " + finding.message());
        }
    }
}
