package io.github.nbauma109.staticanalysis;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;

final class NativeTestAnalysis {

    private NativeTestAnalysis() {
    }

    static List<Issue> analyze(Path project, Path work, Path analyzer, Path symbolicAnalyzer,
            List<Path> sources, Charset encoding, Map<String, String> properties,
            List<ActiveRule> rules, LogOutput logger) throws IOException {
        if (sources.isEmpty() || rules.isEmpty()) {
            return List.of();
        }
        List<JavaSourceFile> files = new ArrayList<>();
        for (Path source : sources) {
            files.add(new JavaSourceFile(project, source, encoding, true));
        }
        AnalysisConfiguration configuration = AnalysisConfiguration.builder()
                .setBaseDir(project)
                .addInputFiles(files)
                .putAllExtraProperties(properties)
                .addActiveRules(rules)
                .build();
        StandaloneAnalyzer engine = new StandaloneAnalyzer(analyzer, work, logger);
        if (symbolicAnalyzer != null) {
            engine = new StandaloneAnalyzer(analyzer, symbolicAnalyzer, work, logger);
        }
        return engine.analyze(configuration);
    }

    static String location(Issue issue) {
        String file = "<project>";
        if (issue.getInputFile() != null) {
            file = issue.getInputFile().relativePath();
        }
        if (issue.getTextRange() != null) {
            return file + ":" + issue.getTextRange().getStartLine();
        }
        return file;
    }
}
